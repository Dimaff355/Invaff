package ru.invaff.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.invaff.Ui
import ru.invaff.data.OpType
import ru.invaff.data.Operation
import ru.invaff.data.Plan
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Период в месяцах (0 — вся загруженная история) и подпись к сумме. */
private val incomePeriods = listOf(
    Triple("3М", 3, "за 3 месяца"), Triple("6М", 6, "за 6 месяцев"), Triple("1Г", 12, "за 12 месяцев"),
    Triple("3Г", 36, "за 3 года"), Triple("Всё", 0, "за всё время"),
)

@Composable
fun IncomeScreen(ui: Ui, plan: Plan) {
    var period by rememberSaveable { mutableIntStateOf(2) }
    val zone = ZoneId.systemDefault()
    val now = YearMonth.now()
    fun month(p: Operation) = YearMonth.from(p.date.atZone(zone))
    fun title(m: YearMonth) = date(m.atDay(1).atStartOfDay(zone).toInstant(), "LLLL yyyy")
    val (_, length, caption) = incomePeriods[period]
    val months = length.takeIf { it > 0 }
        ?: ui.payments.lastOrNull()?.let { ChronoUnit.MONTHS.between(month(it), now).toInt() + 1 } ?: 1
    val start = now.minusMonths(months - 1L)
    val shown = ui.payments.filter { !month(it).isBefore(start) }
    val byMonth = shown.groupBy(::month)
    val total = shown.sumOf { it.rub }
    val dividends = shown.filter { it.type == OpType.Dividend }.sumOf { it.rub }
    // До года — столбик на месяц, дальше — на год.
    val bars = if (months <= 12) (0 until months).map { start.plusMonths(it.toLong()) }.map { m -> title(m) to byMonth[m].orEmpty().sumOf { it.rub } }
    else (start.year..now.year).map { y -> "$y" to shown.filter { month(it).year == y }.sumOf { it.rub } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Title("Доходы")
            Text("Пассивный доход $caption", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = Up)
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("В месяц", money(total / months), Modifier.weight(1f))
                Stat("Дивиденды", money(dividends), Modifier.weight(1f))
                Stat("Купоны", money(total - dividends), Modifier.weight(1f))
            }
            Bars(bars, ::money, tick = if (months <= 12) ({ it.take(1) }) else if (bars.size <= 6) ({ it }) else ({ it.takeLast(2) }))
            Spacer(Modifier.height(12.dp))
            Chips(incomePeriods.map { it.first }, period) { period = it }
            if (plan.fire) Fire(ui, plan)
        }
        byMonth.keys.sortedDescending().forEach { m ->
            item(key = m.toString()) { Section(title(m).replaceFirstChar { it.uppercase() }) }
            items(byMonth.getValue(m)) { OperationRow(it) }
        }
    }
}

/** Потенциальный доход: выплаты за 12 месяцев + ежегодная продажа rate% выбранной части капитала. */
@Composable
private fun Fire(ui: Ui, plan: Plan) {
    val sell = ui.sell(plan)
    val year = ui.passive(plan)
    Section("Потенциально, ничего не делая")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("В год", money(year), Modifier.weight(1f), Up)
        Stat("В месяц", money(year / 12), Modifier.weight(1f), Up)
    }
    Text(
        "Дивиденды и купоны за 12 мес. ${money(ui.income)} + продажа ${share(plan.rate)} в год " +
            "(${plan.scope.part} ${money(plan.sellable(ui.positions))}) ${money(sell)}",
        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
