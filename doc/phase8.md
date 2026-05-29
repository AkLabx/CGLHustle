# PHASE 8 -> Active Quiz Runtime Engine, Session State Machine & Question Interaction Architecture

## 1. ActiveQuizViewModel Orchestration
Created `ActiveQuizViewModel` as the single source of truth for an active test session, hydrating it via a deep navigation link `ActiveQuizRoute(quizId)`. Interaction with the `activeQuestions` uses atomic operations to append strictly bound integer indices, preventing recomposition mismatch when swiping rapidly.

## 2. In-Memory Authority & Score Propagation
All selected answers trigger an immediate atomic mutation flow inside `ActiveQuizViewModel`. `score` and `activeQuestions` evaluate iteratively, enforcing optimistic UI responsiveness within Jetpack Compose state-hoisting, while securely recalculating the result within Coroutine bounds and saving locally for offline-resilience.

## 3. Bookmark & Hidden Options State Resurgence 
Features like Bookmark integration and 50-50 lifeline are successfully coupled to emit modified `QuizRuntimeState` variants internally to avoid heavy Map allocations. Compose handles caching optimizations leveraging `@Stable` UI properties where necessary.

## 4. Compose UI State Segregation
Isolated the entire temporal flow out into independent collection sequences preventing timer-based recomposition loops. `QuizSession` serves as an offline-first resilient snapshot mapping while strictly binding index manipulations locally.
