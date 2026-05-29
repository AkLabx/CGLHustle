package com.example.data.repository

import com.example.data.ActiveSessionDao
import com.example.data.BridgeDao
import com.example.data.QuestionDao
import com.example.data.QuizHistoryDao
import com.example.data.ActiveSessionEntity
import com.example.data.BridgeSavedQuizQuestions
import com.example.data.QuestionEntity
import com.example.data.QuizHistoryEntity
import com.example.di.SupabaseModule.appJson
import com.example.domain.models.InitialFilters
import com.example.domain.models.QuizRuntimeState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.UUID

// Payloads for RPC communication
@Serializable
data class QuestionInput(
    @SerialName("question_id") val questionId: String,
    @SerialName("sort_order") val sortOrder: Int
)

@Serializable
data class CreateQuizSessionParams(
    @SerialName("p_quiz_id") val quizId: String,
    @SerialName("p_user_id") val userId: String,
    @SerialName("p_name") val name: String,
    @SerialName("p_created_at") val createdAt: String,
    @SerialName("p_filters") val filters: InitialFilters,
    @SerialName("p_mode") val mode: String,
    @SerialName("p_state") val state: QuizRuntimeState,
    @SerialName("p_questions") val questions: List<QuestionInput>
)

@Serializable
data class FinalizeQuizSessionParams(
    val quiz_id: String,
    val user_id: String,
    val state: String,
    val subject_stats: String,
    val total_correct: Int,
    val total_time_spent: Double,
    val overall_accuracy: Double
)

@Serializable
data class FinalizeResult(
    val history_id: String
)

@Serializable
data class SavedQuizDTO(
    val id: String,
    val user_id: String,
    val status: String = "paused",
    val state: kotlinx.serialization.json.JsonElement? = null,
    val created_at: String? = null
)

