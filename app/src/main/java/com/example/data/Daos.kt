package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getQuestionsByIds(ids: List<String>): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)
}

@Dao
interface ActiveSessionDao {
    @Query("SELECT * FROM saved_quizzes WHERE id = :quizId")
    fun getSessionFlow(quizId: String): Flow<ActiveSessionEntity?>

    @Query("SELECT * FROM saved_quizzes WHERE id = :quizId")
    suspend fun getSession(quizId: String): ActiveSessionEntity?

    @Query("SELECT * FROM saved_quizzes")
    fun getAllSessionsFlow(): Flow<List<ActiveSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ActiveSessionEntity)

    @Update
    suspend fun updateSession(session: ActiveSessionEntity)

    @Query("UPDATE saved_quizzes SET status = :status WHERE id = :id")
    suspend fun updateSessionStatus(id: String, status: String)
}

@Dao
interface BridgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBridges(bridges: List<BridgeSavedQuizQuestions>)

    @Query("SELECT question_id FROM bridge_saved_quiz_questions WHERE quiz_id = :quizId ORDER BY sort_order ASC")
    suspend fun getQuestionIdsForQuiz(quizId: String): List<String>
}

@Dao
interface QuizHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: QuizHistoryEntity)
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(bookmark: UserBookmarkEntity)
}

@Dao
interface AnalyticsDao {
    @Insert
    suspend fun insertEvent(event: AnalyticsEventEntity)
}
