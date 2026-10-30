package ru.elektrichki.russia

import android.content.Intent
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.elektrichki.russia.Ui.str
import ru.elektrichki.russia.data.YandexRaspApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {
    private lateinit var api: YandexRaspApi
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

    // состояние
    private var fromCode = ""
    private var fromTitle = ""
    private var toCode = ""
    private var toTitle = ""
    private var dayMode = DAY_TODAY
    private var customDate = ""
    private var ticketsTab = false
    private var job: Job? = null

    // views
    private lateinit var fromText: TextView
    private lateinit var toText: TextView
    private lateinit var ticketsExtra: LinearLayout
    private lateinit var ticketDate: TextView
    private lateinit var daysRow: LinearLayout
    private lateinit var dayToday: TextView
    private lateinit var dayTomorrow: TextView
    private lateinit var dayAll: TextView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var fab: FrameLayout
    private lateinit var bookmark: ImageView
    private lateinit var refreshIndicator: ImageView
    private lateinit var panel: LinearLayout
    private lateinit var collapsedBar: LinearLayout
    private lateinit var collapsedFrom: TextView
    private lateinit var collapsedTo: TextView
    private lateinit var collapsedDay: TextView
    private var panelCollapsed = false
    private var panelAnimator: ValueAnimator? = null
    private var swipeHandled = false
    private var panelFraction = 1f      // 1 — шапка полностью видна, 0 — свёрнута
    private var panelFull = 0
    private var lastRawY = 0f
    private var lastDir = 0
    private var fractionAtStart = 1f
    private var pullStartY = 0f
    private var pullDistance = 0f
    private var isPullRefreshing = false

    private val pickStation = registerForResult { r ->
        val d = r.data ?: return@registerForResult
        if (r.resultCode != RESULT_OK) return@registerForResult
        val code = d.getStringExtra(StationSearchActivity.EXTRA_CODE).orEmpty()
        val title = d.getStringExtra(StationSearchActivity.EXTRA_TITLE).orEmpty()
        if (d.getBooleanExtra(StationSearchActivity.EXTRA_IS_FROM, true)) {
            fromCode = code; fromTitle = title
        } else {
            toCode = code; toTitle = title
        }
        saveStations()
        refreshFields()
        if (!ticketsTab) search()
    }

    private val pickDateLauncher = registerForResult { r ->
        if (r.resultCode != RESULT_OK) return@registerForResult
        val date = r.data?.getStringExtra(DatePickActivity.EXTRA_DATE).orEmpty()
        if (date.isBlank()) return@registerForResult
        customDate = date
        dayMode = DAY_CUSTOM
        refreshDays()
        if (!ticketsTab) search()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = YandexRaspApi(this)
        fromCode = intent.getStringExtra(EXTRA_FROM_CODE) ?: prefs.getString("from_code", "").orEmpty()
        fromTitle = intent.getStringExtra(EXTRA_FROM_TITLE) ?: prefs.getString("from_title", "").orEmpty()
        toCode = intent.getStringExtra(EXTRA_TO_CODE) ?: prefs.getString("to_code", "").orEmpty()
        toTitle = intent.getStringExtra(EXTRA_TO_TITLE) ?: prefs.getString("to_title", "").orEmpty()
        if (intent.hasExtra(EXTRA_FROM_CODE)) saveStations()
        buildUi()
        refreshFields()
        refreshTabs()
        refreshDays()
        checkApiKey()
        search()
    }

    override fun onResume() {
        super.onResume()
        // После удаления маршрута на экране «Избранное» иконка закладки должна обновиться
        refreshBookmarkIcon()
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true
        }

        // Панель маршрута A → B
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.PANEL)
        }
        val route = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 8))
        }
        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        badges.addView(badge("A", R.drawable.bg_badge_a), LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)))
        val swap = ImageView(this).apply {
            setImageResource(R.drawable.ic_swap)
            setOnClickListener { swapStations() }
        }
        badges.addView(swap, LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 28)))
        badges.addView(badge("B", R.drawable.bg_badge_b), LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)))
        route.addView(badges, LinearLayout.LayoutParams(Ui.dp(this, 32), -2))

        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fromText = fieldText { openSearch(true) }
        toText = fieldText { openSearch(false) }
        fields.addView(fromText, LinearLayout.LayoutParams(-1, Ui.dp(this, 46)))
        fields.addView(Ui.divider(this), LinearLayout.LayoutParams(-1, 1).apply { topMargin = Ui.dp(this@MainActivity, 0) })
        fields.addView(toText, LinearLayout.LayoutParams(-1, Ui.dp(this, 46)))
        route.addView(fields, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = Ui.dp(this@MainActivity, 16) })

        bookmark = ImageView(this).apply {
            setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
            setOnClickListener { addCurrentStationsToFavorites() }
        }
        route.addView(bookmark, LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginStart = Ui.dp(this@MainActivity, 8)
        })
        panel.addView(route)
        panel.addView(Ui.divider(this), LinearLayout.LayoutParams(-1, 1).apply {
            marginStart = Ui.dp(this@MainActivity, 16); marginEnd = Ui.dp(this@MainActivity, 16)
        })

        // Дата + кнопка «Найти» на вкладке «Ж/д билеты»
        ticketsExtra = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
            setOnClickListener { pickDate() }
        }
        dateRow.addView(Ui.icon(this, R.drawable.ic_calendar, 32).apply { setPadding(Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2)) })
        ticketDate = Ui.text(this, "", 20f, Ui.WHITE)
        dateRow.addView(ticketDate, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(this@MainActivity, 16) })
        ticketsExtra.addView(dateRow, LinearLayout.LayoutParams(-1, Ui.dp(this, 64)))
        ticketsExtra.addView(Ui.divider(this), LinearLayout.LayoutParams(-1, 1).apply {
            marginStart = Ui.dp(this@MainActivity, 16); marginEnd = Ui.dp(this@MainActivity, 16)
        })
        val find = TextView(this).apply {
            text = "Найти"
            textSize = 20f
            setTextColor(0xFF201E21.toInt())
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_button_yellow)
            setOnClickListener { setTicketsTab(false); search() }
        }
        ticketsExtra.addView(find, LinearLayout.LayoutParams(-1, Ui.dp(this, 48)).apply {
            setMargins(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16))
        })
        panel.addView(ticketsExtra)

        // Сегодня / Завтра / Все дни
        daysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
        }
        daysRow.addView(Ui.icon(this, R.drawable.ic_calendar, 32).apply {
            setPadding(Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2))
            setOnClickListener { pickDate() }
        })
        dayToday = dayTab("Сегодня") { dayMode = DAY_TODAY; refreshDays(); search() }
        dayTomorrow = dayTab("Завтра") { dayMode = DAY_TOMORROW; refreshDays(); search() }
        dayAll = dayTab("Все дни") { pickDate() }
        daysRow.addView(dayToday, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(this@MainActivity, 16) })
        daysRow.addView(dayTomorrow, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(this@MainActivity, 20) })
        daysRow.addView(dayAll, LinearLayout.LayoutParams(-2, -2).apply { marginStart = Ui.dp(this@MainActivity, 20) })
        panel.addView(daysRow, LinearLayout.LayoutParams(-1, Ui.dp(this, 64)))
        root.addView(panel)

        // Тонкая полоска вместо свёрнутой шапки: [A станция] → [B станция] день
        collapsedBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.PANEL)
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
            visibility = View.GONE
            setOnClickListener { setPanelCollapsed(false) }
        }
        fun chip(letter: String, bg: Int): Pair<LinearLayout, TextView> {
            val name = TextView(this).apply {
                textSize = 16f
                setTextColor(Ui.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 8), 0)
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_station_chip)
            }
            box.addView(badge(letter, bg).apply { textSize = 18f }, LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)))
            box.addView(name, LinearLayout.LayoutParams(0, -1, 1f))
            return box to name
        }
        val (chipA, nameA) = chip("A", R.drawable.bg_badge_a)
        val (chipB, nameB) = chip("B", R.drawable.bg_badge_b)
        collapsedFrom = nameA
        collapsedTo = nameB
        collapsedDay = Ui.text(this, "", 14f, Ui.GRAY_LIGHT).apply {
            maxLines = 1
            setPadding(Ui.dp(context, 10), 0, 0, 0)
        }
        collapsedBar.addView(chipA, LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1f))
        collapsedBar.addView(Ui.text(this, "→", 20f, Ui.WHITE).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(Ui.dp(this, 32), -2))
        collapsedBar.addView(chipB, LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1f))
        collapsedBar.addView(collapsedDay, LinearLayout.LayoutParams(-2, -2))
        root.addView(collapsedBar, LinearLayout.LayoutParams(-1, Ui.dp(this, 48)))

        // Список + плавающая кнопка
        val body = FrameLayout(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        body.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        refreshIndicator = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_popup_sync)
            setColorFilter(Ui.WHITE)
            alpha = 0f
            visibility = View.INVISIBLE
            elevation = Ui.dp(context, 8).toFloat()
        }
        body.addView(refreshIndicator, FrameLayout.LayoutParams(
            Ui.dp(this, 36), Ui.dp(this, 36), Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = Ui.dp(this@MainActivity, 8) })
        scroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    pullStartY = event.y
                    pullDistance = 0f
                    panelAnimator?.cancel()
                    panelFull = measurePanelFull()
                    lastRawY = event.rawY
                    lastDir = 0
                    fractionAtStart = panelFraction
                    swipeHandled = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - pullStartY
                    // Шапка следует за пальцем: вверх — уменьшается, вниз — растёт.
                    // rawY, потому что список двигается при смене высоты шапки.
                    val y = event.rawY
                    val d = y - lastRawY
                    lastRawY = y
                    if (!ticketsTab && panelFull > 0 && d != 0f) {
                        if (kotlin.math.abs(d) > 1f) lastDir = if (d < 0) -1 else 1
                        val target = (panelFraction + d / panelFull).coerceIn(0f, 1f)
                        if (target != panelFraction) {
                            applyPanelFraction(target)
                            swipeHandled = true
                        }
                    }
                    if (!isPullRefreshing && fractionAtStart >= 1f && !swipeHandled && scroll.scrollY == 0 && dy > 0) {
                        pullDistance = dy
                        val shown = (dy / Ui.dp(this@MainActivity, 100)).coerceIn(0f, 1f)
                        refreshIndicator.visibility = View.VISIBLE
                        refreshIndicator.alpha = shown
                        refreshIndicator.translationY = (dy * 0.45f).coerceAtMost(Ui.dp(this@MainActivity, 64).toFloat())
                        refreshIndicator.rotation = dy * 2f
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (!ticketsTab && panelFraction > 0f && panelFraction < 1f) {
                        // докручиваем до ближайшего состояния с учётом направления
                        val to = when (lastDir) {
                            -1 -> 0f
                            1 -> 1f
                            else -> if (panelFraction < 0.5f) 0f else 1f
                        }
                        animatePanelFraction(to)
                    }
                    if (!isPullRefreshing && fractionAtStart >= 1f && !swipeHandled && scroll.scrollY == 0 && pullDistance >= Ui.dp(this@MainActivity, 80)) {
                        isPullRefreshing = true
                        refreshIndicator.animate().cancel()
                        refreshIndicator.animate()
                            .translationY(Ui.dp(this@MainActivity, 24).toFloat())
                            .alpha(1f)
                            .rotation(0f)
                            .setDuration(180)
                            .withEndAction {
                                refreshIndicator.rotation = 0f
                                spinRefreshIndicator()
                            }
                            .start()
                        search()
                    } else if (!isPullRefreshing) {
                        refreshIndicator.animate().alpha(0f).translationY(0f).setDuration(160)
                            .withEndAction { if (!isPullRefreshing) refreshIndicator.visibility = View.INVISIBLE }
                            .start()
                    }
                    pullDistance = 0f
                }
            }
            false
        }

        fab = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_fab)
            elevation = Ui.dp(context, 6).toFloat()
            setOnClickListener { setTicketsTab(true) }
        }
        fab.addView(ImageView(this).apply { setImageResource(R.drawable.ic_ticket_fab) },
            FrameLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28), Gravity.CENTER))
        body.addView(fab, FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16))
        })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        // Нижняя навигация
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.PANEL)
        }
        val icons = intArrayOf(R.drawable.ic_nav_route, R.drawable.ic_nav_bookmark, R.drawable.ic_nav_ticket, R.drawable.ic_nav_history, R.drawable.ic_nav_settings)
        icons.forEachIndexed { i, res ->
            val v = ImageView(this).apply {
                setImageResource(res)
                setColorFilter(if (i == 0) Ui.NAV_ACTIVE else Ui.NAV_IDLE)
                setPadding(Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16))
                setOnClickListener {
                    when (i) {
                        0 -> setTicketsTab(false)
                        1 -> startActivity(Intent(this@MainActivity, FavoritesActivity::class.java))
                        3 -> openStationSchedule()
                        4 -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        else -> notImplemented()
                    }
                }
            }
            nav.addView(v, LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun badge(letter: String, bg: Int) = TextView(this).apply {
        text = letter
        textSize = 20f
        setTextColor(0xFF201E21.toInt())
        gravity = Gravity.CENTER
        setBackgroundResource(bg)
    }

    private fun fieldText(onClick: () -> Unit) = TextView(this).apply {
        textSize = 22f
        setTextColor(Ui.WHITE)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setOnClickListener { onClick() }
    }

    private fun dayTab(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    // ------------------------------------------------------------------ состояние

    private fun refreshFields() {
        fromText.text = if (fromTitle.isBlank()) "Откуда" else fromTitle
        toText.text = if (toTitle.isBlank()) "Куда" else toTitle
        refreshCollapsedBar()
        refreshBookmarkIcon()
    }

    private fun refreshTabs() {
        ticketsExtra.visibility = if (ticketsTab) View.VISIBLE else View.GONE
        daysRow.visibility = if (ticketsTab) View.GONE else View.VISIBLE
        fab.visibility = if (ticketsTab) View.GONE else View.VISIBLE
        ticketDate.text = Ui.dateLong(currentDate())
    }

    private fun refreshDays() {
        fun style(v: TextView, on: Boolean) {
            v.setTextColor(if (on) Ui.WHITE else Ui.GRAY)
            v.textSize = if (on) 22f else 20f
            v.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        style(dayToday, dayMode == DAY_TODAY)
        style(dayTomorrow, dayMode == DAY_TOMORROW)
        style(dayAll, dayMode == DAY_CUSTOM)
        dayAll.text = if (dayMode == DAY_CUSTOM && customDate.isNotBlank()) Ui.dateShort(customDate) else "Все дни"
        refreshCollapsedBar()
        ticketDate.text = Ui.dateLong(currentDate())
    }

    private fun refreshCollapsedBar() {
        if (!::collapsedBar.isInitialized) return
        val from = fromTitle.ifBlank { "Откуда" }
        val to = toTitle.ifBlank { "Куда" }
        val day = when (dayMode) {
            DAY_TOMORROW -> "Завтра"
            DAY_CUSTOM -> if (customDate.isNotBlank()) Ui.dateShort(customDate) else "Сегодня"
            else -> "Сегодня"
        }
        collapsedFrom.text = from
        collapsedTo.text = to
        collapsedDay.text = day
    }

    private fun measurePanelFull(): Int {
        val width = if (panel.width > 0) panel.width else resources.displayMetrics.widthPixels
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return panel.measuredHeight
    }

    /** Ставит шапку в положение f (0..1): высота панели, высота и прозрачность полоски. */
    private fun applyPanelFraction(f: Float) {
        panelFraction = f.coerceIn(0f, 1f)
        panelCollapsed = panelFraction <= 0f
        val lp = panel.layoutParams
        lp.height = if (panelFraction >= 1f) ViewGroup.LayoutParams.WRAP_CONTENT
        else (panelFull * panelFraction).toInt()
        panel.layoutParams = lp
        // содержимое уезжает вверх вместе с нижним краем шапки
        val shift = if (panelFraction >= 1f) 0f else -(panelFull * (1f - panelFraction))
        for (i in 0 until panel.childCount) panel.getChildAt(i).translationY = shift
        val barLp = collapsedBar.layoutParams
        barLp.height = (Ui.dp(this, 48) * (1f - panelFraction)).toInt()
        collapsedBar.layoutParams = barLp
        collapsedBar.alpha = 1f - panelFraction
        collapsedBar.visibility = if (panelFraction >= 1f) View.GONE else View.VISIBLE
        refreshCollapsedBar()
    }

    private fun animatePanelFraction(to: Float) {
        panelAnimator?.cancel()
        if (panelFull <= 0) panelFull = measurePanelFull()
        panelAnimator = ValueAnimator.ofFloat(panelFraction, to).apply {
            duration = 160
            addUpdateListener { applyPanelFraction(it.animatedValue as Float) }
            start()
        }
    }

    /** Сворачивает/разворачивает шапку целиком (нажатие на полоску, смена вкладки). */
    private fun setPanelCollapsed(collapse: Boolean, animate: Boolean = true) {
        panelAnimator?.cancel()
        panelFull = measurePanelFull()
        val to = if (collapse) 0f else 1f
        if (animate) animatePanelFraction(to) else applyPanelFraction(to)
    }

    private fun setTicketsTab(on: Boolean) {
        if (on) setPanelCollapsed(false, animate = false)
        ticketsTab = on
        refreshTabs()
        if (on) showPrompt() else search()
    }

    private fun swapStations() {
        val c = fromCode; val t = fromTitle
        fromCode = toCode; fromTitle = toTitle
        toCode = c; toTitle = t
        saveStations()
        refreshFields()
        if (!ticketsTab) search()
    }

    private fun saveStations() {
        prefs.edit()
            .putString("from_code", fromCode).putString("from_title", fromTitle)
            .putString("to_code", toCode).putString("to_title", toTitle)
            .apply()
    }

    private fun currentDate(): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return when (dayMode) {
            DAY_TOMORROW -> f.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time)
            DAY_CUSTOM -> customDate.ifBlank { f.format(Date()) }
            else -> f.format(Date())
        }
    }

    private fun openSearch(isFrom: Boolean) {
        pickStation.launch(Intent(this, StationSearchActivity::class.java)
            .putExtra(StationSearchActivity.EXTRA_IS_FROM, isFrom))
    }

    private fun pickDate() {
        pickDateLauncher.launch(Intent(this, DatePickActivity::class.java)
            .putExtra(DatePickActivity.EXTRA_SELECTED, currentDate()))
    }

    /** Четвёртая кнопка: открыть расписание и предложить выбрать станцию. */
    private fun openStationSchedule() {
        startActivity(Intent(this, StationScheduleActivity::class.java))
    }


    private fun refreshBookmarkIcon() {
        if (!::bookmark.isInitialized) return
        bookmark.setImageResource(
            if (isCurrentRouteFavorite()) R.drawable.ic_bookmark_filled
            else R.drawable.ic_bookmark_outline
        )
    }

    private fun isCurrentRouteFavorite(): Boolean {
        if (fromCode.isBlank() || toCode.isBlank()) return false
        val favorites = org.json.JSONArray(prefs.getString("favorite_routes", "[]").orEmpty())
        val routeKey = "$fromCode|$toCode"
        return (0 until favorites.length()).mapNotNull { favorites.optJSONObject(it) }
            .any { "${it.optString("from_code")}|${it.optString("to_code")}" == routeKey }
    }

    private fun addCurrentStationsToFavorites() {
        if (fromCode.isBlank() || fromTitle.isBlank() || toCode.isBlank() || toTitle.isBlank()) {
            Toast.makeText(this, "Выберите станции отправления и назначения", Toast.LENGTH_SHORT).show()
            return
        }
        if (sameDepartureAndArrival()) {
            Toast.makeText(this, "Нельзя добавить маршрут с одинаковыми станциями", Toast.LENGTH_SHORT).show()
            return
        }

        val favorites = org.json.JSONArray(prefs.getString("favorite_routes", "[]").orEmpty())
        val routeKey = "$fromCode|$toCode"
        val existingIndex = (0 until favorites.length()).firstOrNull { i ->
            val item = favorites.optJSONObject(i) ?: return@firstOrNull false
            "${item.optString("from_code")}|${item.optString("to_code")}" == routeKey
        }

        if (existingIndex != null) {
            val updated = org.json.JSONArray()
            for (i in 0 until favorites.length()) {
                if (i != existingIndex) updated.put(favorites.optJSONObject(i))
            }
            prefs.edit().putString("favorite_routes", updated.toString()).apply()
            refreshBookmarkIcon()
            Toast.makeText(this, "Маршрут удалён из избранного", Toast.LENGTH_SHORT).show()
            return
        }

        favorites.put(JSONObject()
            .put("from_code", fromCode)
            .put("from_title", fromTitle)
            .put("to_code", toCode)
            .put("to_title", toTitle))
        prefs.edit().putString("favorite_routes", favorites.toString()).apply()
        refreshBookmarkIcon()
        Toast.makeText(this, "Маршрут добавлен в избранное", Toast.LENGTH_SHORT).show()
    }

    private fun notImplemented() {
        Toast.makeText(this, "Этот раздел пока не реализован", Toast.LENGTH_SHORT).show()
    }

    private fun checkApiKey() {
        if (api.apiKey().isBlank()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("API-ключ не указан")
                .setMessage(
                    "Не найден API-ключ Яндекс Расписаний.\n\n" +
                        "Добавьте YANDEX_RASP_API_KEY в файл api-key.properties и пересоберите приложение " +
                        "или укажите ключ в настройках приложения."
                )
                .setPositiveButton("Настройки") { _, _ -> startActivity(Intent(this, SettingsActivity::class.java)) }
                .setNegativeButton("Закрыть", null)
                .show()
        }
    }

    // ------------------------------------------------------------------ загрузка

    private fun spinRefreshIndicator() {
        if (!isPullRefreshing) return
        refreshIndicator.animate().rotationBy(360f).setDuration(700)
            .withEndAction { spinRefreshIndicator() }
            .start()
    }

    private fun finishPullRefresh() {
        isPullRefreshing = false
        refreshIndicator.animate().cancel()
        refreshIndicator.animate().alpha(0f).translationY(0f).rotation(0f).setDuration(180)
            .withEndAction { refreshIndicator.visibility = View.INVISIBLE }
            .start()
    }

    private fun search() {
        if (ticketsTab) {
            finishPullRefresh()
            return
        }
        if (sameDepartureAndArrival()) {
            job?.cancel()
            finishPullRefresh()
            showSameStationMessage()
            return
        }
        if (fromCode.isBlank()) { finishPullRefresh(); showPrompt(); return }
        val date = currentDate()
        showMessage("Загружаю расписание…")
        job?.cancel()
        job = lifecycleScope.launch {
            try {
                val result = if (toCode.isBlank()) api.stationSchedule(fromCode, date) else api.route(fromCode, toCode, date)
                render(result.json, result.fromCache, result.warning, date)
                finishPullRefresh()
            } catch (e: Exception) {
                finishPullRefresh()
                showMessage("Не удалось загрузить данные.\n${e.message ?: "Неизвестная ошибка"}\n\nПроверьте API-ключ и подключение к интернету.")
            }
        }
    }

    private fun sameDepartureAndArrival(): Boolean {
        val sameCode = fromCode.isNotBlank() && toCode.isNotBlank() &&
            fromCode.equals(toCode, ignoreCase = true)
        val sameTitle = fromTitle.isNotBlank() && toTitle.isNotBlank() &&
            fromTitle.trim().equals(toTitle.trim(), ignoreCase = true)
        return sameCode || sameTitle
    }

    private fun showSameStationMessage() {
        content.removeAllViews()
        nextView = null
        val col = Ui.center(this).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 32), Ui.dp(context, 24), Ui.dp(context, 32), Ui.dp(context, 24))
        }
        col.addView(Ui.text(this, "☹", 64f, Ui.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, Ui.dp(this@MainActivity, 20))
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(Ui.text(
            this,
            "Пункт прибытия совпадает с пунктом отправления",
            20f,
            Ui.WHITE
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(col, LinearLayout.LayoutParams(-1, -1))
    }

    private fun showPrompt() {
        showMessage("Введите станцию отправления и назначения, чтобы найти расписание", true)
    }

    private fun showMessage(message: String, illustration: Boolean = false) {
        content.removeAllViews()
        val col = Ui.center(this).apply { setPadding(Ui.dp(context, 32), Ui.dp(context, 56), Ui.dp(context, 32), Ui.dp(context, 32)) }
        if (illustration) {
            col.addView(ImageView(this).apply { setImageResource(R.drawable.ill_railway) },
                LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)).apply { bottomMargin = Ui.dp(this@MainActivity, 36) })
        }
        col.addView(Ui.text(this, message, 20f, Ui.WHITE).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.1f) })
        content.addView(col, LinearLayout.LayoutParams(-1, -2))
    }

    private fun render(raw: String, cached: Boolean, warning: String?, requestedDate: String) {
        content.removeAllViews()
        nextView = null
        if (cached) content.addView(note("Офлайн: показан сохранённый результат"))
        warning?.let { content.addView(note(it)) }
        try {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("schedule") ?: json.optJSONArray("segments")
            if (arr == null || arr.length() == 0) {
                if (!cached && warning == null) showMessage("На этот день электричек не найдено", true)
                else content.addView(note("На этот день электричек не найдено"))
                return
            }
            val now = System.currentTimeMillis()
            val isToday = requestedDate == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            var lastHeader = ""
            var nextMarked = false
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val thread = item.optJSONObject("thread") ?: JSONObject()
                val departure = item.str("departure").ifBlank { item.str("start_date") }
                if (departure.isBlank()) continue
                val arrival = item.str("arrival")
                val title = thread.str("title").ifBlank { item.str("title") }.ifBlank { "Пригородный поезд" }

                val depMs = Ui.millisOf(departure)
                val arrMs = if (arrival.isBlank()) null else Ui.millisOf(arrival)
                val durationMs: Long? = when {
                    !item.isNull("duration") && item.optDouble("duration", -1.0) > 0 -> (item.optDouble("duration") * 1000).toLong()
                    depMs != null && arrMs != null -> arrMs - depMs
                    else -> null
                }
                val past = isToday && depMs != null && depMs < now
                var caption = ""
                var captionUrgent = false
                if (isToday && !nextMarked && depMs != null && depMs >= now) {
                    val timeUntilDeparture = depMs - now
                    caption = "через " + Ui.duration(timeUntilDeparture)
                    captionUrgent = timeUntilDeparture <= 10 * 60 * 1000L
                    nextMarked = true
                }

                val header = Ui.isoDateOf(departure)
                if (header != lastHeader) {
                    content.addView(Ui.divider(this))
                    content.addView(dateHeader(Ui.dateHeader(header)))
                    lastHeader = header
                }
                content.addView(row(
                    caption, Ui.hhmm(departure), durationMs?.let { Ui.duration(it) }.orEmpty(),
                    Ui.hhmm(arrival), title, price(item), past, captionUrgent
                ))
                content.addView(Ui.divider(this))
            }
            content.addView(note("Источник данных: Яндекс Расписания"))
            // Прокрутка к ближайшему рейсу, чтобы прошедшие не занимали экран
            scroll.post { scrollToNext() }
        } catch (_: Exception) {
            showMessage("Не удалось разобрать ответ API. Проверьте формат ответа.")
        }
    }

    private var nextView: View? = null

    private fun scrollToNext() {
        val v = nextView ?: return
        scroll.smoothScrollTo(0, (v.top - Ui.dp(this, 48)).coerceAtLeast(0))
    }

    private fun price(item: JSONObject): String {
        val places = item.optJSONObject("tickets_info")?.optJSONArray("places") ?: return ""
        for (i in 0 until places.length()) {
            val p = places.optJSONObject(i)?.optJSONObject("price") ?: continue
            if (!p.isNull("whole")) return "${p.optInt("whole")} ₽"
        }
        return ""
    }

    private fun dateHeader(s: String) = Ui.text(this, s, 15f, Ui.GRAY).apply {
        letterSpacing = 0.12f
        setBackgroundColor(0xFF3A3A3A.toInt())
        setPadding(Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun note(s: String) = Ui.text(this, s, 14f, Ui.GRAY).apply {
        setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 14))
    }

    private fun row(caption: String, dep: String, dur: String, arr: String, title: String, price: String, past: Boolean, captionUrgent: Boolean): View {
        val timeColor = if (past) Ui.GRAY else Ui.WHITE
        val arrColor = if (past) Ui.GRAY_DIM else Ui.GRAY_LIGHT
        val titleColor = if (past) Ui.GRAY_DIM else Ui.GRAY_LIGHT
        val priceColor = if (past) Ui.GRAY else Ui.WHITE

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16))
        }
        if (caption.isNotBlank()) {
            box.addView(Ui.text(this, caption, 14f, if (captionUrgent) 0xFFF44336.toInt() else Ui.GRAY_LIGHT).apply {
                setPadding(0, 0, 0, Ui.dp(context, 6))
            })
            nextView = box
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        line.addView(Ui.text(this, dep, 28f, timeColor, true))
        if (dur.isNotBlank()) {
            line.addView(Ui.text(this, dur, 14f, Ui.GRAY).apply { setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), Ui.dp(context, 4)) })
        }
        if (arr.isNotBlank()) line.addView(Ui.text(this, arr, 28f, arrColor))
        line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (price.isNotBlank()) line.addView(Ui.text(this, price, 24f, priceColor))
        box.addView(line, LinearLayout.LayoutParams(-1, -2))
        box.addView(Ui.text(this, title, 18f, titleColor).apply { setPadding(0, Ui.dp(context, 6), 0, 0) })
        return box
    }

    companion object {
        const val EXTRA_FROM_CODE = "favorite_from_code"
        const val EXTRA_FROM_TITLE = "favorite_from_title"
        const val EXTRA_TO_CODE = "favorite_to_code"
        const val EXTRA_TO_TITLE = "favorite_to_title"
        private const val DAY_TODAY = 0
        private const val DAY_TOMORROW = 1
        private const val DAY_CUSTOM = 2
    }
}
