Android Native Architecture Dependency Map

Feature Dependency, Layering, Execution Order & Integration Coordination Framework

This document defines the dependency structure, architectural layering, execution sequencing, lifecycle criticality, threading boundaries, async orchestration rules, and integration-risk areas for the Android Native Quiz platform.

This document should be treated as:

- a construction dependency graph
- a subsystem coordination framework
- a production architecture planning document
- an implementation sequencing guide
- a lifecycle-criticality reference
- a multi-engineer integration coordination layer

The goal of this document is NOT to force rewrites.

The goal is:

- prevent architectural drift
- avoid subsystem conflicts
- avoid dependency inversion mistakes
- reduce future rewrites
- standardize implementation sequencing
- identify lifecycle-sensitive systems early
- identify high-risk integration areas before scaling implementation

This document should be read BEFORE beginning deep implementation phases.

---

1. FEATURE DEPENDENCY GRAPH

Upstream/Downstream Dependency Relationships

This section defines which systems depend on which foundational layers.

The purpose is:

- prevent premature implementation
- avoid unstable orchestration
- identify blocking dependencies
- define safe implementation ordering

---

Quiz Creation Orchestrator (QuizConfigViewModel)

The orchestration layer depends on:

QuizConfigViewModel
→ Quiz Creation UseCase
→ Question Deduplication UseCase
→ Runtime State Generation UseCase
→ Auth Session Provider
→ Supabase Auth Client
→ Question Fetch Repository
→ Supabase PostgREST Client
→ Question DTOs
→ Quiz Mutation Repository
→ SavedQuiz Request DTOs
→ BridgeRow DTOs
→ Kotlinx Serialization Layer

---

Blocking Dependency Rules

The following implementation constraints must be respected:

Constraint 1

QuizConfigViewModel should NOT be finalized until:

- mutation repositories
- state generation layer
- serialization contracts
  are stabilized.

---

Constraint 2

Quiz mutation repositories should NOT finalize until:

- DTO structures
- JSON serialization contracts
- request payload mapping
  are stabilized.

---

Constraint 3

Deduplication logic should NOT finalize until:

- payload normalization rules
- Unicode handling behavior
- regex parity behavior
  are verified.

---

2. MODULE LAYERING MAP

Architectural Boundaries & Responsibilities

This section defines strict subsystem separation rules.

---

UI Layer (Jetpack Compose / XML)

Responsibilities

- emit click intents
- observe StateFlow
- render overlays
- render snackbar/error states
- collect navigation events

Allowed Dependencies

Presentation Layer only

Must NOT Depend On

- repositories
- Supabase SDK
- serialization logic
- database access
- networking implementation

---

Presentation Layer (ViewModel + UiState)

Responsibilities

- orchestration entry points
- mutation state management
- StateFlow ownership
- side-effect handling
- lifecycle-aware coroutine management

Allowed Dependencies

Domain Layer

Must NOT Depend On

- direct networking
- Compose UI references
- serialization internals
- direct DB mutation calls

---

Domain Layer (UseCases)

Responsibilities

- deduplication logic
- timer generation
- payload assembly
- orchestration sequencing
- validation rules

Allowed Dependencies

Repository Layer

Must NOT Depend On

- ViewModels
- UI
- Android Context
- Compose
- navigation
- Supabase SDK directly

---

Data Layer (Repositories)

Responsibilities

- fetchQuestions()
- insertQuiz()
- batching orchestration
- request coordination
- DTO conversion

Allowed Dependencies

Network Layer
Serialization Layer

Must NOT Depend On

- ViewModels
- Compose
- navigation
- UI state

---

Network / Persistence Layer

Responsibilities

- execute HTTP requests
- manage authorization headers
- timeout enforcement
- Ktor/Supabase execution

Must NOT Depend On

Application business logic.

---

Serialization Layer

Responsibilities

- DTO ↔ JSON mapping
- runtime payload generation
- JSONB-safe serialization

Must Remain

- deterministic
- isolated
- platform-safe
- lifecycle-independent

---

3. IMPLEMENTATION ORDER GRAPH

Recommended Safe Implementation Sequence

This sequence exists to:

- avoid architectural deadlocks
- avoid unstable orchestration
- reduce rewrite risk

---

Stage 1 → Foundational Serialization Schemas

Focus:

- DTO stabilization
- runtime payload structures
- Kotlinx Serialization contracts
- nullable handling
- JSON structure stability

Critical:
Payload structures must stabilize before orchestration scales.

---

Stage 2 → Core Network Infrastructure

Focus:

- Supabase client stabilization
- auth propagation
- timeout wrappers
- request execution consistency

---

Stage 3 → Repository Layer Stabilization

Focus:

- fetch batching
- sequential insert orchestration
- request abstraction
- DTO-safe mutation handling

---

Stage 4 → Domain Logic Layer

Focus:

- deduplication
- timer logic
- runtime state generation
- orchestration sequencing

