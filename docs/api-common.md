# API Common

This file defines the shared API contract used by the current backend source code.

Source of truth:
- `src/main/java/com/locnguyen/ecommerce/common/constants/AppConstants.java`
- `src/main/java/com/locnguyen/ecommerce/common/config/SecurityConfig.java`
- `src/main/java/com/locnguyen/ecommerce/common/config/WebMvcConfig.java`
- `src/main/java/com/locnguyen/ecommerce/common/response/*`
- `src/main/java/com/locnguyen/ecommerce/common/exception/*`
- `src/main/java/com/locnguyen/ecommerce/common/filter/RequestLoggingFilter.java`

---

## 1. Base URL

- Base path for all application APIs: `/api/v1`
- Admin APIs live under: `/api/v1/admin/**`
- Customer/public APIs live under: `/api/v1/**` outside the `/admin` prefix

---

## 2. Authentication and authorization

### 2.1 Header

Use a Bearer access token in the `Authorization` header:

```http
Authorization: Bearer <accessToken>
```

### 2.2 JWT model

- The API is stateless JWT only.
- No session or cookie auth is used by the backend.
- Login/register/refresh responses return:
  - `accessToken`
  - `refreshToken`
  - `tokenType`
  - `expiresIn`

### 2.3 Role hierarchy

The active role hierarchy is:

```text
SUPER_ADMIN > ADMIN > STAFF > CUSTOMER
```

A higher role inherits lower-role permissions in both URL security and `@PreAuthorize`.

### 2.4 Public routes from `SecurityConfig`

