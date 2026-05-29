Android Native Quiz Creation Architecture Blueprint

Production Implementation Framework – Create Quiz Flow & Post-Creation Systems

This document defines the intended production architecture, execution flow, subsystem boundaries, async sequencing, state orchestration, and implementation direction for the Android Native Quiz platform after the user enters the “Create Quiz” flow.

The purpose of this blueprint is:

- standardize implementation direction
- stabilize architecture before scaling feature complexity
- define subsystem responsibilities
- establish lifecycle-safe execution patterns
- align future implementation phases
- reduce rewrite risk
- improve long-term maintainability
- preserve scalability and runtime resilience

This document should be treated as:

- implementation architecture guidance
- subsystem dependency reference
- execution sequencing framework
- state-management planning document
- networking and persistence coordination reference

This is NOT a final code implementation document.
This is the production architecture framework future phases will build upon.

---

1. USER FLOW BLUEPRINT

End-to-End Create Quiz Execution Lifecycle

This section defines the high-level runtime flow beginning immediately after the user presses the primary “Create Quiz” action.

The Android implementation must preserve:

- sequential orchestration
- UI locking behavior
- async execution boundaries
- state consistency guarantees
- navigation timing
- error propagation behavior

---

Phase A → Trigger & UI Lock

Trigger Source

The flow begins when the user presses the primary Create Quiz action button from the quiz configuration surface.

Expected Runtime Behavior

Immediately after click:

- the UI enters a mutation state
- duplicate interactions must be blocked
- all further touches/interactions should be intercepted
- visual feedback must appear immediately

UI Locking Requirements

The Android implementation must introduce:

- fullscreen blocking overlay
- gesture interception
- loading state visibility
- prevention of rapid multi-click mutation triggering

State Transition

The UI state transitions into:

isStartingQuiz = true

This state becomes the root mutation state controlling:

- overlay visibility
- button disabled state
- navigation locking
- duplicate submission prevention

---

Phase B → Synchronous Validation

Validation Timing

Validation occurs synchronously before any heavy network or serialization work begins.

Required Validation Rules

Empty Question Validation

If filtered question count == 0:

- mutation halts immediately
- no network requests begin
- no serialization begins
- no DB orchestration begins

User Feedback Requirements

UI must:

- display temporary inline warning/error
- preserve current configuration state
- avoid navigation
- automatically dismiss temporary warning state

Required UI State

showEmptyError = true

Dismissal Behavior

The temporary empty-state warning must self-dismiss after approximately:

4000ms

without requiring user interaction.

---

Phase C → Full Question Fetch + Deduplication

This is the first heavy async execution phase.

---

Objective

The filtering engine only operates on lightweight metadata.
Before quiz creation:

- full question payloads
- answer structures
- explanations
- option structures
  must be hydrated.

---

Fetch Strategy Requirements

Chunking Requirements

Question IDs must be split into batches.

Recommended batch size:

200 IDs

Reasoning

This prevents:

- oversized HTTP requests
- query string overflow
- payload fragmentation instability
- request rejection risks

---

Parallel Fetch Requirements

Chunked requests may execute concurrently using:

async { ... }
awaitAll()

This phase represents the Android equivalent of:

Promise.all(...)

---

Deduplication Pipeline

The Android implementation must preserve deterministic deduplication behavior.

Stage 1 → Identifier Deduplication

Questions should first be deduplicated using:

v1_id

with fallback:

id

---

Stage 2 → Text-Based Deduplication

The system must:

- normalize English text
- normalize Hindi text
- remove whitespace
- lowercase text
- generate composite content keys

Required Normalization Logic

Equivalent normalization behavior:

replace("\\s+".toRegex(), "")

must apply before comparison.

---

Composite Deduplication Key

Composite structure:

normalizedEnglish-normalizedHindi

---

Duplicate Handling Behavior

If duplicates are removed:

- execution MUST continue
- quiz creation MUST NOT fail
- warning should be surfaced asynchronously

User Feedback

Display:

- Snackbar
- Toast
- Event-based warning message

without interrupting orchestration.

---

2. ANDROID UI LAYER BLUEPRINT

Compose Surface Architecture & Runtime UI State Model

This section defines the required Android presentation surfaces and their responsibilities.

---

Primary Surfaces

QuizConfigScreen

Primary orchestration surface responsible for:

- filter configuration
- metadata visualization
- Create Quiz trigger
- validation rendering
- mutation state rendering

---

LoadingOverlay

Dedicated blocking surface responsible for:

- preventing touch interaction
- displaying progress state
- mutation feedback
- preventing duplicate execution

Overlay Requirements

Must:

- sit above all Compose hierarchy
- intercept all touches
- remain lifecycle-safe across recompositions

---

EmptyStateBanner

Inline transient warning surface responsible for:

- empty quiz validation feedback
- temporary visibility state
- self-dismiss lifecycle behavior