---

Stage 5 → Presentation Layer

Focus:

- mutation state machine
- lifecycle-safe orchestration
- event channels
- navigation side effects

---

Stage 6 → UI Integration Layer

Focus:

- overlays
- snackbars
- loading states
- navigation collection
- interaction locking

---

4. STATE OWNERSHIP MAP

State Responsibility & Lifecycle Boundaries

---

filteredMetadata

Owner

QuizConfigViewModel

Characteristics

- transient
- recomputable
- derived state
- survives configuration changes

---

isStartingQuiz

Owner

QuizConfigViewModel

Lifecycle Requirement

Must survive:

- rotation
- recomposition
- temporary lifecycle recreation

Reason:
Loading overlay continuity must remain stable during mutations.

---

Runtime Payload State

Characteristics

- transient during assembly
- persisted after mutation
- memory-heavy
- serialization-sensitive

---

Auth Session

Owner

Supabase Auth Module

Characteristics

- globally scoped
- persistence-backed
- lifecycle-independent

---

5. ASYNC EXECUTION DEPENDENCY MAP

Coroutine Sequencing Rules

---

Parallelizable Operations

The following MAY execute concurrently:

Chunk Fetch 1
Chunk Fetch 2
Chunk Fetch 3
...

using:

async { }
awaitAll()

---

Sequential Dependencies

The following MUST remain sequential:

Deduplication
→ Insert Master Quiz
→ Insert Bridge Rows
→ Navigation

No parallel mutation sequencing should occur here.

---

Timeout Boundaries

Mutation operations require timeout enforcement.

Conceptual pattern:

withTimeout(15000)

---

Cancellation Boundaries

Special handling required when:

- Activity destroyed
- process backgrounded
- navigation interrupted
- mutation canceled mid-flight

---

6. THREADING MODEL MAP

Dispatcher Responsibility Boundaries

---

Dispatchers.Main

Responsibilities

- UI state emission
- navigation events
- overlay rendering
- snackbar dispatch

Forbidden

- regex normalization
- JSON serialization
- heavy filtering
- network requests

---

Dispatchers.IO

Responsibilities

- network requests
- Room access
- auth retrieval
- Supabase mutations

---

Dispatchers.Default

Responsibilities

- deduplication loops
- regex normalization
- payload assembly
- heavy collection mapping

---

7. FAILURE PROPAGATION GRAPH

Exception Bubbling & UI Error Transformation

---

Network Failure Flow

Network Layer
→ Repository
→ UseCase
→ ViewModel
→ UI Event

---

Timeout Flow

TimeoutCancellationException
→ ViewModel
→ Snackbar/Event
→ Overlay Removal

---

Auth Failure Flow

Missing Session
→ Halt Mutation
→ Emit UI Event
→ Prevent DB Mutation

---

Partial Failure Risk

Possible sequence:

Insert #1 succeeds
Insert #2 fails

Result:

orphaned records

This risk must remain visible during implementation.

---

8. LIFECYCLE CRITICALITY MAP

Android Runtime Hazard Areas

---

Activity Recreation

The architecture must survive:

- rotation
- recomposition
- Activity recreation

Mutation orchestration must remain ViewModel-scoped.

---

Navigation Timing Risks

Navigation events must:

- remain lifecycle-safe
- avoid replay duplication
- avoid invalid fragment transactions

---

Process Death Risks

If process dies during mutation:

- coroutine may terminate
- persistence may become partial
- UI restoration may become inconsistent

This area requires special implementation care.

---

9. INTEGRATION RISK HOTSPOTS

High-Risk Coupling Areas

---

Serialization Mismatch Risk

The runtime payload structure must remain:

- stable
- deterministic
- schema-safe

Incorrect DTO mapping may destabilize:

- persistence
- restoration
- hydration
- runtime rendering

---

Schema Coupling Risk

Bridge row mappings must preserve:

- exact field names
- relational consistency
- insertion ordering

---

Deduplication Parity Risk

Unicode normalization behavior must remain deterministic.

Differences in normalization behavior may:

- preserve duplicates unexpectedly
- remove valid questions incorrectly

---

Cache Coherency Risk

Navigation back to library screens requires:

- deterministic refresh behavior
- stale-cache prevention
- hydration consistency

---

10. FINAL IMPLEMENTATION READINESS MATRIX

Work Division & Dependency Coordination Framework

The following systems are safe to build independently:

- DTO stabilization
- auth provider
- deduplication engine
- loading overlay UI

The following systems are dependency-sensitive:

- mutation repositories
- runtime state generator
- orchestration ViewModels
- navigation event coordination

The following systems are lifecycle-critical:

- mutation orchestration
- process-death recovery
- timer continuity
- navigation side effects
- background mutation handling

Implementation should proceed:

- dependency-aware
- lifecycle-aware
- subsystem-by-subsystem
- with controlled stabilization

The goal is:
stable production evolution —
not uncontrolled feature layering.
