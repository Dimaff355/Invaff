package ru.invaff.data

import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.pow

/** Какую часть капитала продавать по правилу FIRE. */
enum class Scope(val title: String, val part: String) {
    All("Всё", "весь капитал"), Russia("Россия", "российская часть"), Foreign("Зарубеж", "зарубежная часть"),
}

/**
 * Цели и допущения прогноза. Все суммы — в рублях; 0 — цель не задана.
 * monthly == null — пополнения считаются автоматически по операциям за год.
 */
data class Plan(
    val capital: Double = 0.0,
    /** Цель пассивного дохода в месяц. */
    val income: Double = 0.0,
    val monthly: Double? = null,
    /** Рост цены бумаг, % в год (без дивидендов — они реинвестируются отдельно). */
    val growth: Double = 5.0,
    /** Учитывать продажу [rate]% выбранной части капитала в год (правило 4% FIRE). */
    val fire: Boolean = false,
    val rate: Double = 4.0,
    val scope: Scope = Scope.All,
) {
    fun save(prefs: SharedPreferences) = prefs.edit().putString(
        KEY, JSONObject().put("capital", capital).put("income", income).put("monthly", monthly ?: -1.0)
            .put("growth", growth).put("fire", fire).put("rate", rate).put("scope", scope.name).toString()
    ).apply()

    /** Капитал, с которого продаётся rate% в год. */
    fun sellable(positions: List<Position>) = positions.filter {
        when (scope) {
            Scope.All -> true
            Scope.Russia -> !it.foreign
            Scope.Foreign -> it.foreign
        }
    }.sumOf { it.value }

    /**
     * Месяцев до момента, когда reached(капитал) станет true; null — не достигается за 100 лет.
     * Каждый месяц: рост цены + реинвестированные дивиденды (доходность yield) + пополнение.
     */
    fun months(capital: Double, yield: Double, monthly: Double, reached: (Double) -> Boolean): Int? {
        val g = (1 + growth / 100).pow(1.0 / 12) - 1
        var c = capital
        for (m in 0..1200) {
            if (reached(c)) return m
            c += c * (g + yield / 12) + monthly
        }
        return null
    }

    companion object {
        private const val KEY = "plan"

        fun load(prefs: SharedPreferences) = runCatching {
            val j = JSONObject(prefs.getString(KEY, null)!!)
            Plan(
                capital = j.optDouble("capital", 0.0),
                income = j.optDouble("income", 0.0),
                monthly = j.optDouble("monthly", -1.0).takeIf { it >= 0 },
                growth = j.optDouble("growth", 5.0),
                fire = j.optBoolean("fire"),
                rate = j.optDouble("rate", 4.0),
                scope = Scope.valueOf(j.optString("scope", Scope.All.name)),
            )
        }.getOrDefault(Plan())
    }
}
