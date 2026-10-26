package ru.elektrichki.russia

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.elektrichki.russia.data.StationMatch
import ru.elektrichki.russia.data.YandexRaspApi

/** Полноэкранный поиск станции: стрелка назад, поле ввода, жёлтая линия, список результатов. */
class StationSearchActivity : AppCompatActivity() {
    private lateinit var api: YandexRaspApi
    private lateinit var results: LinearLayout
    private lateinit var input: EditText
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = YandexRaspApi(this)
        window.statusBarColor = Ui.PANEL
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val isFrom = intent.getBooleanExtra(EXTRA_IS_FROM, true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.PANEL)
            setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
        }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
            setOnClickListener { finish() }
        }
        bar.addView(back, LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 56)))

        input = EditText(this).apply {
            hint = if (isFrom) "Откуда" else "Куда"
            textSize = 22f
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.GRAY)
            background = null
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
        }
        bar.addView(input, LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f))

        val clear = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
            setOnClickListener { input.setText("") }
        }
        bar.addView(clear, LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 56)))
        root.addView(bar)

        val line = View(this).apply { setBackgroundColor(Ui.YELLOW) }
        root.addView(line, LinearLayout.LayoutParams(-1, Ui.dp(this, 2)).apply {
            marginStart = Ui.dp(this@StationSearchActivity, 16)
            marginEnd = Ui.dp(this@StationSearchActivity, 16)
        })

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(results, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clear.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
                scheduleSearch(s?.toString().orEmpty().trim())
            }
        })
        clear.visibility = View.INVISIBLE
        input.requestFocus()
        input.post {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun scheduleSearch(query: String) {
        job?.cancel()
        results.removeAllViews()
        if (query.length < 2) return
        job = lifecycleScope.launch {
            delay(300)
            showInfo("Ищу станции…")
            try {
                val matches = api.searchStations(query)
                results.removeAllViews()
                if (matches.isEmpty()) showInfo("Ничего не найдено") else matches.forEach { addRow(it) }
            } catch (e: Exception) {
                showInfo("Не удалось найти станции.\n${e.message ?: "Проверьте API-ключ и подключение к интернету."}")
            }
        }
    }

    private fun showInfo(message: String) {
        results.removeAllViews()
        results.addView(Ui.text(this, message, 16f, Ui.GRAY).apply {
            setPadding(Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20))
        })
    }

    private fun addRow(m: StationMatch) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.PANEL)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 14))
            isClickable = true
            setOnClickListener {
                setResult(RESULT_OK, Intent()
                    .putExtra(EXTRA_IS_FROM, intent.getBooleanExtra(EXTRA_IS_FROM, true))
                    .putExtra(EXTRA_CODE, m.code)
                    .putExtra(EXTRA_TITLE, m.title))
                finish()
            }
        }
        row.addView(Ui.icon(this, R.drawable.ic_station, 24),
            LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)).apply { marginEnd = Ui.dp(this@StationSearchActivity, 20) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(this, m.title, 20f, Ui.WHITE, true))
        val sub = listOf(m.settlement, m.region).filter { it.isNotBlank() }.distinct().joinToString(" • ")
        if (sub.isNotBlank()) {
            col.addView(Ui.text(this, sub, 15f, Ui.GRAY).apply { setPadding(0, Ui.dp(context, 4), 0, 0) })
        }
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        results.addView(row, LinearLayout.LayoutParams(-1, -2))
        results.addView(Ui.divider(this))
    }

    companion object {
        const val EXTRA_IS_FROM = "is_from"
        const val EXTRA_CODE = "code"
        const val EXTRA_TITLE = "title"
    }
}
