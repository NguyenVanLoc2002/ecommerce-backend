# Codex Working Guide

## 1. Project overview

- Fashion e-commerce backend implemented as a modular monolith.
- Base package: `com.locnguyen.ecommerce`
- Base API prefix: `/api/v1`
- Admin API prefix: `/api/v1/admin/**`
- Treat existing source behavior as the source of truth. Do not change runtime behavior unless the task explicitly requires it.

## 2. Tech stack

- Java `17`
- Spring Boot `3.3.4`
- Maven wrapper: `.\mvnw.cmd`
- Spring Web, Spring Security, Spring Data JPA, Bean Validation, Spring Cache
- MariaDB, Flyway, Redis
- MapStruct, Lombok, springdoc OpenAPI

## 3. Repository structure

- `src/main/java/com/locnguyen/ecommerce/common` -> shared config, security, exceptions, response wrappers, validation, auditing, filters, utils
- `src/main/java/com/locnguyen/ecommerce/domains` -> domain modules
- `src/main/java/com/locnguyen/ecommerce/infrastructure` -> cache, email, payment providers, messaging, storage, external integrations
- `src/main/resources/db/migration` -> Flyway migrations
- `src/test/java` -> unit and integration tests
- `docs/` -> API, security, database, domain, and lifecycle documentation
- `.claude/` and `CLAUDE.md` -> existing Claude-specific guidance; keep intact

## 4. Architecture rules

- Preserve the layered structure: `Controller -> Service -> Repository`.
- Controllers stay thin: validate request DTOs, enforce endpoint auth, delegate, return `ApiResponse<T>` or `ApiResponse<PagedResponse<T>>`.
- Services own business rules, transaction boundaries, authorization/ownership checks, and idempotency.
- Repositories are data-access only.
- Keep DTOs separate from JPA entities. Never expose entities directly in API responses.
- Use service interfaces plus `Impl` implementations.
- Cross-domain access should go through services, not another domain's repository.

## 5. Coding rules

- Follow existing package, naming, and DTO conventions in the current codebase.
- Use constructor injection, existing response wrappers, `AppException`, and `ErrorCode`.
- Keep `@Transactional` on service implementation methods only.
- Put Bean Validation on request DTOs; keep business validation in services.
- Avoid broad exception swallowing, magic constants, and sensitive logging.
- Respect soft-delete behavior where the module already uses `SoftDeleteEntity`.

## 6. API conventions

- Keep `/api/v1` versioning and plural resource naming.
- Admin endpoints stay under `/api/v1/admin/**`.
- For paged list endpoints, use a filter DTO plus `Pageable` with `@PageableDefault`.
- Return `ApiResponse<T>` or `ApiResponse<PagedResponse<T>>`, not raw objects.
- Preserve contract stability: do not rename/remove fields or change semantics without an explicit versioning plan.
- Keep enum handling, UTC date/time formatting, and pagination structure consistent with current controllers and `docs/api-common.md`.
- Required idempotency today: `POST /api/v1/orders` and `POST /api/v1/payments/order/{orderId}/initiate`.

## 7. Security rules

- Auth model: Bearer access token plus HttpOnly refresh-token cookie.
- Do not log passwords, raw JWTs, refresh tokens, OTPs, cookies, payment secrets, or full auth headers.
- Do not weaken Spring Security, JWT validation, cookie settings, CSRF protections, or ownership checks as a shortcut.
- Use `@PreAuthorize` and service-level ownership checks where applicable.
- Public payment callback/webhook routes must remain signature-verified and idempotent.
- Do not add secrets or environment values to docs, prompts, or committed files.

## 8. Database rules

- Never edit an existing Flyway migration. Add a new `V{n}__description.sql` file for schema changes.
- Do not change schema, migrations, indexes, or entity mappings unless the task explicitly requires it.
- Keep transaction-critical modules aware of optimistic locking, idempotency, and concurrency rules.
- Prevent N+1 issues with fetch planning, projections, `@EntityGraph`, or explicit queries when needed.
- Preserve soft-delete filtering semantics.
- Keep order, payment, shipment, and inventory snapshot/integrity rules intact.
- `products.search_text` is internal-only and must never leak through API responses.

## 9. Testing rules

- Add or update tests whenever behavior changes.
- Prioritize service tests for business rules, controller tests for endpoint behavior, and integration tests for auth, idempotency, complex queries, and persistence behavior.
- Cover happy path, validation failure, not found, forbidden/unauthorized, and conflict/business-rule paths.
- Bug fixes should include a regression test when practical.
- Common commands:
  - `.\mvnw.cmd -DskipTests compile`
  - `.\mvnw.cmd test`
  - `.\mvnw.cmd -Dtest=ClassName test`

## 10. Git branch and commit conventions

- Branch pattern: `{type}/{task_id}_{short_title}`
- Common types: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`
- Commit format:

```text
{type}({module}): {short title}

{description of the change}
```

- Keep commits focused. Do not force-push or rewrite shared history without explicit instruction.

## 11. Rules for using existing docs

- Read the code first when documentation and source differ.
- Preferred reference order:
  1. Source code
  2. `docs/api-common.md`
  3. `docs/api-conventions.md`
  4. `docs/security.md`
  5. `docs/admin-api-contract.md` or `docs/customer-api-contract.md`
  6. `docs/database-guidelines.md`, `docs/domain-overview.md`, lifecycle docs
  7. `README.md`
- Keep `AGENTS.md` concise. Link to existing docs instead of copying large blocks.
- If a documented rule looks unclear or stale, preserve behavior conservatively and mark it for review in the final report.

## 12. Rules for avoiding unsafe changes

- Default assumption: source code, migrations, dependency files, runtime config, and environment files are read-only unless the user explicitly asks otherwise.
- Do not modify business logic, API behavior, database schema, dependency versions, or security behavior during documentation/setup-only work.
- Do not delete or rewrite `CLAUDE.md` or `.claude/`.
- Do not reformat unrelated files.
- Before finishing, verify that only intended documentation/setup files changed.

## 13. Final response/reporting format for Codex

- State what changed and what did not.
- Call out tests or verification run, or explicitly say they were not run.
- List any doc conflicts or unclear rules found during the task.
- For documentation-only tasks, explicitly confirm that no source code, business logic, migrations, dependency files, or runtime configuration were changed.

## Key references

- `CLAUDE.md`
- `docs/api-common.md`
- `docs/api-conventions.md`
- `docs/security.md`
- `docs/database-guidelines.md`
- `docs/admin-api-contract.md`
- `docs/customer-api-contract.md`
- `docs/domain-overview.md`
- `docs/order-lifecycle.md`
- `docs/inventory-lifecycle.md`
