---
name: implement-feature
description: Full workflow for implementing a new feature in this backend. Covers reading requirements, inspecting existing code, making an implementation plan, implementing changes in correct layer order, adding tests, updating docs, and running verification. Use at the start of any new feature task.
---

# Implement Feature

## Purpose

Implement a new backend feature end-to-end, following project architecture and conventions.

## Steps

### 1. Understand requirements
- Clarify what the feature must do and what it must not do.
- Identify the affected domain(s): which modules, entities, endpoints are involved.
- Check `docs/domain-overview.md` and relevant lifecycle docs.

### 2. Inspect existing code
- Read existing service, repository, and entity files in the affected domain(s).
- Read related controller files to understand current endpoint patterns.
- Check if there is an existing similar feature to follow as a pattern.

### 3. Read applicable rules
- `@.claude/rules/architecture.md`
- `@.claude/rules/api-conventions.md`
- `@.claude/rules/database.md` (if schema changes needed)
- `@.claude/rules/security.md` (if auth/authorization involved)

### 4. Make implementation plan
Before writing code, produce:
- List of files to create or modify
- New endpoint paths and HTTP methods
- New entity fields or new migration if schema changes needed
- New error codes needed (check `ErrorCode` enum first)
- Authorization requirements
- Whether idempotency is needed

Confirm plan is reasonable before implementing.

### 5. Implement in layer order
1. Migration file (if schema change) — name `V{n+1}__{description}.sql`
2. Entity (add fields or new entity)
3. Repository (add query methods or Specification)
4. DTO: request, response, filter
5. Mapper
6. Service interface + implementation
7. Controller

### 6. Validation checklist before finishing
- [ ] Request DTO has Bean Validation annotations
- [ ] Service has business validation (not just happy path)
- [ ] Authorization check in controller or service
- [ ] Transaction boundary correct in service
- [ ] No N+1 query risk
- [ ] Soft-delete filter applied if entity uses soft delete
- [ ] Pagination and filtering for list endpoints
- [ ] `ApiResponse<T>` / `PagedResponse<T>` used in controller
- [ ] `AppException(ErrorCode.xxx)` used for errors

### 7. Compile check
```bash
.\mvnw.cmd -DskipTests compile
```
Fix any compile errors before proceeding.

### 8. Add tests
- Unit test for service business logic
- Test all error paths, not just happy path
- Integration test if complex query or auth flow

Run tests:
```bash
.\mvnw.cmd -Dtest={NewServiceTest} test
```

### 9. Update docs
- If endpoint added/changed: update `docs/admin-api-contract.md` or `docs/customer-api-contract.md`
- If error codes added: update `docs/api-common.md §7`
- If auth behavior changed: update `docs/security.md`

### 10. Final report
Output:
- Files created / modified
- New endpoints (path, method, auth requirement)
- Migration version if schema changed
- Tests added
- Docs updated
- Known risks or follow-up items
