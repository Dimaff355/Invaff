package ru.invaff.data

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** Finam Trade API v1 (REST): https://tradeapi.finam.ru */
class Finam(private val secret: String) : Broker {
    override val title = "Финам"

    private val mutex = Mutex()
    private var jwt = ""
    private var jwtUntil = 0L
    private class Asset(val name: String, val kind: Kind, val logo: String?, val isin: String)

    private val assets = ConcurrentHashMap<String, Asset>()

    /** Секрет обменивается на JWT, который живёт ~15 минут. */
    private suspend fun jwt() = mutex.withLock {
        if (System.currentTimeMillis() > jwtUntil) {
            jwt = JSONObject(Http.call("$BASE/sessions", JSONObject().put("secret", secret).toString())).getString("token")
            jwtUntil = System.currentTimeMillis() + 10 * 60_000
        }
        jwt
    }

    private suspend fun get(path: String) = JSONObject(Http.call("$BASE/$path", auth = jwt()))

    private suspend fun accounts() = JSONObject(Http.call("$BASE/sessions/details", JSONObject().put("token", jwt()).toString()))
        .getJSONArray("account_ids").let { a -> List(a.length()) { a.getString(it) } }

    override suspend fun positions(fx: Fx) = coroutineScope {
        accounts().map { id ->
            async {
                val a = get("accounts/$id")
                val cash = a.optJSONArray("cash").objects().map {
                    val code = it.optString("currency_code")
                    val amount = it.optString("units", "0").toDouble() + it.optLong("nanos") / 1e9
                    Position(code, code, Kind.Currency, amount, fx[code], "RUB", amount * fx[code], 0.0, 0.0, emptyMap())
                }
                a.optJSONArray("positions").objects()
                    .filter { it.decimal("quantity") != 0.0 }
                    .map { async { position(it, id, fx) } }.awaitAll() + cash
            }
        }.awaitAll().flatten()
    }

    private suspend fun position(p: JSONObject, account: String, fx: Fx): Position {
        val symbol = p.getString("symbol")
        val asset = asset(symbol, account)
        val currency = p.optString("current_price_currency").takeIf { it.length == 3 } ?: "RUB"
        val rate = fx[currency]
        val qty = p.decimal("quantity")
        val price = p.decimal("current_price")
        return Position(
            ticker = symbol.substringBefore('@'),
            name = asset.name,
            kind = asset.kind,
            qty = qty,
            price = price,
            currency = currency,
            value = qty * price * rate,
            profit = p.decimal("unrealized_pnl") * rate,
            day = p.decimal("daily_pnl") * rate,
            refs = mapOf(title to symbol),
            logo = asset.logo,
            isin = asset.isin,
        )
    }

    private suspend fun asset(symbol: String, account: String) = assets.getOrPut(symbol) {
        runCatching {
            val a = get("assets/${Uri.encode(symbol, "@")}?account_id=$account")
            val kind = when (a.optString("type")) {
                "EQUITIES" -> Kind.Share
                "BONDS" -> Kind.Bond
                "FUNDS", "ETF" -> Kind.Fund
                "CURRENCIES" -> Kind.Currency
                else -> Kind.Other
            }
            Asset(a.optString("name"), kind, Logos.byIsin(a.optString("isin")), a.optString("isin"))
        }.getOrDefault(Asset("", Kind.Other, null, ""))
    }

