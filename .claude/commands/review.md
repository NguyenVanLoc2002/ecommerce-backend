---
description: Run a full code review on current changes. Covers backend architecture, security, database impact, and API docs impact. Outputs a consolidated report grouped by severity.
---

Run a complete review of the current changes using specialized reviewers.

## Steps

1. Run `git diff --name-only` to get changed files.

2. Run **backend-reviewer** on all changed Java files.
   Focus: architecture, DTO/entity separation, transaction, validation, exception handling, logging, pagination.

3. Run **security-reviewer** on changed files.
   Focus: sensitive logging, authorization, JWT/session, input validation, payment callback, secrets.

4. If migration, entity, repository, or specification files changed — run **database-reviewer**.
   Focus: migration correctness, N+1, soft-delete, indexes, transaction safety.

5. If controller, DTO, or ErrorCode files changed — check API docs impact.
   Note which endpoints changed and whether `docs/admin-api-contract.md`, `docs/customer-api-contract.md`, or `docs/api-common.md` need to be updated.

## Output

Produce a single consolidated report:

```
## Code Review Report

### Critical — Block merge
- file:line — issue — fix

### Warning — Should fix
- file:line — issue — recommended action

### Suggestion — Nice to have
- file:line — improvement

### API Docs Impact
- List endpoints whose contract changed and which doc file/section needs updating

### Missing Tests
- List test scenarios that should be added

### Summary
Overall assessment in 2–3 sentences.
```

## Constraints

- Do not modify any code or doc files.
- Only report on issues present in the diff — do not re-report pre-existing known issues.
- Be specific: include file:line for every finding.
