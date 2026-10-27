package ru.invaff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    "Ключи" to Icons.Filled.Settings,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as App).store
        setContent {
            InvaffTheme {
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
                            else -> SettingsScreen(store)
                        }
                    }
                }
            }
        }
    }
}
