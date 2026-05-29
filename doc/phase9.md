# PHASE 9 -> Timer Engine, Time Continuity, Background Execution & Temporal Consistency Architecture

## 1. Monotonic Timing Enforcement 
The timer architecture successfully incorporates `android.os.SystemClock.elapsedRealtime()` over unauthoritative native `delay(1000)` variants. Every tick wakes, tracks against its previous timestamp, deducts accurate drifts iteratively, and ensures temporal integrity. 

## 2. App Background Validation
We implemented a strict Compose `LifecycleEventObserver` inside `ActiveQuizScreen` intercepting native `Lifecycle.Event.ON_PAUSE` & `ON_RESUME`. Pause suspends the clock mechanism immediately while keeping score and session indices intact. Resuming dynamically computes offset drift off the process-death safe `lastPausedWallClockTime`, recalculating offline drifts and clamping remaining quiz allocations securely. 

## 3. Recomposition Safe Ticking
Refactored `ActiveQuizScreen` to securely delegate continuous updates using independent fast-emitting state-flows mapping only strictly bounded `ActionRow` tree nodes. This effectively limits full root-node Compose recalculations solving immediate frame drop & heavy GC penalties on 60m+ tests.

## 4. Preemptive Auto-Finalization
Terminative operations sequentially cancel all global/local timer jobs natively before flushing the `TestResultDao`. Submitting the exam acts strictly as the definitive source block halting background operations to avoid multiple submission duplicates natively.
