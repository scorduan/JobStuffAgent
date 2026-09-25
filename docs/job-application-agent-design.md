# Job Application Agent: Domain and Orchestration Design

**Status:** Living design document  
**Last updated:** 2026-09-25

## Purpose and scope

This document defines the initial design for a conversational job-application agent. It is the canonical reference for the domain model and the boundary between deterministic application behavior and LLM-assisted reasoning.

The first goal is a bounded system that can understand a user request, identify relevant job-application records, propose a small set of actions or clarifying questions, validate those proposals, and make deterministic changes only after the application accepts them.

This is deliberately not a general autonomous job-search platform. The initial scope excludes automated applications, autonomous external communication, broad web crawling, and unrestricted model tool access.

## Design principles

- **Job-application records are authoritative.** Their lifecycle, validation, and history are application-owned data.
- **The model proposes; deterministic code decides and executes.** A model never directly changes an application record.
- **State is separated by responsibility.** Domain state, conversation history, agent-run state, model context, and tool-execution history are not interchangeable.
- **Rules live near the state they protect.** A job application owns legal lifecycle transitions; an orchestration service coordinates a workflow across multiple objects.
- **Start with explicit types and simple behavior.** Prefer small records and classes over speculative inheritance hierarchies or generic frameworks.
- **Preserve an audit trail.** State changes and executed tools should be explainable after the fact.
- **Secure each layer deliberately.** Authentication and coarse endpoint authorization happen at the HTTP boundary; business authorization is also enforced at application-service boundaries.

## Conceptual model

```text
ApplicantProfile ── owns ──> JobApplication (many)
       │                         │
       │                         └──> Interview (many)
       │                         └──> ApplicationTransition (many)
       │                         └──> Notes (many)
       │
       └── participates in ──> Conversation (many)
                                      │
                                      └──> AgentSession (many)
                                               │
                                               └──> AgentTurn / ToolExecution (many)

HTTP request → Conversation + AgentSession → classification → data lookup
             → model proposal → validation → deterministic execution → response
```

The diagram describes ownership and relationships, not a required database schema or Java package structure.

## Job-application domain

### JobApplication

`JobApplication` is a stateful domain class, not merely a transport object. It should encapsulate its lifecycle status and reject illegal transitions. It may expose query-oriented accessors and narrowly named transition methods such as `markApplied(...)`, `beginInterviewing(...)`, `recordOffer(...)`, `accept(...)`, `markStale(...)`, and `reject(...)`.

Initial information to retain:

- identity: application ID and creation timestamp;
- opportunity: company, role/title, original job-description text or reference, source, and application URL when available;
- optional structured job-description facts: compensation range/currency, location, remote/hybrid/on-site arrangement, and employment type;
- lifecycle: current status, dates, and an append-oriented transition history;
- people: HR/recruiter contact and hiring-manager contact;
- interviews: scheduled/completed interview history and notes;
- general notes;
- audit metadata: last update time and the actor/source of a change when available.

Do not make an LLM-generated summary the authoritative copy of these facts. A summary may be stored as derived context, but the structured record remains the source of truth.

### Application lifecycle

The intended primary path is:

```text
RECOMMENDED → APPLIED → INTERVIEWING → OFFERED → ACCEPTED
```

The non-response path begins after an application is submitted:

```text
APPLIED → STALE → PRESUMED_DEAD
```

`DECLINED` records that the applicant declined an offer. `REJECTED` records that the employer ended the candidacy, which can still happen after an offer if the employer selects another candidate or withdraws it.

```text
OFFERED → ACCEPTED | DECLINED | REJECTED
```

`REJECTED` is available from every pre-acceptance post-application state:

```text
APPLIED, INTERVIEWING, OFFERED, STALE, PRESUMED_DEAD → REJECTED
```

A late employer response can revive a non-response path:

```text
STALE, PRESUMED_DEAD → INTERVIEWING
```

The initial implementation should represent the states with an `ApplicationStatus` enum and enforce transitions inside `JobApplication`. Every accepted transition creates an `ApplicationTransition` record containing at least the prior state, new state, effective date, recorded-at time, source/actor, and optional note.

### Lifecycle decisions resolved

- `STALE` and `PRESUMED_DEAD` can transition back to `INTERVIEWING` when a late employer response arrives. The transition history records that revival explicitly.
- `DECLINED` is distinct from `REJECTED`. The former is an applicant decision after an offer; the latter is an employer outcome and remains possible from `OFFERED`.
- `RECOMMENDED` is initially user-curated through the agent. Model-generated recommendations are deferred to a later workflow and tool set.
- Transition history is the initial source for lifecycle dates. Add dedicated date projections only when a real query or UI requirement justifies them.

### Interviews and contacts

`Interview` should be a separate value-oriented type, associated with a `JobApplication`, containing the interview type/round, planned and completed times when known, participants, outcome, and notes. It should not independently control the application lifecycle; the application coordinates that lifecycle.

