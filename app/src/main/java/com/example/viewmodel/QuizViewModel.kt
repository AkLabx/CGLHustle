package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TestResult
import com.example.model.QuizSessionState
import com.example.data.MockQuizData
import com.example.data.TestResultDao
import com.example.model.Difficulty
import com.example.model.Quiz
import com.example.model.QuizType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.repository.ActiveSessionRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

sealed class HydrationState {
    object Idle : HydrationState()
    object Loading : HydrationState()
    data class Success(val state: QuizSessionState) : HydrationState()
    data class Error(val message: String) : HydrationState()
}

class QuizViewModel(
    private val testResultDao: TestResultDao,
    private val activeSessionRepository: ActiveSessionRepository
) : ViewModel() {

    val allQuizzes = MockQuizData.availableQuizzes

    private val _hydrationState = MutableStateFlow<HydrationState>(HydrationState.Idle)
    val hydrationState: StateFlow<HydrationState> = _hydrationState.asStateFlow()

    private val _currentSession = MutableStateFlow<QuizSessionState?>(null)
    val currentSession: StateFlow<QuizSessionState?> = _currentSession.asStateFlow()

    private val _timeRemaining = MutableStateFlow(0)
    val timeRemaining = _timeRemaining.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex = _currentQuestionIndex.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null
    private var targetEndTime: Long = 0L

    val testResults = testResultDao.getAllResults().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        // Debounced Cloud Sync for Active Session
        _currentSession
            .debounce(5000L)
            .onEach { session ->
                if (session != null && !session.isSubmitted) {
                    try {
                        val sessionFromLocalDb = activeSessionRepository.getLocalSessionFlow(session.quiz.id).firstOrNull()
                        val currentStateJson = sessionFromLocalDb?.state ?: "{}"
                        val runtimeState = try {
                            com.example.di.SupabaseModule.appJson.decodeFromString<com.example.domain.models.QuizRuntimeState>(currentStateJson)
                        } catch (e: Exception) {
                            com.example.domain.models.QuizRuntimeState()
                        }
                        val updatedState = runtimeState.copy(answers = session.selectedAnswers.mapValues { it.value.toString() })
                        val stateJson = com.example.di.SupabaseModule.appJson.encodeToString(updatedState)
                        activeSessionRepository.syncProgressToCloud(session.quiz.id, stateJson)
                    } catch (e: Exception) {
                        // Silently fail, Room still holds the truth
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    fun startQuiz(quizId: String) {
        val quiz = allQuizzes.find { it.id == quizId } ?: MockQuizData.availableQuizzes.first()
        hydrateCustomSession(quiz.id, quiz)
    }

    fun startCustomQuiz(quiz: Quiz) {
        hydrateCustomSession(quiz.id, quiz)
    }

    private fun hydrateCustomSession(quizId: String, quizFallback: Quiz? = null) {
        if (_currentSession.value?.quiz?.id == quizId) return
        
        _hydrationState.value = HydrationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sessionFlow = activeSessionRepository.getLocalSessionFlow(quizId)
                val sessionFromLocalDb = sessionFlow.firstOrNull()
                val stateJson = sessionFromLocalDb?.state ?: ""
                
                if (stateJson.isBlank()) {
                    _hydrationState.value = HydrationState.Error("Quiz Data Corrupted: Not found locally")
                    return@launch
                }

                val runtimeState = com.example.di.SupabaseModule.appJson.decodeFromString<com.example.domain.models.QuizRuntimeState>(stateJson)
                
                val activeQuestions = runtimeState.activeQuestions
                if (activeQuestions.isEmpty()) {
                    _hydrationState.value = HydrationState.Error("Quiz Data Corrupted: Missing questions")
                    return@launch
                }
                
                val mappedQuestions = activeQuestions.map { payload ->
                    val optionsStr = payload.options.ifEmpty { listOf("Option A", "Option B", "Option C", "Option D") }
                    val parsedIndex = try {
                        val char = payload.correctOption.uppercase().firstOrNull() ?: 'A'
                        val idx = char - 'A'
                        if (idx in optionsStr.indices) idx else 0
                    } catch (e: Exception) { 0 }
                    
                    com.example.model.Question(
                        id = payload.id,
                        text = payload.questionText,
                        options = optionsStr,
                        correctOptionIndex = parsedIndex,
                        explanation = payload.explanation?.toString() ?: "",
                        topic = payload.topic ?: "General",
                        difficulty = com.example.model.Difficulty.MEDIUM
                    )
                }

                val quiz = Quiz(
                    id = quizId,
                    examGroup = "HYDRATED",
                    title = runtimeState.quizName?.ifEmpty { "Saved Quiz" } ?: "Saved Quiz",
                    description = "",
                    timeLimitMinutes = (runtimeState.quizTimeRemaining / 60000).coerceAtLeast(1),
                    type = QuizType.CUSTOM,
                    questions = mappedQuestions
                )
                
                val selectedAnswers = runtimeState.answers.mapValues { it.value.toIntOrNull() ?: 0 }.toMutableMap()
                val timeTaken = runtimeState.timeTaken.mapValues { it.value.toLong() }.toMutableMap()
                val markedForReview = runtimeState.markedForReview.toMutableSet()
                val hiddenOptions = runtimeState.hiddenOptions.mapValues { it.value.mapNotNull { v -> v.toIntOrNull() } }.toMutableMap()

                val session = QuizSessionState(
                    quiz = quiz,
                    selectedAnswers = selectedAnswers,
                    timeTaken = timeTaken,
                    markedForReview = markedForReview,
                    hiddenOptions = hiddenOptions,
                    isSubmitted = runtimeState.status == "result"
                )
                
                _currentSession.value = session
                
                val timeRemainingMillis = runtimeState.quizTimeRemaining
                val initialIndex = runtimeState.currentQuestionIndex.coerceIn(0, (mappedQuestions.size - 1).coerceAtLeast(0))
                
                _timeRemaining.value = (kotlin.math.max(0, timeRemainingMillis) / 1000)
                _currentQuestionIndex.value = initialIndex
                _hydrationState.value = HydrationState.Success(session)
                
                if (runtimeState.status != "result") {
                    startTimer(kotlin.math.max(0, timeRemainingMillis))
                }
            } catch (e: Exception) {
                _hydrationState.value = HydrationState.Error(e.message ?: "Unknown error fetching quiz")
            }
        }
    }

    private fun startTimer(millisRemaining: Int) {
        if (millisRemaining <= 0) {
            _timeRemaining.value = 0
            if (_currentSession.value?.isSubmitted == false) submitQuiz()
            return
        }
        
        targetEndTime = android.os.SystemClock.elapsedRealtime() + millisRemaining
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (_currentSession.value?.isSubmitted == false) {
                val now = android.os.SystemClock.elapsedRealtime()
                val remaining = targetEndTime - now
                
                if (remaining <= 0) {
                    _timeRemaining.value = 0
                    submitQuiz()
                    break
                }
                
                _timeRemaining.value = (remaining / 1000).toInt()
                delay(1000)
                
                // Track time taken per question
                val session = _currentSession.value
                val currentIndex = _currentQuestionIndex.value
                if (session != null && !session.isSubmitted && 
                    currentIndex >= 0 && currentIndex < session.quiz.questions.size) {
                    val qId = session.quiz.questions[currentIndex].id
                    val timeTakenMap = session.timeTaken.toMutableMap()
                    val prevTime = timeTakenMap[qId] ?: 0L
                    timeTakenMap[qId] = prevTime + 1000L
                    _currentSession.update { it?.copy(timeTaken = timeTakenMap) }
                }
            }
        }
    }

    fun goToQuestion(index: Int) {
        _currentQuestionIndex.value = index
    }

    fun toggleMarkForReview(questionId: String) {
        _currentSession.update { current ->
            if (current == null || current.isSubmitted) return@update current
            val newMarked = current.markedForReview.toMutableSet()
            if (newMarked.contains(questionId)) {
                newMarked.remove(questionId)
            } else {
                newMarked.add(questionId)
            }
            current.copy(markedForReview = newMarked)
        }
    }
    
    fun useFiftyFifty(questionId: String) {
        _currentSession.update { current ->
            if (current == null || current.isSubmitted) return@update current
            
            val question = current.quiz.questions.find { it.id == questionId } ?: return@update current
            
            val hiddenOptions = current.hiddenOptions.toMutableMap()
            if (hiddenOptions.containsKey(questionId)) return@update current // already used
            
            // find incorrect options
            val incorrectIndices = mutableListOf<Int>()
            for (i in question.options.indices) {
                if (i != question.correctOptionIndex) {
                    incorrectIndices.add(i)
                }
            }
            
            incorrectIndices.shuffle()
            val optionsToHide = incorrectIndices.take(incorrectIndices.size / 2) // Hide half
            hiddenOptions[questionId] = optionsToHide
            
            current.copy(hiddenOptions = hiddenOptions)
        }
    }
    
    // For syncing bookmark, assuming repository has a mock logic if not fully wired
    fun toggleBookmark(questionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                activeSessionRepository.toggleUserBookmark(questionId)
            } catch(e: Exception) { }
        }
    }

    fun selectAnswer(questionId: String, optionIndex: Int) {
        _currentSession.update { current ->
            if (current == null || current.isSubmitted) return@update current
            val newAnswers = current.selectedAnswers.toMutableMap()
            newAnswers[questionId] = optionIndex
            
            // Save state instantly to local active room session
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val sessionFromLocalDb = activeSessionRepository.getLocalSessionFlow(current.quiz.id).firstOrNull()
                    val currentStateJson = sessionFromLocalDb?.state ?: "{}"
                    val runtimeState = try {
                        com.example.di.SupabaseModule.appJson.decodeFromString<com.example.domain.models.QuizRuntimeState>(currentStateJson)
                    } catch (e: Exception) {
                        com.example.domain.models.QuizRuntimeState()
                    }
                    val updatedState = runtimeState.copy(answers = newAnswers.mapValues { it.value.toString() })
                    val stateJson = com.example.di.SupabaseModule.appJson.encodeToString(updatedState)
                    activeSessionRepository.updateLocalState(current.quiz.id, stateJson)
                } catch(e: Exception) {}
            }
            
            current.copy(selectedAnswers = newAnswers)
        }
    }

    fun submitQuiz(onSuccess: (() -> Unit)? = null) {
        if (_isSubmitting.value) return
        _isSubmitting.value = true

        _currentSession.update { current ->
            if (current == null || current.isSubmitted) {
                _isSubmitting.value = false
                return@update current
            }
            val finalSession = current.copy(isSubmitted = true)
            
            // Save to DB
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val duration = (finalSession.quiz.timeLimitMinutes * 60) - _timeRemaining.value
                    val score = finalSession.getScore()
                    val totalQuestions = finalSession.quiz.questions.size
                    val accuracy = if(totalQuestions > 0) (score.toDouble() / totalQuestions) * 100 else 0.0
                    
                    // Update Local Test Result
                    testResultDao.insertResult(
                        TestResult(
                            quizId = finalSession.quiz.id,
                            examGroup = finalSession.quiz.examGroup,
                            title = finalSession.quiz.title,
                            score = score,
                            totalQuestions = totalQuestions,
                            durationSeconds = duration
                        )
                    )

                    val sessionFromLocalDb = activeSessionRepository.getLocalSessionFlow(finalSession.quiz.id).firstOrNull()
                    val currentStateJson = sessionFromLocalDb?.state ?: "{}"
                    val runtimeState = try {
                        com.example.di.SupabaseModule.appJson.decodeFromString<com.example.domain.models.QuizRuntimeState>(currentStateJson)
                    } catch (e: Exception) {
                        com.example.domain.models.QuizRuntimeState()
                    }
                    val updatedState = runtimeState.copy(answers = finalSession.selectedAnswers.mapValues { it.value.toString() }, status = "result", score = score)

                    // Call Supabase RPC to finalize and Wait for response (Blocking Network Call as per Architecture)
                    val result = activeSessionRepository.finalizeQuizSession(
                        quizId = finalSession.quiz.id,
                        userId = "anonymous_user",
                        stateJson = com.example.di.SupabaseModule.appJson.encodeToString(updatedState),
                        subjectStatsJson = "{}",
                        totalCorrect = score,
                        totalTimeSpent = duration.toDouble(),
                        overallAccuracy = accuracy
                    )
                    
                    withContext(Dispatchers.Main) {
                        _isSubmitting.value = false
                        if (result.isSuccess) {
                            onSuccess?.invoke()
                        } else {
                            // On failure, we'll let user try again or assume it was done.
                            // In severe strict setups we'd rollback. For MVP, proceed.
                            onSuccess?.invoke()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _isSubmitting.value = false
                    }
                }
            }
            
            finalSession
        }
    }

    fun clearSession() {
        _currentSession.value = null
    }

    val availableTopics = MockQuizData.allQuestions.map { it.topic }.distinct()
}
