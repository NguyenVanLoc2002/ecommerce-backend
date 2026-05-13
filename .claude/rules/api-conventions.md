# API Conventions Rules

Full convention reference: `docs/api-conventions.md`
Contract source of truth: `docs/api-common.md`

---

## 1. Critical Rules (apply to every endpoint)

### Response wrapper — mandatory

All endpoints must return `ApiResponse<T>` or `ApiResponse<PagedResponse<T>>`. Never return a raw object.

```java
// Success
return ApiResponse.success(data);
return ApiResponse.success("Custom message", data);
return ApiResponse.created(data);
return ApiResponse.noContent();

// Paged
return ApiResponse.success(service.getItems(filter, pageable));
// where service returns PagedResponse<T>
```

### Error codes — use stable enum values from ErrorCode

Use domain-specific error codes, not generic strings. See `docs/api-common.md §7` for the full catalogue.

Examples: `PRODUCT_NOT_FOUND`, `INVENTORY_NOT_ENOUGH`, `VOUCHER_EXPIRED`, `ORDER_STATUS_INVALID`

### Pagination — always use @PageableDefault

```java
@GetMapping
public ApiResponse<PagedResponse<XxxResponse>> getXxxs(
        XxxFilter filter,
        @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE, sort = "createdAt",
                         direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.success(xxxService.getXxxs(filter, pageable));
}
```

Never use manual `@RequestParam int page / int size / String sort / String direction`.

### List endpoints — require filter DTO

Every `GET` returning `PagedResponse<T>` must accept a Filter DTO bound from query params.

### Service method naming for lists

Use `get{Domain}s(filter, pageable)` — not `listXxx()`, `getAll()`, `findAll()`.

---

## 2. URL Conventions

- Base: `/api/v1`
- Admin: `/api/v1/admin/**` (requires STAFF+)
- Payment callbacks: `POST /api/v1/payments/callback`
- Use plural nouns: `/products`, `/orders`, `/customers`
- Sub-resources: `GET /api/v1/products/{id}/variants`
- State transitions: `POST /api/v1/orders/{id}/cancel` (not `PATCH /orders/{id}` for critical state changes)

---

## 3. Idempotency

Two endpoints require `Idempotency-Key` header:
- `POST /api/v1/orders`
- `POST /api/v1/payments/order/{orderId}/initiate`

Max length: 100 characters. Error codes: `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_CONFLICT`, etc.

---

## 4. Auth Model

- Access token: `Authorization: Bearer <accessToken>` in response body
- Refresh token: HttpOnly cookie at path `/api/v1/auth`
- Role hierarchy: `SUPER_ADMIN > ADMIN > STAFF > CUSTOMER`
- Use `@PreAuthorize` for fine-grained control beyond URL-level security

---

## 5. Request Validation

DTO validation annotations: `@NotBlank`, `@NotNull`, `@Size`, `@Email`, `@Positive`, `@Min`, `@Max`, `@Pattern`, `@PhoneNumber` (custom).

Business validation goes in service layer, not DTO.

---

## 6. Enum Handling

- Query params and JSON body: case-insensitive (configured in `WebMvcConfig` and `application.properties`)
- Invalid enum values in query/path: `400 Bad Request`
- Response: enums returned as strings

---

## 7. Datetime

- Backend timezone: UTC
- Format: ISO-8601 (`2026-04-06T10:00:00Z`)
- Date-only fields: `yyyy-MM-dd`
- Money fields: `BigDecimal` (JSON number)
- IDs: UUID strings

---

## 8. Contract Stability

Once frontend/mobile consumes an endpoint:
- Do not rename fields
- Do not change field semantics
- Do not remove fields without a deprecation plan
- Breaking changes require versioning (`/api/v2`)
