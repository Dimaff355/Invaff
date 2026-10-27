package ru.invaff.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.invaff.Store
import ru.invaff.Ui
import ru.invaff.data.Point
import ru.invaff.data.Position
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun AssetScreen(p: Position, ui: Ui, store: Store, back: () -> Unit) {
    var period by rememberSaveable { mutableIntStateOf(1) }
    val candles by produceState(emptyList<Point>(), p.key) {
        value = store.candles(p, Instant.now().minus(365, ChronoUnit.DAYS))
    }
    val payments = ui.payments.filter { it.ticker == p.ticker }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            IconButton(back, Modifier.padding(start = 0.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p, 48)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(p.name.ifEmpty { p.ticker }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${p.ticker} · ${p.kind.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(16.dp))
            Text(price(p.price, p.currency), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text("${signed(p.day)} сегодня", color = tone(p.day))
            Spacer(Modifier.size(16.dp))
            val from = Instant.now().minus(periods[period].second, ChronoUnit.DAYS).epochSecond
            LineChart(candles.filter { it.t >= from }, { price(it, p.currency) })
            Chips(periods.map { it.first }, period) { period = it }

            Section("В портфеле")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Количество", qty(p.qty), Modifier.weight(1f))
                Stat("Стоимость", money(p.value), Modifier.weight(1f))
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Прибыль", "${signed(p.profit)} ${pct(p.profitPct)}", Modifier.weight(1f), tone(p.profit))
                Stat("Доля", share(if (ui.total > 0) p.value / ui.total * 100 else 0.0), Modifier.weight(1f))
            }
            Text(
                "Брокеры: " + p.refs.keys.joinToString(), Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (payments.isNotEmpty()) Section("Выплаты за год · ${money(payments.sumOf { it.amount })}")
        }
        items(payments) { PaymentRow(it) }
    }
}
