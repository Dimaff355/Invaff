package ru.invaff.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.invaff.Ui
import ru.invaff.data.Payment
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun IncomeScreen(ui: Ui) {
    val zone = ZoneId.systemDefault()
    val now = YearMonth.now()
    val byMonth = ui.payments.groupBy { YearMonth.from(it.date.atZone(zone)) }
    val months = (11 downTo 0).map { now.minusMonths(it.toLong()) }
    val dividends = ui.payments.filter { !it.coupon }.sumOf { it.amount }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Title("Доходы")
            Text("Пассивный доход за 12 месяцев", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(ui.income), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = Up)
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("В месяц", money(ui.income / 12), Modifier.weight(1f))
                Stat("Дивиденды", money(dividends), Modifier.weight(1f))
                Stat("Купоны", money(ui.income - dividends), Modifier.weight(1f))
            }
            Bars(months.map { m -> date(m.atDay(1).atStartOfDay(zone).toInstant(), "LLLL yyyy") to byMonth[m].orEmpty().sumOf { it.amount } }, ::money)
        }
        months.reversed().filter { byMonth[it] != null }.forEach { m ->
            item(key = m.toString()) { Section(date(m.atDay(1).atStartOfDay(zone).toInstant(), "LLLL yyyy").replaceFirstChar { it.uppercase() }) }
            items(byMonth.getValue(m)) { PaymentRow(it) }
        }
    }
}

@Composable
fun PaymentRow(p: Payment) = Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
