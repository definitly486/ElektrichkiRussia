package ru.elektrichki.russia

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.launch
import ru.elektrichki.russia.data.ApiKeyFile
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
        // Содержимое прокручивается, а нижняя навигация остаётся на месте
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

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

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        body.addView(Ui.text(this, "Зашифрованный файл с ключом (api.enc)", 14f, Ui.GRAY_LIGHT).apply {
            setPadding(0, Ui.dp(context, 20), 0, 0)
        })
        val url = EditText(this).apply {
            hint = ApiKeyFile.DEFAULT_URL
            textSize = 14f
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.GRAY)
            setBackgroundColor(Ui.PANEL)
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString(KEY_FILE_URL, ApiKeyFile.DEFAULT_URL))
        }
        body.addView(url, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

        var busy = false
        body.addView(darkButton("Скачать ключ из файла") {
            if (busy) return@darkButton
            val address = url.text.toString().trim().ifEmpty { ApiKeyFile.DEFAULT_URL }
            prefs.edit().putString(KEY_FILE_URL, address).apply()
            askPassword { password ->
                busy = true
                Toast.makeText(this, "Скачиваю и расшифровываю…", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    try {
                        val encrypted = ApiKeyFile.download(address)
                        val plain = ApiKeyFile.decrypt(encrypted, password)
                        key.setText(plain)
                        key.setSelection(key.text.length)
                        Toast.makeText(this@SettingsActivity,
                            "Ключ расшифрован и подставлен. Нажмите «Сохранить ключ».", Toast.LENGTH_LONG).show()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: ApiKeyFile.WrongPasswordException) {
                        Toast.makeText(this@SettingsActivity, "Неверный пароль или повреждённый файл", Toast.LENGTH_LONG).show()
                    } catch (e: ApiKeyFile.BadFormatException) {
                        Toast.makeText(this@SettingsActivity,
                            "Файл не в формате openssl enc (нет заголовка Salted__)", Toast.LENGTH_LONG).show()
                    } catch (e: java.io.IOException) {
                        Toast.makeText(this@SettingsActivity,
                            "Не удалось скачать файл: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(this@SettingsActivity, e.message ?: "Неверные данные", Toast.LENGTH_LONG).show()
                    } catch (e: IllegalStateException) {
                        Toast.makeText(this@SettingsActivity, e.message ?: "Ошибка", Toast.LENGTH_LONG).show()
                    } finally {
                        busy = false
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, Ui.dp(this, 48)).apply { topMargin = Ui.dp(this@SettingsActivity, 12) })

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

        // Нижняя навигация, как на остальных экранах; активна пятая иконка
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.PANEL)
        }
        val icons = intArrayOf(R.drawable.ic_nav_route, R.drawable.ic_nav_bookmark, R.drawable.ic_nav_ticket, R.drawable.ic_nav_history, R.drawable.ic_nav_settings)
        icons.forEachIndexed { i, res ->
            val v = ImageView(this).apply {
                setImageResource(res)
                setColorFilter(if (i == 4) Ui.NAV_ACTIVE else Ui.NAV_IDLE)
                setPadding(Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16))
                setOnClickListener {
                    when (i) {
                        0 -> finish()
                        1 -> startActivity(Intent(this@SettingsActivity, FavoritesActivity::class.java))
                        3 -> startActivity(Intent(this@SettingsActivity, StationScheduleActivity::class.java))
                        4 -> {}
                        else -> Toast.makeText(this@SettingsActivity, "Этот раздел пока не реализован", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            nav.addView(v, LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1f))
        }
        root.addView(nav)
    }

    /** Диалог ввода пароля от api.enc; пароль нигде не сохраняется. */
    private fun askPassword(onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Пароль от файла"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.GRAY)
        }
        val pad = Ui.dp(this, 20)
        val frame = FrameLayout(this).apply { setPadding(pad, Ui.dp(context, 8), pad, 0); addView(input) }
        AlertDialog.Builder(this)
            .setTitle("Расшифровка ключа")
            .setView(frame)
            .setPositiveButton("Расшифровать") { _, _ ->
                val password = input.text.toString()
                if (password.isEmpty()) Toast.makeText(this, "Введите пароль", Toast.LENGTH_SHORT).show()
                else onOk(password)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private companion object {
        const val KEY_FILE_URL = "key_file_url"
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