---

UI State Representation

Required UI State Model

The UI layer must expose centralized immutable state.

Example conceptual structure:

data class QuizConfigUiState(
    val isStartingQuiz: Boolean,
    val showEmptyError: Boolean,
    val filteredMetadata: List<QuestionMetadata>,
    val error: String?
)

---

Lifecycle-Sensitive UI Requirements

Warning/Event Delivery

The UI layer requires:

- Snackbar queue
- one-shot event system
- replay-safe event delivery
- lifecycle-aware collection

Events include:

- duplicate warnings
- auth expiration
- timeout failures
- insertion failures
- partial fetch warnings

---

Navigation Behavior

Navigation Destination

Successful mutation navigates to:

QuizLibraryScreen

---

BackStack Behavior

QuizConfigScreen must be removed from back stack after successful creation.

Reason:
Hardware back navigation must NOT return to:

- stale mutation state
- already-created quiz config
- completed orchestration flow

---

3. VIEWMODEL & STATE MANAGEMENT BLUEPRINT

Mutation Orchestration & State Ownership

This section defines orchestration ownership responsibilities.

---

Required ViewModels

QuizConfigViewModel

This ViewModel becomes the primary orchestrator responsible for:

- validation
- orchestration sequencing
- deduplication coordination
- state assembly
- mutation execution
- error propagation
- navigation events

---

State Ownership Model

Persistent vs Transient State

Transient State

Examples:

- filteredMetadata
- temporary banners
- loading overlays
- snackbar events

These may be recomputed or regenerated.

---

Persistent Runtime State

Examples:

- generated runtime quiz state
- timer structures
- active question lists
- progress tracking
- persisted quiz session structures

These must survive:

- process death
- lifecycle recreation
- persistence restoration

---

Mutation Lifecycle

Expected Mutation Sequence

handleCreate()
→ validation
→ fetchQuestionsByIds()
→ deduplication
→ auth verification
→ payload assembly
→ insert #1
→ insert #2
→ navigation event

---

Coroutine Scope Ownership

All orchestration must execute inside:

viewModelScope.launch

to preserve:

- lifecycle cancellation
- structured concurrency
- cleanup boundaries

---

State Emission Requirements

The ViewModel must emit:

- loading states
- warning events
- navigation events
- mutation completion states
- failure states

through:

- StateFlow
- SharedFlow
- Channel-based side effects

depending on event type semantics.

---

4. DOMAIN LOGIC BLUEPRINT

Client-Side Runtime Rule Engine

This section defines the core runtime business logic executed on-device.

---

Validation Stages

Empty Question Guard

Mutation blocked when:

filteredMetadata.isEmpty()

---

Authentication Guard

Mutation blocked when:

session == null

---

Deduplication Engine

The deduplication engine is considered:

- CPU-heavy
- memory-sensitive
- parity-critical

Requirements

The Android implementation must preserve:

- deterministic ordering
- normalization parity
- duplicate elimination parity
- stable runtime output

---

Timer Generation Rules

Mock Mode

Global timer generation:

questionCount * defaultQuestionTime

---

Learning Mode

Per-question timer generation required.

Structure:

Map<QuestionId, RemainingTime>

---

Runtime State Generation

The Android runtime state generator must produce:

- a deeply nested runtime state object
- structurally stable persistence payloads
- deterministic restoration-compatible state

The generated structure must remain:

- serialization-safe
- persistence-safe
- restoration-safe
- future-sync-safe

---

5. DATA LAYER BLUEPRINT

Repository & Serialization Architecture

This section defines repository ownership and serialization boundaries.

---

Supabase Integration Layer

Required Technology

supabase-kt

using:

- PostgREST
- Auth
- structured serialization

---

Repository Responsibilities

QuizRepository

Responsible for:

- quiz insertion
- bridge insertion
- payload orchestration
- DTO conversion
- remote mutation handling

---

Serialization Requirements

Technology

kotlinx.serialization

must be used for:

- runtime state persistence
- JSONB payload generation
- nested runtime structures

---

Serializable Requirements

All deeply nested models must include:

@Serializable

including:

- ActiveQuestion
- RuntimeState
- Progress structures
- Timer structures
- Nested answer maps

---

Session Access

Session retrieval must occur:
BEFORE payload assembly.

Example conceptual flow:

supabase.auth.currentSessionOrNull()

---

6. NETWORK FLOW BLUEPRINT

Coroutine + HTTP Sequencing Architecture

This section defines network orchestration rules.

---

Chunk Fetch Pipeline

Input

List<QuestionId>

Transformation

Split into:

List<List<QuestionId>>

using chunk size:

200

---

Parallelization Rules

Chunk fetches MAY execute concurrently.

Example structure:

async { fetchChunk() }

with:

awaitAll()

---

Sequential Insert Requirements

These operations MUST remain sequential:

