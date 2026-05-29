package com.example.domain.usecase

import com.example.data.repository.ActiveSessionRepository
import com.example.data.repository.QuestionInput
import com.example.data.repository.QuizRepository
import com.example.domain.models.InitialFilters
import com.example.domain.models.QuestionPayload
import com.example.domain.models.QuizRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.github.jan.supabase.auth.auth
import java.util.UUID

class CreateQuizUseCase(
    private val repository: QuizRepository,
    private val activeSessionRepository: ActiveSessionRepository,
    private val stateGenerationUseCase: StateGenerationUseCase = StateGenerationUseCase()
) {
    suspend fun execute(
        targetIds: List<String>,
        filters: InitialFilters,
        mode: String,
        quizName: String
    ): Result<QuizSuccessData> = withContext(Dispatchers.IO) {
        try {
            val payloads = mutableListOf<QuestionPayload>()
            val batches = targetIds.chunked(200)
            
            // Parallel Fetch
            coroutineScope {
                val results = batches.map { batch ->
                    async { repository.fetchQuestionPayloads(batch).getOrThrow() }
                }.awaitAll()
                payloads.addAll(results.flatten())
            }

            // Default Dispatcher for Heavy Deduplication
            val cleanPayloads = withContext(Dispatchers.Default) {
                // Stage 1: ID Deduplication
                val idDeduped = payloads.associateBy { it.v1_id ?: it.id }.values.toList()
                
                // Stage 2: Text Deduplication
                val seenContent = HashSet<String>()
                idDeduped.filter { q ->
                    val normEn = q.questionText.lowercase(java.util.Locale.ROOT).replace("\\s+".toRegex(), "")
                    val normHi = q.question_hi?.lowercase(java.util.Locale.ROOT)?.replace("\\s+".toRegex(), "") ?: ""
                    val contentKey = "$normEn-$normHi"
                    
                    if (contentKey == "-") return@filter true
                    seenContent.add(contentKey)
                }
            }

            val droppedCount = payloads.size - cleanPayloads.size

            if (cleanPayloads.isEmpty()) {
                return@withContext Result.failure(Exception("All questions were deduplicated or unavailable."))
            }

            val initialState = stateGenerationUseCase.generateState(cleanPayloads, mode, quizName, filters)
            val quizSessionId = initialState.quizId
            
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val currentUserId = com.example.di.SupabaseModule.primaryClient.auth.currentUserOrNull()?.id 
                ?: return@withContext Result.failure(Exception("User not authenticated (ID is null). Please log in again."))
            val masterDto = com.example.domain.models.SavedQuizInsertRequest(
                id = quizSessionId,
                user_id = currentUserId, // Logged in user ID
                name = quizName,
                created_at = formatter.format(java.util.Date()),
                filters = filters,
                mode = mode,
                state = initialState
            )
            
            val bridgeList = cleanPayloads.mapIndexed { index, payload -> 
                com.example.domain.models.BridgeInsertRequest(
                    quiz_id = quizSessionId,
                    question_id = payload.id,
                    sort_order = index,
                    user_id = currentUserId
                )
            }
            
            val rpcResult = activeSessionRepository.insertQuizWithBridge(masterDto, bridgeList)
            
            if (rpcResult.isFailure) {
                return@withContext Result.failure(rpcResult.exceptionOrNull() ?: Exception("Unknown Insertion Failure"))
            }

            val warning = if (droppedCount > 0) DuplicateWarning(droppedCount) else null

            Result.success(QuizSuccessData(quizSessionId, warning))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class QuizSuccessData(
    val quizId: String,
    val warning: DuplicateWarning?
)

data class DuplicateWarning(val droppedCount: Int)
