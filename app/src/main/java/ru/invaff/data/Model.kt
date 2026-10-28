package ru.invaff.data

import java.time.Instant

enum class Kind(val title: String) { Share("Акции"), Bond("Облигации"), Fund("Фонды"), Currency("Валюта"), Other("Другое") }

/** Позиция; все суммы (value, profit, day) — в рублях, price — в валюте инструмента. */
data class Position(
    val ticker: String,
    val name: String,
    val kind: Kind,
    val qty: Double,
    val price: Double,
    val currency: String,
    val value: Double,
    val profit: Double,
    val day: Double,
    /** Брокер → идентификатор инструмента у него (для свечей). */
    val refs: Map<String, String>,
    val color: Int? = null,
    /** URL логотипа (см. [Logos]). */
    val logo: String? = null,
    val isin: String = "",
) {
    val key get() = "$kind:$ticker"

    /** Зарубежный актив: валюта — по тикеру (RUB000UTSTOM, RUB), бумага — по ISIN, без ISIN — по валюте цены. */
    val foreign get() = when {
        kind == Kind.Currency -> !ticker.startsWith("RUB")
        isin.isNotEmpty() -> !isin.startsWith("RU")
        else -> currency != "RUB"
    }
    val profitPct get() = if (value - profit != 0.0) profit / (value - profit) * 100 else 0.0
}

/** title — для фильтра, one — для строки операции. */
enum class OpType(val title: String, val one: String) {
    Buy("Покупки", "Покупка"), Sell("Продажи", "Продажа"), Dividend("Дивиденды", "Дивиденды"), Coupon("Купоны", "Купон"),
    Repayment("Погашения", "Погашение"), Deposit("Пополнения", "Пополнение"), Withdraw("Выводы", "Вывод"),
    Tax("Налоги", "Налог"), Fee("Комиссии", "Комиссия"), Other("Прочее", "Прочее"),
}

/** Операция по счёту: amount — со знаком, в валюте операции; rub — то же по текущему курсу ЦБ. */
data class Operation(
    val date: Instant,
    val type: OpType,
    val ticker: String,
    val name: String,
    /** Вид бумаги; null — операция без бумаги (пополнение, налог…). */
    val kind: Kind?,
    val qty: Double,
    val amount: Double,
    val currency: String,
    val rub: Double,
    val broker: String,
    val logo: String? = null,
) {
    val income get() = type == OpType.Dividend || type == OpType.Coupon
}

/** Точка графика: t — секунды epoch. */
data class Point(val t: Long, val v: Double)

interface Broker {
    val title: String
    suspend fun positions(fx: Fx): List<Position>
    suspend fun operations(from: Instant, fx: Fx): List<Operation>
    suspend fun candles(ref: String, from: Instant): List<Point>
}

/** Курсы ЦБ: рублей за единицу валюты. */
class Fx(private val rates: Map<String, Double>) {
    operator fun get(currency: String) = rates[currency.uppercase()] ?: 1.0

    companion object {
        private val re = Regex("""<CharCode>(\w{3})</CharCode>.*?<VunitRate>([\d,]+)</VunitRate>""", RegexOption.DOT_MATCHES_ALL)

        suspend fun load() = Fx(
            re.findAll(Http.call("https://www.cbr.ru/scripts/XML_daily.asp"))
                .associate { it.groupValues[1] to it.groupValues[2].replace(',', '.').toDouble() }
        )
    }
}

/** Объединяет одинаковые бумаги разных брокеров/счетов в одну позицию. */
fun merge(positions: List<Position>) = positions.groupBy { it.key }.values.map { same ->
    same.reduce { a, b ->
        a.copy(
            name = a.name.ifEmpty { b.name },
            qty = a.qty + b.qty,
            value = a.value + b.value,
            profit = a.profit + b.profit,
            day = a.day + b.day,
            refs = b.refs + a.refs,
            color = a.color ?: b.color,
            logo = a.logo ?: b.logo,
            isin = a.isin.ifEmpty { b.isin },
        )
    }
}.sortedByDescending { it.value }
