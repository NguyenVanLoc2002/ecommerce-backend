# Database Rules

Full guidelines: `docs/database-guidelines.md`

---

## 1. Migration Rules (Flyway)

- **Never edit an existing migration file** that has already been applied (even in dev).
- All schema changes must be a new `V{n}__description.sql` file.
- Name format: `V{n}__{snake_case_description}.sql` (two underscores)
- Always test migration on a clean schema before committing.
- Migration files live in `src/main/resources/db/migration/`

Latest migration: check `src/main/resources/db/migration/` for highest `V{n}`.

## 2. Schema Rules

- Table names: `snake_case`, plural (`users`, `product_variants`, `order_items`)
- Column names: `snake_case` (`created_at`, `is_deleted`, `product_id`)
- Money columns: `DECIMAL(18,2)` — never `FLOAT` or `DOUBLE`
- Use foreign keys for core relational data
- No `ddl-auto=create` or `ddl-auto=update` in production

## 3. Primary Key Strategy

- Internal PK: `BIGINT AUTO_INCREMENT`
- Public-facing business codes: separate column, e.g. `order_code VARCHAR(20)` (`ORD202604060001`)
- UUIDs exposed in API (mapped from PK via hash or separate UUID column)

## 4. Soft Delete

Modules using `SoftDeleteEntity` base class:
- Admin/catalog data: categories, brands, products, variants, promotions, vouchers
- `is_deleted = 1` means soft-deleted
- Default queries must exclude soft-deleted rows (`WHERE is_deleted = 0`)
- Soft-deleted resource lookup by ID should return 404, not the deleted record
- Admin list filters use: `isDeleted=false` (active only), `isDeleted=true` (deleted only), `includeDeleted=true` (both)
- Do NOT overuse soft delete on large log/event tables

## 5. Indexing

Add indexes for:
- Columns used in `WHERE` filters (`status`, `is_deleted`, `customer_id`)
- Columns used in `JOIN` (`product_id`, `order_id`, `variant_id`)
- Columns used in `ORDER BY` (`created_at`, `updated_at`)
- Unique business keys (`order_code`, `sku`, `slug`, `email`, `phone`)

## 6. Avoid N+1 Queries

- Use `@EntityGraph` or `JOIN FETCH` for associations loaded together
- Use projections or custom `@Query` for read-heavy list queries
- Use `JpaSpecificationExecutor` + `Specification<T>` for filterable list queries
- Check `EXPLAIN` for new complex queries

## 7. Concurrency and Transactions

- Use `@Version` (optimistic locking) on `inventory` and other high-write aggregates
- Validate and update inventory inside the same transaction
- Avoid long-running transactions that hold locks
- Payment callbacks must be idempotent — check `provider_txn_id` uniqueness before mutating

## 8. Data Integrity

- Prefer explicit constraints in migration (FK, UNIQUE, NOT NULL) over application-only enforcement
- Money arithmetic: use `BigDecimal` in Java, `DECIMAL(18,2)` in DB
- Snapshot pattern: `OrderItem` stores product name, variant name, SKU, unit price at order time — not just FK
- Order shipping address: stored as snapshot columns, not only a FK to `addresses`

## 9. When Changing Entities

Before modifying a JPA entity:
1. Does it require a new Flyway migration?
2. Does it affect any existing query / Specification?
3. Does it affect a response DTO or API contract?
4. Does it affect any test that uses this entity?
