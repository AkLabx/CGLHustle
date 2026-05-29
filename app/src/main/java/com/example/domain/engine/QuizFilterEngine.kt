package com.example.domain.engine

import com.example.domain.models.QuestionMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuizFilterEngine {
    private val subjectIndex = mutableMapOf<String, MutableSet<String>>()
    private val topicIndex = mutableMapOf<String, MutableSet<String>>()
    private val subTopicIndex = mutableMapOf<String, MutableSet<String>>()
    private val difficultyIndex = mutableMapOf<String, MutableSet<String>>()
    private val examIndex = mutableMapOf<String, MutableSet<String>>()
    private val yearIndex = mutableMapOf<String, MutableSet<String>>()
    private val shiftIndex = mutableMapOf<String, MutableSet<String>>()
    private val tagIndex = mutableMapOf<String, MutableSet<String>>()
    private val allQuestionIds = mutableSetOf<String>()
    
    // Dependency Map (For UI Cascading)
    private val subjectToTopicsMap = mutableMapOf<String, MutableSet<String>>()
    private val topicToSubTopicsMap = mutableMapOf<String, MutableSet<String>>()

    suspend fun buildIndex(metadataList: List<QuestionMetadata>) {
        withContext(Dispatchers.Default) {
            subjectIndex.clear()
            topicIndex.clear()
            subTopicIndex.clear()
            difficultyIndex.clear()
            examIndex.clear()
            yearIndex.clear()
            shiftIndex.clear()
            tagIndex.clear()
            allQuestionIds.clear()
            subjectToTopicsMap.clear()
            topicToSubTopicsMap.clear()

            for (item in metadataList) {
                allQuestionIds.add(item.id)
                
                if (item.subject != null) subjectIndex.getOrPut(item.subject) { mutableSetOf() }.add(item.id)
                if (item.topic != null) topicIndex.getOrPut(item.topic) { mutableSetOf() }.add(item.id)
                if (item.subTopic != null) subTopicIndex.getOrPut(item.subTopic) { mutableSetOf() }.add(item.id)
                if (item.difficulty != null) difficultyIndex.getOrPut(item.difficulty) { mutableSetOf() }.add(item.id)
                
                if (item.examName != null) examIndex.getOrPut(item.examName) { mutableSetOf() }.add(item.id)
                if (item.year != null) yearIndex.getOrPut(item.year) { mutableSetOf() }.add(item.id)
                if (item.shift != null) shiftIndex.getOrPut(item.shift) { mutableSetOf() }.add(item.id)
                
                item.tags?.let { tags ->
                    for (tag in tags) {
                        tagIndex.getOrPut(tag) { mutableSetOf() }.add(item.id)
                    }
                }
                
                if (item.subject != null && item.topic != null) {
                    subjectToTopicsMap.getOrPut(item.subject) { mutableSetOf() }.add(item.topic)
                }
                if (item.topic != null && item.subTopic != null) {
                    topicToSubTopicsMap.getOrPut(item.topic) { mutableSetOf() }.add(item.subTopic)
                }
            }
        }
    }

    fun getAllQuestionIds(): Set<String> = allQuestionIds
    fun getSubjectIndex(): Map<String, Set<String>> = subjectIndex
    fun getTopicIndex(): Map<String, Set<String>> = topicIndex
    fun getSubTopicIndex(): Map<String, Set<String>> = subTopicIndex
    fun getDifficultyIndex(): Map<String, Set<String>> = difficultyIndex
    fun getExamIndex(): Map<String, Set<String>> = examIndex
    fun getYearIndex(): Map<String, Set<String>> = yearIndex
    fun getShiftIndex(): Map<String, Set<String>> = shiftIndex
    fun getTagIndex(): Map<String, Set<String>> = tagIndex

    fun getTopicsForSubjects(subjects: Set<String>): Set<String> {
        if (subjects.isEmpty()) {
            return setOf() // Usually, no themes available if subjects not selected, based on prompt step 3 
        }
        val result = mutableSetOf<String>()
        for (subject in subjects) {
            subjectToTopicsMap[subject]?.let { result.addAll(it) }
        }
        return result
    }

    fun getSubTopicsForTopics(topics: Set<String>): Set<String> {
        if (topics.isEmpty()) {
            return setOf()
        }
        val result = mutableSetOf<String>()
        for (topic in topics) {
            topicToSubTopicsMap[topic]?.let { result.addAll(it) }
        }
        return result
    }

    fun calculateValidIds(
        selectedSubjects: Set<String>,
        selectedTopics: Set<String>,
        selectedSubTopics: Set<String> = emptySet(),
        selectedDifficulties: Set<String>,
        selectedExams: Set<String> = emptySet(),
        selectedYears: Set<String> = emptySet(),
        selectedShifts: Set<String> = emptySet(),
        selectedTags: Set<String> = emptySet(),
        ignoreCategory: String? = null
    ): Set<String> {
        val validSubjectIds = if (selectedSubjects.isEmpty() || ignoreCategory == "SUBJECT") allQuestionIds else selectedSubjects.flatMap { subjectIndex[it].orEmpty() }.toSet()
        val validTopicIds = if (selectedTopics.isEmpty() || ignoreCategory == "TOPIC") allQuestionIds else selectedTopics.flatMap { topicIndex[it].orEmpty() }.toSet()
        val validSubTopicIds = if (selectedSubTopics.isEmpty() || ignoreCategory == "SUB-TOPIC") allQuestionIds else selectedSubTopics.flatMap { subTopicIndex[it].orEmpty() }.toSet()
        val validDifficultyIds = if (selectedDifficulties.isEmpty() || ignoreCategory == "DIFFICULTY") allQuestionIds else selectedDifficulties.flatMap { difficultyIndex[it].orEmpty() }.toSet()
        val validExamIds = if (selectedExams.isEmpty() || ignoreCategory == "EXAM NAME") allQuestionIds else selectedExams.flatMap { examIndex[it].orEmpty() }.toSet()
        val validYearIds = if (selectedYears.isEmpty() || ignoreCategory == "EXAM YEAR") allQuestionIds else selectedYears.flatMap { yearIndex[it].orEmpty() }.toSet()
        val validShiftIds = if (selectedShifts.isEmpty() || ignoreCategory == "EXAM SHIFT") allQuestionIds else selectedShifts.flatMap { shiftIndex[it].orEmpty() }.toSet()
        val validTagIds = if (selectedTags.isEmpty() || ignoreCategory == "TAGS") allQuestionIds else selectedTags.flatMap { tagIndex[it].orEmpty() }.toSet()
        
        return validSubjectIds intersect validTopicIds intersect validSubTopicIds intersect validDifficultyIds intersect validExamIds intersect validYearIds intersect validShiftIds intersect validTagIds
    }

    fun getProjectedCounts(validIds: Set<String>, targetIndex: Map<String, Set<String>>): Map<String, Int> {
        val projectedCounts = mutableMapOf<String, Int>()
        for ((key, ids) in targetIndex) {
            val count = (validIds intersect ids).size
            if (count > 0) {
                projectedCounts[key] = count
            }
        }
        return projectedCounts
    }
}
