package ru.elektrichki.russia

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.launch
import ru.elektrichki.russia.data.ScheduleDatabase
import ru.elektrichki.russia.data.YandexRaspApi

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = YandexRaspApi(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true
        }
        setContentView(root)

        root.addView(Ui.text(this, "Настройки", 26f, Ui.WHITE, true).apply {
            setBackgroundColor(Ui.PANEL)
            setPadding(Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20))
        })

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 16))
        }
        root.addView(body)

        body.addView(Ui.text(this, "API-ключ Яндекс Расписаний", 16f, Ui.GRAY_LIGHT))
        val key = EditText(this).apply {
            hint = "Вставьте API-ключ"
            textSize = 18f
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.GRAY)
            setBackgroundColor(Ui.PANEL)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(api.apiKey())
        }
        body.addView(key, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

        body.addView(yellowButton("Сохранить ключ") {
            api.saveApiKey(key.text.toString())
            Toast.makeText(this, "Ключ сохранён на устройстве", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, Ui.dp(this, 48)).apply { topMargin = Ui.dp(this@SettingsActivity, 16) })

        body.addView(Ui.text(this,
            "Получите ключ в Кабинете разработчика Яндекса и активируйте API Расписаний. " +
                "Ключ хранится локально в настройках приложения. Не публикуйте его в Git.",
            14f, Ui.GRAY).apply { setPadding(0, Ui.dp(context, 16), 0, Ui.dp(context, 16)); setLineSpacing(0f, 1.1f) })

        body.addView(darkButton("Очистить офлайн-кэш") {
            lifecycleScope.launch {
                ScheduleDatabase.get(this@SettingsActivity).scheduleDao().clear()
                runOnUiThread { Toast.makeText(this@SettingsActivity, "Кэш очищен", Toast.LENGTH_SHORT).show() }
            }
        }, LinearLayout.LayoutParams(-1, Ui.dp(this, 48)))
        body.addView(darkButton("Назад") { finish() },
            LinearLayout.LayoutParams(-1, Ui.dp(this, 48)).apply { topMargin = Ui.dp(this@SettingsActivity, 12) })
    }

    private fun yellowButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 18f
        setTextColor(0xFF201E21.toInt())
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.bg_button_yellow)
        setOnClickListener { onClick() }
    }

    private fun darkButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 18f
        setTextColor(Ui.WHITE)
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.bg_segment_selected)
        setOnClickListener { onClick() }
    }
}
