package com.example.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class QuizStatus {
    QUIZ, RESULT, PAUSED, ABORTED
}

@Serializable
data class QuizRuntimeState(
    val quizName: String? = null,
    val isToolbarExpanded: Boolean = true,
    val last_updated: Long? = null,
    val currentQuestionIndex: Int = 0,
    val score: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val timeTaken: Map<String, Int> = emptyMap(),
    val remainingTimes: Map<String, Int> = emptyMap(),
    val quizTimeRemaining: Int = 0,
    val bookmarks: List<String> = emptyList(),
    val markedForReview: List<String> = emptyList(),
    val hiddenOptions: Map<String, List<String>> = emptyMap(),
    val isPaused: Boolean = false,
    val syncStatus: String = "idle",
    val quizId: String = "",
    val status: String = "quiz",
    val mode: String = "",
    val activeQuestions: List<QuestionPayload> = emptyList(),
    val filters: InitialFilters? = null,
    val lastPausedWallClockTime: Long? = null
)
