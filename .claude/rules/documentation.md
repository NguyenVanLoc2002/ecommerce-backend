# Documentation Rules

## 1. When to Update Documentation

| Change | Doc to update |
|--------|--------------|
| Add/remove/rename endpoint | `docs/admin-api-contract.md` or `docs/customer-api-contract.md` |
| Change request/response shape | Relevant contract doc + `docs/api-common.md` if shared |
| Change error codes | `docs/api-common.md §7` |
| Change auth behavior (cookie, token, refresh flow) | `docs/security.md`, `docs/api-common.md §2` |
| Change Flyway schema | `docs/database-guidelines.md` if guideline changes |
| Change order state machine | `docs/order-lifecycle.md` |
| Change inventory model | `docs/inventory-lifecycle.md` |
| Add new domain or major module | `docs/domain-overview.md` |
| Change build/run commands | `CLAUDE.md` and `README.md` |
| Change Claude Code setup rules | `CLAUDE.md` and `.claude/rules/*.md` |

## 2. Rules

- Docs must reflect actual source code — not aspirational behavior
- Never document behavior that is not yet implemented as if it is live
- If behavior is pending/TODO, mark it explicitly as such (with source file reference)
- Do not copy-paste docs — reference the source of truth doc from dependent docs
- Keep `CLAUDE.md` concise — put detailed rule content in `.claude/rules/*.md`

## 3. Source of Truth Hierarchy

For any question about a behavior, check in this order:
1. Source code (controller, service, security config)
2. `docs/api-common.md` (shared contract: response format, error codes, auth, idempotency)
3. `docs/api-conventions.md` (endpoint design rules)
4. `docs/security.md` (auth implementation details)
5. `docs/admin-api-contract.md` / `docs/customer-api-contract.md` (endpoint-level contracts)

## 4. What NOT to Write

- Do not document obvious things derivable from the code (e.g. what a getter does)
- Do not write docs for in-progress work as if it were complete
- Do not duplicate information across files — reference instead