class ActiveSessionRepository(
    private val primaryClient: SupabaseClient,
    private val questionClient: SupabaseClient,
    private val activeSessionDao: ActiveSessionDao,
    private val bridgeDao: BridgeDao,
    private val quizHistoryDao: QuizHistoryDao,
    private val questionDao: QuestionDao
) {
    suspend fun insertQuizWithBridge(
        masterDto: com.example.domain.models.SavedQuizInsertRequest,
        bridgeList: List<com.example.domain.models.BridgeInsertRequest>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            kotlinx.coroutines.withTimeout(15_000L) {
                primaryClient.postgrest["saved_quizzes"].insert(masterDto)
                primaryClient.postgrest["bridge_saved_quiz_questions"].insert(bridgeList)
            }
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(com.example.domain.models.QuizMutationError.NetworkTimeout)
        } catch (e: io.github.jan.supabase.exceptions.RestException) {
            val msg = e.error ?: e.description ?: e.message ?: "Unknown Server Error"
            if (msg.contains("401") == true || msg.contains("jwt", ignoreCase = true) == true) {
                Result.failure(com.example.domain.models.QuizMutationError.AuthExpired)
            } else {
                Result.failure(com.example.domain.models.QuizMutationError.ServerRejected(msg))
            }
        } catch (e: Exception) {
            Result.failure(com.example.domain.models.QuizMutationError.UnknownError(e))
        }
    }


    suspend fun finalizeQuizSession(
        quizId: String,
        userId: String,
        stateJson: String,
        subjectStatsJson: String,
        totalCorrect: Int,
        totalTimeSpent: Double,
        overallAccuracy: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = primaryClient.auth.currentUserOrNull()?.id 
                ?: throw Exception("User not authenticated (ID is null). Please log in again.")
            val params = FinalizeQuizSessionParams(
                quiz_id = quizId,
                user_id = currentUserId,
                state = stateJson,
                subject_stats = subjectStatsJson,
                total_correct = totalCorrect,
                total_time_spent = totalTimeSpent,
                overall_accuracy = overallAccuracy
            )
            
            // This expects a JSON response containing history_id
            val result = primaryClient.postgrest.rpc("finalize_quiz_session", params).decodeAs<FinalizeResult>()
            
            // Update local state
            activeSessionDao.updateSessionStatus(quizId, "result")
            quizHistoryDao.insertHistory(
                QuizHistoryEntity(
                    id = result.history_id,
                    quizId = quizId,
                    userId = currentUserId,
                    totalCorrect = totalCorrect,
                    totalTimeSpent = totalTimeSpent,
                    overallAccuracy = overallAccuracy,
                    subjectStats = subjectStatsJson
                )
            )

            Result.success(result.history_id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Example local progress update
    suspend fun updateLocalState(quizId: String, newStateStr: String) {
        val session = activeSessionDao.getSession(quizId)
        if (session != null) {
            activeSessionDao.updateSession(session.copy(state = newStateStr))
        }
    }

    suspend fun syncProgressToCloud(quizId: String, newStateStr: String) = withContext(Dispatchers.IO) {
        try {
            // Simplified: Update 'saved_quizzes' with new state JSON. 
            // We use Postgrest UPSERT or UPDATE
            primaryClient.postgrest["saved_quizzes"]
                .update({ set("state", newStateStr) }) {
                    filter { eq("id", quizId) }
                }
        } catch (e: Exception) {
            // Silently ignore sync failures
        }
    }

    fun getLocalSessionFlow(quizId: String): Flow<ActiveSessionEntity?> {
        return activeSessionDao.getSessionFlow(quizId)
    }

    fun getAllSessionsFlow(): Flow<List<ActiveSessionEntity>> {
        return activeSessionDao.getAllSessionsFlow()
    }

    suspend fun refreshFromSupabase() = withContext(Dispatchers.IO) {
        try {
            // Ideally should fetch all saved_quizzes for current user but we'll fetch all or top 50 
            // from Supabase and insert them into local Room database to hydrate the table.
            // For now, if we use strongly typed object:
            val remoteSessions = primaryClient.postgrest["saved_quizzes"]
                .select()
                .decodeList<SavedQuizDTO>()
            
            remoteSessions.forEach { dto ->
                activeSessionDao.insertSession(
                    ActiveSessionEntity(
                        id = dto.id,
                        userId = dto.user_id,
                        status = dto.status,
                        mode = "learning", // Or parse from state if needed
                        state = dto.state?.let { try { it.toString() } catch (e: Exception) { "{}" } } ?: "{}"
                    )
                )
            }
        } catch(e: Exception) {
            // Silently fail, Room flow keeps the UI intact
        }
    }

    suspend fun toggleUserBookmark(questionId: String) = withContext(Dispatchers.IO) {
        try {
            // Placeholder: Call bookmarkDao and sync with Supabase UserBookmark table
        } catch(e: Exception) {
            // Silently fail for MVP offline state
        }
    }

    suspend fun hydrateSessionQuestions(quizId: String): Result<List<com.example.model.Question>> = withContext(Dispatchers.IO) {
        try {
            // First check local bridge & questions mapping
            var questionIds = bridgeDao.getQuestionIdsForQuiz(quizId)
            
            if (questionIds.isEmpty()) {
                // In a real app we'd fetch bridge table from Supabase primary backend here
                // For this offline-first optimistic layer, we expect it was just inserted by createQuizSession
            }
            
            val localQuestions = questionDao.getQuestionsByIds(questionIds)
            
            val questions = if (localQuestions.size == questionIds.size && questionIds.isNotEmpty()) {
                // Assemble from local
                questionIds.mapNotNull { qId ->
                    localQuestions.find { it.id == qId }?.let { entity ->
                        com.example.model.Question(
                            id = entity.id,
                            text = entity.question,
                            options = try { kotlinx.serialization.json.Json.decodeFromString<List<String>>(entity.options) } catch(e: Exception) { emptyList() },
                            correctOptionIndex = entity.correct.toIntOrNull() ?: 0,
                            explanation = entity.explanation,
                            topic = entity.topic,
                            difficulty = com.example.model.Difficulty.MEDIUM
                        )
                    }
                }
            } else {
                // Fetch from GK LLM Backend (questionClient)
                val fetched = questionClient.postgrest["questions"]
                    .select {
                        filter {
                            isIn("id", questionIds)
                        }
                    }.decodeList<QuestionEntityWrapper>()
                
                // Map and sort properly
                val sortedFetched = questionIds.mapNotNull { id -> fetched.find { it.id == id } }
                
                // Cache locally
                val entitiesToInsert = sortedFetched.map {
                    QuestionEntity(
                        id = it.id,
                        subject = it.subject ?: "",
                        topic = it.topic ?: "",
                        difficulty = 2,
                        question = it.question ?: "",
                        options = kotlinx.serialization.json.Json.encodeToString(it.options ?: emptyList<String>()),
                        correct = it.correct ?: "0",
                        explanation = "{}"
                    )
                }
                if (entitiesToInsert.isNotEmpty()) {
                    questionDao.insertQuestions(entitiesToInsert)
                }
                
                sortedFetched.map {
                    com.example.model.Question(
                        id = it.id,
                        text = it.question ?: "",
                        options = it.options ?: emptyList(),
                        correctOptionIndex = it.correct?.toIntOrNull() ?: 0,
                        explanation = "",
                        topic = it.topic ?: "",
                        difficulty = com.example.model.Difficulty.MEDIUM
                    )
                }
            }
            Result.success(questions)
        } catch(e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
private data class QuestionEntityWrapper(
    val id: String,
    val subject: String? = null,
    val topic: String? = null,
    val question: String? = null,
    val options: List<String>? = null,
    val correct: String? = null
)
