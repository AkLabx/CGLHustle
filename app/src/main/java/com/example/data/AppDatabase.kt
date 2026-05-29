package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "test_results")
data class TestResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quizId: String,
    val examGroup: String,
    val title: String,
    val score: Int,
    val totalQuestions: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int // total time taken
)

@Dao
interface TestResultDao {
    @Query("SELECT * FROM test_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<TestResult>>

    @Query("SELECT * FROM test_results WHERE examGroup = :group ORDER BY timestamp ASC")
    fun getResultsByGroup(group: String): Flow<List<TestResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: TestResult)
}

@Database(
    entities = [
        TestResult::class,
        QuestionEntity::class,
        ActiveSessionEntity::class,
        BridgeSavedQuizQuestions::class,
        QuizHistoryEntity::class,
        UserBookmarkEntity::class,
        AnalyticsEventEntity::class
    ], 
    version = 2, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun testResultDao(): TestResultDao
    abstract fun questionDao(): QuestionDao
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun bridgeDao(): BridgeDao
    abstract fun quizHistoryDao(): QuizHistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun analyticsDao(): AnalyticsDao
}
