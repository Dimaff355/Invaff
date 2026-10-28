package ru.invaff.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.invaff.Ui
import ru.invaff.data.Operation
import java.time.YearMonth
import java.time.ZoneId

/** Лента всех операций с фильтрами по типу операции, виду бумаги и поиском по тикеру/названию. */
@Composable
fun OperationsScreen(ui: Ui) {
    val ops = ui.operations
    val types = remember(ops) { ops.map { it.type }.distinct().sorted() }
    val kinds = remember(ops) { ops.mapNotNull { it.kind }.distinct().sorted() }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val t = types.getOrNull(type - 1)
    val k = kinds.getOrNull(kind - 1)
    val q = query.trim()
    val shown = remember(ops, t, k, q) {
        ops.filter {
            (t == null || it.type == t) && (k == null || it.kind == k) &&
                (q.isEmpty() || it.ticker.contains(q, true) || it.name.contains(q, true))
        }
    }
    val zone = ZoneId.systemDefault()
    val byMonth = remember(shown) { shown.groupBy { YearMonth.from(it.date.atZone(zone)) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Title("Операции")
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Тикер или название") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Clear, "Очистить") } },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Chips(listOf("Все операции") + types.map { it.title }, type) { type = it }
            Spacer(Modifier.height(4.dp))
            Chips(listOf("Все бумаги") + kinds.map { it.title }, kind) { kind = it }
            Text(
                "Операций: ${shown.size} · итого ${signed(shown.sumOf { it.rub })}",
                Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        byMonth.forEach { (m, list) ->
            item(key = m.toString()) { Section(date(m.atDay(1).atStartOfDay(zone).toInstant(), "LLLL yyyy").replaceFirstChar { it.uppercase() }) }
            items(list) { OperationRow(it) }
        }
    }
}

@Composable
fun OperationRow(o: Operation) = Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Avatar(o.ticker.ifEmpty { o.type.one }, null, o.logo, 36)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text(o.name.ifEmpty { o.ticker.ifEmpty { o.type.one } }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(o.type.one, o.ticker, if (o.qty != 0.0 && o.ticker.isNotEmpty()) "${qty(o.qty)} шт." else "", o.broker)
                .filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
        Text(
            (if (o.amount > 0) "+" else "") + money(o.amount, o.currency),
            color = if (o.amount > 0) Up else MaterialTheme.colorScheme.onSurface,
        )
        Text(date(o.date, "d MMM"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
