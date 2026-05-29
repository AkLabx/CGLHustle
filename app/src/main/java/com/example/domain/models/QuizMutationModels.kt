package com.example.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class SavedQuizInsertRequest(
    val id: String, // UUID
    val user_id: String, // UUID
    val name: String,
    val created_at: String, // ISO-8601 String
    val filters: InitialFilters,
    val mode: String, // "learning" | "mock" | "god"
    val state: QuizRuntimeState // The massive JSONB payload
)

@Serializable
data class BridgeInsertRequest(
    val quiz_id: String,
    val question_id: String,
    val sort_order: Int,
    val user_id: String
)