    /**
     * Движения денег — /transactions, сделки — /trades. Не больше 1000 записей за запрос, поэтому по годам.
     * Истории старше ~2017–2018 нет: на такой интервал Финам отвечает 400 — /transactions «'StartTime' must be less then 'endTime'»,
     * /trades «'endTime' must be greater than seconds: …». Это пустой год, а не ошибка.
     * Параллельные запросы истории Финам отвергает HTTP 500 «Failed to get transactions» — поэтому строго по очереди
     * (10 лет ≈ 3 с) и с одним повтором.
     */
    override suspend fun operations(from: Instant, fx: Fx) = coroutineScope {
        val now = Instant.now()
        val years = generateSequence(from) { it.plus(365, ChronoUnit.DAYS) }.takeWhile { it < now }.toList()
        val raw = accounts().flatMap { id -> years.flatMap { listOf(Triple(id, it, "transactions"), Triple(id, it, "trades")) } }
            .flatMap { (id, start, what) ->
                val end = minOf(start.plus(365, ChronoUnit.DAYS), now)
                history("accounts/$id/$what?interval.start_time=${start.sec()}&interval.end_time=${end.sec()}&limit=1000")
                    .optJSONArray(what).objects().map { Triple(id, what == "trades", it) }
            }
        // Название, вид и логотип — по одному запросу на бумагу, в том числе уже проданную.
        val found = raw.filter { it.third.optString("symbol").isNotEmpty() }.distinctBy { it.third.getString("symbol") }
            .map { (id, _, t) -> async { t.getString("symbol").let { it to asset(it, id) } } }.awaitAll().toMap()
        raw.map { (_, trade, t) ->
            val symbol = t.optString("symbol")
            val a = found[symbol]
            if (trade) {
                val buy = t.optString("side") == "SIDE_BUY"
                val qty = t.decimal("size")
                val currency = t.optString("currency").ifEmpty { "RUB" }
                val amount = qty * t.decimal("price") * if (buy) -1 else 1
                Operation(
                    Instant.parse(t.getString("timestamp")), if (buy) OpType.Buy else OpType.Sell, symbol.substringBefore('@'),
                    a?.name.orEmpty(), a?.kind, qty, amount, currency, amount * fx[currency], title, a?.logo,
                )
            } else {
                val change = t.optJSONObject("change")
                val currency = change?.optString("currency_code").orEmpty().ifEmpty { "RUB" }
                val amount = t.money("change")
                val name = t.optString("transaction_name")
                val type = when (t.optString("category")) {
                    "DEPOSIT" -> OpType.Deposit
                    "WITHDRAW" -> OpType.Withdraw
                    "TAX" -> OpType.Tax
                    "COMMISSION" -> OpType.Fee
                    "INCOME" -> when {
                        symbol.isEmpty() -> OpType.Other
                        "упон" in name -> OpType.Coupon
                        "огашен" in name -> OpType.Repayment
                        else -> OpType.Dividend
                    }
                    else -> OpType.Other
                }
                // Дивиденды Финам зачисляет уже за вычетом НДФЛ (проверено на MRKP: на акцию ровно как у Т-Банка после налога),
                // купоны — до налога: НДФЛ списывается отдельно в конце года.
                Operation(
                    Instant.parse(t.getString("timestamp")), type, symbol.substringBefore('@'),
                    a?.name?.ifEmpty { null } ?: name, a?.kind, t.decimal("change_qty"), amount, currency, amount * fx[currency], title, a?.logo,
                    taxed = type == OpType.Dividend,
                )
            }
        }.distinct()
    }

    private suspend fun history(path: String, retry: Boolean = true): JSONObject = try {
        get(path)
    } catch (e: ApiError) {
        when {
            e.code == 400 && ("'StartTime'" in e.body || "'endTime'" in e.body) -> JSONObject()
            e.code == 500 && retry -> { delay(1_000); history(path, false) }
            else -> throw e
        }
    }

    override suspend fun candles(ref: String, from: Instant) =
        get("instruments/${Uri.encode(ref, "@")}/bars?timeframe=TIME_FRAME_D&interval.start_time=${from.sec()}&interval.end_time=${Instant.now().sec()}")
            .optJSONArray("bars").objects()
            .map { Point(Instant.parse(it.getString("timestamp")).epochSecond, it.decimal("close")) }

    private fun Instant.sec() = truncatedTo(ChronoUnit.SECONDS)

    private companion object {
        const val BASE = "https://api.finam.ru/v1"
    }
}
