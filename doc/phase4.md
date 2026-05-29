# PHASE 4 -> Supabase Sequential Insert Pipeline, Repository Mutation Architecture & Network Consistency Layer

## 1. COMPLETE REPOSITORY MUTATION ARCHITECTURE
The `ActiveSessionRepository` exposes `insertQuizWithBridge(masterDto, bridgeList)` replacing the database RPC approach (`create_quiz_session`). The UseCase explicitly delegates DTO assembly to its own scope and passes purely structured models to the repository. 

## 2. SEQUENTIAL PERSISTENCE GUARANTEE
`masterDto` is inserted prior to `bridgeList` logically enforcing referential integrity. No parallelization is present (`awaitAll` or `supervisorScope` parallel variants) avoiding Foreign Key Rejection.

## 3. TIMEOUT-SAFE SUPABASE ORCHESTRATION 
A strict `withTimeout(15_000L)` wrap around the remote queries terminates unresponsive backend networks.

## 4. TYPED REPOSITORY FAILURE CONTRACT
Remote HTTP failures are caught and coerced into `QuizMutationError` (e.g. `NetworkTimeout`, `AuthExpired`, `ServerRejected`, `UnknownError`), preventing ambiguous UI collapse.

## 5. PESSIMISTIC SYNCHRONIZATION
Optimistic `Room` caching is eliminated. `CreateQuizUseCase` executes without local DB mutations. Post-computation UI relies on `event(NavigateToLibrary)`, explicitly delegating visual state ownership to actual remote-synced network operations.
