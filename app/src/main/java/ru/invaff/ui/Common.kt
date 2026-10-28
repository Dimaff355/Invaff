package ru.invaff.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.invaff.data.Logos
import ru.invaff.data.Position
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val ru = Locale.forLanguageTag("ru")

fun money(v: Double, currency: String = "RUB") =
    String.format(ru, if (currency == "RUB" || abs(v) >= 1000) "%,.0f %s" else "%,.2f %s", v, symbol(currency))

fun price(v: Double, currency: String) = String.format(ru, if (abs(v) < 10) "%,.4f %s" else "%,.2f %s", v, symbol(currency))

fun signed(v: Double) = (if (v > 0) "+" else "") + money(v)

fun pct(v: Double) = String.format(ru, "%+.1f%%", v)

fun share(v: Double) = String.format(ru, "%.1f%%", v)

fun qty(v: Double) = String.format(ru, if (v % 1.0 == 0.0) "%,.0f" else "%,.2f", v)

fun symbol(currency: String) = when (currency.uppercase()) {
    "RUB" -> "₽"; "USD" -> "$"; "EUR" -> "€"; "CNY" -> "¥"; "HKD" -> "HK$"
    else -> currency
}

fun date(epochSec: Long, pattern: String = "d MMM yyyy"): String = date(Instant.ofEpochSecond(epochSec), pattern)
fun date(i: Instant, pattern: String = "d MMM yyyy"): String =
    DateTimeFormatter.ofPattern(pattern, ru).withZone(ZoneId.systemDefault()).format(i)

fun tone(v: Double) = if (v >= 0) Up else Down

@Composable
fun Title(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 8.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

@Composable
fun Section(text: String) =
    Text(text, Modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(
    modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp)
) { content() }

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) = Card(modifier) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.size(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) = Row(
    Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    options.forEachIndexed { i, s -> FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(s) }) }
}

@Composable
fun Avatar(p: Position, size: Int = 40) = Avatar(p.ticker, p.color, p.logo, size)

/** Логотип эмитента; пока он грузится или если его нет — буква тикера на цвете бренда. */
@Composable
fun Avatar(ticker: String, color: Int?, logo: String?, size: Int = 40) {
    // Состояние привязано к URL: строка списка переиспользуется, и старая картинка не должна остаться у другой бумаги.
    var bitmap by remember(logo) { mutableStateOf(logo?.let(Logos::cached)) }
    LaunchedEffect(logo) { if (logo != null && bitmap == null) bitmap = Logos.load(logo) }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val shape = Modifier.size(size.dp).clip(CircleShape)
    if (image != null) return Image(image, ticker, shape, contentScale = ContentScale.Crop)
    val bg = color?.let(::Color) ?: MaterialTheme.colorScheme.surfaceContainerHighest
    Box(shape.background(bg), contentAlignment = Alignment.Center) {
        Text(ticker.take(1), color = if (bg.luminance() > 0.5f) Color.Black else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun PositionRow(p: Position, onClick: () -> Unit) = Row(
    Modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Avatar(p)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
        Text(p.name.ifEmpty { p.ticker }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${p.ticker} · ${qty(p.qty)} шт.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
        Text(money(p.value))
        if (p.profit != 0.0) Text("${signed(p.profit)}  ${pct(p.profitPct)}", style = MaterialTheme.typography.bodySmall, color = tone(p.profit))
    }
}

/** Поле для числа: пустое — null; допускает пробелы и запятую. */
@Composable
fun NumberField(label: String, value: Double?, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.toBigDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    OutlinedTextField(
        text,
        { t ->
            text = t.filter { it.isDigit() || it in " ,." }
            onChange(text.replace(" ", "").replace(',', '.').toDoubleOrNull())
        },
        modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** Поиск по тикеру или названию с кнопкой очистки. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit) = OutlinedTextField(
    query, onChange, Modifier.fillMaxWidth(),
    placeholder = { Text("Тикер или название") },
    leadingIcon = { Icon(Icons.Filled.Search, null) },
    trailingIcon = { if (query.isNotEmpty()) IconButton({ onChange("") }) { Icon(Icons.Filled.Clear, "Очистить") } },
    singleLine = true,
)
