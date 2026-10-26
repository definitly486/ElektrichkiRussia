package ru.elektrichki.russia

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.elektrichki.russia.data.ScheduleDatabase
import ru.elektrichki.russia.data.YandexRaspApi

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = YandexRaspApi(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(0xFFF5F3F1.toInt())
        }
        setContentView(root)
        root.addView(TextView(this).apply {
            text = "Настройки"; textSize = 26f; setTextColor(0xFF201E21.toInt())
        })
        root.addView(TextView(this).apply {
            text = "API-ключ Яндекс Расписаний"; textSize = 16f; setPadding(0, 18, 0, 6)
            setTextColor(0xFF201E21.toInt())
        })
        val key = EditText(this).apply {
            hint = "Вставьте API-ключ"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(api.apiKey())
        }
        root.addView(key)
        root.addView(Button(this).apply {
            text = "Сохранить ключ"
            setOnClickListener {
                api.saveApiKey(key.text.toString())
                Toast.makeText(this@SettingsActivity, "Ключ сохранён на устройстве", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "Получите ключ в Кабинете разработчика Яндекса и активируйте API Расписаний. Ключ хранится локально в настройках приложения. Не публикуйте его в Git."
            textSize = 14f; setPadding(0, 12, 0, 12); setTextColor(0xFF625E62.toInt())
        })
        root.addView(Button(this).apply {
            text = "Очистить офлайн-кэш"
            setOnClickListener {
                lifecycleScope.launch {
                    ScheduleDatabase.get(this@SettingsActivity).scheduleDao().clear()
                    runOnUiThread { Toast.makeText(this@SettingsActivity, "Кэш очищен", Toast.LENGTH_SHORT).show() }
                }
            }
        })
        root.addView(Button(this).apply { text = "Назад"; setOnClickListener { finish() } })
    }
}
