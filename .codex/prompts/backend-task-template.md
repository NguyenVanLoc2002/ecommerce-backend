# Backend Task Template

## Context to read

- `AGENTS.md`
- `CLAUDE.md`
- Relevant files under `docs/`
- Relevant controller, service, repository, DTO, mapper, and test files

## Task

Describe the backend task here:

`<task description>`

## Implementation requirements

- Follow `Controller -> Service -> Repository`.
- Keep DTO/entity separation.
- Use existing response wrappers, error codes, validation, and auth patterns.
- Preserve API contracts unless the task explicitly requires a change.

## Safety constraints

- Do not change unrelated business logic.
- Do not edit existing Flyway migrations.
- Do not weaken security, ownership checks, or idempotency behavior.
- Do not add secrets or environment values to code or docs.

## Testing expectations

- Add or update the smallest correct test coverage for changed behavior.
- Run `.\mvnw.cmd -DskipTests compile` if code changes are made.
- Run targeted tests first, then broader tests if practical.

## Final output format

- Files changed
- Behavior impact
- Tests run or not run
- Risks, follow-ups, or docs needing review
