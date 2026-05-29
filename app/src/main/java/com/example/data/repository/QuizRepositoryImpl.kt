package com.example.data.repository

import android.util.Log
import com.example.domain.models.QuestionMetadata
import com.example.domain.models.QuestionPayload
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull

class QuizRepositoryImpl(
    private val supabaseClient: SupabaseClient
) : QuizRepository {
    override suspend fun fetchAllQuestionMetadata(): Result<List<QuestionMetadata>> {
        return withContext(Dispatchers.IO) {
            try {
                val allMetadata = mutableListOf<QuestionMetadata>()
                val pageSize = 1000L
                var currentOffset = 0L
                var hasMoreData = true

                while (hasMoreData) {
                    val chunk = supabaseClient.postgrest["questions"]
                        .select(columns = Columns.list("id", "v1_id", "subject", "topic", "subTopic", "examName", "examYear", "examDateShift", "difficulty", "questionType", "tags")) {
                            range(currentOffset, currentOffset + pageSize - 1)
                        }
                        .decodeList<QuestionMetadata>()
                    
                    val parsedChunk = chunk
                    
                    allMetadata.addAll(parsedChunk)
                    Log.d("QuizRepository", "Fetched chunk of size: ${parsedChunk.size}, Total so far: ${allMetadata.size}")
                    
                    if (chunk.size < pageSize) {
                        hasMoreData = false
                    }
                    
                    currentOffset += pageSize
                }
                Result.success(allMetadata)
            } catch (e: Exception) {
                Log.e("QuizRepository", "Error fetching metadata: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun fetchQuestionPayloads(ids: List<String>): Result<List<QuestionPayload>> {
        return withContext(Dispatchers.IO) {
            try {
                val allPayloads = supabaseClient.postgrest["questions"]
                    .select { // need full columns since phase 1? Actually the previous code had select(columns = Columns.list("id", "question", ...)) Let's see what columns were fetched.
                        filter {
                            isIn("id", ids)
                        }
                    }
                    .decodeList<QuestionPayload>()
                Result.success(allPayloads)
            } catch (e: Exception) {
                Log.e("QuizRepository", "Error fetching payloads: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}
