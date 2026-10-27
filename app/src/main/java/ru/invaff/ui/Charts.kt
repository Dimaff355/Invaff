package ru.invaff.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.invaff.data.Point
import kotlin.math.roundToInt

/** Касание и протяжка пальцем: индекс ближайшей по X точки, null — когда палец отпущен. */
private fun Modifier.scrub(count: Int, onIndex: (Int?) -> Unit) = pointerInput(count) {
    fun index(x: Float) = (x / size.width * (count - 1)).roundToInt().coerceIn(0, count - 1)
    awaitEachGesture {
        val down = awaitFirstDown()
        onIndex(index(down.position.x))
        do {
            val change = awaitPointerEvent().changes.first()
            onIndex(index(change.position.x))
        } while (change.pressed)
        onIndex(null)
    }
}

/** Линейный график с заливкой и перекрестием; подпись над графиком показывает выбранную точку. */
@Composable
fun LineChart(points: List<Point>, format: (Double) -> String, modifier: Modifier = Modifier) {
    var sel by remember(points) { mutableStateOf<Int?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        val label = sel?.let { "${date(points[it].t)} · ${format(points[it].v)}" }
            ?: points.takeIf { it.size > 1 }?.let { "${date(it.first().t)} — ${date(it.last().t)}" }.orEmpty()
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(bottom = 8.dp))
        if (points.size < 2) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("Нет данных", color = muted) }
            return
        }
        val color = tone(points.last().v - points.first().v)
        Canvas(Modifier.fillMaxWidth().height(180.dp).scrub(points.size) { sel = it }) {
            val min = points.minOf { it.v }
            val span = (points.maxOf { it.v } - min).takeIf { it > 0 } ?: 1.0
            fun at(i: Int) = Offset(
                size.width * i / (points.size - 1),
                (size.height * (1 - (points[i].v - min) / span) * 0.9 + size.height * 0.05).toFloat(),
            )
            val line = Path().apply { points.indices.forEach { if (it == 0) moveTo(at(it).x, at(it).y) else lineTo(at(it).x, at(it).y) } }
            val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val dot = at(sel ?: points.lastIndex)
            sel?.let { drawLine(muted.copy(alpha = 0.6f), Offset(dot.x, 0f), Offset(dot.x, size.height), 1.dp.toPx()) }
            drawCircle(Color(0xFF0E0F11), 6.dp.toPx(), dot)
            drawCircle(color, 4.dp.toPx(), dot)
        }
    }
}

/** Кольцевая диаграмма с зазорами между сегментами. */
@Composable
fun Donut(slices: List<Pair<Color, Double>>, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val total = slices.sumOf { it.second }.takeIf { it > 0 } ?: return
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            val stroke = 18.dp.toPx()
            val gap = if (slices.size > 1) 3.dp.toPx() / ((size.minDimension - stroke) / 2) * 57.3f else 0f
            var start = -90f
            slices.forEach { (c, v) ->
                val sweep = (v / total * 360).toFloat()
                drawArc(
                    c, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        center()
    }
}

/** Столбики по периодам; касание показывает значение, tick — короткая подпись под столбцом. */
@Composable
fun Bars(bars: List<Pair<String, Double>>, format: (Double) -> String, modifier: Modifier = Modifier, tick: (String) -> String = { it.take(1) }) {
    var sel by remember(bars) { mutableStateOf<Int?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val max = bars.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Text(
            sel?.let { "${bars[it].first}: ${format(bars[it].second)}" } ?: "Нажмите на столбец",
            style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(bottom = 8.dp),
        )
        Canvas(Modifier.fillMaxWidth().height(140.dp).scrub(bars.size) { if (it != null) sel = it }) {
            val slot = size.width / bars.size
            val w = slot - 4.dp.toPx()
            bars.forEachIndexed { i, (_, v) ->
                val h = (size.height * v / max).toFloat().coerceAtLeast(if (v > 0) 4.dp.toPx() else 1.dp.toPx())
                drawRoundRect(
                    if (sel == null || sel == i) Up else Up.copy(alpha = 0.4f),
                    Offset(i * slot + 2.dp.toPx(), size.height - h), Size(w, h), CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            bars.forEach { (l, _) ->
                Text(tick(l), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.Center)
            }
        }
    }
}
