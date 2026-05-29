# PHASE 6 -> Lifecycle Resilience, Configuration Change Survival & Process-Death Recovery Architecture

## 1. SavedStateHandle Injection & Bounds
The `QuizConfigViewModel` initializes and caches its parameters strictly using `SavedStateHandle`. Fields like `quizName`, filters, and `mode` are reliably kept via bundle state restoration avoiding any custom JSON mapping overhead. Expensive `filteredMetadata` is re-calculated in `Dispatchers.Default` relying natively on the re-injected filters after process death to avoid `TransactionTooLargeException`.

## 2. Room Authority Validation
During `hydrateCustomSession`, `ActiveQuizViewModel` treats the existing local Room DB object as authoritative ensuring remote overwrites cannot silently stomp existing local mutations if the network temporarily crashes out. Hydration is derived purely from stringified parsing validating JSON schemas natively via Kotlinx-Serialization and natively recovering unparseable properties cleanly onto base fallbacks (`""`, `0`).

## 3. Elapsed-time Engine
A secure `targetEndTime` calculates relative bounds using `android.os.SystemClock.elapsedRealtime()`. This covers explicit thread pauses out of Android bounds natively keeping timestamps monotonically synced inside background contexts seamlessly covering UI interruptions safely.

## 4. Single-Action Execution
Loading transitions inside `QuizConfigViewModel` (`isStartingQuiz`) are decoupled from specific UI coroutine blocks rendering natively scoped UI loading safely wrapped within the deterministic `viewModelScope.launch`.

## 5. Coroutine Delivery
All View-destined intent navigations (`NavigateToLibrary`) fire out of a `Channel(Channel.BUFFERED).receiveAsFlow()`. This avoids sharedflow repeat emissions cleanly destroying double-renders or accidental dual-intent backstack corruptions during manual device rotation triggers across the `Compose.navController`.
