package ru.elektrichki.russia

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.Calendar
import java.util.Locale

/** Полноэкранный выбор даты: список месяцев с сеткой дней (ПН–ВС), как в Яндекс Электричках. */
class DatePickActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.PANEL

        val today = Calendar.getInstance().stripTime()
        val selected = parseIso(intent.getStringExtra(EXTRA_SELECTED)) ?: today

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true
        }

        // Шапка: стрелка назад + «Выбор даты»
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.PANEL)
            setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
        }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 56)))
        bar.addView(Ui.text(this, "Выбор даты", 24f, Ui.WHITE, true).apply {
            setPadding(Ui.dp(context, 16), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar, LinearLayout.LayoutParams(-1, Ui.dp(this, 56)))

        // Месяцы
        val months = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val cursor = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        repeat(MONTHS_SHOWN) {
            months.addView(monthView(cursor, today, selected))
            cursor.add(Calendar.MONTH, 1)
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(months, FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun monthView(first: Calendar, today: Calendar, selected: Calendar): View {
        val month = first.get(Calendar.MONTH)
        val year = first.get(Calendar.YEAR)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Заголовок месяца: «октябрь ........ 2026»
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 10), Ui.dp(context, 16), Ui.dp(context, 10))
        }
        header.addView(Ui.text(this, MONTHS_NOM[month], 22f, Ui.GRAY), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Ui.text(this, year.toString(), 22f, Ui.GRAY))
        box.addView(header)
        box.addView(Ui.divider(this, Ui.GRAY_DIM))

        // Дни недели
        val names = arrayOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
        val week = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(context, 8), Ui.dp(context, 18), Ui.dp(context, 8), Ui.dp(context, 6))
        }
        names.forEachIndexed { i, n ->
            week.addView(
                Ui.text(this, n, 18f, if (i >= 5) RED else Ui.WHITE).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, -2, 1f)
            )
        }
        box.addView(week)

        // Сетка: смещение первого числа (понедельник = 0)
        val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        var day = 1 - offset
        while (day <= daysInMonth) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
            }
            for (col in 0 until 7) {
                val cell = FrameLayout(this)
                if (day in 1..daysInMonth) {
                    val date = Calendar.getInstance().apply { clear(); set(year, month, day) }
                    cell.addView(dayView(date, col >= 5, today, selected))
                }
                row.addView(cell, LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1f))
                day++
            }
            box.addView(row, LinearLayout.LayoutParams(-1, Ui.dp(this, 50)))
        }
        box.addView(View(this), LinearLayout.LayoutParams(-1, Ui.dp(this, 12)))
        return box
    }

    private fun dayView(date: Calendar, weekend: Boolean, today: Calendar, selected: Calendar): View {
        val past = date.before(today)
        val isSelected = date.timeInMillis == selected.timeInMillis
        return TextView(this).apply {
            text = date.get(Calendar.DAY_OF_MONTH).toString()
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                setMargins(Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2), Ui.dp(context, 2))
            }
            when {
                isSelected -> {
                    setBackgroundResource(R.drawable.bg_date_selected)
                    setTextColor(0xFF201E21.toInt())
                }
                past -> setTextColor(Ui.GRAY_DIM)
                weekend -> setTextColor(RED)
                else -> setTextColor(Ui.WHITE)
            }
            if (!past) {
                setOnClickListener {
                    val iso = String.format(
                        Locale.US, "%04d-%02d-%02d",
                        date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH)
                    )
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_DATE, iso))
                    finish()
                }
            }
        }
    }

    private fun Calendar.stripTime(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun parseIso(s: String?): Calendar? {
        val p = s?.split("-") ?: return null
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        return Calendar.getInstance().apply { clear(); set(y, m - 1, d) }
    }

    companion object {
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_DATE = "date"
        private const val MONTHS_SHOWN = 12
        private val RED = 0xFFFF3B30.toInt()
        private val MONTHS_NOM = arrayOf(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
        )
    }
}
