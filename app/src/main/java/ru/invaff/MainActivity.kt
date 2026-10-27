package ru.invaff

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.invaff.ui.AssetScreen
import ru.invaff.ui.AssetsScreen
import ru.invaff.ui.IncomeScreen
import ru.invaff.ui.InvaffTheme
import ru.invaff.ui.PortfolioScreen
import ru.invaff.ui.SettingsScreen

private val tabs = listOf(
    "Портфель" to Icons.Filled.Home,
    "Активы" to Icons.AutoMirrored.Filled.List,
    "Доходы" to Icons.Filled.DateRange,
    "Настройки" to Icons.Filled.Settings,
)

/** Сколько приложение может пробыть в фоне без повторного запроса отпечатка. */
private const val GRACE_MS = 30_000L

class MainActivity : ComponentActivity() {
    private val store get() = (application as App).store
    private var authenticating = false

    /** Системная биометрия; на API 29 нельзя комбинировать только STRONG с PIN — там setDeviceCredentialAllowed. */
    private val canLock get() = SDK_INT >= 29 && getSystemService(KeyguardManager::class.java).isDeviceSecure

    private fun authenticate(onOk: () -> Unit) {
        // Без блокировки экрана запрос невозможен: не запираем пользователя в приложении навсегда.
        if (!canLock) return onOk()
        val b = BiometricPrompt.Builder(this).setTitle("Вход в Invaff").setSubtitle("Приложите палец к сканеру")
        if (SDK_INT >= 30) b.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        else @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        authenticating = true
        b.build().authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                authenticating = false
                onOk()
                if (SDK_INT >= 33) setRecentsScreenshotEnabled(!store.lock)
            }

            override fun onAuthenticationError(code: Int, message: CharSequence?) {
                authenticating = false
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (store.lock && store.hiddenAt > 0 && SystemClock.elapsedRealtime() - store.hiddenAt > GRACE_MS) store.locked = true
        store.hiddenAt = 0
    }

    override fun onResume() {
        super.onResume()
        if (SDK_INT >= 33) setRecentsScreenshotEnabled(!store.lock)
        // Запрос показывается сам один раз на блокировку; повтор — кнопкой на экране блокировки.
        if (store.locked && !store.prompted) {
            store.prompted = true
            authenticate { store.locked = false }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !authenticating) {
            store.hiddenAt = SystemClock.elapsedRealtime()
            store.prompted = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = store
        setContent {
            InvaffTheme {
                if (store.locked) return@InvaffTheme LockScreen { authenticate { store.locked = false } }
                var tab by rememberSaveable { mutableIntStateOf(if (store.connected.isEmpty()) 3 else 0) }
                var asset by rememberSaveable { mutableStateOf<String?>(null) }
                BackHandler(asset != null) { asset = null }
                val ui = store.ui
                Scaffold(bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i && asset == null,
                                onClick = { tab = i; asset = null },
                                icon = { Icon(icon, null) },
                                label = { Text(label) },
                            )
                        }
                    }
                }) { pad ->
                    PullToRefreshBox(ui.loading, store::refresh, Modifier.padding(pad)) {
                        val position = asset?.let { k -> ui.positions.find { it.key == k } }
                        when {
                            position != null -> AssetScreen(position, ui, store, back = { asset = null })
                            tab == 0 -> PortfolioScreen(ui) { asset = it }
                            tab == 1 -> AssetsScreen(ui) { asset = it }
                            tab == 2 -> IncomeScreen(ui)
                            else -> SettingsScreen(store, canLock, ::authenticate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockScreen(unlock: () -> Unit) = Column(
    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(16.dp))
    Text("Invaff заблокирован", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.size(24.dp))
    Button(unlock) { Text("Разблокировать") }
}
