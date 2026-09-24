# Project Guidance

## Canonical domain design

[`docs/job-application-agent-design.md`](docs/job-application-agent-design.md) is the canonical living design document for the job-application domain, applicant profile, conversation model, and agent orchestration boundaries.

Before changing domain models, workflow behavior, agent state, tools, or persistence, read that document and update it in the same change when the design changes. Preserve its explicit open questions rather than silently turning an assumption into a rule.

## Design principles

- The deterministic application owns authoritative data, validation, state transitions, and execution.
- An LLM proposes interpretations, questions, and bounded actions; it does not directly mutate application records.
- Keep job-application domain state, agent execution state, model context, and persisted conversation history distinct.
- Prefer a small, explainable implementation over speculative abstractions. Add persistence, retrieval, and external model integration only when their corresponding workflow behavior exists.
