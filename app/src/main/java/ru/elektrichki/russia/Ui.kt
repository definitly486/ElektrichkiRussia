package ru.elektrichki.russia

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Цвета и мелкие помощники в стиле Яндекс Электричек (тёмная тема). */
object Ui {
    val BG = 0xFF333333.toInt()
    val PANEL = 0xFF222222.toInt()
    val DIVIDER = 0xFF4A4A4A.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val GRAY = 0xFF8A8A8A.toInt()
    val GRAY_LIGHT = 0xFFB5B5B5.toInt()
    val GRAY_DIM = 0xFF6E6E6E.toInt()
    val YELLOW = 0xFFFFCC00.toInt()
    val NAV_ACTIVE = 0xFFF5C842.toInt()
    val NAV_IDLE = 0xFF9A9A9A.toInt()

    private val MONTHS_GEN = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()

    fun text(context: Context, s: String, sizeSp: Float, color: Int, bold: Boolean = false) =
        TextView(context).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }

    fun icon(context: Context, res: Int, sizeDp: Int, tint: Int? = null) =
        ImageView(context).apply {
            setImageResource(res)
            if (tint != null) setColorFilter(tint)
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
        }

    fun divider(context: Context, color: Int = DIVIDER) = View(context).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(-1, 1)
    }

    /** Центрированная колонка. */
    fun center(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    /** "2026-10-26" -> "26 ОКТЯБРЯ" */
    fun dateHeader(isoDate: String): String {
        val p = isoDate.split("-")
        if (p.size != 3) return isoDate.uppercase(Locale("ru", "RU"))
        val m = (p[1].toIntOrNull() ?: 1).coerceIn(1, 12) - 1
        return "${p[2].toIntOrNull() ?: p[2]} ${MONTHS_GEN[m]}".uppercase(Locale("ru", "RU"))
    }

    /** "2026-10-26" -> "26 октября" */
    fun dateLong(isoDate: String): String {
        val p = isoDate.split("-")
        if (p.size != 3) return isoDate
        val m = (p[1].toIntOrNull() ?: 1).coerceIn(1, 12) - 1
        return "${p[2].toIntOrNull() ?: p[2]} ${MONTHS_GEN[m]}"
    }

    /** "2026-10-26" -> "26 окт" */
    fun dateShort(isoDate: String): String {
        val p = isoDate.split("-")
        if (p.size != 3) return isoDate
        val m = (p[1].toIntOrNull() ?: 1).coerceIn(1, 12) - 1
        return "${p[2].toIntOrNull() ?: p[2]} ${MONTHS_GEN[m].take(3)}"
    }

    /** На Android optString для JSON null возвращает "null" — здесь пустая строка. */
    fun JSONObject.str(key: String): String =
        if (isNull(key)) "" else optString(key, "").trim()

    /** Локальная дата из строки вида 2026-10-26T10:15:00+03:00. */
    fun isoDateOf(s: String): String = s.replace(' ', 'T').take(10)

    /** Локальное время станции HH:mm. */
    fun hhmm(s: String): String {
        val t = s.replace(' ', 'T')
        return if (t.length >= 16 && t[10] == 'T') t.substring(11, 16) else ""
    }

    /** Момент времени в мс; без смещения в строке считается временем устройства. */
    fun millisOf(s: String): Long? {
        return try {
            val t = s.replace(' ', 'T')
            if (t.length < 19) return null
            val rest = t.substring(19)
            val m = Regex("([+-])(\\d{2}):?(\\d{2})$").find(rest)
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            if (m != null) fmt.timeZone = TimeZone.getTimeZone("UTC")
            var ms = fmt.parse(t.substring(0, 19))?.time ?: return null
            if (m != null) {
                val sign = if (m.groupValues[1] == "-") -1 else 1
                val off = (m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()) * 60_000L
                ms -= sign * off
            }
            ms
        } catch (_: Exception) {
            null
        }
    }

    /** 8520000 мс -> "2ч 22мин"; 3600000 -> "1ч"; 600000 -> "10мин" */
    fun duration(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}ч ${m}мин"
            h > 0 -> "${h}ч"
            else -> "${m}мин"
        }
    }
}
