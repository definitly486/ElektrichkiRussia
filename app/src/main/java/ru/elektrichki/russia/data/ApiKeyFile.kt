package ru.elektrichki.russia.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Скачивание и расшифровка файла с API-ключом, зашифрованного командой
 *
 *     openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -in api -out api.enc
 *
 * Формат файла openssl: "Salted__" + 8 байт соли + шифртекст AES-256-CBC (PKCS#7).
 * Ключ (32 байта) и IV (16 байт) получаются из пароля через PBKDF2-HMAC-SHA256
 * (digest по умолчанию в openssl 1.1.1+), 200000 итераций.
 *
 * PBKDF2 реализован вручную поверх HmacSHA256: фабрика PBKDF2WithHmacSHA256 есть только с API 26,
 * а minSdk приложения — 23.
 */
object ApiKeyFile {
    const val DEFAULT_URL =
        "https://raw.githubusercontent.com/definitly486/ElektrichkiRussia/feature/swipe-collapse-header/api.enc"

    private const val ITERATIONS = 200_000
    private const val SALT_LEN = 8
    private const val KEY_LEN = 32
    private const val IV_LEN = 16
    private const val MAX_FILE_BYTES = 64 * 1024
    private const val MAX_KEY_CHARS = 512
    private val MAGIC = "Salted__".toByteArray(Charsets.US_ASCII)

    /** Пароль не подошёл (или файл повреждён): расшифровка не дала осмысленного ключа. */
    class WrongPasswordException : Exception()

    /** Файл не похож на вывод `openssl enc` с солью. */
    class BadFormatException : Exception()

    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Адрес файла должен начинаться с https://" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Сервер вернул HTTP $code. Проверьте адрес файла.")
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_FILE_BYTES) throw IllegalStateException("Файл слишком большой для файла с ключом.")
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    /** Расшифровывает содержимое api.enc и возвращает ключ без пробелов и переводов строк. */
    suspend fun decrypt(encrypted: ByteArray, password: String): String = withContext(Dispatchers.Default) {
        require(password.isNotEmpty()) { "Введите пароль." }
        if (encrypted.size < MAGIC.size + SALT_LEN + 16 ||
            (encrypted.size - MAGIC.size - SALT_LEN) % 16 != 0 ||
            !encrypted.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        ) throw BadFormatException()

        val salt = encrypted.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val body = encrypted.copyOfRange(MAGIC.size + SALT_LEN, encrypted.size)

        val derived = pbkdf2(password.toByteArray(Charsets.UTF_8), salt, ITERATIONS, KEY_LEN + IV_LEN)
        val plain = try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(derived, 0, KEY_LEN, "AES"),
                IvParameterSpec(derived, KEY_LEN, IV_LEN)
            )
            cipher.doFinal(body)
        } catch (e: BadPaddingException) {
            throw WrongPasswordException()
        } catch (e: IllegalBlockSizeException) {
            throw WrongPasswordException()
        }

        // С вероятностью ~1/256 неверный пароль даёт корректный padding — отсекаем мусор проверкой текста.
        val key = String(plain, Charsets.UTF_8).lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        if (key.isEmpty() || key.length > MAX_KEY_CHARS || key.any { it < ' ' || it == '\u007f' || it == '�' }) {
            throw WrongPasswordException()
        }
        key
    }

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hLen = mac.macLength
        val blocks = (length + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)
        for (i in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (j in 2..iterations) {
                u = mac.doFinal(u)
                for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (i - 1) * hLen, hLen)
        }
        return out.copyOf(length)
    }
}
