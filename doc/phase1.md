[ Here is the FIRST DEEP IMPLEMENTATION EXPANSION focused strictly on: PHASE 1 → Detailed Android Data Models & JSON Serialization Schema

This maps the exact parity required to ensure the Kotlin Android app natively generates PostgREST-compatible JSON that the existing Web app can seamlessly hydrate.

1. COMPLETE DTO HIERARCHY
1.1 SavedQuizInsertRequest (Master Insert DTO)
Purpose: Represents the top-level payload sent to supabase.from("saved_quizzes").insert().
Ownership: QuizMutationRepository.
Persistence: Transient memory model used strictly for serialization into the HTTP body.
Relation to Web: Maps to the anonymous object created in QuizConfig.tsx (line 213).
Kotlin Structure:
@Serializable
data class SavedQuizInsertRequest(
val id: String, // UUID
val user_id: String, // UUID
val name: String,
val created_at: Long, // Epoch ms
val filters: InitialFilters,
val mode: String, // "learning" | "mock" | "god"
val state: QuizRuntimeState // The massive JSONB payload
)
1.2 BridgeInsertRequest (Relational Insert DTO)
Purpose: Represents the payload sent to bridge_saved_quiz_questions.
Kotlin Structure:
@Serializable
data class BridgeInsertRequest(
val quiz_id: String,
val question_id: String,
val sort_order: Int,
val user_id: String
)
1.3 QuizRuntimeState (The JSONB Monolith)
Purpose: The exact shape of the JSON stored in the saved_quizzes.state column.
Ownership: Persisted in Supabase JSONB.
Relation to Web: Maps exactly to QuizRuntimeState in src/features/quiz/types/store.ts.
Kotlin Structure:
@Serializable
data class QuizRuntimeState(
val quizName: String? = null,
val isToolbarExpanded: Boolean = true,
val last_updated: Long? = null,
val currentQuestionIndex: Int = 0,
val score: Int = 0,
val answers: Map<String, String> = emptyMap(),
val timeTaken: Map<String, Int> = emptyMap(),
val remainingTimes: Map<String, Int> = emptyMap(),
val quizTimeRemaining: Int = 0,
val bookmarks: List<String> = emptyList(),
val markedForReview: List<String> = emptyList(),
val hiddenOptions: Map<String, List<String>> = emptyMap(),
val isPaused: Boolean = false,
val syncStatus: String = "idle",
val quizId: String,
val status: String = "quiz",
val mode: String,
val activeQuestions: List<Question>,
val filters: InitialFilters? = null
)
1.4 Question (The Heavy Nested Payload)
Purpose: Stores the exact snapshot of a question at the moment the quiz was created.
Kotlin Structure:
@Serializable
data class Question(
val id: String,
val v1_id: String? = null,
val examName: String? = null,
val examYear: Int? = null,
val examDateShift: String? = null,
val subject: String? = null,
val topic: String? = null,
val subTopic: String? = null,
val sourceInfo: SourceInfo,
val classification: Classification,
val tags: List<String> = emptyList(),
val properties: Properties,
val question: String,
val question_hi: String? = null,
val options: List<String>,
val options_hi: List<String>? = null,
val correct: String,
val explanation: JsonElement, // Maps to ANY valid JSON or {}. Crucial for parity.
val ask_ai_explanation: JsonElement? = null
)


2. EXACT JSON SHAPE PARITY
Exact Field Names: Kotlin @SerialName MUST be used if Kotlin casing differs from Web. However, the Web app uses a chaotic mix of camelCase (e.g., timeTaken, activeQuestions) and snake_case (e.g., last_updated, user_id). Do not use a global naming strategy in kotlinx.serialization. Explicitly type the Kotlin fields to match the Web names exactly.
Optional Field Behavior: TypeScript uses undefined. When serialized to JSON via JSON.stringify(), undefined fields are omitted entirely from the JSON payload. To match this in Kotlin, you MUST set default values (= null or = emptyMap()) and use encodeDefaults = false (see Section 3).
Exact List Behavior: Empty arrays [] in TS stringify to [] in JSON. Kotlin emptyList() maps perfectly.
Exact Enum Behavior: The Web uses string literal types ('learning' | 'mock'). In Kotlin, use a String (with constants) rather than an Enum to prevent fatal serialization crashes if the Web app later adds a new mode ('god_v2') that the older Android app doesn't understand.
UUID Handling: Handled as standard String types in Kotlin.


3. KOTLIN SERIALIZATION STRATEGY
Configuration Required: The Android team MUST configure their Json instance exactly like this:



val quizJsonFormat = Json {
ignoreUnknownKeys = true // CRITICAL: Allows Web app to add new fields without crashing older Android apps.
encodeDefaults = false   // CRITICAL: Prevents Kotlin from writing nulls explicitly to the JSON string, mimicking JS undefined.
explicitNulls = false    // CRITICAL: Further ensures omitted fields simply vanish from the payload.
isLenient = true
}
Polymorphism / Nested Object Handling:

The explanation field in Web is typed as Explanation but is often populated as an empty object {}.
Risk: If Android maps this to a rigid Kotlin data class Explanation, and the web sends {}, kotlinx.serialization will crash stating "missing required fields".
Solution: Type explanation as kotlinx.serialization.json.JsonElement. This allows Android to blindly pass whatever JSON tree exists in the DB back into the DB without attempting to parse it into a rigid Kotlin structure.
4. NULLABILITY FORENSICS
Dangerous Null Assumptions from Web:

options: string[] in Web is assumed non-null. However, if the DB row has a SQL NULL for options, Supabase JS returns null. The Web app's TS interface says string[] (lying to the compiler).
If Android strict-types this as val options: List<String>, and a bad DB row returns null, the Android app will fatally crash on fetch.
Recommendation: Android MUST type it as val options: List<String> = emptyList() but implement a custom serializer or backing field to catch null and coerce it to emptyList().
v1_id is an optional string. Nullable in Kotlin (String?).
5. TYPE PARITY WITH TYPESCRIPT
Record<string, number> → Map<String, Int>
Web: timeTaken: Record<string, number>. JS number is a double-precision float. However, the logic stores milliseconds (integers). Maps to Kotlin Map<String, Int>. (Safe, up to 2.1 billion ms ~ 24 days per question).
Record<string, string[]> → Map<String, List<String>>
Web: hiddenOptions. Maps perfectly.
Date vs Timestamp String:
Web uses Date.now() which returns epoch milliseconds.
Kotlin MUST use System.currentTimeMillis() mapped to Long. Do NOT use ISO strings for created_at in the master insert; the Web uses ms for SavedQuiz.createdAt. Note: The DB created_at column is timestamptz. Supabase JS handles the conversion implicitly, but supabase-kt might require an ISO-8601 string depending on how the Instant serializer is configured. This is a critical integration test point.
6. JSONB STORAGE COMPATIBILITY
How PostgREST parses JSONB: When saved_quizzes.state is updated, PostgREST blindly accepts valid JSON and casts it to JSONB. Field ordering does not matter (JSONB strips whitespace and reorders keys alphabetically internally).
Omitted-Null Behavior: Because Android is configured with encodeDefaults = false, fields like isPaused (which defaults to false) will NOT be included in the JSON payload unless explicitly modified. This exactly mimics Web behavior.
7. SERIALIZATION FAILURE MODES
Hindi Text / UTF-8: Kotlin strings handle UTF-8 natively. Ktor serializes to UTF-8. There is zero risk of unicode corruption for question_hi assuming standard supabase-kt usage.
Large Payload Stalls: Serializing a 200-item ActiveQuestion list into a JSON string will take ~5-15ms of CPU time. It MUST execute on Dispatchers.Default before passing the string/object to the Ktor client, otherwise the UI thread will drop frames.
Circular Reference Risks: Kotlin data classes are acyclic by default. The Web architecture does not use circular graph models. Risk is zero.
8. MEMORY + PAYLOAD SIZE ANALYSIS
Heavy Object Graph: The activeQuestions list duplicates the entire Question object.
Heap Pressure: A 200-question quiz fetched from DB creates 200 Question objects. Adding them to QuizRuntimeState creates a reference copy (cheap). Serializing it via kotlinx creates an enormous intermediate String buffer (heavy).
Android Low-Memory Risk: Older devices might throw OutOfMemoryError during the Json.encodeToString() phase of the PostgREST request. Ensure the HTTP client streams the body if possible, or expect rare crashes on 2GB RAM devices.
9. STRICT PARITY REQUIREMENTS
Fields that MUST match exactly (No variance allowed):

status: String (Must precisely match Web literal strings like "quiz", "finalizing").
mode: String (Must precisely match "learning", "mock", "god").
activeQuestions: List<Question> (Hierarchy must be 1:1).
quizId: String (Must match the id of the master record exactly).
Fields safe for Android-only abstraction:

Internal ViewModel states (isStartingQuiz). They never hit JSON.
Fields with highest compatibility risk:

filters: InitialFilters?. The web app pushes a massive nested object of arrays. If Android sends a slightly different shape, Web filters will crash upon resuming the quiz.
explanation: JsonElement. Do not attempt to strongly type this on Android.
10. FINAL SERIALIZATION READINESS MATRIX

Model	Persisted?	JSONB?	Nullable Risk?	Cross-Platform Critical?	Heavy Object?

SavedQuizInsertRequest	Yes (DB Row)	No	Low	CRITICAL	No
QuizRuntimeState	Yes	YES	High	CRITICAL	YES (Massive)
Question	Yes	YES	EXTREME (options)	CRITICAL	YES
InitialFilters	Yes	YES	Low	High	No
BridgeInsertRequest	Yes (DB Row)	No	Low	High	No


END OF PHASE 1 EXPANSION.
]
