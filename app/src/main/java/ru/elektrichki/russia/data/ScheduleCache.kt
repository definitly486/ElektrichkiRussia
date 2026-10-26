package ru.elektrichki.russia.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "schedule_cache")
data class CachedSchedule(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val savedAt: Long
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): CachedSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: CachedSchedule)

    @Query("DELETE FROM schedule_cache")
    suspend fun clear()
}

@Database(entities = [CachedSchedule::class], version = 1, exportSchema = false)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    companion object {
        @Volatile private var INSTANCE: ScheduleDatabase? = null
        fun get(context: Context): ScheduleDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, ScheduleDatabase::class.java, "schedule-cache.db"
            ).build().also { INSTANCE = it }
        }
    }
}
