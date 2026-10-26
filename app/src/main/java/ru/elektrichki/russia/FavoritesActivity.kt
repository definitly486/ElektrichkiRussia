package ru.elektrichki.russia

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

/** Список сохранённых маршрутов. */
class FavoritesActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private lateinit var list: LinearLayout

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

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.PANEL)
        }
        val icons = intArrayOf(R.drawable.ic_nav_route, R.drawable.ic_nav_bookmark, R.drawable.ic_nav_ticket, R.drawable.ic_nav_history, R.drawable.ic_nav_settings)
        icons.forEachIndexed { i, res ->
            val v = ImageView(this).apply {
                setImageResource(res)
                setColorFilter(if (i == 1) Ui.NAV_ACTIVE else Ui.NAV_IDLE)
                setPadding(Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16))
                setOnClickListener {
                    when (i) {
                        0 -> finish()
                        1 -> renderFavorites()
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

    private fun renderFavorites() {
        list.removeAllViews()
        val favorites = readFavorites()
        if (favorites.length() == 0) {
            list.addView(Ui.text(this, "Здесь будут маршруты, добавленные в избранное с главного экрана.\n\nВыберите станции «Откуда» и «Куда», затем нажмите значок закладки.", 18f, Ui.GRAY).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(context, 28), Ui.dp(context, 48), Ui.dp(context, 28), Ui.dp(context, 48))
            }, LinearLayout.LayoutParams(-1, -1))
            return
        }
        for (i in 0 until favorites.length()) {
            val item = favorites.optJSONObject(i) ?: continue
            val fromCode = item.optString("from_code")
            val fromTitle = item.optString("from_title", "Откуда")
            val toCode = item.optString("to_code")
            val toTitle = item.optString("to_title", "Куда")
            val routeKey = "$fromCode|$toCode"
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Ui.PANEL)
                setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12))
                setOnClickListener {
                    startActivity(Intent(this@FavoritesActivity, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_FROM_CODE, fromCode)
                        .putExtra(MainActivity.EXTRA_FROM_TITLE, fromTitle)
                        .putExtra(MainActivity.EXTRA_TO_CODE, toCode)
                        .putExtra(MainActivity.EXTRA_TO_TITLE, toTitle))
                }
            }
            row.addView(Ui.icon(this, R.drawable.ic_nav_route, 24), LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)).apply {
                marginEnd = Ui.dp(this@FavoritesActivity, 16)
            })
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(this@FavoritesActivity, fromTitle, 18f, Ui.WHITE, true))
                addView(Ui.text(this@FavoritesActivity, "↓  $toTitle", 16f, Ui.GRAY_LIGHT))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(Ui.GRAY)
                setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
                contentDescription = "Удалить маршрут из избранного"
                setOnClickListener { removeFavorite(routeKey) }
            }, LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)))
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
            list.addView(Ui.divider(this))
        }
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
