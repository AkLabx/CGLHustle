# PHASE 5 -> Runtime State Generator, ActiveQuestion Assembly & Quiz Session State Architecture

## 1. RUNTIME STATE GENERATION PIPELINE
The `StateGenerationUseCase` isolates the pure logic surrounding the massive `QuizRuntimeState` generation. It operates entirely on `Dispatchers.Default` handling the necessary deduplicated `cleanPayloads` map inputs.

## 2. ACTIVEQUESTION ASSEMBLY
The payload preserves the exact structure inherited by the `QuestionPayload` output. `activeQuestions` inside `QuizRuntimeState` maps 1:1, preserving sequential indexing and avoiding accidental order scrambles for valid downstream hydration inside the UI components.

## 3. DETERMINISTIC TIMER INITIALIZATION
The `StateGenerationUseCase` securely translates `learning`, `mock`, and `god` configurations into safe integer payloads in milliseconds natively supporting downstream Javascript parity parsing bounds.

## 4. OMITTED NULLS PARITY
Relying on the underlying serialization rules `encodeDefaults = false` and `explicitNulls = false` specified in the `SupabaseModule` config, default map structures (empty hashes) and explicit null bindings are fully stripped acting exactly like typical stringified payloads over Web runtimes.

## 5. DEFAULT ANSWER EXCLUSION
The default `bookmark`, `review`, and `answers` lists are explicitly generated with immutable `emptyList`/`emptyMap` stubs ensuring deterministic structure consistency.
