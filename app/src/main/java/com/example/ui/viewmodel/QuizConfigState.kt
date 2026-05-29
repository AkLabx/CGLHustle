package com.example.ui.viewmodel

data class QuizConfigState(
    val totalAvailableCount: Int = 0,
    val selectedSubjects: Set<String> = emptySet(),
    val selectedTopics: Set<String> = emptySet(),
    val selectedSubTopics: Set<String> = emptySet(),
    val selectedDifficulties: Set<String> = emptySet(),
    val selectedExams: Set<String> = emptySet(),
    val selectedYears: Set<String> = emptySet(),
    val selectedShifts: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val selectedMode: String = "learning",
    val quizName: String = "",
    
    // Live Counters for UI Projection
    val subjectCounts: Map<String, Int> = emptyMap(),
    val topicCounts: Map<String, Int> = emptyMap(),
    val subTopicCounts: Map<String, Int> = emptyMap(),
    val difficultyCounts: Map<String, Int> = emptyMap(),
    val examCounts: Map<String, Int> = emptyMap(),
    val yearCounts: Map<String, Int> = emptyMap(),
    val shiftCounts: Map<String, Int> = emptyMap(),
    val tagCounts: Map<String, Int> = emptyMap(),
    
    // Available choices extracted from index keys
    val availableExams: List<String> = emptyList(),
    val availableYears: List<String> = emptyList(),
    val availableShifts: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),

    // For Dependent Dropdowns/Chips
    val availableTopics: List<String> = emptyList(),
    val availableSubTopics: List<String> = emptyList(),
    val isLoadingMetadata: Boolean = false,
    val isStartingQuiz: Boolean = false, 
    val showEmptyError: Boolean = false,
    val errorMessage: String? = null
)
