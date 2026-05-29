package com.example.ui.viewmodel

sealed class QuizConfigEvent {
    data class ShowToast(val message: String, val isWarning: Boolean = false) : QuizConfigEvent()
    object NavigateToLibrary : QuizConfigEvent()
}
