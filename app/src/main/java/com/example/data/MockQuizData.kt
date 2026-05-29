package com.example.data

import com.example.model.Difficulty
import com.example.model.Question
import com.example.model.Quiz
import com.example.model.QuizType

object MockQuizData {

    private val cglQuestions = listOf(
        Question("c_q1", "Who is the Father of the Indian Constitution?", listOf("Mahatma Gandhi", "B. R. Ambedkar", "Jawaharlal Nehru", "Sardar Patel"), 1, "Dr. B. R. Ambedkar is recognized as the Father of the Indian Constitution.", "Polity", Difficulty.EASY),
        Question("c_q2", "Which planet is known as the Red Planet?", listOf("Venus", "Jupiter", "Mars", "Saturn"), 2, "Mars is called the Red Planet because of iron oxide (rust) on its surface.", "Geography", Difficulty.EASY),
        Question("c_q3", "If 15% of A is equal to 20% of B, then A : B is?", listOf("3:4", "4:3", "5:4", "4:5"), 1, "15/100 * A = 20/100 * B => A/B = 20/15 = 4/3.", "Quantitative Aptitude", Difficulty.EASY),
        Question("c_q4", "A train 120m long passes a telegraph post in 6 seconds. Find the speed of the train.", listOf("72 km/hr", "60 km/hr", "20 km/hr", "54 km/hr"), 0, "Speed = Distance / Time = 120 / 6 = 20 m/s. In km/hr = 20 * (18/5) = 72 km/hr.", "Quantitative Aptitude", Difficulty.MEDIUM),
        Question("c_q5", "Choose the synonym for 'ABANDON'.", listOf("Keep", "Pursue", "Forsake", "Protect"), 2, "Abandon means to leave completely, which is synonymous with Forsake.", "English", Difficulty.EASY)
    )

    private val bpscQuestions = listOf(
        Question("b_q1", "Who was the founder of the Mauryan Empire?", listOf("Ashoka", "Chandragupta Maurya", "Bindusara", "Bimbisara"), 1, "Chandragupta Maurya founded the Mauryan Empire in ancient India.", "History", Difficulty.EASY),
        Question("b_q2", "Tilu Rauteli is a famous folklore character of which state?", listOf("Uttarakhand", "Bihar", "Jharkhand", "Uttar Pradesh"), 0, "Though famous in exam circles, she is from Uttarakhand.", "General Knowledge", Difficulty.MEDIUM),
        Question("b_q3", "What is the chemical formula of ozone?", listOf("O2", "O", "O3", "O4"), 2, "Ozone is comprised of three oxygen atoms, hence O3.", "Science", Difficulty.EASY),
        Question("b_q4", "In which year did the Champaran Satyagraha take place?", listOf("1915", "1917", "1919", "1920"), 1, "The Champaran Satyagraha of 1917 was Mahatma Gandhi's first Satyagraha in India.", "History", Difficulty.MEDIUM),
        Question("b_q5", "The fundamental rights are mentioned in which part of the Constitution?", listOf("Part II", "Part III", "Part IV", "Part V"), 1, "Part III of the Indian Constitution contains the Fundamental Rights.", "Polity", Difficulty.MEDIUM)
    )

    val availableQuizzes = listOf(
        Quiz("q1", "CGL", "CGL Full Mock Test 1", "Comprehensive test for Staff Selection Commission Combined Graduate Level.", 60, QuizType.FULL_MOCK, cglQuestions + cglQuestions.map { it.copy(id = it.id + "_copy") }), // Total 10 questions
        Quiz("q2", "CGL", "Quant Sectional - Time & Distance", "Practice Time, Speed and Distance concepts for CGL.", 15, QuizType.SECTIONAL, cglQuestions.filter { it.topic == "Quantitative Aptitude" }),
        Quiz("q3", "BPSC", "BPSC 69th Prelims Mock", "Full-length mock test based on the BPSC Prelims syllabus.", 120, QuizType.FULL_MOCK, bpscQuestions + bpscQuestions.map { it.copy(id = it.id + "_copy") }),
        Quiz("q4", "BPSC", "Modern History Subject Test", "Subject specific test focusing on Modern Indian History.", 30, QuizType.SUBJECT, bpscQuestions.filter { it.topic == "History" })
    )

    val allQuestions = (cglQuestions + bpscQuestions).distinctBy { it.id }
}