Insert Master Quiz
→ wait for success
→ Insert Bridge Rows

Under no condition should:

- bridge insertion race ahead
- parallel insert mutation occur

---

Timeout Rules

Mutation operations require timeout boundaries.

Conceptual pattern:

withTimeout(15000)

---

Error Propagation Rules

Network-layer failures must:

- bubble upward
- remain typed
- preserve failure category

Examples:

- timeout
- auth failure
- insertion failure
- malformed payload
- partial response

---

7. DATABASE INTERACTION BLUEPRINT

Supabase Mutation Architecture

This section defines DB-level orchestration behavior.

---

saved_quizzes Mutation

Purpose

Master persisted quiz record.

Payload Includes

- UUID
- user_id
- runtime JSONB state
- filters
- timestamps
- mode
- metadata

---

bridge_saved_quiz_questions Mutation

Purpose

Relational mapping between:

- quiz
- question
- sort order

---

Bulk Insert Behavior

Bridge rows should insert as:

bulk insert array

not:

N individual insert requests

---

Consistency Model

Current orchestration assumes:

non-transactional sequential mutation

This means:

- insert #1 may succeed
- insert #2 may fail

resulting in:

orphaned master quiz records

The Android implementation must understand this risk and preserve deterministic handling behavior.

---

8. PERFORMANCE-SENSITIVE AREAS BLUEPRINT

CPU, Memory & Threading Risk Zones

This section defines runtime-sensitive execution boundaries.

---

CPU-Heavy Operations

Deduplication

Includes:

- regex replacement
- normalization
- set comparison
- composite-key generation

Must execute on:

Dispatchers.Default

NEVER:

Dispatchers.Main

---

Serialization Cost

Large runtime state serialization:

- blocks CPU
- allocates large object graphs
- generates temporary heap churn

Must never execute on Main thread.

---

Memory Pressure Areas

Heavy Allocation Zones

- flattened batch arrays
- deduplication Sets
- normalization strings
- serialized JSON payloads

Large quiz generation may create:

- temporary memory spikes
- GC pauses
- frame drops on low-memory devices

---

9. FAILURE HANDLING BLUEPRINT

Runtime Failure & Fault-Tolerance Behavior

This section defines expected mutation failure behavior.

---

Empty Fetch Handling

If fetched question count is smaller than requested:

- calculate missing count
- warn user asynchronously
- continue execution

Do NOT hard fail mutation.

---

Session Expiration Handling

If auth session unavailable:

- halt orchestration
- dismiss loading overlay
- emit auth error event
- prevent DB mutation

---

Insert Failure Handling

Master Insert Failure

Behavior:

- log failure
- emit UI error
- remove loading overlay
- halt execution

---

Bridge Insert Failure

Behavior:

- log failure
- emit UI error
- remove loading overlay
- halt execution

Potential side effect:

orphaned master records

---

10. ARCHITECTURAL RISK BLUEPRINT

Android Runtime & Lifecycle Risk Zones

This section defines major architectural risk areas.

---

Lifecycle Mismatch Risks

Unlike web:
Android Activities may:

- recreate
- rotate
- suspend
- background
- die under memory pressure

The architecture must preserve:

- mutation continuity
- state restoration
- overlay continuity
- navigation correctness

---

Process Death Risk

If process dies during:

active mutation window

possible outcomes:

- unfinished coroutine
- incomplete mutation
- orphaned records
- stale local state

---

Navigation Race Conditions

After successful creation:

- destination screen must refresh correctly
- stale cache must not dominate UI
- hydration timing must remain deterministic

---

11. IMPLEMENTATION EXPANSION ROADMAP

Controlled Architecture Expansion Sequence

The following phases represent controlled implementation expansion modules.

These are NOT “rewrite phases.”

These are:

- stabilization phases
- subsystem hardening phases
- controlled architecture expansion phases

---

Phase 1

Detailed Android Data Models & JSON Serialization Schema

---

Phase 2

QuizConfigViewModel Mutation Orchestrator

---

Phase 3

Parallel Chunk Fetching & Coroutine Deduplication Engine

---

Phase 4

Supabase Sequential Insert Pipeline & Timeout Infrastructure

---

Phase 5

Compose Blocking Overlay + Navigation Teardown Handling

---

Phase 6

Configuration Change + Process Death Recovery

---

Phase 7

Target Screen Hydration + Cache Invalidation Alignment

---

FINAL IMPLEMENTATION PRINCIPLES

The Android implementation must evolve through:

- controlled subsystem stabilization
- dependency-aware expansion
- lifecycle-safe orchestration
- deterministic state management
- async-safe execution
- serialization-safe persistence
- production-grade runtime resilience

Existing stable systems should be preserved.

Unsafe systems should be hardened.

Missing systems should be implemented incrementally.

The goal is:
controlled production evolution —
NOT uncontrolled feature growth.
