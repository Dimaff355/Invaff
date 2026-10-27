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
) {
    val key get() = "$kind:$ticker"
    val profitPct get() = if (value - profit != 0.0) profit / (value - profit) * 100 else 0.0
}

/** Выплата (дивиденд или купон) в рублях. */
data class Payment(
    val date: Instant, val ticker: String, val name: String, val amount: Double, val coupon: Boolean, val broker: String,
    val logo: String? = null,
)

/** Точка графика: t — секунды epoch. */
data class Point(val t: Long, val v: Double)

interface Broker {
    val title: String
    suspend fun positions(fx: Fx): List<Position>
    suspend fun payments(from: Instant, fx: Fx): List<Payment>
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
        )
    }
}.sortedByDescending { it.value }
