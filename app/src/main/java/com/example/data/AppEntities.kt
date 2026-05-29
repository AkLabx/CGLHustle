package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val topic: String,
    val difficulty: Int,
    val question: String,
    val options: String, // Stored as JSON string
    val correct: String,
    val explanation: String // Stored as JSON string
)

@Entity(tableName = "saved_quizzes")
data class ActiveSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val status: String,
    val mode: String,
    val state: String // Stored as JSON string
)

@Entity(
    tableName = "bridge_saved_quiz_questions",
    primaryKeys = ["quiz_id", "question_id"],
    indices = [
        Index("quiz_id")
    ]
)
data class BridgeSavedQuizQuestions(
    @ColumnInfo(name = "quiz_id") val quizId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int
)

@Entity(
    tableName = "quiz_history",
    indices = [
        Index(value = ["quiz_id", "user_id"], unique = true)
    ]
)
data class QuizHistoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "quiz_id") val quizId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "total_correct") val totalCorrect: Int,
    @ColumnInfo(name = "total_time_spent") val totalTimeSpent: Double,
    @ColumnInfo(name = "overall_accuracy") val overallAccuracy: Double,
    @ColumnInfo(name = "subject_stats") val subjectStats: String // JSON
)

@Entity(
    tableName = "user_bookmarks",
    primaryKeys = ["user_id", "question_id"]
)
data class UserBookmarkEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "question_id") val questionId: String
)

@Entity(tableName = "analytics_events")
data class AnalyticsEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_name") val eventName: String,
    @ColumnInfo(name = "event_data") val eventData: String // JSON
)
