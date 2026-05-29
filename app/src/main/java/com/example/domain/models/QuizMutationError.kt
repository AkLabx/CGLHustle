package com.example.domain.models

sealed class QuizMutationError : Exception() {
    object NetworkTimeout : QuizMutationError()
    object AuthExpired : QuizMutationError()
    data class ServerRejected(val reason: String) : QuizMutationError()
    data class UnknownError(val throwable: Throwable) : QuizMutationError()
}
