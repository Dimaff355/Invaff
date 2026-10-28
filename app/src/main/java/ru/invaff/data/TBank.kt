package ru.invaff.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** Т-Инвестиции, REST-шлюз gRPC API: https://developer.tbank.ru/invest/api */
class TBank(private val token: String) : Broker {
    override val title = "Т-Банк"

    private class Info(val name: String, val color: Int?, val logo: String?, val isin: String)

    private val info = ConcurrentHashMap<String, Info>()

    private suspend fun rpc(method: String, body: JSONObject) =
        JSONObject(Http.call("$BASE.$method", body.toString(), "Bearer $token"))

    private suspend fun accounts() = rpc("UsersService/GetAccounts", JSONObject()).optJSONArray("accounts").objects()
        .filter { it.optString("status") == "ACCOUNT_STATUS_OPEN" }
        .map { it.getString("id") }

    override suspend fun positions(fx: Fx) = coroutineScope {
        accounts()
            .map { id -> async { runCatching { rpc("OperationsService/GetPortfolio", JSONObject().put("accountId", id).put("currency", "RUB")) }.getOrNull() } }
            .awaitAll().filterNotNull()
            .flatMap { it.optJSONArray("positions").objects() }
            .map { async { position(it, fx) } }
            .awaitAll()
    }

    private suspend fun position(p: JSONObject, fx: Fx): Position {
        val uid = p.getString("instrumentUid")
        val i = info.getOrPut(uid) { lookup(uid) }
        val currency = p.getJSONObject("currentPrice").optString("currency")
        val rate = fx[currency]
        val qty = p.money("quantity")
        val price = p.money("currentPrice")
        return Position(
            ticker = p.optString("ticker"),
            name = i.name,
            kind = kind(p.optString("instrumentType")) ?: Kind.Other,
            qty = qty,
            price = price,
            currency = currency.uppercase(),
            value = qty * (price + p.money("currentNkd")) * rate,
            profit = p.money("expectedYield") * rate,
            day = p.money("dailyYield") * rate,
            refs = mapOf(title to uid),
            color = i.color,
            logo = i.logo,
            isin = i.isin,
        )
    }

    private suspend fun lookup(uid: String) = runCatching {
        val i = rpc("InstrumentsService/GetInstrumentBy", JSONObject().put("idType", "INSTRUMENT_ID_TYPE_UID").put("id", uid))
            .getJSONObject("instrument")
        val brand = i.optJSONObject("brand")
        Info(
            i.optString("name"),
            brand?.optString("logoBaseColor")?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() },
            brand?.optString("logoName")?.let(Logos::byName) ?: Logos.byIsin(i.optString("isin")),
            i.optString("isin"),
        )
    }.getOrDefault(Info("", null, null, ""))

    /** Годовые интервалы по каждому счёту — параллельно; внутри интервала — постранично по курсору. */
    override suspend fun operations(from: Instant, fx: Fx) = coroutineScope {
        val now = Instant.now()
        val years = generateSequence(from) { it.plus(365, ChronoUnit.DAYS) }.takeWhile { it < now }.toList()
        val items = accounts().flatMap { a -> years.map { a to it } }
            .map { (a, start) -> async { page(a, start, minOf(start.plus(365, ChronoUnit.DAYS), now)) } }
            .awaitAll().flatten()
        // Логотипы и для уже проданных бумаг: по одному запросу на инструмент.
        val logos = items.map { it.optString("instrumentUid") }.filter { it.isNotEmpty() }.distinct()
            .map { uid -> async { uid to info.getOrPut(uid) { lookup(uid) }.logo } }.awaitAll().toMap()
        items.map {
            val payment = it.optJSONObject("payment")
            val currency = payment?.optString("currency").orEmpty().uppercase().ifEmpty { "RUB" }
            val amount = it.money("payment")
            Operation(
                date = Instant.parse(it.getString("date")),
                type = type(it.optString("type")),
                ticker = it.optString("ticker"),
                name = it.optString("name").ifEmpty { it.optString("description") },
                kind = kind(it.optString("instrumentType")),
                qty = it.optString("quantityDone").toDoubleOrNull() ?: 0.0,
                amount = amount,
                currency = currency,
                rub = amount * fx[currency],
                broker = title,
                logo = logos[it.optString("instrumentUid")],
            )
        }
    }

    private suspend fun page(account: String, from: Instant, to: Instant): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var cursor = ""
        do {
            val r = rpc(
                "OperationsService/GetOperationsByCursor", JSONObject()
                    .put("accountId", account).put("from", from.toString()).put("to", to.toString())
                    .put("cursor", cursor).put("limit", 1000).put("state", "OPERATION_STATE_EXECUTED")
                    .put("withoutTrades", true).put("withoutOvernights", true)
            )
            items += r.optJSONArray("items").objects()
            cursor = r.optString("nextCursor")
        } while (r.optBoolean("hasNext"))
        return items
    }

    private fun kind(instrumentType: String) = when (instrumentType) {
        "share" -> Kind.Share
        "bond" -> Kind.Bond
        "etf" -> Kind.Fund
        "currency" -> Kind.Currency
        "" -> null
        else -> Kind.Other
    }

    private fun type(t: String) = when (t.removePrefix("OPERATION_TYPE_")) {
        "BUY", "BUY_CARD", "BUY_MARGIN", "DELIVERY_BUY" -> OpType.Buy
        "SELL", "SELL_CARD", "SELL_MARGIN", "DELIVERY_SELL" -> OpType.Sell
        "DIVIDEND", "DIV_EXT" -> OpType.Dividend
        "COUPON" -> OpType.Coupon
        "BOND_REPAYMENT", "BOND_REPAYMENT_FULL" -> OpType.Repayment
        "INPUT", "INPUT_SECURITIES", "INPUT_SWIFT", "INPUT_ACQUIRING", "INP_MULTI" -> OpType.Deposit
        "OUTPUT", "OUTPUT_SECURITIES", "OUTPUT_SWIFT", "OUTPUT_ACQUIRING", "OUT_MULTI" -> OpType.Withdraw
        else -> when {
            "TAX" in t -> OpType.Tax
            "FEE" in t || "COMMISSION" in t -> OpType.Fee
            else -> OpType.Other
        }
    }

    override suspend fun candles(ref: String, from: Instant) = rpc(
        "MarketDataService/GetCandles", JSONObject()
            .put("instrumentId", ref).put("from", from.toString()).put("to", Instant.now().toString())
            .put("interval", "CANDLE_INTERVAL_DAY")
    ).optJSONArray("candles").objects().map { Point(Instant.parse(it.getString("time")).epochSecond, it.money("close")) }

    private companion object {
        const val BASE = "https://invest-public-api.tbank.ru/rest/tinkoff.public.invest.api.contract.v1"
    }
}
