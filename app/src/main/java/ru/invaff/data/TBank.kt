package ru.invaff.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Т-Инвестиции, REST-шлюз gRPC API: https://developer.tbank.ru/invest/api */
class TBank(private val token: String) : Broker {
    override val title = "Т-Банк"

    private class Info(val name: String, val color: Int?)

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
            kind = when (p.optString("instrumentType")) {
                "share" -> Kind.Share
                "bond" -> Kind.Bond
                "etf" -> Kind.Fund
                "currency" -> Kind.Currency
                else -> Kind.Other
            },
            qty = qty,
            price = price,
            currency = currency.uppercase(),
            value = qty * (price + p.money("currentNkd")) * rate,
            profit = p.money("expectedYield") * rate,
            day = p.money("dailyYield") * rate,
            refs = mapOf(title to uid),
            color = i.color,
        )
    }

    private suspend fun lookup(uid: String) = runCatching {
        val i = rpc("InstrumentsService/GetInstrumentBy", JSONObject().put("idType", "INSTRUMENT_ID_TYPE_UID").put("id", uid))
            .getJSONObject("instrument")
        Info(i.optString("name"), i.optJSONObject("brand")?.optString("logoBaseColor")?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() })
    }.getOrDefault(Info("", null))

    override suspend fun payments(from: Instant, fx: Fx) = coroutineScope {
        accounts().map { async { operations(it, from, fx) } }.awaitAll().flatten()
    }

    private suspend fun operations(account: String, from: Instant, fx: Fx): List<Payment> {
        val out = mutableListOf<Payment>()
        var cursor = ""
        do {
            val r = rpc(
                "OperationsService/GetOperationsByCursor", JSONObject()
                    .put("accountId", account).put("from", from.toString()).put("to", Instant.now().toString())
                    .put("cursor", cursor).put("limit", 1000).put("state", "OPERATION_STATE_EXECUTED")
                    .put("operationTypes", JSONArray(listOf("OPERATION_TYPE_DIVIDEND", "OPERATION_TYPE_COUPON")))
                    .put("withoutCommissions", true).put("withoutTrades", true).put("withoutOvernights", true)
            )
            r.optJSONArray("items").objects().mapTo(out) {
                Payment(
                    date = Instant.parse(it.getString("date")),
                    ticker = it.optString("ticker"),
                    name = it.optString("name"),
                    amount = it.money("payment") * fx[it.getJSONObject("payment").optString("currency")],
                    coupon = it.optString("type") == "OPERATION_TYPE_COUPON",
                    broker = title,
                )
            }
            cursor = r.optString("nextCursor")
        } while (r.optBoolean("hasNext"))
        return out
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
