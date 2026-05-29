# PHASE 3 -> Parallel Chunk Fetching, Question Hydration & Deduplication Engine

## 1. COMPLETE QUESTION FETCH PIPELINE
The list of selected metadata questions is divided into batches of up to 200 question IDs.
`repository.fetchQuestionPayloads()` handles the actual DB fetch by chunk.

## 2. CHUNKING + PARALLELIZATION DESIGN
Coroutines orchestrate execution ensuring maximum fetch velocity.
`awaitAll()` flattens execution into a monolithic deterministic list.
Uses `getOrThrow()` inside `coroutineScope` to enable pure Fail-Fast behavior so a failed chunk aborts the entire mutation, avoiding corrupt quizzes.

## 3. DEDUPLICATION ENGINE ARCHITECTURE
Split into Stage 1 (ID Uniqueness) and Stage 2 (Composite Text).
Normalizes purely text characters relying on `java.util.Locale.ROOT` and `\\s+` to eliminate redundant whitespace formatting while respecting Unicode ligatures.

## 4. DETERMINISTIC OUTPUT
`filter { }` along with a HashSet guarantees chronological consistency. Sort_order dependencies inside Bridge remain exact. Missing options fallback efficiently.
