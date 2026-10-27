package ru.invaff.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Логотипы эмитентов с публичного CDN Т-Инвестиций (без токена): по brand.logoName или по ISIN.
 * Кэш: память (уменьшенные битмапы) + файлы в cacheDir; отсутствующий логотип помечается пустым файлом на неделю.
 */
object Logos {
    private const val CDN = "https://invest-brands.cdn-tinkoff.ru/"
    private const val SIZE = 96
    private const val MISS_TTL = 7 * 86_400_000L
    private val safe = Regex("[A-Za-z0-9_-]{1,64}")

    lateinit var dir: File
    private val memory = ConcurrentHashMap<String, Bitmap>()
    private val limit = Semaphore(4)

    /** URL по имени из brand.logoName («sber3.png») или по ISIN; null, если имя подозрительное. */
    fun byName(logoName: String) = logoName.substringBefore('.').takeIf { safe.matches(it) }?.let { "$CDN${it}x160.png" }
    fun byIsin(isin: String) = isin.takeIf { it.length == 12 }?.let(::byName)

    fun cached(url: String): Bitmap? = memory[url]

    suspend fun load(url: String): Bitmap? = memory[url] ?: withContext(Dispatchers.IO) {
        val file = File(dir, "logos/" + url.removePrefix(CDN).substringBefore('.'))
        val fresh = file.length() > 0 || System.currentTimeMillis() - file.lastModified() < MISS_TTL
        val data = if (file.exists() && fresh) file.readBytes() else {
            val d = runCatching { limit.withPermit { Http.bytes(url) } }
                .getOrElse { e -> if (e is ApiError) ByteArray(0) else return@withContext null }
            file.parentFile?.mkdirs()
            file.writeBytes(d)
            d
        }
        decode(data)?.also { memory[url] = it }
    }

    /** Только небольшие картинки: защищает память от неожиданно огромного ответа. */
    private fun decode(data: ByteArray): Bitmap? {
        if (data.isEmpty() || data.size > 512 * 1024) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return null
        val b = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
        return if (b.width <= SIZE) b else Bitmap.createScaledBitmap(b, SIZE, SIZE * b.height / b.width, true).also { b.recycle() }
    }
}
