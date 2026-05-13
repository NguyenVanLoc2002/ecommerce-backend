# Architecture Rules

## 1. Layered Architecture

Every domain follows a strict layered structure:

```
Controller → Service → Repository
```

**Controller** — thin layer only:
- Receive and validate request DTO (`@Valid`)
- Call service method
- Return `ApiResponse<T>` or `ApiResponse<PagedResponse<T>>`
- Apply authorization annotations (`@PreAuthorize`)
- No business logic

**Service** — owns all business logic:
- Business validation (variant exists, inventory enough, voucher valid, state transition valid)
- Transaction boundary (`@Transactional`)
- Orchestrate across repositories
- Idempotency enforcement
- No direct HTTP concerns

**Repository** — data access only:
- Spring Data JPA queries
- `@Query` JPQL or native queries when needed
- `JpaSpecificationExecutor<T>` for dynamic filtering
- No business logic

**Mapper** — conversion only:
- MapStruct-based
- Entity → ResponseDTO
- RequestDTO → Entity
- No business logic, no DB calls

**Specification** — dynamic query logic:
- Implements `Specification<T>` for JPA modules
- Called by service, not controller

## 2. Domain Module Structure

Each domain under `com.locnguyen.ecommerce.domains.{domain}` has:

```
{domain}/
├── controller/       # REST controllers
├── service/          # Service interfaces
│   └── impl/         # Service implementations
├── repository/       # Spring Data repositories
├── entity/           # JPA entities
├── dto/              # Request DTOs + Response DTOs (separate classes)
├── mapper/           # MapStruct mappers
├── specification/    # JPA Specification classes (if filtering needed)
└── enums/            # Domain-specific enums
```

Common shared code lives in `com.locnguyen.ecommerce.common`:
- `config/` — Spring configs (Security, Web, Redis, Jackson, etc.)
- `constants/` — `AppConstants` with shared values
- `exception/` — `AppException`, `ErrorCode`, `GlobalExceptionHandler`
- `response/` — `ApiResponse<T>`, `PagedResponse<T>`, `ErrorResponse`
- `security/` — JWT filter, token provider, token blacklist
- `validation/` — custom validators (e.g. `@PhoneNumber`)
- `auditing/` — `BaseEntity`, `SoftDeleteEntity`, audit field wiring
- `filter/` — `RequestLoggingFilter`, `CsrfDoubleSubmitFilter`
- `specification/` — `SpecificationUtils` or shared spec helpers

Infrastructure in `com.locnguyen.ecommerce.infrastructure`:
- `cache/` — Redis cache helpers
- `email/` — `EmailSender` interface + implementations
- `external/` — third-party integrations
- `messaging/` — async messaging if used
- `storage/` — MinIO/S3 storage abstraction

## 3. Cross-Module Rules

- Modules communicate through service interfaces, never directly via repository.
- Do not import one domain's repository inside another domain's service.
- Shared entities (e.g. `User`, `Customer`) are referenced by ID, not loaded eagerly across domains unless required.
- Hide soft-deleted resources as if they don't exist (return 404, not a deleted record).

## 4. What Not To Do

- No business logic in Controller, Entity, or Repository
- No direct entity return from Controller (always use DTO)
- No `@Transactional` on Controller layer
- No cross-module Specification sharing unless in `common/specification/`
- No circular dependency between domain services
