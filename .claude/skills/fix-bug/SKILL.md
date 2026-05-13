---
name: fix-bug
description: Bug diagnosis and minimal fix workflow. Reproduces the issue, identifies root cause, implements the smallest correct fix, adds a regression test, and verifies behavior. Use for any bug fix task.
---

# Fix Bug

## Purpose

Diagnose a bug, apply a minimal correct fix, and prevent regression.

## Steps

### 1. Understand the bug
- What is the incorrect behavior?
- What is the expected behavior?
- Is there an error message, stack trace, or error code?
- Is it reproducible? Under what conditions?

### 2. Locate the root cause
- Read the controller, service, and repository files in the affected domain.
- Trace the code path from the endpoint down to the failing operation.
- Check: validation gaps, missing edge case, incorrect state check, missing error handling, N+1, stale cache, wrong transaction boundary, race condition.

### 3. Read applicable rules
- `@.claude/rules/architecture.md` — confirm fix is in the correct layer
- `@.claude/rules/security.md` — if auth or permission related
- `@.claude/rules/database.md` — if query or migration related

### 4. Propose the minimal fix
- Fix only what is broken — do not refactor unrelated code in the same change
- If root cause is in service: fix in service
- If root cause is a missing DB index or wrong query: fix query + add migration if needed
- If root cause is a race condition: consider optimistic locking or transaction isolation

State the proposed fix and root cause before implementing.

### 5. Implement the fix
- Smallest change that correctly fixes the issue
- Do not change architecture or rename things unless directly related to the bug

### 6. Compile check
```bash
.\mvnw.cmd -DskipTests compile
```

### 7. Add regression test
Write a test that:
- Reproduces the original bug condition
- Verifies the bug is now fixed
- Test name: `methodName_whenBugCondition_expectedBehavior`

Run the test:
```bash
.\mvnw.cmd -Dtest={TestClass}#{testMethod} test
```

### 8. Run related tests
```bash
.\mvnw.cmd -Dtest="{AffectedServiceTest}" test
```

Confirm no regression in existing tests.

### 9. Final report
Output:
- Root cause identified
- Fix applied (file:line)
- Regression test added
- Other tests still passing
- Any related follow-up issues to track
