package com.example.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TestResult
import com.example.data.TestResultDao
import com.example.data.repository.ActiveSessionRepository
import com.example.domain.models.QuizRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActiveQuizViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ActiveSessionRepository,
    private val testResultDao: TestResultDao
) : ViewModel() {

    private val quizId: String = savedStateHandle["quizId"] ?: ""

    private val _uiState = MutableStateFlow<QuizRuntimeState?>(null)
    val uiState: StateFlow<QuizRuntimeState?> = _uiState.asStateFlow()

    private val _timeRemaining = MutableStateFlow(0)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _hydrationState = MutableStateFlow<com.example.viewmodel.HydrationState>(com.example.viewmodel.HydrationState.Idle)
    val hydrationState: StateFlow<com.example.viewmodel.HydrationState> = _hydrationState.asStateFlow()

    private val _saveTrigger = MutableSharedFlow<QuizRuntimeState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var timerJob: Job? = null
    private var targetEndTime: Long = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _saveTrigger.debounce(500L).collect { state ->
                val stateJson = com.example.di.SupabaseModule.appJson.encodeToString(
                    com.example.domain.models.QuizRuntimeState.serializer(),
                    state
                )
                repository.updateLocalState(quizId, stateJson)
            }
        }
        
        loadSession()
    }

    private fun loadSession() {
        if (quizId.isEmpty()) return
        _hydrationState.value = com.example.viewmodel.HydrationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sessionEntity = repository.getLocalSessionFlow(quizId).firstOrNull()
                val stateJson = sessionEntity?.state ?: ""
                if (stateJson.isNotBlank()) {
                    val state = try {
                        com.example.di.SupabaseModule.appJson.decodeFromString(
                            com.example.domain.models.QuizRuntimeState.serializer(),
                            stateJson
                        )
                    } catch (e: Exception) {
                        QuizRuntimeState(quizName = "Corrupted Session")
                    }
                    
                    val maxIndex = kotlin.math.max(0, state.activeQuestions.size - 1)
                    val safeIndex = state.currentQuestionIndex.coerceIn(0, maxIndex)
                    
                    val finalState = state.copy(currentQuestionIndex = safeIndex)
                    
                    _uiState.update { finalState }
                    _timeRemaining.value = kotlin.math.max(0, finalState.quizTimeRemaining) / 1000

                    // Keep existing hydration success format for compatibility
                    // Map QuizRuntimeState to QuizSessionState just for the success wrap, though UI will use uiState directly
                    val mappedSession = com.example.model.QuizSessionState(
                        quiz = com.example.model.Quiz(
                            id = finalState.quizId.ifEmpty { quizId },
                            examGroup = "",
                            title = finalState.quizName ?: "",
                            description = "",
                            timeLimitMinutes = 0,
                            type = com.example.model.QuizType.CUSTOM,
                            questions = emptyList() // The UI should read questions from uiState
                        ),
                        isSubmitted = finalState.status == "result"
                    )
                    _hydrationState.value = com.example.viewmodel.HydrationState.Success(mappedSession)
                    
                    if (finalState.activeQuestions.isNotEmpty() && finalState.status != "result") {
                        startTimer(kotlin.math.max(0, finalState.quizTimeRemaining))
                    }
                } else {
                    val mockQuiz = com.example.data.MockQuizData.availableQuizzes.find { it.id == quizId }
                    if (mockQuiz != null) {
                        val activeQuestions = mockQuiz.questions.map { q ->
                            com.example.domain.models.QuestionPayload(
                                id = q.id,
                                questionText = q.text,
                                options = q.options,
                                correctOption = ('A' + q.correctOptionIndex).toString(),
                                explanation = kotlinx.serialization.json.JsonPrimitive(q.explanation),
                                topic = q.topic
                            )
                        }
                        val finalState = com.example.domain.models.QuizRuntimeState(
                            quizId = mockQuiz.id,
                            quizName = mockQuiz.title,
                            quizTimeRemaining = mockQuiz.timeLimitMinutes * 60000,
                            activeQuestions = activeQuestions,
                            mode = "learning",
                            currentQuestionIndex = 0
                        )
                        _uiState.update { finalState }
                        _timeRemaining.value = mockQuiz.timeLimitMinutes * 60
                        
                        val mappedSession = com.example.model.QuizSessionState(
                            quiz = mockQuiz,
                            isSubmitted = false
                        )
                        _hydrationState.value = com.example.viewmodel.HydrationState.Success(mappedSession)
                        
                        startTimer(mockQuiz.timeLimitMinutes * 60000)
                    } else {
                        _hydrationState.value = com.example.viewmodel.HydrationState.Error("Quiz Data Corrupted: Not found locally")
                    }
                }
            } catch (e: Exception) {
                _hydrationState.value = com.example.viewmodel.HydrationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun pauseTimer() {
        if (_uiState.value?.status == "result" || _uiState.value?.isPaused == true) return
        timerJob?.cancel()
        _uiState.update { state ->
            if (state == null) return@update state
            val newState = state.copy(
                isPaused = true,
                lastPausedWallClockTime = System.currentTimeMillis(),
                quizTimeRemaining = kotlin.math.max(0, (targetEndTime - android.os.SystemClock.elapsedRealtime()).toInt())
            )
            _saveTrigger.tryEmit(newState)
            newState
        }
    }

    fun resumeTimer() {
        val state = _uiState.value ?: return
        if (state.status == "result" || !state.isPaused) {
            if (timerJob?.isActive != true && state.status != "result") {
                startTimer(kotlin.math.max(0, state.quizTimeRemaining))
            }
            return
        }

        val offlineDrift = if (state.lastPausedWallClockTime != null && state.lastPausedWallClockTime > 0) {
            System.currentTimeMillis() - state.lastPausedWallClockTime
        } else {
            0L
        }

        val newRemaining = kotlin.math.max(0, state.quizTimeRemaining - offlineDrift.toInt())
        
        _uiState.update { 
            val newState = it?.copy(
                isPaused = false,
                lastPausedWallClockTime = null,
                quizTimeRemaining = newRemaining
            )
            if (newState != null) _saveTrigger.tryEmit(newState)
            newState
        }
        
        startTimer(newRemaining)
    }

    private fun startTimer(millisRemaining: Int) {
        val mode = _uiState.value?.mode?.lowercase() ?: "learning"
        if (mode == "learning") {
            timerJob?.cancel()
            timerJob = viewModelScope.launch(Dispatchers.Default) {
                var lastTickTime = android.os.SystemClock.elapsedRealtime()
                while (_uiState.value?.status != "result" && _uiState.value?.isPaused == false) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val deltaMillis = now - lastTickTime
                    lastTickTime = now
                    
                    val currentState = _uiState.value
                    if (currentState != null && currentState.activeQuestions.isNotEmpty()) {
                        val qId = currentState.activeQuestions[currentState.currentQuestionIndex].id
                        
                        // Update time taken
                        val prevTimeTaken = currentState.timeTaken[qId] ?: 0
                        val newTimeTaken = currentState.timeTaken + (qId to (prevTimeTaken + deltaMillis.toInt()))
                        
                        // Update remaining time for this question
                        val currentRemaining = currentState.remainingTimes[qId] ?: 60000
                        val newRemaining = kotlin.math.max(0, currentRemaining - deltaMillis.toInt())
                        val newRemainingTimes = currentState.remainingTimes + (qId to newRemaining)
                        
                        _uiState.update { it?.copy(timeTaken = newTimeTaken, remainingTimes = newRemainingTimes) }
                        _timeRemaining.value = newRemaining / 1000
                    }
                    delay(100) // Shorter delay for smoother per-question updates, but 1000 is fine too. Let's use 1000 and calculate delta.
                }
            }
            return
        }

        if (millisRemaining <= 0) {
            _timeRemaining.value = 0
            if (_uiState.value?.status != "result") {
                submitQuiz()
            }
            return
        }

        targetEndTime = android.os.SystemClock.elapsedRealtime() + millisRemaining
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            var lastTickTime = android.os.SystemClock.elapsedRealtime()
            while (_uiState.value?.status != "result" && _uiState.value?.isPaused == false) {
                val now = android.os.SystemClock.elapsedRealtime()
                val remaining = targetEndTime - now

                if (remaining <= 0) {
                    _timeRemaining.value = 0
                    submitQuiz()
                    break
                }

                _timeRemaining.value = (remaining / 1000).toInt()
                
                val deltaMillis = now - lastTickTime
                lastTickTime = now
                
                // update timeTaken per question
                val currentState = _uiState.value
                if (currentState != null && currentState.activeQuestions.isNotEmpty()) {
                    val qId = currentState.activeQuestions[currentState.currentQuestionIndex].id
                    val prevTime = currentState.timeTaken[qId] ?: 0
                    val newTimeTaken = currentState.timeTaken + (qId to (prevTime + deltaMillis.toInt()))
                    _uiState.update { it?.copy(timeTaken = newTimeTaken) }
                }
                
                delay(1000)
            }
        }
    }

    fun goToQuestion(index: Int) {
        _uiState.update { state ->
            if (state == null || state.status == "result") return@update state
            val maxIndex = kotlin.math.max(0, state.activeQuestions.size - 1)
            val safeIndex = index.coerceIn(0, maxIndex)
            val newState = state.copy(currentQuestionIndex = safeIndex)
            _saveTrigger.tryEmit(newState)
            newState
        }
    }

    fun selectAnswer(questionId: String, selectedOptionIndex: Int) {
        _uiState.update { state ->
            if (state == null || state.status == "result") return@update state
            
            val newAnswers = state.answers + (questionId to selectedOptionIndex.toString())
            
            // Score recalculation
            var newScore = 0
            state.activeQuestions.forEach { q ->
                val chosenIndexStr = newAnswers[q.id]
                if (chosenIndexStr != null) {
                    val chosenIndex = chosenIndexStr.toIntOrNull() ?: -1
                    val correctIndex = try {
                        val char = q.correctOption.uppercase().firstOrNull() ?: 'A'
                        char - 'A'
                    } catch (e: Exception) { 0 }
                    
                    if (chosenIndex == correctIndex) {
                        newScore++
                    }
                }
            }

            val newState = state.copy(
                answers = newAnswers, 
                score = newScore,
                syncStatus = "offline_pending"
            )
            _saveTrigger.tryEmit(newState)
            newState
        }
    }

    fun toggleMarkForReview(questionId: String) {
        _uiState.update { state ->
            if (state == null || state.status == "result") return@update state
            val isMarked = state.markedForReview.contains(questionId)
            val newMarked = if (isMarked) {
                state.markedForReview - questionId
            } else {
                state.markedForReview + questionId
            }
            val newState = state.copy(
                markedForReview = newMarked,
                syncStatus = "offline_pending"
            )
            _saveTrigger.tryEmit(newState)
            newState
        }
    }
    
    fun useFiftyFifty(questionId: String) {
        _uiState.update { state ->
            if (state == null || state.status == "result") return@update state
            
            val question = state.activeQuestions.find { it.id == questionId } ?: return@update state
            
            if (state.hiddenOptions.containsKey(questionId)) return@update state
            
            val correctIndex = try {
                val char = question.correctOption.uppercase().firstOrNull() ?: 'A'
                char - 'A'
            } catch (e: Exception) { 0 }
            
            val incorrectIndices = mutableListOf<String>()
            for (i in question.options.indices) {
                if (i != correctIndex) {
                    incorrectIndices.add(i.toString())
                }
            }
            
            incorrectIndices.shuffle()
            val optionsToHide = incorrectIndices.take(incorrectIndices.size / 2)
            
            val hiddenOptions = state.hiddenOptions + (questionId to optionsToHide)
            val newState = state.copy(
                hiddenOptions = hiddenOptions,
                syncStatus = "offline_pending"
            )
            _saveTrigger.tryEmit(newState)
            newState
        }
    }

    fun toggleBookmark(questionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.toggleUserBookmark(questionId)
            } catch(e: Exception) { }
        }
    }

    fun submitQuiz(onSuccess: (() -> Unit)? = null) {
        if (_isSubmitting.value) return
        _isSubmitting.value = true

        timerJob?.cancel()

        val currentState = _uiState.value
        if (currentState == null || currentState.status == "result") {
            _isSubmitting.value = false
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val duration = kotlin.math.max(0, currentState.quizTimeRemaining / 1000 - _timeRemaining.value)
                val finalScore = currentState.score
                val totalQs = currentState.activeQuestions.size
                val accuracy = if(totalQs > 0) (finalScore.toDouble() / totalQs) * 100 else 0.0

                val updatedState = currentState.copy(
                    status = "result",
                    syncStatus = "synced" // Optional
                )
                
                // Update Local Test Result
                testResultDao.insertResult(
                    TestResult(
                        quizId = quizId,
                        examGroup = currentState.mode,
                        title = currentState.quizName ?: "Saved Quiz",
                        score = finalScore,
                        totalQuestions = totalQs,
                        durationSeconds = duration
                    )
                )

                // Blocking network call to finalize
                val result = repository.finalizeQuizSession(
                    quizId = quizId,
                    userId = "anonymous_user",
                    stateJson = com.example.di.SupabaseModule.appJson.encodeToString(com.example.domain.models.QuizRuntimeState.serializer(), updatedState),
                    subjectStatsJson = "{}",
                    totalCorrect = finalScore,
                    totalTimeSpent = duration.toDouble(),
                    overallAccuracy = accuracy
                )
                
                withContext(Dispatchers.Main) {
                    _uiState.update { updatedState }
                    _saveTrigger.tryEmit(updatedState)
                    _isSubmitting.value = false
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isSubmitting.value = false
                }
            }
        }
    }

    fun clearSession() {
        _uiState.value = null
    }
}
