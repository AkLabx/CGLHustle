package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class QuizType {
    SUBJECT, SECTIONAL, FULL_MOCK, CUSTOM
}

@Serializable
enum class Difficulty {
    EASY, MEDIUM, HARD
}

data class Question(
    val id: String,
    val text: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
    val topic: String,
    val difficulty: Difficulty
)

data class Quiz(
    val id: String,
    val examGroup: String, // "CGL", "BPSC", "CUSTOM"
    val title: String,
    val description: String,
    val timeLimitMinutes: Int,
    val type: QuizType,
    val questions: List<Question>
)

data class QuizSessionState(
    val quiz: Quiz,
    val selectedAnswers: MutableMap<String, Int> = mutableMapOf(), // questionId -> optionIndex
    val timeTaken: MutableMap<String, Long> = mutableMapOf(),
    val markedForReview: MutableSet<String> = mutableSetOf(),
    val hiddenOptions: MutableMap<String, List<Int>> = mutableMapOf(),
    var startTimeMillis: Long = System.currentTimeMillis(),
    var isSubmitted: Boolean = false
) {
    fun getScore(): Int {
        var score = 0
        for (q in quiz.questions) {
            if (selectedAnswers[q.id] == q.correctOptionIndex) {
                score++
            }
        }
        return score
    }
}
