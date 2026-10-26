package ru.elektrichki.russia.data

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.elektrichki.russia.BuildConfig
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class YandexRaspApi(context: Context) {
    private val filesDir: File = context.applicationContext.filesDir
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val dao = ScheduleDatabase.get(context).scheduleDao()

    /** Ключ из настроек приложения; если его нет — ключ, зашитый при сборке из api-key.properties. */
    fun apiKey(): String =
        prefs.getString("api_key", "")?.trim().orEmpty()
            .ifBlank { BuildConfig.YANDEX_RASP_API_KEY.trim() }

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

    // ---------------------------------------------------------------- станции

    /**
     * Официальный список станций — десятки мегабайт JSON. Раньше он целиком читался в строку и
     * разбирался через JSONObject, что приводило к OutOfMemoryError (это Error, а не Exception,
     * поэтому приложение просто падало). Теперь список разбирается потоково один раз, из него
     * сохраняется компактный индекс (только российские станции с кодом), а сам JSON удаляется.
     */
    suspend fun searchStations(query: String): List<StationMatch> = withContext(Dispatchers.IO) {
        val q = query.trim()
        require(q.length >= 2) { "Введите минимум 2 символа названия станции." }

        val index = File(filesDir, INDEX_FILE)
        if (!index.exists() || index.length() == 0L) buildStationIndex(index)

        val needle = normalize(q)
        val byTitle = ArrayList<StationMatch>()
        val bySettlement = ArrayList<StationMatch>()
        index.bufferedReader().useLines { lines ->
            for (line in lines) {
                val p = line.split('\t')
                if (p.size < 4) continue
                val title = p[0]
                val settlement = p[2]
                val inTitle = normalize(title).contains(needle)
                val inSettlement = !inTitle && normalize(settlement).contains(needle)
                if (!inTitle && !inSettlement) continue
                val match = StationMatch(title, p[1], settlement, p[3])
                if (inTitle) byTitle.add(match) else bySettlement.add(match)
                if (byTitle.size >= MAX_RESULTS) break
            }
        }
        // Сначала совпадения по названию станции, потом по названию города.
        (byTitle + bySettlement).take(MAX_RESULTS)
    }

    private fun buildStationIndex(index: File) {
        require(apiKey().isNotBlank()) { "Сначала укажите API-ключ в настройках." }
        File(filesDir, "stations-list-ru.json").delete() // файл старой версии

        val raw = File(filesDir, "stations-list.tmp")
        val tmpIndex = File(filesDir, "$INDEX_FILE.tmp")
        try {
            val url = buildUrl("/stations_list/", mapOf("lang" to "ru_RU", "format" to "json"))
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                if (code !in 200..299) throw httpError(code, readErrorBody(connection))
                connection.inputStream.use { input -> raw.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }

            tmpIndex.bufferedWriter().use { out ->
                JsonReader(InputStreamReader(raw.inputStream().buffered(), Charsets.UTF_8)).use { reader ->
                    parseStationList(reader, out)
                }
            }
            if (tmpIndex.length() == 0L) throw IllegalStateException("В списке станций не нашлось подходящих станций.")
            if (!tmpIndex.renameTo(index)) {
                tmpIndex.copyTo(index, overwrite = true)
            }
        } catch (e: Exception) {
            index.delete()
            throw e
        } finally {
            raw.delete()
            tmpIndex.delete()
        }
    }

    private class Station(val title: String, val code: String)
    private class Settlement(val title: String, val stations: List<Station>)
    private class Region(val title: String, val settlements: List<Settlement>)

    /** Поля в объектах Яндекса идут в произвольном порядке (title может быть после вложенных массивов), поэтому каждый уровень буферизуется. */
    private fun parseStationList(reader: JsonReader, out: java.io.Writer) {
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "countries" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.hasNext()) {
                    var title = ""
                    var regions: List<Region> = emptyList()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "title" -> title = reader.nextStringOrEmpty()
                            "regions" -> regions = readList(reader) { readRegion(it) }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (title.isNotBlank() && !title.contains("Россия", ignoreCase = true)) continue
                    for (r in regions) for (s in r.settlements) for (st in s.stations) {
                        out.write(clean(st.title)); out.write("\t")
                        out.write(st.code); out.write("\t")
                        out.write(clean(s.title)); out.write("\t")
                        out.write(clean(r.title)); out.write("\n")
                    }
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readRegion(reader: JsonReader): Region {
        var title = ""
        var settlements: List<Settlement> = emptyList()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "title" -> title = reader.nextStringOrEmpty()
                "settlements" -> settlements = readList(reader) { readSettlement(it) }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Region(title, settlements)
    }

    private fun readSettlement(reader: JsonReader): Settlement {
        var title = ""
        var stations: List<Station> = emptyList()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "title" -> title = reader.nextStringOrEmpty()
                "stations" -> stations = readList(reader) { readStation(it) }.filterNotNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Settlement(title, stations)
    }

    private fun readStation(reader: JsonReader): Station? {
        var title = ""
        var transport = ""
        var code = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "title" -> title = reader.nextStringOrEmpty()
                "transport_type" -> transport = reader.nextStringOrEmpty()
                "codes" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            if (reader.nextName() == "yandex_code") code = reader.nextStringOrEmpty() else reader.skipValue()
                        }
                        reader.endObject()
                    } else reader.skipValue()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (transport.isNotBlank() && transport != "suburban" && transport != "train") return null
        if (title.isBlank() || code.isBlank()) return null
        return Station(title, code)
    }

    private fun <T> readList(reader: JsonReader, item: (JsonReader) -> T): List<T> {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return emptyList() }
        val list = ArrayList<T>()
        reader.beginArray()
        while (reader.hasNext()) list.add(item(reader))
        reader.endArray()
        return list
    }

    private fun JsonReader.nextStringOrEmpty(): String =
        if (peek() == JsonToken.NULL) { nextNull(); "" } else nextString()

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()

    private fun normalize(s: String) = s.lowercase(Locale("ru", "RU")).replace('ё', 'е')

    // ---------------------------------------------------------------- HTTP

    private fun buildUrl(path: String, params: Map<String, String>): String {
        val key = apiKey()
        require(key.isNotBlank()) { "Сначала укажите API-ключ в настройках." }
        val uri = Uri.parse("https://api.rasp.yandex-net.ru/v3.0$path").buildUpon()
        params.forEach { (k, v) -> uri.appendQueryParameter(k, v) }
        uri.appendQueryParameter("apikey", key)
        return uri.build().toString()
    }

    private fun readErrorBody(connection: HttpURLConnection): String =
        connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()

    private fun httpError(code: Int, body: String): IllegalStateException {
        val text = runCatching {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("text").orEmpty().ifBlank { obj.optString("error", "") }
        }.getOrDefault("").ifBlank { body.take(300) }
        val hint = when (code) {
            401, 403 -> "\nКлюч не принят: проверьте, что он верный и активирован для API Расписаний."
            429 -> "\nПревышен лимит запросов ключа."
            else -> ""
        }
        return IllegalStateException("API вернул HTTP $code: $text$hint")
    }

    /**
     * Кэш показывается только при сетевой ошибке (IOException). Ошибки API (неверный ключ,
     * неверный код станции и т.д.) раньше тоже подменялись кэшем и выглядели как «нет соединения».
     */
    private suspend fun requestCached(key: String, url: () -> String): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url()).openConnection() as HttpURLConnection
                val code: Int
                val body: String
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.setRequestProperty("Accept", "application/json")
                    code = connection.responseCode
                    body = if (code in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        readErrorBody(connection)
                    }
                } finally {
                    connection.disconnect()
                }
                if (code !in 200..299) throw httpError(code, body)
                JSONObject(body) // проверяем JSON перед кэшированием
                val now = System.currentTimeMillis()
                dao.put(CachedSchedule(key, body, now))
                ApiResult(body, fromCache = false, savedAt = now)
            } catch (e: IOException) {
                val cached = dao.get(key)
                if (cached != null) ApiResult(
                    cached.json, fromCache = true, savedAt = cached.savedAt,
                    warning = "Нет соединения с сервером. Показан сохранённый результат."
                ) else throw IllegalStateException("Нет соединения с сервером, а сохранённого результата нет. (${e.message ?: e.javaClass.simpleName})")
            }
        }

    private companion object {
        const val INDEX_FILE = "stations-index-v1.tsv"
        const val MAX_RESULTS = 80
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
