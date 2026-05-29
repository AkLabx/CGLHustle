# PHASE 2 -> QuizConfigViewModel Mutation Orchestrator

## 1. COMPLETE QuizConfigViewModel ARCHITECTURE
ViewModel owns `StateFlow` and one-shot `Channel` events. Delegates logic to `CreateQuizUseCase`.

## 2. UI STATE MACHINE DESIGN
`QuizConfigUiState` holds `isLoadingMetadata`, `isStartingQuiz`, `showEmptyError` flags alongside UI properties.

## 3. MUTATION EXECUTION FLOW
ViewModel sets `isStartingQuiz = true`.
Fetches/deduplicates via UseCase.
Sends success/warning events via Channel.
Finally block ensures `isStartingQuiz = false`.

## 4. COROUTINE ORCHESTRATION ARCHITECTURE
Bound to `viewModelScope.launch`.
Parallellized chunk fetch.
Network boundary timeout at `15_000L`.

## 5. EVENT CHANNEL ARCHITECTURE
One-shot events (`ShowToast`, `NavigateToLibrary` etc) delivered via buffered channel to prevent UI recreation duplicate emissions.

## 6. THREADING + DISPATCHER ARCHITECTURE
`Dispatchers.Main` handles UI state.
`Dispatchers.Default` powers deduplication and heavy mappings.
`Dispatchers.IO` utilized for backend queries.

## 7. FAILURE PROPAGATION + RECOVERY
Timeouts invoke `TimeoutCancellationException` catching, propagating safe user toasts and resetting overlays.
