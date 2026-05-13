# Git Workflow Rules

## 1. Branch Naming

```
{type}/{task_id}_{short_title}
```

Examples:
- `feat/ORDER-42_order-cancel-flow`
- `fix/INV-7_oversell-race-condition`
- `refactor/AUTH-12_refresh-session-cleanup`
- `chore/DB-3_add-product-fulltext-index`

Types: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`

## 2. Commit Message Format

```
{type}({module}): {short title}

{description of the change — what and why}

{Fixes/Closes #issue_number}
```

Examples:
```
feat(order): add order cancellation with inventory release

Customers can now cancel PENDING orders. Cancellation releases reserved
inventory and records a CANCELLED stock movement for audit.

Closes #42
```

```
fix(inventory): prevent oversell under concurrent checkout

Add optimistic locking on inventory rows to prevent two concurrent
checkouts from both seeing enough stock and both succeeding.

Fixes #7
```

## 3. Rules

- Run `.\mvnw.cmd spotless:check` before committing
- Run `.\mvnw.cmd -DskipTests compile` to catch compile errors
- Never commit: `.env`, `application-prod.properties`, credentials, private keys
- Never force-push to `main` or `master`
- Never rewrite history on shared branches without explicit instruction
- Do not commit local config or IDE files that should be gitignored
- Keep commits focused — one logical change per commit

## 4. Before Creating a PR

1. Run `.\mvnw.cmd verify` — must pass
2. Run `.\mvnw.cmd spotless:check` — must pass
3. Check `git diff` — no unintended files
4. Run `/pre-merge` or `/review` to get a code review report
5. Update API docs if endpoint behavior changed

## 5. Main Branch

Development branch: `dev`
Main branch (production): `main`

PRs should target `dev` during development phase.
