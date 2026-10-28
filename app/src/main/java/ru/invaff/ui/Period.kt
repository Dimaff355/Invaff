package ru.invaff.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private const val DAY_MS = 86_400_000L

/** Свой период: дни epoch [from, to] включительно. Pair — Serializable, поэтому живёт в rememberSaveable. */
typealias Days = Pair<Long, Long>

/** Границы периода в секундах epoch по локальному времени: [начало from, начало дня после to). */
fun Days.seconds(zone: ZoneId = ZoneId.systemDefault()) =
    LocalDate.ofEpochDay(first).atStartOfDay(zone).toEpochSecond() to LocalDate.ofEpochDay(second + 1).atStartOfDay(zone).toEpochSecond()

/**
 * Готовые периоды + последний чип «Свой…» с календарём; выбран свой, когда selected == labels.size.
 * min — самая ранняя дата, для которой есть данные.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodChips(labels: List<String>, selected: Int, custom: Days?, min: LocalDate?, onSelect: (Int) -> Unit, onCustom: (Days) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val last = custom?.takeIf { selected == labels.size }?.let { (a, b) ->
        "${short(a)} – ${short(b)}"
    } ?: "Свой…"
    Chips(labels + last, selected) { if (it == labels.size) picking = true else onSelect(it) }
    if (!picking) return
    val today = LocalDate.now().toEpochDay()
    val first = min?.toEpochDay() ?: Long.MIN_VALUE
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = custom?.first?.times(DAY_MS),
        initialSelectedEndDateMillis = custom?.second?.times(DAY_MS),
        yearRange = (min?.year ?: 2000)..LocalDate.now().year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / DAY_MS in first..today
        },
    )
    DatePickerDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(
                {
                    val a = state.selectedStartDateMillis!! / DAY_MS
                    onCustom(a to (state.selectedEndDateMillis?.div(DAY_MS) ?: a))
                    onSelect(labels.size)
                    picking = false
                },
                enabled = state.selectedStartDateMillis != null,
            ) { Text("Готово") }
        },
        dismissButton = { TextButton({ picking = false }) { Text("Отмена") } },
    ) { DateRangePicker(state, Modifier.fillMaxWidth().weight(1f)) }
}

private fun short(day: Long) = LocalDate.ofEpochDay(day).let { String.format(Locale.ROOT, "%02d.%02d.%02d", it.dayOfMonth, it.monthValue, it.year % 100) }