Contacts can begin as a small `Contact` value type with name, role, email, phone, and notes. Avoid a global contact aggregate until sharing or independent lifecycle requirements emerge.

### Source information

Each user-curated `RECOMMENDED` application should retain a source URL when one exists, so the user can return to the original listing or other source material. This is distinct from an application URL or employer career-site URL, which may be recorded later when the application is submitted.

## Applicant profile

The applicant profile is user-owned context for job-search decisions and content generation. It is not application state and should not be copied blindly into every model prompt.

Initial categories:

- basic professional information (the user’s chosen identity and contact details);
- work history;
- education and certifications;
- skills and accomplishments;
- job-search preferences (role, location, compensation, remote preference, industries, constraints);
- reusable application materials and preferences;
- optional personal/contextual notes, including the user’s stated state of mind.

The last category is especially sensitive. Store it only with an explicit user purpose, make it easy to correct or delete, and exclude it from model context by default. The model receives only the minimum profile data needed for the current task.

The profile can initially be an aggregate made from small immutable value types or records. It does not need a universal, schema-less JSON blob just because some fields may evolve. Use an explicit extension mechanism only after a concrete need appears.

## Conversations and agent sessions

### Conversation

A `Conversation` is the user-visible thread. It retains the messages or structured interaction history needed to continue the user experience. It may contain multiple `AgentSession` records because a single conversation can produce several distinct attempts to fulfill a request.

Conversation history is not automatically valid model context. The orchestrator selects and compacts only relevant material for each model call.

### AgentSession

An `AgentSession` represents one bounded execution attempt, started from a user message in a conversation. It stores:

- session ID, conversation ID, initiating message/prompt reference, start/end timestamps, and terminal outcome;
- workflow/FSM state and iteration or time budget;
- known facts, missing facts, and references to selected application records;
- model calls, model proposals, tool proposals, tool executions, validation outcomes, and errors;
- a user-facing summary and any pending questions;
- model/provider metadata sufficient for debugging and evaluation, while avoiding sensitive prompt logging by default.

Proposed session states:

```text
RECEIVED
  → CLASSIFYING
CLASSIFYING
  → LOOKING_UP_DATA | PLANNING | COMPLETED_NEEDS_INPUT
LOOKING_UP_DATA
  → PLANNING | COMPLETED_NEEDS_INPUT
PLANNING
  → VALIDATING | COMPLETED_NEEDS_INPUT
VALIDATING
  → EXECUTING | HUMAN_REVIEW | FAILED
EXECUTING
  → COMPLETED | COMPLETED_NEEDS_INPUT | HUMAN_REVIEW | FAILED
```

`COMPLETED_NEEDS_INPUT` is a terminal response outcome, not a parked execution state. If classification, lookup, or planning identifies missing or ambiguous information, the session ends with a structured clarification response containing its precise question(s). The next user message begins a new session associated with the same conversation and can use prior conversation context as appropriate. This keeps `AgentSession` an execution attempt rather than a parked interaction.

The initial HTTP workflow is a single bounded request/response execution, not a separately cancellable asynchronous job. Therefore it has no `CANCELLED` state. Long-running or background agent runs may add cancellation later if the product actually introduces that capability.

This is a starting model. The exact states should be simplified if they do not support a real control decision.

### Agent workflow

1. Receive a user prompt and associate it with a conversation.
2. Make a constrained **classification** call. It identifies the likely intent, structured lookup criteria, and information required to continue. It may return a clarification response immediately. Classification does not select records and does not carry a natural-language query for deterministic lookup.
3. Deterministically apply the structured criteria to authoritative application/profile data identified by classification. The lookup result contains selected record references, structured retrieval errors, and a user-facing summary. Lookup does not formulate questions. For example, no match and ambiguous match are retrieval errors.
4. Make an optional constrained **planning** call using the goal, selected authoritative facts, allowed actions, applicable policy/context, and any retrieval errors. It returns a structured plan, bounded action proposals, and/or further questions. Planning decides how retrieval errors affect the response. In phase four, a deterministic planner stub recognizes an explicit status-transition request; it exercises the same proposal contract intended for a future model planner.
5. If planning needs additional human information, return those questions and end the session.
6. Validate each proposed action against schemas, permissions, lifecycle rules, selected records, and session budgets.
7. Execute permitted CRUD actions deterministically and record their results.
8. Return a summary, including any required next questions.

The model may request an action; it never receives a generic write capability. For example, `transitionApplicationStatus`, `createApplication`, `updateApplicationDetails`, and `addInterviewNote` would each have explicit schemas and validation.

### Multiple requested activities

A single user message may request several activities, such as recording an application, updating another application, and drafting a cover letter. Classification and planning must identify dependencies for each activity independently. If any activity needs clarification, the response can use `COMPLETED_NEEDS_INPUT` and identify the incomplete activity precisely.

Whether the executor may perform the fully specified, independent actions from a mixed request while asking for information about the remaining actions is an explicit policy decision. The initial implementation should choose and test one rule before allowing multi-action writes; it must never execute an action whose required identity, facts, or lifecycle preconditions are missing.

