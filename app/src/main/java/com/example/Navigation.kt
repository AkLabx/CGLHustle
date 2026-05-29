package com.example

import kotlinx.serialization.Serializable

@Serializable
object LoginRoute

@Serializable
object McqsQuizHomeRoute

@Serializable
object DashboardRoute

@Serializable
object ExamSelectionRoute

@Serializable
data class CustomQuizRoute(val initialMode: String? = null)

@Serializable
data class ActiveQuizRoute(val quizId: String = "")

@Serializable
data class QuizLibraryRoute(val tab: String = "saved")

@Serializable
object QuizResultRoute

@Serializable
data class DynamicQuizResultRoute(val quizId: String)

