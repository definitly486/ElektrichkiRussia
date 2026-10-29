package ru.elektrichki.russia

import android.app.Activity
import android.content.Intent
import android.util.SparseArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Результат вызванной activity (замена androidx.activity.result.ActivityResult). */
class ActivityResult(val resultCode: Int, val data: Intent?)

/** Запускает activity и отдаёт результат в колбэк (замена ActivityResultLauncher). */
class ResultLauncher internal constructor(private val activity: Activity, private val requestCode: Int) {
    @Suppress("DEPRECATION")
    fun launch(intent: Intent) = activity.startActivityForResult(intent, requestCode)
}

/**
 * Минимальная база без AndroidX: корутинный scope, отменяемый вместе с экраном,
 * и регистрация колбэков результата. Позволяет не тянуть в APK AndroidX и его ресурсы.
 */
abstract class BaseActivity : Activity() {
    val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val callbacks = SparseArray<(ActivityResult) -> Unit>()
    private var nextCode = 1

    protected fun registerForResult(callback: (ActivityResult) -> Unit): ResultLauncher {
        val code = nextCode++
        callbacks.put(code, callback)
        return ResultLauncher(this, code)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        callbacks[requestCode]?.invoke(ActivityResult(resultCode, data))
    }

    override fun onDestroy() {
        lifecycleScope.cancel()
        super.onDestroy()
    }
}
