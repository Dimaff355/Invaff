package ru.invaff.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.invaff.Ui
import ru.invaff.data.Plan
import java.time.YearMonth
import java.time.ZoneId

/** Цели по капиталу и пассивному доходу с прогнозом срока; нажатие на карточку — изменить цель. */
@Composable
fun Goals(ui: Ui, plan: Plan, update: (Plan) -> Unit) {
    var edit by remember { mutableStateOf<Boolean?>(null) } // true — капитал, false — доход
    val total = ui.total
    val monthly = plan.monthly ?: ui.deposits
    val yield = if (total > 0) ui.income(plan) / total else 0.0
    val sellShare = if (total > 0) ui.sell(plan) / total else 0.0
    Section("Цели")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Goal("Капитал", total, plan.capital, plan.months(total, yield, monthly) { it >= plan.capital }) { edit = true }
        Goal(
            if (plan.fire) "Потенциальный доход в месяц" else "Пассивный доход в месяц", ui.passive(plan) / 12, plan.income,
            plan.months(total, yield, monthly) { it * (yield + sellShare) / 12 >= plan.income },
        ) { edit = false }
    }
    Text(
        "Прогноз: пополнения ${money(monthly)} в месяц, рост цены ${share(plan.growth)} в год, выплаты реинвестируются. " +
            "Допущения — в настройках.",
        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    edit?.let { capital ->
        GoalDialog(
            if (capital) "Цель по капиталу, ₽" else "Пассивный доход в месяц, ₽",
            if (capital) plan.capital else plan.income,
            dismiss = { edit = null },
        ) { v -> update(if (capital) plan.copy(capital = v) else plan.copy(income = v)); edit = null }
    }
}

@Composable
private fun Goal(title: String, current: Double, target: Double, months: Int?, onClick: () -> Unit) =
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (target > 0) Text(share(current / target * 100), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (target <= 0) {
                Text("Сейчас ${money(current)} · нажмите, чтобы задать цель", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            LinearProgressIndicator({ (current / target).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text("${money(current)} из ${money(target)}", style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    current >= target -> "Цель достигнута"
                    months == null -> "При текущих допущениях не достигается за 100 лет"
                    else -> "≈ ${span(months)} · ${date(YearMonth.now().plusMonths(months.toLong()).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant(), "LLLL yyyy")}"
                },
                style = MaterialTheme.typography.bodySmall, color = if (current >= target) Up else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

/** «4 г. 7 мес.» */
fun span(months: Int) = listOfNotNull(
    (months / 12).takeIf { it > 0 }?.let { "$it г." },
    (months % 12).takeIf { it > 0 }?.let { "$it мес." },
).joinToString(" ").ifEmpty { "меньше месяца" }

@Composable
private fun GoalDialog(title: String, value: Double, dismiss: () -> Unit, save: (Double) -> Unit) {
    var v by remember { mutableStateOf(value.takeIf { it > 0 }) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = { NumberField("Сумма", v) { v = it } },
        confirmButton = { TextButton({ save(v ?: 0.0) }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (value > 0) TextButton({ save(0.0) }) { Text("Убрать", color = MaterialTheme.colorScheme.error) }
                TextButton(dismiss) { Text("Отмена") }
            }
        },
    )
}
