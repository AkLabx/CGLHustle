package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.data.repository.QuizRepository
import com.example.domain.engine.QuizFilterEngine
import com.example.data.repository.ActiveSessionRepository
import com.example.data.repository.QuestionInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.example.domain.usecase.CreateQuizUseCase
import kotlinx.coroutines.TimeoutCancellationException

class QuizConfigViewModel(
    private val repository: QuizRepository,
    private val engine: QuizFilterEngine,
    private val createQuizUseCase: CreateQuizUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_QUIZ_NAME = "quizName"
        const val KEY_SUBJECTS = "subjects"
        const val KEY_TOPICS = "topics"
        const val KEY_SUBTOPICS = "subTopics"
        const val KEY_DIFFICULTIES = "difficulties"
        const val KEY_EXAMS = "exams"
        const val KEY_YEARS = "years"
        const val KEY_SHIFTS = "shifts"
        const val KEY_TAGS = "tags"
    }

    private val _uiState = MutableStateFlow(
        QuizConfigState(
            selectedMode = savedStateHandle.get<String>(KEY_MODE) ?: "learning",
            quizName = savedStateHandle.get<String>(KEY_QUIZ_NAME) ?: "",
            selectedSubjects = savedStateHandle.get<Array<String>>(KEY_SUBJECTS)?.toSet() ?: emptySet(),
            selectedTopics = savedStateHandle.get<Array<String>>(KEY_TOPICS)?.toSet() ?: emptySet(),
            selectedSubTopics = savedStateHandle.get<Array<String>>(KEY_SUBTOPICS)?.toSet() ?: emptySet(),
            selectedDifficulties = savedStateHandle.get<Array<String>>(KEY_DIFFICULTIES)?.toSet() ?: emptySet(),
            selectedExams = savedStateHandle.get<Array<String>>(KEY_EXAMS)?.toSet() ?: emptySet(),
            selectedYears = savedStateHandle.get<Array<String>>(KEY_YEARS)?.toSet() ?: emptySet(),
            selectedShifts = savedStateHandle.get<Array<String>>(KEY_SHIFTS)?.toSet() ?: emptySet(),
            selectedTags = savedStateHandle.get<Array<String>>(KEY_TAGS)?.toSet() ?: emptySet()
        )
    )
    val uiState: StateFlow<QuizConfigState> = _uiState.asStateFlow()
    
    private val _events = Channel<QuizConfigEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    
    var fetchedQuiz: com.example.model.Quiz? = null
    
    private var localMetadataCache: List<com.example.domain.models.QuestionMetadata> = emptyList()

    init {
        bootEngine()
    }

    private fun bootEngine() {
        _uiState.update { it.copy(isLoadingMetadata = true) }
        viewModelScope.launch {
            val result = repository.fetchAllQuestionMetadata()
            result.onSuccess { list ->
                localMetadataCache = list
                engine.buildIndex(list)
                recalculateState(_uiState.value)
                _uiState.update { it.copy(isLoadingMetadata = false) }
            }
            result.onFailure { error ->
                android.util.Log.e("BootEngine", "Failed to fetch metadata", error)
                // Ignore in MVP or show error
                _uiState.update { it.copy(isLoadingMetadata = false) }
            }
        }
    }

    fun setMode(mode: String) {
        savedStateHandle[KEY_MODE] = mode
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun toggleSubject(subject: String) {
        val current = _uiState.value
        val newSet = if (current.selectedSubjects.contains(subject)) {
            current.selectedSubjects - subject
        } else {
            current.selectedSubjects + subject
        }
        savedStateHandle[KEY_SUBJECTS] = newSet.toTypedArray()
        _uiState.update { it.copy(selectedSubjects = newSet) }
        recalculateState(_uiState.value)
    }

    fun toggleTopic(topic: String) {
        val current = _uiState.value
        val newSet = if (current.selectedTopics.contains(topic)) {
            current.selectedTopics - topic
        } else {
            current.selectedTopics + topic
        }
        savedStateHandle[KEY_TOPICS] = newSet.toTypedArray()
        _uiState.update { it.copy(selectedTopics = newSet) }
        recalculateState(_uiState.value)
    }

    fun toggleSubTopic(subTopic: String) {
        val current = _uiState.value
        val newSet = if (current.selectedSubTopics.contains(subTopic)) {
            current.selectedSubTopics - subTopic
        } else {
            current.selectedSubTopics + subTopic
        }
        savedStateHandle[KEY_SUBTOPICS] = newSet.toTypedArray()
        _uiState.update { it.copy(selectedSubTopics = newSet) }
        recalculateState(_uiState.value)
    }

    fun toggleDifficulty(difficulty: String) {
        val current = _uiState.value
        val newSet = if (current.selectedDifficulties.contains(difficulty)) {
            current.selectedDifficulties - difficulty
        } else {
            current.selectedDifficulties + difficulty
        }
        savedStateHandle[KEY_DIFFICULTIES] = newSet.toTypedArray()
        _uiState.update { it.copy(selectedDifficulties = newSet) }
        recalculateState(_uiState.value)
    }

    fun toggleExam(exam: String) {
        val current = _uiState.value
        val newSet = if (current.selectedExams.contains(exam)) current.selectedExams - exam else current.selectedExams + exam
        savedStateHandle[KEY_EXAMS] = newSet.toTypedArray()
        _uiState.update { it.copy(selectedExams = newSet) }
        recalculateState(_uiState.value)
    }

    fun toggleYear(year: String) {
        val current = _uiState.value
        val newSet = if (current.selectedYears.contains(year)) current.selectedYears - year else current.selectedYears + year
        savedStateHandle[KEY_YEARS] = newSet.toTypedArray()
        _uiState.update { it.copy(selectedYears = newSet) }
        recalculateState(_uiState.value)
    }

    fun toggleShift(shift: String) {
        val current = _uiState.value
        val newSet = if (current.selectedShifts.contains(shift)) current.selectedShifts - shift else current.selectedShifts + shift
        savedStateHandle[KEY_SHIFTS] = newSet.toTypedArray()
        _uiState.update { it.copy(selectedShifts = newSet) }
        recalculateState(_uiState.value)
    }

    fun clearFilter(category: String) {
        _uiState.update { 
            when (category) {
                "SUBJECT" -> { savedStateHandle[KEY_SUBJECTS] = emptyArray<String>(); it.copy(selectedSubjects = emptySet()) }
                "TOPIC" -> { savedStateHandle[KEY_TOPICS] = emptyArray<String>(); it.copy(selectedTopics = emptySet()) }
                "SUB-TOPIC" -> { savedStateHandle[KEY_SUBTOPICS] = emptyArray<String>(); it.copy(selectedSubTopics = emptySet()) }
                "EXAM NAME" -> { savedStateHandle[KEY_EXAMS] = emptyArray<String>(); it.copy(selectedExams = emptySet()) }
                "EXAM YEAR" -> { savedStateHandle[KEY_YEARS] = emptyArray<String>(); it.copy(selectedYears = emptySet()) }
                "EXAM SHIFT" -> { savedStateHandle[KEY_SHIFTS] = emptyArray<String>(); it.copy(selectedShifts = emptySet()) }
                "DIFFICULTY" -> { savedStateHandle[KEY_DIFFICULTIES] = emptyArray<String>(); it.copy(selectedDifficulties = emptySet()) }
                "TAGS" -> { savedStateHandle[KEY_TAGS] = emptyArray<String>(); it.copy(selectedTags = emptySet()) }
                else -> it
            }
        }
        recalculateState(_uiState.value)
    }

    fun toggleTag(tag: String) {
        val current = _uiState.value
        val newSet = if (current.selectedTags.contains(tag)) current.selectedTags - tag else current.selectedTags + tag
        savedStateHandle[KEY_TAGS] = newSet.toTypedArray()
        _uiState.update { it.copy(selectedTags = newSet) }
        recalculateState(_uiState.value)
    }

    fun applyQuickPreset(presetName: String) {
        val current = _uiState.value
        _uiState.update { 
            when (presetName) {
                "Quick 10" -> { savedStateHandle[KEY_DIFFICULTIES] = arrayOf("Easy", "Medium"); it.copy(selectedDifficulties = setOf("Easy", "Medium")) }
                "Quick 20" -> { savedStateHandle[KEY_DIFFICULTIES] = arrayOf("Easy", "Medium", "Hard"); it.copy(selectedDifficulties = setOf("Easy", "Medium", "Hard")) }
                "Quick 50" -> { savedStateHandle[KEY_DIFFICULTIES] = emptyArray<String>(); it.copy(selectedDifficulties = emptySet()) } // all difficulties
                "Quick Revision" -> { savedStateHandle[KEY_TAGS] = arrayOf("Important", "PYQ"); it.copy(selectedTags = setOf("Important", "PYQ")) }
                else -> it
            }
        }
        recalculateState(_uiState.value)
    }
    
    fun resetAllFilters() {
        savedStateHandle[KEY_SUBJECTS] = emptyArray<String>()
        savedStateHandle[KEY_TOPICS] = emptyArray<String>()
        savedStateHandle[KEY_SUBTOPICS] = emptyArray<String>()
        savedStateHandle[KEY_DIFFICULTIES] = emptyArray<String>()
        savedStateHandle[KEY_EXAMS] = emptyArray<String>()
        savedStateHandle[KEY_YEARS] = emptyArray<String>()
        savedStateHandle[KEY_SHIFTS] = emptyArray<String>()
        savedStateHandle[KEY_TAGS] = emptyArray<String>()
        
        _uiState.update { 
            it.copy(
                selectedSubjects = emptySet(),
                selectedTopics = emptySet(),
                selectedSubTopics = emptySet(),
                selectedDifficulties = emptySet(),
                selectedExams = emptySet(),
                selectedYears = emptySet(),
                selectedShifts = emptySet(),
                selectedTags = emptySet()
            )
        }
        recalculateState(_uiState.value)
    }

    fun createQuizWithQuestions(questionCount: Int = 25) {
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingQuiz = true) }
            try {
                // authSessionProvider.getUserId() logic would go here. For now string 
                val userId = "anonymous_user"
                
                val currentUiState = _uiState.value
                val filteredList = filterQuestions(localMetadataCache, currentUiState)
                
                if (filteredList.isEmpty()) {
                    _uiState.update { it.copy(showEmptyError = true) }
                    launch {
                        kotlinx.coroutines.delay(4000)
                        _uiState.update { it.copy(showEmptyError = false) }
                    }
                    return@launch
                }
                
                val targetIds = filteredList.map { it.id }.shuffled().take(questionCount)

                val filterOptions = com.example.domain.models.InitialFilters(
                    subjects = currentUiState.selectedSubjects,
                    topics = currentUiState.selectedTopics,
                    subTopics = currentUiState.selectedSubTopics,
                    difficulties = currentUiState.selectedDifficulties,
                    exams = currentUiState.selectedExams,
                    years = currentUiState.selectedYears,
                    shifts = currentUiState.selectedShifts,
                    tags = currentUiState.selectedTags
                )

                val quizName = "Quiz " + java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                val mode = currentUiState.selectedMode

                val result = createQuizUseCase.execute(targetIds, filterOptions, mode, quizName)
                
                result.onSuccess { data ->
                    data.warning?.let {
                        _events.send(QuizConfigEvent.ShowToast("Dropped ${it.droppedCount} duplicates", true))
                    }
                    _events.send(QuizConfigEvent.NavigateToLibrary)
                }.onFailure { error ->
                    val msg = when (error) {
                        is com.example.domain.models.QuizMutationError.NetworkTimeout -> "Request timed out. Please try again."
                        is com.example.domain.models.QuizMutationError.AuthExpired -> "Failed: You must be logged in."
                        is com.example.domain.models.QuizMutationError.ServerRejected -> "Failed: ${error.reason}"
                        else -> "Failed to create quiz."
                    }
                    _events.send(QuizConfigEvent.ShowToast(msg))
                }

            } catch (e: TimeoutCancellationException) {
                _events.send(QuizConfigEvent.ShowToast("Request timed out. Please try again."))
            } catch (e: Exception) {
                _events.send(QuizConfigEvent.ShowToast("Failed to create quiz."))
            } finally {
                _uiState.update { it.copy(isStartingQuiz = false) }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun recalculateState(currentState: QuizConfigState) {
        viewModelScope.launch(Dispatchers.Default) {
            val validMetadata = filterQuestions(localMetadataCache, currentState)
            val totalAvailableCount = validMetadata.size

            val subjectCounts = engine.getProjectedCounts(
                filterQuestions(localMetadataCache, currentState.copy(selectedSubjects = emptySet())).map { it.id }.toSet(), engine.getSubjectIndex()
            )
            val topicCounts = engine.getProjectedCounts(
                filterQuestions(localMetadataCache, currentState.copy(selectedTopics = emptySet())).map { it.id }.toSet(), engine.getTopicIndex()
            )
            val subTopicCounts = engine.getProjectedCounts(
                filterQuestions(localMetadataCache, currentState.copy(selectedSubTopics = emptySet())).map { it.id }.toSet(), engine.getSubTopicIndex()
            )
            val difficultyCounts = engine.getProjectedCounts(
                filterQuestions(localMetadataCache, currentState.copy(selectedDifficulties = emptySet())).map { it.id }.toSet(), engine.getDifficultyIndex()
            )
            val examCounts = engine.getProjectedCounts(
                filterQuestions(localMetadataCache, currentState.copy(selectedExams = emptySet())).map { it.id }.toSet(), engine.getExamIndex()
            )
            val yearCounts = engine.getProjectedCounts(
                filterQuestions(localMetadataCache, currentState.copy(selectedYears = emptySet())).map { it.id }.toSet(), engine.getYearIndex()
            )
            val shiftCounts = engine.getProjectedCounts(
                filterQuestions(localMetadataCache, currentState.copy(selectedShifts = emptySet())).map { it.id }.toSet(), engine.getShiftIndex()
            )
            val tagCounts = engine.getProjectedCounts(
                filterQuestions(localMetadataCache, currentState.copy(selectedTags = emptySet())).map { it.id }.toSet(), engine.getTagIndex()
            )

            val availableTopics = if (currentState.selectedSubjects.isEmpty()) {
                emptyList()
            } else {
                engine.getTopicsForSubjects(currentState.selectedSubjects).intersect(topicCounts.keys).sorted()
            }
            
            val availableSubTopics = if (currentState.selectedTopics.isEmpty()) {
                emptyList()
            } else {
                engine.getSubTopicsForTopics(currentState.selectedTopics).intersect(subTopicCounts.keys).sorted()
            }
            
            // Available indices based on everything available globally, sorted
            val availableExams = examCounts.keys.sorted()
            val availableYears = yearCounts.keys.sorted()
            val availableShifts = shiftCounts.keys.sorted()
            val availableTags = tagCounts.keys.sorted()

            _uiState.update { state ->
                state.copy(
                    totalAvailableCount = totalAvailableCount,
                    subjectCounts = subjectCounts,
                    topicCounts = topicCounts,
                    subTopicCounts = subTopicCounts,
                    difficultyCounts = difficultyCounts,
                    examCounts = examCounts,
                    yearCounts = yearCounts,
                    shiftCounts = shiftCounts,
                    tagCounts = tagCounts,
                    availableTopics = availableTopics,
                    availableSubTopics = availableSubTopics,
                    availableExams = availableExams,
                    availableYears = availableYears,
                    availableShifts = availableShifts,
                    availableTags = availableTags
                )
            }
        }
    }
    
    private fun filterQuestions(
        questions: List<com.example.domain.models.QuestionMetadata>, 
        filters: QuizConfigState
    ): List<com.example.domain.models.QuestionMetadata> {
        
        if (filters.selectedSubjects.isEmpty() && 
            filters.selectedTopics.isEmpty() &&
            filters.selectedSubTopics.isEmpty() &&
            filters.selectedDifficulties.isEmpty() && 
            filters.selectedExams.isEmpty() &&
            filters.selectedYears.isEmpty() &&
            filters.selectedShifts.isEmpty() &&
            filters.selectedTags.isEmpty()) {
            return questions
        }

        return questions.filter { question ->
            var isValid = true

            if (filters.selectedSubjects.isNotEmpty()) {
                if (question.subject == null || !filters.selectedSubjects.contains(question.subject)) {
                    isValid = false
                }
            }
            
            if (isValid && filters.selectedTopics.isNotEmpty()) {
                if (question.topic == null || !filters.selectedTopics.contains(question.topic)) {
                    isValid = false
                }
            }

            if (isValid && filters.selectedSubTopics.isNotEmpty()) {
                if (question.subTopic == null || !filters.selectedSubTopics.contains(question.subTopic)) {
                    isValid = false
                }
            }

            if (isValid && filters.selectedDifficulties.isNotEmpty()) {
                if (question.difficulty == null || !filters.selectedDifficulties.contains(question.difficulty)) {
                    isValid = false
                }
            }

            if (isValid && filters.selectedExams.isNotEmpty()) {
                if (question.examName == null || !filters.selectedExams.contains(question.examName)) {
                    isValid = false
                }
            }

            if (isValid && filters.selectedYears.isNotEmpty()) {
                if (question.year == null || !filters.selectedYears.contains(question.year)) {
                    isValid = false
                }
            }

            if (isValid && filters.selectedShifts.isNotEmpty()) {
                if (question.shift == null || !filters.selectedShifts.contains(question.shift)) {
                    isValid = false
                }
            }

            if (isValid && filters.selectedTags.isNotEmpty()) {
                val qTags = question.tags ?: emptyList()
                if (qTags.none { it in filters.selectedTags }) {
                    isValid = false
                }
            }

            isValid
        }
    }
}
