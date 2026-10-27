package ru.invaff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.invaff.Store

private class Link(val key: String, val title: String, val hint: String)

private val links = listOf(
    Link(Store.TBANK, "Т-Банк", "Токен T-Invest API (t.…): Т-Инвестиции → Настройки → Токены"),
    Link(Store.FINAM, "Финам", "Секрет Trade API (tapi_sk_…): tradeapi.finam.ru → Токены"),
)

/** canLock — на устройстве есть блокировка экрана; auth — показать системный запрос биометрии. */
@Composable
fun SettingsScreen(store: Store, canLock: Boolean, auth: (() -> Unit) -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Title("Настройки")

    Section("Брокеры")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { links.forEach { BrokerCard(it, store) } }
    Text(
        "Используйте токены только для чтения. Они хранятся на устройстве в зашифрованном виде " +
            "(ключ в Android Keystore) и отправляются только на серверы брокера.",
        Modifier.padding(top = 8.dp), color = muted, style = MaterialTheme.typography.bodySmall,
    )

    Section("Безопасность")
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Вход по отпечатку")
                Text(
                    if (canLock) "Запрашивать при открытии и после 30 с в фоне" else "Включите блокировку экрана в настройках Android",
                    style = MaterialTheme.typography.bodySmall, color = muted,
                )
            }
            Switch(store.lock, { on -> auth { store.enableLock(on) } }, enabled = canLock || store.lock)
        }
    }

    Spacer(Modifier.size(24.dp))
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    Text("Invaff $version", color = muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BrokerCard(l: Link, store: Store) = Card(Modifier.fillMaxWidth()) {
    val on = l.title in store.connected
    val count = store.ui.positions.count { l.title in it.refs }
    val tail = remember(on) { store.tail(l.key) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (on) Up else muted, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(l.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (on) "подключён" else "не подключён", style = MaterialTheme.typography.bodySmall, color = if (on) Up else muted)
        }
        if (on) Text(
            "Токен ••••$tail" + if (count > 0) " · позиций: $count" else "",
            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = muted,
        )
        if (!on || editing) TokenField(l, if (on) "Новый токен" else "Токен", onCancel = { editing = false }.takeIf { on }) {
            store.setKey(l.key, it)
            editing = false
        } else Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ editing = true }) { Text("Заменить") }
            TextButton({ confirm = true }) { Text("Отключить", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Отключить ${l.title}?") },
        text = { Text("Токен будет удалён с устройства, позиции брокера пропадут из портфеля.") },
        confirmButton = { TextButton({ confirm = false; store.setKey(l.key, null) }) { Text("Отключить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } },
    )
}

@Composable
private fun TokenField(l: Link, placeholder: String, onCancel: (() -> Unit)?, onSave: (String) -> Unit) = Column(Modifier.padding(top = 12.dp)) {
    val focus = LocalFocusManager.current
    var value by remember { mutableStateOf("") }
    val save = { onSave(value); value = ""; focus.clearFocus() }
    OutlinedTextField(
        value, { value = it.trim() }, Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        supportingText = { Text(l.hint) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(save, enabled = value.isNotEmpty()) { Text("Сохранить") }
        onCancel?.let { TextButton(it) { Text("Отмена") } }
    }
}
