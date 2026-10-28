package ru.invaff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import ru.invaff.Ui
import ru.invaff.data.Plan
import java.time.Instant
import java.time.temporal.ChronoUnit

val periods = listOf("1М" to 30L, "3М" to 91L, "6М" to 182L, "1Г" to 365L)

@Composable
fun PortfolioScreen(ui: Ui, plan: Plan, update: (Plan) -> Unit, open: (String) -> Unit) {
    var period by rememberSaveable { mutableIntStateOf(3) }
    val total = ui.total
    val profit = ui.positions.sumOf { it.profit }
    val day = ui.positions.sumOf { it.day }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Title("Портфель")
            ui.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(money(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${signed(profit)}  ${pct(if (total - profit != 0.0) profit / (total - profit) * 100 else 0.0)}  за всё время",
                color = tone(profit),
            )
            Text("${signed(day)} сегодня", color = tone(day), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(16.dp))
            val from = Instant.now().minus(periods[period].second, ChronoUnit.DAYS).epochSecond
            LineChart(ui.history.filter { it.t >= from }, ::money)
            Chips(periods.map { it.first }, period) { period = it }
        }
        item {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Пассивный доход", money(ui.income), Modifier.weight(1f), Up)
                Stat("Дох-ть за год", share(if (total > 0) ui.income / total * 100 else 0.0), Modifier.weight(1f))
            }
        }
        item { Goals(ui, plan, update) }
        item { Allocation(ui) }
        val best = ui.positions.filter { it.profit != 0.0 }.sortedByDescending { it.profitPct }
        if (best.isNotEmpty()) {
            item { Section("Лучшие") }
            items(best.take(3), key = { "b" + it.key }) { PositionRow(it) { open(it.key) } }
            item { Section("Худшие") }
            items(best.takeLast(3).reversed(), key = { "w" + it.key }) { PositionRow(it) { open(it.key) } }
        }
    }
}

@Composable
private fun Allocation(ui: Ui) {
    val total = ui.total.takeIf { it > 0 } ?: return
    val byKind = ui.positions.groupBy { it.kind }.mapValues { (_, ps) -> ps.sumOf { it.value } }.toSortedMap()
    Section("Распределение")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Donut(byKind.map { (k, v) -> k.color to v }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${ui.positions.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("активов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            byKind.forEach { (k, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(k.color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(k.title, Modifier.weight(1f))
                    Text(share(v / total * 100), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
