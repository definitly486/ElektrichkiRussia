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

/** Список сохранённых станций. */
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
            list.addView(Ui.text(this, "Здесь будут станции, добавленные в избранное с главного экрана.\n\nВыберите станцию отправления или назначения и нажмите значок закладки.", 18f, Ui.GRAY).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(context, 28), Ui.dp(context, 48), Ui.dp(context, 28), Ui.dp(context, 48))
            }, LinearLayout.LayoutParams(-1, -1))
            return
        }
        for (i in 0 until favorites.length()) {
            val item = favorites.optJSONObject(i) ?: continue
            val code = item.optString("code")
            val title = item.optString("title", "Станция")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Ui.PANEL)
                setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12))
                setOnClickListener {
                    startActivity(Intent(this@FavoritesActivity, StationScheduleActivity::class.java)
                        .putExtra(StationScheduleActivity.EXTRA_CODE, code)
                        .putExtra(StationScheduleActivity.EXTRA_TITLE, title))
                }
            }
            row.addView(Ui.icon(this, R.drawable.ic_station, 24), LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)).apply {
                marginEnd = Ui.dp(this@FavoritesActivity, 16)
            })
            row.addView(Ui.text(this, title, 19f, Ui.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(Ui.GRAY)
                setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
                contentDescription = "Удалить из избранного"
                setOnClickListener { removeFavorite(code) }
            }, LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)))
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
            list.addView(Ui.divider(this))
        }
    }

    private fun readFavorites(): JSONArray = try {
        JSONArray(prefs.getString("favorite_stations", "[]").orEmpty())
    } catch (_: Exception) {
        JSONArray()
    }

    private fun removeFavorite(code: String) {
        val old = readFavorites()
        val updated = JSONArray()
        for (i in 0 until old.length()) {
            val item: JSONObject = old.optJSONObject(i) ?: continue
            if (item.optString("code") != code) updated.put(item)
        }
        prefs.edit().putString("favorite_stations", updated.toString()).apply()
        renderFavorites()
    }
}