Phase four chooses the conservative initial policy: a validated mutation plan contains at most one proposal. Multi-action mutation plans are rejected until an execution boundary can provide an explicit atomicity or partial-execution policy.

## Context, plans, and execution records

The model-facing context is a derived, disposable representation. It should contain relevant facts and references, not the canonical database objects or unlimited conversation history.

A proposed plan is also not an executed plan. Persist a distinction among:

- **classification:** the interpreted intent and structured criteria for the next deterministic lookup;
- **lookup result:** the authoritative records selected by those criteria, or structured retrieval errors such as no match or ambiguous match;
- **model proposal:** the model’s structured requested next actions/questions;
- **validated plan:** actions the deterministic workflow has accepted as legal and within scope;
- **tool execution:** an attempt, its normalized result, timestamps, and outcome;
- **domain change:** the actual authoritative job-application change and its transition/audit record.

This separation lets the application explain why a change occurred and safely reject surprising proposals.

## Initial Java shape

The following is a direction, not a commitment to implement all types immediately:

```text
domain/
  JobApplication
  ApplicationStatus
  ApplicationTransition
  Interview
  Contact
  ApplicantProfile

application/
  JobApplicationService
  AgentWorkflowService
  AgentSession
  AgentSessionStatus
  AgentProposal
  AgentProposalValidator
  AgentValidatedPlan
  ApplicationToolExecutor
  ToolExecution

adapter/
  web/WorkflowAgentController
  model/ModelClient
  persistence/...          (defer until persistence is introduced)
```

Use request/response DTOs at the web boundary. Do not expose domain entities as a default API contract. Use records for immutable messages and value types; use regular classes for aggregates with identity and protected state transitions.

## Phased implementation

1. Implement `ApplicationStatus`, `JobApplication`, and unit tests for legal and illegal lifecycle transitions. Include a table-driven test of every supported transition command from every reachable state, not only representative happy paths. No LLM, persistence, or conversation handling is required.
2. Add an in-memory application repository and a deterministic `JobApplicationService` for basic CRUD and lookup. The server allocates UUIDs; creation requests never supply authoritative IDs. Wire it to the REST adapter with request/response DTOs so the controller delegates to the service rather than holding business logic.
3. Add a minimal `Conversation` and `AgentSession` with a deterministic end-to-end FSM stub. The normal read-only path traverses classification, lookup, planning, validation, and execution; ambiguity exits early with clarification questions. Wire the workflow endpoint to `AgentWorkflowService` at this point. Both local roles may run this read-only workflow; future mutation tools must continue to enforce the manager role through `JobApplicationService`.
4. Add structured proposal validation and deterministic tool execution. The initial mutation tool is `transitionApplicationStatus`; proposals must target selected records, satisfy the domain lifecycle rules, and run through the manager-authorized application service. A deterministic planner stub exercises this path for explicit user status-transition requests. Reject multi-action mutation plans until their execution policy is resolved.
5. Integrate an LLM for one semantic decision boundary only, then add persistence and execution-history records as the workflow requires them.

### Framework boundary

Spring MVC is an adapter, not the home of business rules. The tie-in begins in phase 2:

```text
HTTP JSON → request DTO → controller → application service → domain object/repository
                                             ↓
                                      response DTO → HTTP JSON
```

Phase 1 domain tests should construct domain objects directly, without Spring. In phase 2, controller tests verify the HTTP contract and application-service tests verify use-case behavior. The controller translates HTTP concerns (routing, JSON, validation, status codes) into an application-service call; `JobApplication` continues to enforce its own lifecycle rules. When the agent workflow arrives in phase 3, `WorkflowAgentController` similarly delegates to `AgentWorkflowService` rather than coordinating model calls itself.

### Phase 2 security baseline

The initial API uses a stateless HTTP Basic authentication baseline for local development. Usernames and passwords are supplied only through `APP_SECURITY_READ_ONLY_USERNAME`, `APP_SECURITY_READ_ONLY_PASSWORD`, `APP_SECURITY_MANAGER_USERNAME`, and `APP_SECURITY_MANAGER_PASSWORD`; no reusable credential is committed. The hard-coded `APPLICATIONS_READ_ONLY` role can read applications. The `APPLICATIONS_MANAGER` role can perform full application CRUD. Health checks remain public.

The endpoint rules provide coarse HTTP protection. Service methods also require the corresponding role through method security, so future callers such as an agent tool or scheduled task do not bypass business authorization merely by avoiding a controller. OAuth/OIDC is deliberately deferred: an Okta or Cognito JWT resource-server configuration should replace the local user source later while retaining the role names and service-level rules.

## Change protocol

When the model, workflow, or domain evolves:

1. Update this document when a behavior, state, responsibility, or decision changes.
2. Add or revise unit tests for deterministic domain transitions and validators.
3. Keep unknowns in the open-decisions sections until they are deliberately resolved.
4. Avoid expanding scope merely because the data model could support it.
