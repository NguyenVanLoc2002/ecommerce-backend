---
description: Synchronize API contract documentation with current source code. Compares controllers, DTOs, and error handling with existing docs and updates only mismatched sections. Does not invent behavior.
---

Synchronize API contract docs with the current source code.

## Steps

1. Run `git diff --name-only` to identify changed controllers, DTOs, and error handling files.

2. Use **api-doc-writer** agent to:
   - Read each changed controller and its DTOs
   - Compare against the relevant doc section
   - Update only what has changed

3. Docs to check and update:
   - `docs/admin-api-contract.md` — admin endpoints
   - `docs/customer-api-contract.md` — customer/public endpoints
   - `docs/api-common.md` — if error codes, auth model, or response format changed

## Rules

- Only update sections that are actually out of sync with source code.
- Do not modify sections that are already accurate.
- Do not document behavior that does not exist in source — mark as TODO if needed.
- If a change is a breaking change, note it explicitly.

## Output

```
## Docs Sync Report

### Updated
- docs/{file} §{section}: {what changed}

### Already accurate (no change needed)
- docs/{file} §{section}

### Needs human review
- docs/{file} §{section}: {why it's unclear or ambiguous}
```
