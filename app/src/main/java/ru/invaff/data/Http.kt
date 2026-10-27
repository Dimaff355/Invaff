package ru.invaff.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ApiError(code: Int, body: String) : Exception("HTTP $code ${body.take(160)}")

internal object Http {
    private val limit = Semaphore(8)

    /** GET, либо POST JSON, если передан body. Только HTTPS: приведение к HttpsURLConnection упадёт на http://. */
    suspend fun call(url: String, body: String? = null, auth: String? = null): String = limit.withPermit {
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpsURLConnection
            try {
                c.connectTimeout = 10_000
                c.readTimeout = 30_000
                auth?.let { c.setRequestProperty("Authorization", it) }
                if (body != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code >= 400) throw ApiError(code, text)
                text
            } finally {
                c.disconnect()
            }
        }
    }
}

internal fun JSONArray?.objects() = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

/** Денежное значение в формате {units, nano} (Т-Банк) или {units, nanos} (Финам). */
internal fun JSONObject.money(key: String) = optJSONObject(key)?.let {
    it.optString("units", "0").toDouble() + (it.optLong("nano") + it.optLong("nanos")) / 1e9
} ?: 0.0

/** Десятичное значение Финама: {"value": "123.45"}. */
internal fun JSONObject.decimal(key: String) = optJSONObject(key)?.optString("value")?.toDoubleOrNull() ?: 0.0
