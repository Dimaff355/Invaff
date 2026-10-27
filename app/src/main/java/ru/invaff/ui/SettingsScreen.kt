package ru.invaff.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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

@Composable
fun SettingsScreen(store: Store) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)
) {
    val focus = LocalFocusManager.current
    Title("Ключи")
    Text(
        "Используйте токены только для чтения. Они хранятся на устройстве в зашифрованном виде " +
            "(ключ в Android Keystore) и отправляются только на серверы брокера.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
    )
    links.forEach { l ->
        val on = l.title in store.connected
        var value by remember { mutableStateOf("") }
        Section(l.title + if (on) " · подключён" else "")
        OutlinedTextField(
            value, { value = it.trim() }, Modifier.fillMaxWidth(),
            placeholder = { Text(if (on) "Новый токен" else "Токен") },
            supportingText = { Text(l.hint) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button({ store.setKey(l.key, value); value = ""; focus.clearFocus() }, enabled = value.isNotEmpty()) { Text("Сохранить") }
            if (on) TextButton({ store.setKey(l.key, null) }) { Text("Отключить", color = MaterialTheme.colorScheme.error) }
        }
    }
    Spacer(Modifier.size(24.dp))
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    Text("Invaff $version", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}
