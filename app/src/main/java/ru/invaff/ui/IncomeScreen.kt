package ru.invaff.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.invaff.Ui
import ru.invaff.data.Payment
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Период в месяцах (0 — вся загруженная история) и подпись к сумме. */
private val incomePeriods = listOf(
    Triple("3М", 3, "за 3 месяца"), Triple("6М", 6, "за 6 месяцев"), Triple("1Г", 12, "за 12 месяцев"),
    Triple("3Г", 36, "за 3 года"), Triple("Всё", 0, "за всё время"),
)

@Composable
fun IncomeScreen(ui: Ui) {
    var period by rememberSaveable { mutableIntStateOf(2) }
    val zone = ZoneId.systemDefault()
    val now = YearMonth.now()
    fun month(p: Payment) = YearMonth.from(p.date.atZone(zone))
    fun title(m: YearMonth) = date(m.atDay(1).atStartOfDay(zone).toInstant(), "LLLL yyyy")
    val (_, length, caption) = incomePeriods[period]
    val months = length.takeIf { it > 0 }
        ?: ui.payments.lastOrNull()?.let { ChronoUnit.MONTHS.between(month(it), now).toInt() + 1 } ?: 1
    val start = now.minusMonths(months - 1L)
    val shown = ui.payments.filter { !month(it).isBefore(start) }
    val byMonth = shown.groupBy(::month)
    val total = shown.sumOf { it.amount }
    val dividends = shown.filter { !it.coupon }.sumOf { it.amount }
    // До года — столбик на месяц, дальше — на год.
    val bars = if (months <= 12) (0 until months).map { start.plusMonths(it.toLong()) }.map { m -> title(m) to byMonth[m].orEmpty().sumOf { it.amount } }
    else (start.year..now.year).map { y -> "$y" to shown.filter { month(it).year == y }.sumOf { it.amount } }
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
        }
        byMonth.keys.sortedDescending().forEach { m ->
            item(key = m.toString()) { Section(title(m).replaceFirstChar { it.uppercase() }) }
            items(byMonth.getValue(m)) { PaymentRow(it) }
        }
    }
}

@Composable
fun PaymentRow(p: Payment) = Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Avatar(p.ticker, null, p.logo, 36)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text(p.name.ifEmpty { p.ticker }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${if (p.coupon) "Купон" else "Дивиденды"} · ${p.ticker} · ${p.broker}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
        Text(signed(p.amount), color = tone(p.amount))
        Text(date(p.date, "d MMM"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
