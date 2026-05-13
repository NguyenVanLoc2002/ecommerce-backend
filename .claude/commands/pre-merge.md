---
description: Run a pre-merge quality check before creating a PR. Covers code review, security, database, API docs, test coverage, build, and lint. Outputs a final checklist with overall merge readiness.
---

Run the full pre-merge quality check using the pre-merge-check skill.

## Steps

1. Run `git diff --name-only` and `git diff --stat` to understand scope.

2. Run the **pre-merge-check** skill:
   - Backend architecture review
   - Security review
   - Database review (if DB files changed)
   - API docs impact check
   - Test coverage check
   - Build: `.\mvnw.cmd -DskipTests compile`
   - Lint: `.\mvnw.cmd spotless:check`
   - Tests (if practical): `.\mvnw.cmd test`

## Output

Produce the pre-merge report:

```
## Pre-Merge Check Report

### Build Status
- Compile: PASS / FAIL
- Tests: PASS / FAIL / NOT RUN
- Lint: PASS / FAIL

### Critical — Block merge
...

### Warning — Should fix
...

### Suggestion
...

### API Docs Impact
...

### Missing Tests
...

### Final Verdict
READY TO MERGE | NEEDS FIXES | NEEDS HUMAN REVIEW
```

## Constraints

- Do not modify code.
- Do not push or create PRs.
- Do not auto-fix issues — report only.
