---
name: pre-merge-check
description: Final quality gate before merging a branch. Reviews architecture compliance, security, database impact, API docs impact, missing tests, and build status. Use before creating a PR or merging a branch.
---

# Pre-Merge Check

## Purpose

Catch issues before they reach the main branch. Consolidate findings from all reviewers into a single report.

## Steps

### 1. Get the diff
```bash
git diff --name-only
git diff --stat
```

Categorize changed files:
- Java backend files
- Migration files
- Doc files
- Config files
- Test files

### 2. Run backend review
Use `backend-reviewer` agent on changed Java files.

Check:
- Architecture compliance (layering, DTO/entity separation)
- Transaction boundaries
- Pagination and filter patterns
- Exception handling
- Logging (no sensitive data)

### 3. Run security review
Use `security-reviewer` agent on changed files.

Check:
- Sensitive data in logs
- Authorization gaps
- JWT/session/cookie handling
- Input validation
- Secrets in code

### 4. Run database review
Use `database-reviewer` agent if migration, entity, repository, or specification files changed.

Check:
- Migration file correctness
- N+1 query risk
- Soft-delete filter
- Index coverage
- Optimistic locking

### 5. Check API docs impact
If controller, DTO, or ErrorCode files changed:
- List endpoints whose contract changed
- Confirm matching doc sections are up to date (or flag as needing update)

### 6. Check test coverage
- Are new service methods tested?
- Are error paths tested?
- Is there a regression test for any bug fix?

### 7. Run build
```bash
.\mvnw.cmd -DskipTests compile
```

Run tests if practical:
```bash
.\mvnw.cmd test
```

Or run only affected test classes:
```bash
.\mvnw.cmd -Dtest="{AffectedTest1},{AffectedTest2}" test
```

Run lint:
```bash
.\mvnw.cmd spotless:check
```

## Output Format

```
## Pre-Merge Check Report

### Build Status
- Compile: PASS / FAIL
- Tests: PASS / FAIL / NOT RUN
- Lint: PASS / FAIL

### Critical — Block merge
- file:line — issue — fix

### Warning — Should fix
- file:line — issue — recommended fix

### Suggestion — Nice to have
- file:line — improvement

### API Docs Impact
- Endpoint changed, doc needs update: {endpoint} → {doc file §section}
- Endpoint changed, doc already accurate: {endpoint}

### Missing Tests
- {test scenario that should be added}

### Summary
Overall: READY TO MERGE | NEEDS FIXES | NEEDS REVIEW
```

## Constraints

- Do not modify source code.
- Do not push or create PRs.
- Flag pre-existing known issues (from `docs/security.md` known limitations) separately from new issues.
