package com.example.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class InitialFilters(
    val subjects: Set<String> = emptySet(),
    val topics: Set<String> = emptySet(),
    val subTopics: Set<String> = emptySet(),
    val difficulties: Set<String> = emptySet(),
    val exams: Set<String> = emptySet(),
    val years: Set<String> = emptySet(),
    val shifts: Set<String> = emptySet(),
    val tags: Set<String> = emptySet()
)

@Serializable
data class QuizConfigPayload(
    val quizName: String,
    val mode: String, // learning, mock, god
    val questionCount: Int,
    val filters: InitialFilters
)
