package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.repository.ActiveSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.data.ActiveSessionEntity

class QuizLibraryViewModel(
    private val activeSessionRepository: ActiveSessionRepository
) : ViewModel() {

    // Room Flow to UI
    val quizzes: StateFlow<List<ActiveSessionEntity>> = activeSessionRepository.getAllSessionsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun initialize(forceRefresh: Boolean) {
        if (forceRefresh) {
            refresh()
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Network call to update Room DB
                activeSessionRepository.refreshFromSupabase()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to sync: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