These routes are currently unauthenticated at the filter-chain level:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh-token`
- `GET /api/v1/products/**`
- `GET /api/v1/categories/**`
- `GET /api/v1/brands/**`
- `GET /swagger-ui/**`
- `GET /swagger-ui.html`
- `GET /v3/api-docs/**`
- `GET /actuator/health`
- `GET /actuator/info`

### 2.5 Protected route rules

- `/api/v1/admin/**` requires `STAFF` or higher at the URL layer.
- Some admin endpoints are further restricted by `@PreAuthorize`, for example:
  - admin-only mutation routes
  - audit log access
  - user creation
- All other routes require authentication unless explicitly whitelisted above.

### 2.6 Important current-code note

`POST /api/v1/payments/callback` is described in its controller as a gateway callback, but it is **not** whitelisted in `SecurityConfig`. In the current backend source, it therefore requires authentication.

---

## 3. Success response format

All successful controller responses use `ApiResponse<T>`.

### 3.1 Standard shape

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {},
  "timestamp": "2026-04-28T12:00:00Z"
}
```

### 3.2 Fields

- `success`: always `true`
- `code`: always `SUCCESS`
- `message`: factory-dependent success message
- `data`: endpoint payload, list payload, paged payload, or `null`
- `timestamp`: `Instant.now().toString()` in UTC

### 3.3 Success message variants used by code

- `ApiResponse.success(data)` -> `Request processed successfully`
- `ApiResponse.success(message, data)` -> custom message
- `ApiResponse.created(data)` -> `Created successfully`
- `ApiResponse.noContent()` -> `Operation completed successfully`

### 3.4 No-content convention

Most delete/mark-all endpoints still return HTTP `200` with `ApiResponse<Void>` and `data: null`.

Two promotion delete endpoints are exceptions because the controller is annotated with `204 No Content`:

- `DELETE /api/v1/admin/promotions/{id}`
- `DELETE /api/v1/admin/promotions/{id}/rules/{ruleId}`

Clients should treat those responses as empty bodies.

---

## 4. Pagination format

Paged endpoints return `ApiResponse<PagedResponse<T>>`.

### 4.1 Exact payload shape

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalItems": 100,
    "totalPages": 5,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-04-28T12:00:00Z"
}
```

### 4.2 PagedResponse fields

- `items`
- `page`
- `size`
- `totalItems`
- `totalPages`
- `hasNext`
- `hasPrevious`

The backend does **not** return a top-level `total` field.

---

## 5. Error response format

All structured errors use `ErrorResponse`.

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Email is required"
    }
  ],
  "timestamp": "2026-04-28T12:00:00Z",
  "path": "/api/v1/auth/register"
}
```

### 5.1 Fields

- `success`: always `false`
- `code`: stable machine-readable error code
- `message`: user-facing error message
- `errors`: optional field-level validation list
- `timestamp`: UTC ISO-8601 string
- `path`: request URI

### 5.2 Validation errors

Validation failures from `@Valid` and `@Validated` return:

- HTTP status: `422 Unprocessable Entity`
- `code`: `VALIDATION_ERROR`
- `message`: `Validation failed`
- `errors`: array of `{ field, message }`

---

## 6. HTTP status conventions

Current backend mappings:

- `200 OK`
  - successful reads
  - successful updates
  - many successful create actions without `@ResponseStatus(CREATED)`
  - no-content helper responses that still return a body
- `201 Created`
  - only endpoints explicitly annotated with `@ResponseStatus(HttpStatus.CREATED)`
- `204 No Content`
  - only the two admin promotion delete endpoints noted above
- `400 Bad Request`
  - malformed JSON
  - missing required request parameter
  - query/path type mismatch
  - unsupported enum text in query/path binding
  - business rules that throw `AppException(ErrorCode.BAD_REQUEST, ...)`
- `401 Unauthorized`
  - missing/invalid token at filter-chain level
  - auth business errors such as invalid credentials or invalid token
- `403 Forbidden`
  - authenticated but not authorized
- `404 Not Found`
  - missing resources
  - ownership-protected lookups that intentionally mask foreign resources
- `405 Method Not Allowed`
- `409 Conflict`
  - uniqueness and already-processed conflicts
- `415 Unsupported Media Type`
- `422 Unprocessable Entity`
  - bean validation failures
  - business rule violations mapped to 422 error codes
- `500 Internal Server Error`
  - uncaught exceptions

---

## 7. Error code catalogue

The current `ErrorCode` enum defines these domain codes.

### 7.1 General

- `BAD_REQUEST`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `CONFLICT`
- `INTERNAL_SERVER_ERROR`

### 7.2 Auth

- `INVALID_CREDENTIALS`
- `TOKEN_EXPIRED`
- `TOKEN_INVALID`
- `REFRESH_TOKEN_INVALID`
- `TOKEN_BLACKLISTED`
- `ACCOUNT_DISABLED`
- `ACCOUNT_ALREADY_EXISTS`

### 7.3 User and customer

- `USER_NOT_FOUND`
- `CUSTOMER_NOT_FOUND`
- `EMAIL_ALREADY_EXISTS`
- `PHONE_ALREADY_EXISTS`

### 7.4 Address

- `ADDRESS_NOT_FOUND`

### 7.5 Catalog

- `CATEGORY_NOT_FOUND`
- `BRAND_NOT_FOUND`
- `SLUG_ALREADY_EXISTS`
- `PRODUCT_NOT_FOUND`
- `PRODUCT_INACTIVE`
- `PRODUCT_VARIANT_NOT_FOUND`
- `PRODUCT_VARIANT_INACTIVE`
- `SKU_ALREADY_EXISTS`
- `BARCODE_ALREADY_EXISTS`
- `VARIANT_INVALID_PRICE`
- `VARIANT_INVALID_WEIGHT`
- `VARIANT_ATTRIBUTE_INVALID`
- `VARIANT_COMBINATION_DUPLICATE`
- `PRODUCT_ATTRIBUTE_NOT_FOUND`
- `PRODUCT_ATTRIBUTE_VALUE_NOT_FOUND`
- `PRODUCT_ATTRIBUTE_CODE_ALREADY_EXISTS`
- `PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS`
- `PRODUCT_ATTRIBUTE_VALUE_IN_USE`

### 7.6 Inventory

- `INVENTORY_NOT_FOUND`
- `INVENTORY_NOT_ENOUGH`
- `VARIANT_OUT_OF_STOCK`
- `STOCK_RESERVATION_FAILED`
- `WAREHOUSE_NOT_FOUND`

### 7.7 Cart

- `CART_NOT_FOUND`
- `CART_ITEM_NOT_FOUND`
- `CART_ITEM_QUANTITY_INVALID`

### 7.8 Order

- `ORDER_NOT_FOUND`
- `ORDER_STATUS_INVALID`
- `ORDER_CANNOT_CANCEL`
- `ORDER_CANNOT_COMPLETE`
- `ORDER_EMPTY`

### 7.9 Payment

- `PAYMENT_NOT_FOUND`
- `PAYMENT_FAILED`
- `PAYMENT_ALREADY_PROCESSED`
- `PAYMENT_CALLBACK_INVALID`

### 7.10 Promotion and voucher

- `VOUCHER_NOT_FOUND`
- `VOUCHER_INVALID`
- `VOUCHER_EXPIRED`
- `VOUCHER_USAGE_LIMIT_EXCEEDED`
- `VOUCHER_NOT_APPLICABLE`
- `VOUCHER_MIN_ORDER_NOT_MET`
- `VOUCHER_CODE_ALREADY_EXISTS`
- `VOUCHER_USER_LIMIT_EXCEEDED`
- `PROMOTION_NOT_FOUND`
- `PROMOTION_RULE_NOT_FOUND`

### 7.11 Shipment and invoice

- `SHIPMENT_NOT_FOUND`
- `SHIPMENT_ALREADY_EXISTS`
- `SHIPMENT_STATUS_INVALID`
- `INVOICE_NOT_FOUND`
- `INVOICE_ALREADY_EXISTS`
- `INVOICE_STATUS_INVALID`

### 7.12 Review

- `REVIEW_NOT_FOUND`
- `REVIEW_NOT_ELIGIBLE`
- `REVIEW_ALREADY_EXISTS`
- `REVIEW_ALREADY_MODERATED`

### 7.13 Notification

- `NOTIFICATION_NOT_FOUND`

---

## 8. Query parameter conventions

### 8.1 Pagination and sorting

Most pageable endpoints use Spring Data `Pageable` binding:

- `page`
- `size`
- `sort`

Examples:

- `?page=0&size=20`
- `?sort=createdAt,desc`
- `?sort=createdAt,desc&sort=id,asc`

### 8.2 Defaults

- `page` is zero-based
- When an endpoint uses `@PageableDefault`, the default size is usually `20`
- `AppConstants.DEFAULT_PAGE_SIZE = 20`
- Some endpoints also define default sort fields in `@PageableDefault`

### 8.3 Direction parameter

`direction` is **not** a global query parameter. It is explicitly exposed only by:

- `GET /api/v1/admin/reviews`

That endpoint uses:

- `page`
- `size`
- `sort`
- `direction`

All other pageable routes use Spring `sort=field,direction`.

### 8.4 No universal controller clamp

The codebase contains `PaginationUtils` and `MAX_PAGE_SIZE = 100`, but current controllers bind `Pageable` directly. There is no shared controller-layer clamp applied across all endpoints.

---

## 9. Enum handling

Enum values are string-based in both requests and responses.

### 9.1 Query/path binding

`WebMvcConfig` installs a case-insensitive `String -> Enum` converter for:

- `@RequestParam`
- `@ModelAttribute`
- `@PathVariable`

Examples:

- `?status=ACTIVE`
- `?status=active`
- `?status=Active`

All bind to the same enum value.

Unknown enum text in query/path binding results in `400 Bad Request`.

### 9.2 JSON body binding

`spring.jackson.mapper.accept-case-insensitive-enums=true` is enabled.

Examples:

- `"status": "active"`
- `"status": "ACTIVE"`

Both are accepted for JSON enums.

### 9.3 Response format

Enum values are returned as strings. Some DTOs expose enum-typed fields directly; others map enums to string fields in response DTOs.

---

## 10. Naming and data conventions

- All entity IDs exposed by the API are UUIDs.
- Business codes such as `orderCode`, `paymentCode`, `invoiceCode`, and `shipmentCode` are strings.
- `LocalDate` fields are serialized as `yyyy-MM-dd`.
- `LocalDateTime` and `Instant` values are serialized as ISO-8601 date-time strings.
- Money fields are JSON numbers backed by `BigDecimal`.
- Boolean flags use normal JSON booleans.

---

## 11. Validation conventions

Current code uses Jakarta Bean Validation on request DTOs.

Common rules used across the API:

- `@NotBlank`
- `@NotNull`
- `@NotEmpty`
- `@Size`
- `@Email`
- `@Pattern`
- `@Min`
- `@Max`
- `@Positive`
- `@DecimalMin`
- `@Digits`

### 11.1 Phone number validation

Fields annotated with `@PhoneNumber` accept:

- `0xxxxxxxxx`
- `+84xxxxxxxxx`

The current validator regex is:

```text
^(0|\+84)[3-9][0-9]{8}$
```

Blank/null values pass `@PhoneNumber` itself and must be paired with `@NotBlank` when the field is required.

---

## 12. Request tracing

`RequestLoggingFilter` manages `X-Request-ID`.

- If the client sends `X-Request-ID`, the server reuses it.
- Otherwise the server generates a UUID.
- The response echoes `X-Request-ID`.
- The same ID is also used in request logging and audit logging.

---

## 13. Media type

The API expects JSON request bodies for body-based endpoints.

- Use `Content-Type: application/json`
- Unsupported body content types return `415 Unsupported Media Type`

