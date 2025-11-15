package com.lingualens.utils

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_translations")
data class SavedTranslation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalLabel: String,
    val translatedLabel: String,
    val imagePath: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM saved_translations ORDER BY timestamp DESC")
    fun getAllTranslations(): Flow<List<SavedTranslation>>

    @Insert
    suspend fun insertTranslation(translation: SavedTranslation): Long

    @Delete
    suspend fun deleteTranslation(translation: SavedTranslation)

    @Query("DELETE FROM saved_translations")
    suspend fun deleteAll()
}

@Database(entities = [SavedTranslation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lingualens_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}