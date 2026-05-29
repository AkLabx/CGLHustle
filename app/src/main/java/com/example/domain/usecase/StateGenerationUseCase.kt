package com.example.domain.usecase

import com.example.domain.models.InitialFilters
import com.example.domain.models.QuestionPayload
import com.example.domain.models.QuizRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class StateGenerationUseCase {
    suspend fun generateState(
        cleanPayloads: List<QuestionPayload>,
        mode: String,
        quizName: String,
        filters: InitialFilters
    ): QuizRuntimeState = withContext(Dispatchers.Default) {
        val quizSessionId = UUID.randomUUID().toString()
        
        val quizTimeRemaining = if (mode == "mock" || mode == "god") {
            kotlin.math.max(60000L, cleanPayloads.size * 60000L).toInt()
        } else {
            0
        }
        
        val remainingTimesMap = if (mode == "learning") {
            cleanPayloads.associate { it.id to 60000 }
        } else {
            emptyMap()
        }

        QuizRuntimeState(
            status = "quiz",
            currentQuestionIndex = 0,
            score = 0,
            answers = emptyMap(),
            quizTimeRemaining = quizTimeRemaining,
            remainingTimes = remainingTimesMap,
            filters = filters,
            mode = mode,
            quizId = quizSessionId,
            activeQuestions = cleanPayloads,
            quizName = quizName,
            timeTaken = emptyMap(),
            bookmarks = emptyList(),
            markedForReview = emptyList(),
            hiddenOptions = emptyMap(),
            isPaused = false,
            syncStatus = "idle"
        )
    }
}
