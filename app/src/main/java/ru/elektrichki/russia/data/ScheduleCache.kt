package ru.elektrichki.russia.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CachedSchedule(
    val cacheKey: String,
    val json: String,
    val savedAt: Long
)

/**
 * Лёгкая замена Room: та же база "schedule-cache.db", версия 1 и та же таблица schedule_cache,
 * поэтому уже сохранённый кэш продолжает работать после обновления приложения.
 */
class ScheduleDao(private val helper: SQLiteOpenHelper) {
    suspend fun get(key: String): CachedSchedule? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT cacheKey, json, savedAt FROM schedule_cache WHERE cacheKey = ? LIMIT 1",
            arrayOf(key)
        ).use { c ->
            if (c.moveToFirst()) CachedSchedule(c.getString(0), c.getString(1), c.getLong(2)) else null
        }
    }

    suspend fun put(item: CachedSchedule) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("cacheKey", item.cacheKey)
            put("json", item.json)
            put("savedAt", item.savedAt)
        }
        helper.writableDatabase.insertWithOnConflict(
            "schedule_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("schedule_cache", null, null)
    }
}

class ScheduleDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "schedule-cache.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS schedule_cache (" +
                "cacheKey TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL, savedAt INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private val dao = ScheduleDao(this)
    fun scheduleDao(): ScheduleDao = dao

    companion object {
        @Volatile private var INSTANCE: ScheduleDatabase? = null
        fun get(context: Context): ScheduleDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: ScheduleDatabase(context).also { INSTANCE = it }
        }
    }
}
