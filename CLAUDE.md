# CLAUDE.md

## Project Overview

Fashion e-commerce backend (Modular Monolith) serving Admin Web, Customer Web, and Mobile App.

- Base package: `com.locnguyen.ecommerce`
- Base API prefix: `/api/v1`
- Admin APIs: `/api/v1/admin/**`

## Tech Stack

- Java 17 + Spring Boot 3.x
- Spring Security (JWT, stateless), Spring Data JPA, Spring Validation, Spring Cache
- Flyway migrations, MapStruct, Lombok, OpenAPI/Swagger
- MariaDB (primary), Redis (session/blacklist/OTP/cache), Docker/Docker Compose

## Architecture

Layered inside each domain module: `Controller → Service → Repository`

```
com.locnguyen.ecommerce
├── common/         # config, constants, exception, response, security, validation, auditing, utils, filter, specification
├── domains/        # address, admin, auditlog, auth, brand, cart, category, customer, idempotency,
│                   # inventory, invoice, notification, order, payment, product, productvariant,
│                   # promotion, review, shipment, user, verification
└── infrastructure/ # cache, email, external, messaging, storage
```

Each domain contains: `controller`, `service/impl`, `repository`, `entity`, `dto`, `mapper`, `specification`, `enums`

## Mandatory Rules

Read these before making any code change:

- @.claude/rules/architecture.md
- @.claude/rules/api-conventions.md
- @.claude/rules/code-style.md
- @.claude/rules/database.md
- @.claude/rules/security.md
- @.claude/rules/testing.md
- @.claude/rules/documentation.md
- @.claude/rules/git-workflow.md

## Hard Rules (non-negotiable)

- Never edit existing Flyway migrations that may already be applied.
- Never expose JPA entities directly in API responses — use DTOs.
- Never log passwords, raw JWT, refresh token, OTP, cookies, or payment secrets.
- Never commit `.env`, secrets, credentials, or private keys.
- Never disable security checks to fix something temporarily.
- Never return stacktrace in production API responses.
- Never create fake success responses or leave TODOs for required production behavior.
- Always validate authorization (not just authentication) at service or endpoint boundary.
- Always keep transaction boundaries in the service layer.
- Always use `ApiResponse<T>` / `PagedResponse<T>` wrappers — never return raw objects.

## Build Commands

```bash
# Compile (skip tests)
.\mvnw.cmd -DskipTests compile

# Run all tests
.\mvnw.cmd test

# Verify (compile + test + package)
.\mvnw.cmd verify

# Lint check
.\mvnw.cmd spotless:check

# Lint apply
.\mvnw.cmd spotless:apply

# Run local
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

## Domain Docs

- `docs/api-common.md` — response format, error codes, auth model, idempotency
- `docs/api-conventions.md` — endpoint naming, pagination, filtering, sorting
- `docs/security.md` — auth implementation, JWT, OTP, CSRF, known limitations
- `docs/database-guidelines.md` — schema rules, naming, migration, indexing
- `docs/admin-api-contract.md` — admin endpoint contracts
- `docs/customer-api-contract.md` — customer endpoint contracts
- `docs/domain-overview.md` — domain model and relationships
- `docs/order-lifecycle.md` — order state machine
- `docs/inventory-lifecycle.md` — inventory management model

## Available Skills / Commands

| Command | Purpose |
|---------|---------|
| `/implement-feature` | Full feature implementation workflow |
| `/fix-bug` | Bug diagnosis and fix workflow |
| `/review` | Code review: backend + security + DB + API docs |
| `/docs-sync` | Sync API contract docs with current source |
| `/pre-merge` | Pre-merge quality check |
