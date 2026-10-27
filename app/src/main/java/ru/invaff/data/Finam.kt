package ru.invaff.data

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private class Asset(val name: String, val kind: Kind, val logo: String?)

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
            Asset(a.optString("name"), kind, Logos.byIsin(a.optString("isin")))
        }.getOrDefault(Asset("", Kind.Other, null))
    }

    /** Не больше 1000 транзакций за запрос, поэтому история запрашивается по годам. */
    override suspend fun payments(from: Instant, fx: Fx) = coroutineScope {
        val now = Instant.now()
        val years = generateSequence(from) { it.plus(365, ChronoUnit.DAYS) }.takeWhile { it < now }.toList()
        val items = accounts().flatMap { id -> years.map { id to it } }.map { (id, start) ->
            async {
                val end = minOf(start.plus(365, ChronoUnit.DAYS), now)
                get("accounts/$id/transactions?interval.start_time=${start.sec()}&interval.end_time=${end.sec()}&limit=1000")
                    .optJSONArray("transactions").objects()
                    .filter { it.optString("category") == "INCOME" && it.optString("symbol").isNotEmpty() }
                    .map { id to it }
            }
        }.awaitAll().flatten()
        // Название и логотип — по одному запросу на бумагу, в том числе уже проданную.
        val found = items.distinctBy { it.second.getString("symbol") }
            .map { (id, t) -> async { t.getString("symbol").let { it to asset(it, id) } } }.awaitAll().toMap()
        items.map { (_, t) ->
            val symbol = t.getString("symbol")
            Payment(
                date = Instant.parse(t.getString("timestamp")),
                ticker = symbol.substringBefore('@'),
                name = found[symbol]?.name.orEmpty(),
                amount = t.money("change") * fx[t.getJSONObject("change").optString("currency_code")],
                coupon = t.optString("transaction_name").contains("упон"),
                broker = title,
                logo = found[symbol]?.logo,
            )
        }.distinct()
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
