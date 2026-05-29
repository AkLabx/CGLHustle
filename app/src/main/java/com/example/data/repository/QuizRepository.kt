package com.example.data.repository

import com.example.domain.models.QuestionMetadata
import com.example.domain.models.QuestionPayload

interface QuizRepository {
    suspend fun fetchAllQuestionMetadata(): Result<List<QuestionMetadata>>
    suspend fun fetchQuestionPayloads(ids: List<String>): Result<List<QuestionPayload>>
}
