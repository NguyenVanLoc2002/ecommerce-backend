---
name: update-api-docs
description: Synchronize API contract documentation with current source code after controllers, DTOs, error codes, auth requirements, or pagination behavior change. Use when backend endpoint behavior has changed and docs need to reflect it.
---

# Update API Docs

## Purpose

Keep `docs/admin-api-contract.md`, `docs/customer-api-contract.md`, and `docs/api-common.md` in sync with actual source code.

## Steps

### 1. Identify what changed
Run `git diff --name-only` and look for changes in:
- Controllers (`*Controller.java`)
- Request DTOs (`*Request.java`)
- Response DTOs (`*Response.java`)
- Filter DTOs (`*Filter.java`)
- `ErrorCode.java`
- `SecurityConfig.java`
- `GlobalExceptionHandler.java`

### 2. Read source for changed endpoints
For each changed controller:
- Note: HTTP method, path, auth requirement
- Note: request body fields (from DTO), validation constraints
- Note: response fields (from response DTO)
- Note: possible error codes (from service calls + ErrorCode references)
- Note: pagination/filter params (from Filter DTO + `@PageableDefault`)

### 3. Read current doc section
Open the relevant doc section and compare against what you found in source.

**Admin endpoints** → `docs/admin-api-contract.md`
**Customer/public endpoints** → `docs/customer-api-contract.md`
**Shared contract** (response format, error codes, auth model) → `docs/api-common.md`

### 4. Update only mismatched sections
- Update only what is different between source and doc
- Do not rewrite accurate sections
- Do not invent behavior not present in source
- If a TODO exists in source, document it as a known limitation with the source reference

### 5. Rules for editing docs
- Preserve existing structure and formatting
- Use consistent table format for endpoints
- Add `[DEPRECATED]` tag if old behavior is being phased out
- If a new error code is added to `ErrorCode.java`, add it to `docs/api-common.md §7`
- If auth requirements changed, update both the endpoint doc and `docs/api-common.md §2`

### 6. Output

```
### Updated
- docs/xxx.md §{section}: {what changed}

### Already accurate (no change needed)
- docs/xxx.md §{section}

### Needs human review
- docs/xxx.md §{section}: {why it's unclear}
```

## Constraints

- Only edit doc files, not source code.
- Only document what the code actually does, not what it should do.
- If behavior is ambiguous, mark it as "TODO: verify" rather than guessing.
