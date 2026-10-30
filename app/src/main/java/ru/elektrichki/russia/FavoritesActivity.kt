package ru.elektrichki.russia

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject

/** Список сохранённых маршрутов. */
class FavoritesActivity : BaseActivity() {
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private lateinit var actionBar: LinearLayout
    private lateinit var nav: LinearLayout

    /** routeKey выбранного по long-press маршрута; null — режим выбора выключен. */
    private var selectedKey: String? = null

    /** Временный разворот Откуда/Куда в списке (без открытия главного экрана). */
    private val flippedKeys = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.PANEL
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true
        }
        root.addView(Ui.text(this, "Избранное", 24f, Ui.WHITE, true).apply {
            setBackgroundColor(Ui.PANEL)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
        }, LinearLayout.LayoutParams(-1, Ui.dp(this, 64)))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(list, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Нижняя шапка действий (как на скриншоте): назад + корзина. Появляется после long-press.
        actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.PANEL)
            visibility = View.GONE
            minimumHeight = Ui.dp(context, 56)
        }
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(Ui.WHITE)
            setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16))
            contentDescription = "Отменить выбор"
            setOnClickListener { clearSelection() }
        }
        actionBar.addView(backBtn, LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)))
        actionBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val deleteBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_delete)
            setColorFilter(Ui.WHITE)
            setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16))
            contentDescription = "Удалить из избранного"
            setOnClickListener {
                val key = selectedKey
                if (key != null) {
                    removeFavorite(key)
                    clearSelection()
                }
            }
        }
        actionBar.addView(deleteBtn, LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)))
        root.addView(actionBar, LinearLayout.LayoutParams(-1, Ui.dp(this, 56)))

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.PANEL)
        }
        val icons = intArrayOf(
            R.drawable.ic_nav_route,
            R.drawable.ic_nav_bookmark,
            R.drawable.ic_nav_ticket,
            R.drawable.ic_nav_history,
            R.drawable.ic_nav_settings
        )
        icons.forEachIndexed { i, res ->
            val v = ImageView(this).apply {
                setImageResource(res)
                setColorFilter(if (i == 1) Ui.NAV_ACTIVE else Ui.NAV_IDLE)
                setPadding(Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16))
                setOnClickListener {
                    when (i) {
                        0 -> finish()
                        1 -> {
                            clearSelection()
                            renderFavorites()
                        }
                        3 -> startActivity(Intent(this@FavoritesActivity, StationScheduleActivity::class.java))
                        4 -> startActivity(Intent(this@FavoritesActivity, SettingsActivity::class.java))
                    }
                }
            }
            nav.addView(v, LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1f))
        }
        root.addView(nav)
        setContentView(root)
        renderFavorites()
    }

    override fun onBackPressed() {
        if (selectedKey != null) {
            clearSelection()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun clearSelection() {
        selectedKey = null
        actionBar.visibility = View.GONE
        nav.visibility = View.VISIBLE
        renderFavorites()
    }

    private fun enterSelection(routeKey: String) {
        selectedKey = routeKey
        actionBar.visibility = View.VISIBLE
        nav.visibility = View.GONE
        renderFavorites()
    }

    private fun renderFavorites() {
        list.removeAllViews()
        val favorites = readFavorites()
        if (favorites.length() == 0) {
            clearSelectionUiOnly()
            list.addView(Ui.text(
                this,
                "Здесь будут маршруты, добавленные в избранное с главного экрана.\n\nВыберите станции «Откуда» и «Куда», затем нажмите значок закладки.",
                18f,
                Ui.GRAY
            ).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(context, 28), Ui.dp(context, 48), Ui.dp(context, 28), Ui.dp(context, 48))
            }, LinearLayout.LayoutParams(-1, -1))
            return
        }
        for (i in 0 until favorites.length()) {
            val item = favorites.optJSONObject(i) ?: continue
            val savedFromCode = item.optString("from_code")
            val savedFromTitle = item.optString("from_title", "Откуда")
            val savedToCode = item.optString("to_code")
            val savedToTitle = item.optString("to_title", "Куда")
            val routeKey = "$savedFromCode|$savedToCode"
            val flipped = routeKey in flippedKeys
            val fromCode = if (flipped) savedToCode else savedFromCode
            val fromTitle = if (flipped) savedToTitle else savedFromTitle
            val toCode = if (flipped) savedFromCode else savedToCode
            val toTitle = if (flipped) savedFromTitle else savedToTitle
            val selected = selectedKey == routeKey
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (selected) 0xFF3A3A3A.toInt() else Ui.PANEL)
                setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12))
                isLongClickable = true
                setOnLongClickListener {
                    enterSelection(routeKey)
                    true
                }
            }
            row.addView(
                Ui.icon(this, R.drawable.ic_nav_route, 24),
                LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)).apply {
                    marginEnd = Ui.dp(this@FavoritesActivity, 16)
                }
            )
            val titles = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (selectedKey != null) {
                        enterSelection(routeKey)
                    } else {
                        openRoute(fromCode, fromTitle, toCode, toTitle)
                    }
                }
                addView(Ui.text(this@FavoritesActivity, fromTitle, 18f, Ui.WHITE, true))
                addView(Ui.text(this@FavoritesActivity, "↓  $toTitle", 16f, Ui.GRAY_LIGHT))
            }
            row.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
            // Только меняет местами Откуда/Куда в списке; главный экран не открывает
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_swap)
                setColorFilter(if (flipped) Ui.NAV_ACTIVE else Ui.GRAY_LIGHT)
                setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
                contentDescription = "Поменять станции местами"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (routeKey in flippedKeys) flippedKeys.remove(routeKey)
                    else flippedKeys.add(routeKey)
                    renderFavorites()
                }
            }, LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)))
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
            list.addView(Ui.divider(this))
        }
    }

    /** Скрыть action bar без перерисовки списка (для пустого состояния). */
    private fun clearSelectionUiOnly() {
        selectedKey = null
        if (::actionBar.isInitialized) actionBar.visibility = View.GONE
        if (::nav.isInitialized) nav.visibility = View.VISIBLE
    }

    private fun openRoute(fromCode: String, fromTitle: String, toCode: String, toTitle: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FROM_CODE, fromCode)
                .putExtra(MainActivity.EXTRA_FROM_TITLE, fromTitle)
                .putExtra(MainActivity.EXTRA_TO_CODE, toCode)
                .putExtra(MainActivity.EXTRA_TO_TITLE, toTitle)
        )
    }

    private fun readFavorites(): JSONArray = try {
        JSONArray(prefs.getString("favorite_routes", "[]").orEmpty())
    } catch (_: Exception) {
        JSONArray()
    }

    private fun removeFavorite(routeKey: String) {
        val old = readFavorites()
        val updated = JSONArray()
        for (i in 0 until old.length()) {
            val item: JSONObject = old.optJSONObject(i) ?: continue
            val key = "${item.optString("from_code")}|${item.optString("to_code")}"
            if (key != routeKey) updated.put(item)
        }
        prefs.edit().putString("favorite_routes", updated.toString()).apply()
        renderFavorites()
    }
}
