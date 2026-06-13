package com.viddown.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ──────────────────────────────────────────────
// Room Entity
// ──────────────────────────────────────────────

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val thumbnail: String,
    val quality: String,
    val format: String,
    val filePath: String,
    val fileSize: Long,
    val platform: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED"
)

// ──────────────────────────────────────────────
// DAO
// ──────────────────────────────────────────────

@Dao
interface DownloadHistoryDao {

    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadHistoryEntity)

    @Delete
    suspend fun delete(item: DownloadHistoryEntity)

    @Query("DELETE FROM download_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM download_history WHERE id = :id")
    suspend fun getById(id: String): DownloadHistoryEntity?
}

// ──────────────────────────────────────────────
// Database
// ──────────────────────────────────────────────

@Database(entities = [DownloadHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "viddown_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
