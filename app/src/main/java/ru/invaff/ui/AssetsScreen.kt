package ru.invaff.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.invaff.Ui

@Composable
fun AssetsScreen(ui: Ui, open: (String) -> Unit) {
    val groups = ui.positions.groupBy { it.kind }.toSortedMap()
    val filters = listOf(null) + groups.keys
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val kind = filters.getOrNull(sel)
    val q = query.trim()
    val shown = (if (kind == null) ui.positions else groups[kind].orEmpty())
        .filter { q.isEmpty() || it.ticker.contains(q, true) || it.name.contains(q, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Title("Активы")
            SearchField(query) { query = it }
            Spacer(Modifier.height(12.dp))
            Chips(listOf("Все ${ui.positions.size}") + groups.map { (k, v) -> "${k.title} ${v.size}" }, sel) { sel = it }
        }
        item {
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty() && q.isNotEmpty()) Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(shown, key = { it.key }) { PositionRow(it) { open(it.key) } }
    }
}
