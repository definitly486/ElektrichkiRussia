package ru.elektrichki.russia

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.elektrichki.russia.data.YandexRaspApi
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var api: YandexRaspApi
    private lateinit var output: LinearLayout
    private lateinit var dateField: Button
    private lateinit var fromField: EditText
    private lateinit var toField: EditText
    private var date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private var selectedFromCode: String = ""
    private var selectedToCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = YandexRaspApi(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 24)
            setBackgroundColor(0xFFF5F3F1.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)
        checkApiKey()

        root.addView(text("Электрички России", 27f, 0xFF201E21.toInt(), true))
        root.addView(text("Расписание пригородных поездов", 14f, 0xFF666166.toInt()))
        root.addView(space(14))
        fromField = edit("Станция отправления — нажмите поиск")
        toField = edit("Станция прибытия — нажмите поиск")
        fromField.isFocusable = false
        fromField.isClickable = true
        toField.isFocusable = false
        toField.isClickable = true
        root.addView(fromField)
        root.addView(button("Найти станцию отправления по названию") { chooseStation(true) })
        root.addView(toField)
        root.addView(button("Найти станцию прибытия по названию") { chooseStation(false) })
        dateField = Button(this).apply { text = "Дата: $date"; setOnClickListener { chooseDate() } }
        root.addView(dateField)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button("Расписание станции") { loadStation() }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(button("Найти маршрут") { loadRoute() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)
        root.addView(button("Настройки API-ключа") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        root.addView(text("Введите часть названия станции, выберите результат из списка и затем загрузите расписание или маршрут.", 13f, 0xFF625E62.toInt()))
        root.addView(space(10))
        output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(output)
        showMessage(if (api.apiKey().isBlank()) "Добавьте API-ключ в настройках, чтобы получать реальные данные." else "Введите код станции и выберите действие.")
    }

private fun checkApiKey() {
    if (BuildConfig.YANDEX_RASP_API_KEY.isBlank()) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("API-ключ не указан")
            .setMessage(
                "Не найден API-ключ Яндекс Расписаний.\n\n" +
                "Добавьте YANDEX_RASP_API_KEY в файл " +
                "api-key.properties и пересоберите приложение " +
                "или укажите ключ в настройках приложения."
            )
            .setPositiveButton("Настройки") { _, _ ->
                startActivity(
                    android.content.Intent(
                        this,
                        SettingsActivity::class.java
                    )
                )
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }
}



    private fun chooseStation(isFrom: Boolean) {
        val input = EditText(this).apply {
            hint = "Например, Казанский вокзал"
            setSingleLine(true)
            setPadding(24, 12, 24, 12)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (isFrom) "Станция отправления" else "Станция прибытия")
            .setMessage("Поиск выполняется по официальному списку станций Яндекс Расписаний. Первый поиск может занять время из-за загрузки списка.")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Найти", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = input.text.toString().trim()
                if (query.length < 2) {
                    input.error = "Введите минимум 2 символа"
                    return@setOnClickListener
                }
                dialog.dismiss()
                showMessage("Загружаю список станций и ищу «$query»…")
                lifecycleScope.launch {
                    try {
                        val matches = api.searchStations(query)
                        if (matches.isEmpty()) {
                            showMessage("Станции по запросу «$query» не найдены. Попробуйте другое написание.")
                        } else {
                            val labels = matches.map { "${it.title} — ${it.settlement}, ${it.region} [${it.code}]" }.toTypedArray()
                            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("Выберите станцию")
                                .setItems(labels) { _, which ->
                                    val selected = matches[which]
                                    if (isFrom) {
                                        selectedFromCode = selected.code
                                        fromField.setText("${selected.title} — ${selected.settlement}")
                                    } else {
                                        selectedToCode = selected.code
                                        toField.setText("${selected.title} — ${selected.settlement}")
                                    }
                                    showMessage("Выбрана станция: ${selected.title}")
                                }
                                .setNegativeButton("Отмена", null)
                                .show()
                        }
                    } catch (e: Exception) {
                        showMessage("Не удалось найти станции.\n${e.message ?: "Проверьте API-ключ и подключение к интернету."}")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun chooseDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            dateField.text = "Дата: $date"
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadStation() {
        val station = selectedFromCode
        if (station.isBlank()) { showMessage("Сначала найдите и выберите станцию отправления по названию."); return }
        runQuery("Загружаю расписание…") { api.stationSchedule(station, date) }
    }

    private fun loadRoute() {
        val from = selectedFromCode
        val to = selectedToCode
        if (from.isBlank() || to.isBlank()) { showMessage("Сначала найдите и выберите обе станции по названию."); return }
        runQuery("Ищу электрички…") { api.route(from, to, date) }
    }

    private fun runQuery(progress: String, query: suspend () -> ru.elektrichki.russia.data.ApiResult) {
        showMessage(progress)
        lifecycleScope.launch {
            try {
                val result = query()
                render(result.json, result.fromCache, result.warning)
            } catch (e: Exception) {
                showMessage("Не удалось загрузить данные.\n${e.message ?: "Неизвестная ошибка"}\n\nПроверьте API-ключ, коды станций и подключение к интернету.")
            }
        }
    }

    private fun render(raw: String, cached: Boolean, warning: String?) {
        output.removeAllViews()
        if (cached) addOutputText("ОФЛАЙН: сохранённый результат", true)
        warning?.let { addOutputText(it, false) }
        try {
            val json = JSONObject(raw)
            val station = json.optJSONObject("station")
            if (station != null) addOutputText(station.optString("title", "Станция"), true)
            val arr = json.optJSONArray("schedule") ?: json.optJSONArray("segments")
            if (arr == null || arr.length() == 0) {
                addOutputText("По этому запросу рейсы не найдены. Проверьте дату и коды станций.", false)
                return
            }
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val thread = item.optJSONObject("thread") ?: JSONObject()
                val departure = item.optString("departure", item.optString("start_date", ""))
                val arrival = item.optString("arrival", "")
                val title = thread.optString("title", item.optString("title", "Пригородный поезд"))
                val number = thread.optString("number", "")
                val platform = item.optString("platform", "")
                val stops = item.optString("stops", "")
                val line = buildString {
                    append(title)
                    if (number.isNotBlank()) append("\nПоезд: $number")
                    if (departure.isNotBlank()) append("\nОтправление: ${departure.replace('T', ' ')}")
                    if (arrival.isNotBlank()) append("\nПрибытие: ${arrival.replace('T', ' ')}")
                    if (platform.isNotBlank()) append("\nПуть: $platform")
                    if (stops.isNotBlank()) append("\nОстановки: $stops")
                }
                val card = TextView(this).apply {
                    text = line
                    textSize = 15f
                    setTextColor(0xFF292629.toInt())
                    setPadding(16, 14, 16, 14)
                    setBackgroundColor(0xFFFFFFFF.toInt())
                }
                val lp = LinearLayout.LayoutParams(-1, -2)
                lp.setMargins(0, 0, 0, 10)
                output.addView(card, lp)
            }
            addOutputText("Источник данных: Яндекс Расписания", false)
        } catch (_: Exception) {
            addOutputText("Не удалось разобрать ответ API. Проверьте формат ответа.", false)
        }
    }

    private fun showMessage(message: String) {
        output.removeAllViews()
        addOutputText(message, false)
    }
    private fun addOutputText(s: String, bold: Boolean) { output.addView(text(s, 15f, 0xFF292629.toInt(), bold)) }
    private fun edit(hintText: String) = EditText(this).apply {
        hint = hintText; textSize = 15f; setSingleLine(true)
        setPadding(12, 8, 12, 8)
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 4, 0, 4)
        layoutParams = lp
    }
    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 3, 0, 3)
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, height) }
}
