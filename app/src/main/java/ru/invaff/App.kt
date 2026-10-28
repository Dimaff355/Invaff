package ru.invaff

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.invaff.data.Broker
import ru.invaff.data.Finam
import ru.invaff.data.Fx
import ru.invaff.data.Kind
import ru.invaff.data.Logos
import ru.invaff.data.OpType
import ru.invaff.data.Operation
import ru.invaff.data.Plan
import ru.invaff.data.Point
import ru.invaff.data.Position
import ru.invaff.data.TBank
import ru.invaff.data.Vault
import ru.invaff.data.merge
import java.time.Instant
import java.time.temporal.ChronoUnit

class App : Application() {
    val store by lazy { Store(Vault(this), getSharedPreferences("settings", MODE_PRIVATE)) }

    override fun onCreate() {
        super.onCreate()
        Logos.dir = cacheDir
    }
}

data class Ui(
    val positions: List<Position> = emptyList(),
    /** Все операции за [Store.HISTORY_DAYS], новые сверху. */
    val operations: List<Operation> = emptyList(),
    /** Стоимость текущего состава портфеля за год (по дневным закрытиям). */
    val history: List<Point> = emptyList(),
    val loading: Boolean = false,
    val errors: List<String> = emptyList(),
) {
    val total get() = positions.sumOf { it.value }

    /** Дивиденды и купоны, новые сверху. */
    val payments by lazy { operations.filter { it.income } }

    private val yearAgo get() = Instant.now().minus(365, ChronoUnit.DAYS)

    /** Пассивный доход за последние 12 месяцев. */
    val income by lazy { payments.filter { it.date > yearAgo }.sumOf { it.rub } }

    /** Средние чистые пополнения в месяц за последний год (не меньше нуля). */
    val deposits by lazy {
        operations.filter { it.date > yearAgo && (it.type == OpType.Deposit || it.type == OpType.Withdraw) }
            .sumOf { it.rub }.div(12).coerceAtLeast(0.0)
    }

    /** Продажа части капитала по правилу FIRE, в год. */
    fun sell(plan: Plan) = if (plan.fire) plan.sellable(positions) * plan.rate / 100 else 0.0

    /** Потенциальный пассивный доход в год: дивиденды и купоны + продажа по FIRE, если включена. */
    fun passive(plan: Plan) = income + sell(plan)
}

/** Единственный источник состояния приложения; живёт в Application, поэтому переживает поворот экрана. */
class Store(private val vault: Vault, private val prefs: SharedPreferences) {
    var ui by mutableStateOf(Ui())
        private set

    /** Цели, допущения прогноза и настройки FIRE. */
    var plan by mutableStateOf(Plan.load(prefs))
        private set

    fun update(p: Plan) {
        plan = p
        p.save(prefs)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var brokers = brokers()

    /** Названия подключённых брокеров. */
    var connected by mutableStateOf(brokers.keys)
        private set

    /** Вход по биометрии включён; locked — приложение сейчас заблокировано. */
    var lock by mutableStateOf(vault.lock)
        private set
    var locked by mutableStateOf(vault.lock)

    /** Когда приложение ушло в фон (elapsedRealtime) и показан ли уже автоматический запрос — для MainActivity. */
    var hiddenAt = 0L
    var prompted = false

    fun enableLock(on: Boolean) {
        vault.lock = on
        lock = on
    }

    /** Последние символы сохранённого токена — чтобы было видно, какой ключ подключён. */
    fun tail(name: String) = vault[name]?.takeLast(4)

    init {
        refresh()
    }

    private fun brokers(): Map<String, Broker> = listOfNotNull(
        vault[TBANK]?.let(::TBank),
        vault[FINAM]?.let(::Finam),
    ).associateBy { it.title }

    fun setKey(name: String, value: String?) {
        vault[name] = value?.trim()
        brokers = brokers()
        connected = brokers.keys
        ui = Ui()
        refresh()
    }

    fun refresh() {
        if (ui.loading || brokers.isEmpty()) return
        ui = ui.copy(loading = true, errors = emptyList())
        scope.launch {
            val errors = mutableListOf<String>()
            val data = runCatching {
                val fx = Fx.load()
                val from = Instant.now().minus(HISTORY_DAYS, ChronoUnit.DAYS)
                brokers.values.map { b ->
                    // Позиции и операции независимы: сбой одного не прячет другое.
                    async {
                        val ops = async { runCatching { b.operations(from, fx) }.onFailure { errors += "${b.title}, операции: ${it.message}" }.getOrDefault(emptyList()) }
                        val positions = runCatching { b.positions(fx) }.onFailure { errors += "${b.title}: ${it.message}" }.getOrDefault(emptyList())
                        positions to ops.await()
                    }
                }.awaitAll()
            }.onFailure { errors += "Курсы ЦБ: ${it.message}" }.getOrDefault(emptyList())
            val positions = merge(data.flatMap { it.first })
            ui = ui.copy(positions = positions, operations = data.flatMap { it.second }.sortedByDescending { it.date }, errors = errors)
            ui = ui.copy(history = history(positions), loading = false)
        }
    }

    suspend fun candles(p: Position, from: Instant): List<Point> = p.refs.entries.firstNotNullOfOrNull { (broker, ref) ->
        runCatching { brokers[broker]?.candles(ref, from) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }.orEmpty()

    /** Ряды цен масштабируются к текущей стоимости позиции: так одинаково работают акции, облигации (в % номинала) и лоты. */
    private suspend fun history(positions: List<Position>): List<Point> = coroutineScope {
        val from = Instant.now().minus(365, ChronoUnit.DAYS)
        val series = positions.map { p ->
            async {
                val c = if (p.kind == Kind.Currency) emptyList() else candles(p, from)
                val last = c.lastOrNull()?.v ?: 0.0
                if (last == 0.0) listOf(Point(0, p.value)) else c.map { Point(it.t / DAY, p.value * it.v / last) }
            }
        }.awaitAll()
        withContext(Dispatchers.Default) {
            val days = series.flatMap { s -> s.map { it.t } }.filter { it > 0 }.toSortedSet()
            val idx = IntArray(series.size)
            days.map { d ->
                Point(d * DAY, series.indices.sumOf { i ->
                    val s = series[i]
                    while (idx[i] + 1 < s.size && s[idx[i] + 1].t <= d) idx[i]++
                    s[idx[i]].v
                })
            }
        }
    }

    companion object {
        const val TBANK = "tbank"
        const val FINAM = "finam"
        const val HISTORY_DAYS = 3653L
        private const val DAY = 86_400L
    }
}
