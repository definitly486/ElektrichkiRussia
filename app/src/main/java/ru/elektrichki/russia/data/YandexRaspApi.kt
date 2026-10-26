package ru.elektrichki.russia.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class YandexRaspApi(context: Context) {
    private val appContextFilesPath = context.applicationContext.filesDir.absolutePath
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val dao = ScheduleDatabase.get(context).scheduleDao()
    fun apiKey(): String = prefs.getString("api_key", "")?.trim().orEmpty()
    fun saveApiKey(key: String) { prefs.edit().putString("api_key", key.trim()).apply() }

    suspend fun stationSchedule(stationCode: String, date: String): ApiResult =
        requestCached("station|$stationCode|$date") {
            buildUrl("/schedule/", mapOf(
                "station" to stationCode.trim(),
                "date" to date,
                "transport_types" to "suburban",
                "event" to "departure",
                "lang" to "ru_RU",
                "format" to "json",
                "limit" to "100"
            ))
        }

    suspend fun route(from: String, to: String, date: String): ApiResult =
        requestCached("route|$from|$to|$date") {
            buildUrl("/search/", mapOf(
                "from" to from.trim(), "to" to to.trim(), "date" to date,
                "transport_types" to "suburban", "lang" to "ru_RU",
                "format" to "json", "limit" to "100"
            ))
        }


    /**
     * Список станций Яндекс Расписаний — большой файл (десятки мегабайт).
     * Загружаем его один раз и сохраняем во внутреннем каталоге приложения.
     */
    suspend fun searchStations(query: String): List<StationMatch> = withContext(Dispatchers.IO) {
        val q = query.trim()
        require(q.length >= 2) { "Введите минимум 2 символа названия станции." }
        require(apiKey().isNotBlank()) { "Сначала укажите API-ключ в настройках." }

        val file = File(contextFilesDir(), "stations-list-ru.json")
        if (!file.exists() || file.length() == 0L) {
            val requestUrl = buildUrl("/stations_list/", mapOf("lang" to "ru_RU", "format" to "json"))
            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.setRequestProperty("Accept", "application/json")
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("API вернул HTTP $code: $error")
                }
                val temp = File(file.parentFile, file.name + ".tmp")
                connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            } finally {
                connection.disconnect()
            }
        }

        val found = ArrayList<StationMatch>()
        val needle = q.lowercase(java.util.Locale("ru", "RU"))
        val root = JSONObject(file.readText())
        val countries = root.optJSONArray("countries") ?: JSONArray()
        for (ci in 0 until countries.length()) {
            val country = countries.optJSONObject(ci) ?: continue
            val countryName = country.optString("title")
            if (countryName.isNotBlank() && !countryName.contains("Россия", ignoreCase = true)) continue
            val regions = country.optJSONArray("regions") ?: JSONArray()
            for (ri in 0 until regions.length()) {
                val region = regions.optJSONObject(ri) ?: continue
                val regionName = region.optString("title")
                val settlements = region.optJSONArray("settlements") ?: JSONArray()
                for (si in 0 until settlements.length()) {
                    val settlement = settlements.optJSONObject(si) ?: continue
                    val settlementName = settlement.optString("title")
                    val stations = settlement.optJSONArray("stations") ?: JSONArray()
                    for (sti in 0 until stations.length()) {
                        val station = stations.optJSONObject(sti) ?: continue
                        val title = station.optString("title")
                        val transport = station.optString("transport_type")
                        if (transport.isNotBlank() && transport != "suburban" && transport != "train") continue
                        if (!title.lowercase(java.util.Locale("ru", "RU")).contains(needle) &&
                            !settlementName.lowercase(java.util.Locale("ru", "RU")).contains(needle)) continue
                        val code = station.optJSONObject("codes")?.optString("yandex_code").orEmpty()
                        if (code.isBlank()) continue
                        found.add(StationMatch(title, code, settlementName, regionName))
                        if (found.size >= 80) return@withContext found
                    }
                }
            }
        }
        found
    }

    private fun contextFilesDir(): File = File(appContextFilesPath)

    private fun buildUrl(path: String, params: Map<String, String>): String {
        val key = apiKey()
        require(key.isNotBlank()) { "Сначала укажите API-ключ в настройках." }
        val uri = Uri.parse("https://api.rasp.yandex-net.ru/v3.0$path").buildUpon()
        params.forEach { (k, v) -> uri.appendQueryParameter(k, v) }
        uri.appendQueryParameter("apikey", key)
        return uri.build().toString()
    }

    private suspend fun requestCached(key: String, url: () -> String): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url()).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                connection.disconnect()
                if (code !in 200..299) {
                    val message = runCatching { JSONObject(body).optString("error", body) }.getOrDefault(body)
                    throw IllegalStateException("API вернул HTTP $code: $message")
                }
                JSONObject(body) // validate JSON before caching
                dao.put(CachedSchedule(key, body, System.currentTimeMillis()))
                ApiResult(body, fromCache = false, savedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                val cached = dao.get(key)
                if (cached != null) ApiResult(
                    cached.json, fromCache = true, savedAt = cached.savedAt,
                    warning = "Нет соединения или запрос не выполнен. Показан сохранённый результат."
                ) else throw e
            }
        }
}

data class ApiResult(
    val json: String,
    val fromCache: Boolean,
    val savedAt: Long,
    val warning: String? = null
)


data class StationMatch(val title: String, val code: String, val settlement: String, val region: String) {
    override fun toString(): String = "$title — $settlement ($region)"
}
