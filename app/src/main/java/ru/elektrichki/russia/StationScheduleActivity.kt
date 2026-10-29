package ru.elektrichki.russia

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.elektrichki.russia.Ui.str
import ru.elektrichki.russia.data.YandexRaspApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Расписание станции: название станции, «В любую сторону», выбор дня и список всех
 * отправлений со станции (в обе стороны), как в Яндекс Электричках.
 */
class StationScheduleActivity : ComponentActivity() {
    private lateinit var api: YandexRaspApi
    private var stationCode = ""
    private var stationTitle = ""
    private var dayMode = DAY_TODAY
    private var customDate = ""
    private var showPast = false
    private var entries: List<Entry> = emptyList()
    private var loadedDate = ""
    private var cached = false
    private var job: Job? = null

    private lateinit var stationTitleText: TextView
    private lateinit var dayToday: TextView
    private lateinit var dayTomorrow: TextView
    private lateinit var dayAll: TextView
    private lateinit var pastButton: TextView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private var nextView: View? = null

    private class Entry(val departure: String, val depMs: Long?, val title: String, val express: Boolean)

    private val pickDateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != RESULT_OK) return@registerForActivityResult
        val date = r.data?.getStringExtra(DatePickActivity.EXTRA_DATE).orEmpty()
        if (date.isBlank()) return@registerForActivityResult
        customDate = date
        dayMode = DAY_CUSTOM
        refreshDays()
        load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.PANEL
        api = YandexRaspApi(this)
        stationCode = intent.getStringExtra(EXTRA_CODE).orEmpty()
        stationTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        buildUi()
        refreshDays()
        if (stationCode.isNotBlank() && stationTitle.isNotBlank()) {
            stationTitleText.text = stationTitle
            stationTitleText.setTextColor(Ui.WHITE)
            load()
        } else {
            showMessage("Введите название станции")
        }
    }

    private val pickStation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != RESULT_OK) return@registerForActivityResult
        val data = r.data ?: return@registerForActivityResult
        val code = data.getStringExtra(StationSearchActivity.EXTRA_CODE).orEmpty()
        val title = data.getStringExtra(StationSearchActivity.EXTRA_TITLE).orEmpty()
        if (code.isBlank() || title.isBlank()) return@registerForActivityResult
        stationCode = code
        stationTitle = title
        stationTitleText.text = title
        stationTitleText.setTextColor(Ui.WHITE)
        entries = emptyList()
        load()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true
        }

        // Станция + значок «i»
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.PANEL)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 8), 0)
        }
        stationTitleText = Ui.text(
            this,
            stationTitle.ifBlank { "Введите станцию" },
            24f,
            if (stationTitle.isBlank()) Ui.GRAY else Ui.WHITE,
            true
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                pickStation.launch(Intent(this@StationScheduleActivity, StationSearchActivity::class.java)
                    .putExtra(StationSearchActivity.EXTRA_IS_FROM, true))
            }
        }
        titleRow.addView(stationTitleText, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_info)
            setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10))
            setOnClickListener {
                if (stationCode.isBlank()) {
                    Toast.makeText(this@StationScheduleActivity, "Сначала выберите станцию", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@StationScheduleActivity, "$stationTitle · $stationCode", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)))
        panel.addView(titleRow, LinearLayout.LayoutParams(-1, Ui.dp(this, 64)))
        panel.addView(Ui.divider(this), LinearLayout.LayoutParams(-1, 1).apply {
            marginStart = Ui.dp(this@StationScheduleActivity, 16); marginEnd = Ui.dp(this@StationScheduleActivity, 16)
        })

        // «В любую сторону»
        panel.addView(Ui.text(this, "В любую сторону", 24f, Ui.WHITE, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
        }, LinearLayout.LayoutParams(-1, Ui.dp(this, 64)))
        panel.addView(Ui.divider(this), LinearLayout.LayoutParams(-1, 1).apply {
            marginStart = Ui.dp(this@StationScheduleActivity, 16); marginEnd = Ui.dp(this@StationScheduleActivity, 16)
        })

        // Сегодня / Завтра / Все дни
        val daysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
        }
        daysRow.addView(Ui.icon(this, R.drawable.ic_calendar, 32).apply {
            setPadding(Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2))
            setOnClickListener { pickDate() }
        })
        dayToday = dayTab("Сегодня") { dayMode = DAY_TODAY; refreshDays(); load() }
        dayTomorrow = dayTab("Завтра") { dayMode = DAY_TOMORROW; refreshDays(); load() }
        dayAll = dayTab("Все дни") { pickDate() }
        daysRow.addView(dayToday, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(this@StationScheduleActivity, 16) })
        daysRow.addView(dayTomorrow, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(this@StationScheduleActivity, 20) })
        daysRow.addView(dayAll, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(this@StationScheduleActivity, 20) })
        panel.addView(daysRow, LinearLayout.LayoutParams(-1, Ui.dp(this, 64)))
        root.addView(panel)

        // «ПОКАЗАТЬ УШЕДШИЕ»
        pastButton = Ui.text(this, PAST_SHOW, 18f, Ui.GRAY_LIGHT).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
            setBackgroundColor(0xFF4A4A4A.toInt())
            visibility = View.GONE
            setOnClickListener {
                showPast = !showPast
                text = if (showPast) PAST_HIDE else PAST_SHOW
                render()
            }
        }
        root.addView(pastButton, LinearLayout.LayoutParams(-1, Ui.dp(this, 52)))

        // Список
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Нижняя навигация; активна четвёртая иконка
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.PANEL)
        }
        val icons = intArrayOf(R.drawable.ic_nav_route, R.drawable.ic_nav_bookmark, R.drawable.ic_nav_ticket, R.drawable.ic_nav_history, R.drawable.ic_nav_settings)
        icons.forEachIndexed { i, res ->
            val v = ImageView(this).apply {
                setImageResource(res)
                setColorFilter(if (i == 3) Ui.NAV_ACTIVE else Ui.NAV_IDLE)
                setPadding(Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16))
                setOnClickListener {
                    when (i) {
                        0 -> finish()
                        1 -> startActivity(Intent(this@StationScheduleActivity, FavoritesActivity::class.java))
                        3 -> {}
                        4 -> startActivity(Intent(this@StationScheduleActivity, SettingsActivity::class.java))
                        else -> Toast.makeText(this@StationScheduleActivity, "Этот раздел пока не реализован", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            nav.addView(v, LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun dayTab(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun refreshDays() {
        fun style(v: TextView, on: Boolean) {
            v.setTextColor(if (on) Ui.WHITE else Ui.GRAY)
            v.textSize = if (on) 22f else 20f
            v.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
        style(dayToday, dayMode == DAY_TODAY)
        style(dayTomorrow, dayMode == DAY_TOMORROW)
        style(dayAll, dayMode == DAY_CUSTOM)
        dayAll.text = if (dayMode == DAY_CUSTOM && customDate.isNotBlank()) Ui.dateShort(customDate) else "Все дни"
    }

    private fun currentDate(): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return when (dayMode) {
            DAY_TOMORROW -> f.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time)
            DAY_CUSTOM -> customDate.ifBlank { f.format(Date()) }
            else -> f.format(Date())
        }
    }

    private fun pickDate() {
        pickDateLauncher.launch(Intent(this, DatePickActivity::class.java)
            .putExtra(DatePickActivity.EXTRA_SELECTED, currentDate()))
    }

    // ------------------------------------------------------------------ загрузка

    private fun load() {
        if (stationCode.isBlank()) { showMessage("Станция не выбрана"); return }
        val date = currentDate()
        showMessage("Загружаю расписание…")
        pastButton.visibility = View.GONE
        job?.cancel()
        job = lifecycleScope.launch {
            try {
                val list = ArrayList<Entry>()
                var fromCache = false
                var offset = 0
                // API отдаёт рейсы страницами (по 100), собираем все
                for (page in 0 until MAX_PAGES) {
                    val res = api.stationSchedule(stationCode, date, offset)
                    fromCache = fromCache || res.fromCache
                    val json = JSONObject(res.json)
                    val arr = json.optJSONArray("schedule")
                    val count = arr?.length() ?: 0
                    for (i in 0 until count) {
                        val item = arr!!.optJSONObject(i) ?: continue
                        val dep = item.str("departure")
                        if (dep.isBlank()) continue
                        val thread = item.optJSONObject("thread") ?: JSONObject()
                        val title = thread.str("title").ifBlank { "Пригородный поезд" }
                        list.add(Entry(dep, Ui.millisOf(dep), title, thread.str("express_type") == "express"))
                    }
                    offset += count
                    val total = json.optJSONObject("pagination")?.optInt("total", 0) ?: 0
                    if (count == 0 || offset >= total) break
                }
                list.sortWith(compareBy({ it.depMs ?: Long.MAX_VALUE }, { it.departure }))
                entries = list
                loadedDate = date
                cached = fromCache
                render()
            } catch (e: Exception) {
                showMessage("Не удалось загрузить данные.\n${e.message ?: "Неизвестная ошибка"}")
            }
        }
    }

    private fun showMessage(message: String) {
        content.removeAllViews()
        nextView = null
        val col = Ui.center(this).apply { setPadding(Ui.dp(context, 32), Ui.dp(context, 56), Ui.dp(context, 32), Ui.dp(context, 32)) }
        col.addView(Ui.text(this, message, 20f, Ui.WHITE).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.1f) })
        content.addView(col, LinearLayout.LayoutParams(-1, -2))
    }

    private fun render() {
        content.removeAllViews()
        nextView = null
        if (entries.isEmpty()) {
            pastButton.visibility = View.GONE
            showMessage("На этот день электричек не найдено")
            return
        }
        val now = System.currentTimeMillis()
        val isToday = loadedDate == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        fun isPast(e: Entry) = isToday && e.depMs != null && e.depMs < now

        pastButton.visibility = if (isToday && entries.any { isPast(it) }) View.VISIBLE else View.GONE
        if (cached) content.addView(note("Офлайн: показан сохранённый результат"))

        var lastHeader = ""
        var index = 0
        for (e in entries) {
            val past = isPast(e)
            if (past && !showPast) continue
            val header = Ui.isoDateOf(e.departure)
            if (header != lastHeader) {
                content.addView(Ui.divider(this))
                content.addView(dateHeader(Ui.dateHeader(header)))
                content.addView(Ui.divider(this))
                lastHeader = header
            }
            val row = row(e, past, index++)
            if (!past && nextView == null) nextView = row
            content.addView(row)
        }
        content.addView(note("Источник данных: Яндекс Расписания"))
        if (showPast) scroll.post { nextView?.let { scroll.scrollTo(0, (it.top - Ui.dp(this, 8)).coerceAtLeast(0)) } }
        else scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun dateHeader(s: String) = Ui.text(this, s, 15f, Ui.GRAY).apply {
        letterSpacing = 0.12f
        setPadding(Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun note(s: String) = Ui.text(this, s, 14f, Ui.GRAY).apply {
        setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 14))
    }

    private fun row(e: Entry, past: Boolean, index: Int): View {
        val timeColor = if (past) Ui.GRAY else Ui.WHITE
        val titleColor = if (past) Ui.GRAY_DIM else Ui.GRAY_LIGHT
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (index % 2 == 0) 0xFF383838.toInt() else Ui.BG)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 14))
        }
        box.addView(Ui.text(this, Ui.hhmm(e.departure), 24f, timeColor, true),
            LinearLayout.LayoutParams(Ui.dp(this, 74), -2))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (e.express) {
            col.addView(Ui.text(this, "экспресс (билеты с указанием мест)", 17f,
                if (past) 0xFF8A4A18.toInt() else 0xFFE8700A.toInt()).apply { setPadding(0, 0, 0, Ui.dp(context, 2)) })
        }
        col.addView(Ui.text(this, e.title, 19f, titleColor))
        box.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return box
    }

    companion object {
        const val EXTRA_CODE = "station_code"
        const val EXTRA_TITLE = "station_title"
        private const val DAY_TODAY = 0
        private const val DAY_TOMORROW = 1
        private const val DAY_CUSTOM = 2
        private const val MAX_PAGES = 6
        private const val PAST_SHOW = "ПОКАЗАТЬ УШЕДШИЕ"
        private const val PAST_HIDE = "СКРЫТЬ УШЕДШИЕ"
    }
}
