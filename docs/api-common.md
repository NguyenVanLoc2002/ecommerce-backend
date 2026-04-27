# API Common — Shared Conventions

> **Generated from source code on 2026-04-19. Do not edit manually — regenerate when code changes.**

---

## 1. Base URL

```
/api/v1
```

All endpoints are prefixed with `/api/v1`. Breaking changes require a new version (`/api/v2`).

**Local dev:**
```
http://localhost:8080/api/v1
```

**Swagger UI:**
```
http://localhost:8080/swagger-ui.html
```

---

## 2. Authentication

### Mechanism
Bearer JWT — stateless. All protected endpoints require:

```
Authorization: Bearer <access_token>
```

### Token Types

| Token | Lifetime (default) | Purpose |
|---|---|---|
| Access token | 1 hour (3 600 000 ms) | Authenticate every API request |
| Refresh token | 7 days (604 800 000 ms) | Obtain a new token pair |

- Access tokens carry `sub` (email) + `roles` claims.
- Refresh tokens carry only `sub`. They cannot be used as access tokens (rejected at filter level).
- After logout, the access token is added to a **Redis blacklist** with TTL = remaining lifetime. Blacklisted tokens are rejected by the filter.

### Token Lifecycle Flow

```
POST /auth/register  ──► { accessToken, refreshToken }
POST /auth/login     ──► { accessToken, refreshToken }

GET /protected       ──► Authorization: Bearer <accessToken>

POST /auth/refresh-token  body: { refreshToken } ──► { accessToken, refreshToken }

POST /auth/logout    ──► Authorization: Bearer <accessToken>  (blacklists access token)
```

---

## 3. Response Envelope

### 3.1 Success Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": { },
  "timestamp": "2026-04-06T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `success` | boolean | Always `true` for success responses |
| `code` | string | Always `"SUCCESS"` |
| `message` | string | Human-readable description |
| `data` | object \| array \| null | Response payload |
| `timestamp` | string (ISO-8601) | Server timestamp in UTC |

### 3.2 Created Response (HTTP 201)

Same structure as success, `message` = `"Created successfully"`.

### 3.3 Empty Response (no data)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": null,
  "timestamp": "2026-04-06T10:00:00Z"
}
```

### 3.4 Error Response

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": [
    { "field": "email", "message": "Email is invalid" }
  ],
  "timestamp": "2026-04-06T10:00:00Z",
  "path": "/api/v1/auth/register"
}
```

| Field | Type | Present when |
|---|---|---|
| `success` | boolean | Always `false` |
| `code` | string | Always present — see error codes below |
| `message` | string | Human-readable error |
| `errors` | array | Only for `VALIDATION_ERROR` (field-level errors) |
| `errors[].field` | string | Field name that failed validation |
| `errors[].message` | string | Validation message |
| `timestamp` | string (ISO-8601) | Always |
| `path` | string | Request path that caused the error |

---

## 4. HTTP Status Codes

| Code | Meaning |
|---|---|
| `200 OK` | Success (GET, PATCH, POST non-create, DELETE) |
| `201 Created` | Resource created |
| `400 Bad Request` | Malformed request or missing required param |
| `401 Unauthorized` | No/invalid/blacklisted access token |
| `403 Forbidden` | Authenticated but insufficient role |
| `404 Not Found` | Resource does not exist |
| `409 Conflict` | Duplicate email, phone, SKU, etc. |
| `422 Unprocessable Entity` | DTO validation failed OR business rule violation |
| `500 Internal Server Error` | Unexpected server-side error |

---

## 5. Error Codes Reference

### General

| Code | HTTP | Description |
|---|---|---|
| `SUCCESS` | 200 | Success |
| `BAD_REQUEST` | 400 | Malformed or incomplete request |
| `UNAUTHORIZED` | 401 | Not authenticated |
| `FORBIDDEN` | 403 | Insufficient role/permission |
| `NOT_FOUND` | 404 | Resource not found |
| `VALIDATION_ERROR` | 422 | DTO validation failed |
| `CONFLICT` | 409 | Data conflict |
| `INTERNAL_SERVER_ERROR` | 500 | Unhandled server error |

### Auth

| Code | HTTP | Description |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | Wrong email or password |
| `TOKEN_EXPIRED` | 401 | Access token has expired |
| `TOKEN_INVALID` | 401 | Token is malformed or wrong type |
| `TOKEN_BLACKLISTED` | 401 | Token was invalidated via logout |
| `REFRESH_TOKEN_INVALID` | 401 | Refresh token is invalid or expired |
| `ACCOUNT_DISABLED` | 403 | Account is disabled/locked |
| `ACCOUNT_ALREADY_EXISTS` | 409 | Email already registered |

### User / Customer

| Code | HTTP | Description |
|---|---|---|
| `USER_NOT_FOUND` | 404 | User not found |
| `CUSTOMER_NOT_FOUND` | 404 | Customer profile not found |
| `EMAIL_ALREADY_EXISTS` | 409 | Email already registered |
| `PHONE_ALREADY_EXISTS` | 409 | Phone number already registered |

### Address

| Code | HTTP | Description |
|---|---|---|
| `ADDRESS_NOT_FOUND` | 404 | Address not found or does not belong to user |

### Catalog

| Code | HTTP | Description |
|---|---|---|
| `CATEGORY_NOT_FOUND` | 404 | Category not found |
| `BRAND_NOT_FOUND` | 404 | Brand not found |
| `SLUG_ALREADY_EXISTS` | 409 | Slug already in use |

### Product / Variant

| Code | HTTP | Description |
|---|---|---|
| `PRODUCT_NOT_FOUND` | 404 | Product not found |
| `PRODUCT_INACTIVE` | 422 | Product is not published |
| `PRODUCT_VARIANT_NOT_FOUND` | 404 | Variant not found |
| `PRODUCT_VARIANT_INACTIVE` | 422 | Variant is inactive |
| `SKU_ALREADY_EXISTS` | 409 | SKU already in use |

### Inventory

| Code | HTTP | Description |
|---|---|---|
| `INVENTORY_NOT_FOUND` | 404 | Inventory record not found |
| `INVENTORY_NOT_ENOUGH` | 422 | Not enough stock |
| `VARIANT_OUT_OF_STOCK` | 422 | Variant is out of stock |
| `STOCK_RESERVATION_FAILED` | 422 | Stock reservation failed |
| `WAREHOUSE_NOT_FOUND` | 404 | Warehouse not found |

### Cart

| Code | HTTP | Description |
|---|---|---|
| `CART_NOT_FOUND` | 404 | Cart not found |
| `CART_ITEM_NOT_FOUND` | 404 | Cart item not found |
| `CART_ITEM_QUANTITY_INVALID` | 422 | Quantity must be > 0 |

### Order

| Code | HTTP | Description |
|---|---|---|
| `ORDER_NOT_FOUND` | 404 | Order not found |
| `ORDER_STATUS_INVALID` | 422 | Invalid status transition |
| `ORDER_CANNOT_CANCEL` | 422 | Order cannot be cancelled at this status |
| `ORDER_CANNOT_COMPLETE` | 422 | Order cannot be completed at this status |
| `ORDER_EMPTY` | 422 | Cart is empty |

### Payment

| Code | HTTP | Description |
|---|---|---|
| `PAYMENT_NOT_FOUND` | 404 | Payment not found |
| `PAYMENT_FAILED` | 422 | Payment processing failed |
| `PAYMENT_ALREADY_PROCESSED` | 409 | Payment already processed |
| `PAYMENT_CALLBACK_INVALID` | 400 | Invalid gateway callback |

### Promotion / Voucher

| Code | HTTP | Description |
|---|---|---|
| `VOUCHER_NOT_FOUND` | 404 | Voucher not found |
| `VOUCHER_INVALID` | 422 | Voucher is invalid |
| `VOUCHER_EXPIRED` | 422 | Voucher has expired |
| `VOUCHER_USAGE_LIMIT_EXCEEDED` | 422 | Global usage limit reached |
| `VOUCHER_NOT_APPLICABLE` | 422 | Voucher not applicable to this order |
| `VOUCHER_MIN_ORDER_NOT_MET` | 422 | Order amount below minimum |
| `VOUCHER_CODE_ALREADY_EXISTS` | 409 | Voucher code already in use |
| `VOUCHER_USER_LIMIT_EXCEEDED` | 422 | Per-user limit reached |
| `PROMOTION_NOT_FOUND` | 404 | Promotion not found |
| `PROMOTION_RULE_NOT_FOUND` | 404 | Promotion rule not found |

### Shipment / Invoice

| Code | HTTP | Description |
|---|---|---|
| `SHIPMENT_NOT_FOUND` | 404 | Shipment not found |
| `SHIPMENT_ALREADY_EXISTS` | 409 | Shipment already exists for this order |
| `SHIPMENT_STATUS_INVALID` | 422 | Invalid shipment status transition |
| `INVOICE_NOT_FOUND` | 404 | Invoice not found |
| `INVOICE_ALREADY_EXISTS` | 409 | Invoice already exists for this order |
| `INVOICE_STATUS_INVALID` | 422 | Invalid invoice status transition |

### Review / Notification

| Code | HTTP | Description |
|---|---|---|
| `REVIEW_NOT_FOUND` | 404 | Review not found |
| `REVIEW_NOT_ELIGIBLE` | 422 | Review only allowed for completed order items |
| `REVIEW_ALREADY_EXISTS` | 409 | Already reviewed this product |
| `REVIEW_ALREADY_MODERATED` | 409 | Review already moderated |
| `NOTIFICATION_NOT_FOUND` | 404 | Notification not found |

---

## 6. Pagination

### Request Parameters

| Param | Type | Default | Max | Description |
|---|---|---|---|---|
| `page` | integer | `0` | — | Zero-based page index |
| `size` | integer | `20` | `100` | Items per page |
| `sort` | string | `createdAt,desc` | — | `field,direction` |

Example:
```
GET /api/v1/products?page=0&size=20&sort=createdAt,desc
```

Multiple sorts:
```
GET /api/v1/products?sort=price,asc&sort=createdAt,desc
```

### Paginated Response Structure

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalItems": 125,
    "totalPages": 7,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-04-06T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `items` | array | Current page items |
| `page` | integer | Current page (0-based) |
| `size` | integer | Requested page size |
| `totalItems` | long | Total matching records |
| `totalPages` | integer | Total pages |
| `hasNext` | boolean | More pages available |
| `hasPrevious` | boolean | Previous pages available |

---

## 7. Date / Time Format

- All timestamps use **ISO-8601 UTC**: `2026-04-06T10:00:00Z`
- Date-only fields (birthDate, startDate, endDate): `2026-04-06`
- **Do not** send or parse `06/04/2026` or `04-06-2026` formats.

---

## 8. Enum Dictionaries

### UserStatus
| Value | Description |
|---|---|
| `ACTIVE` | Account is active |
| `INACTIVE` | Account is deactivated |
| `BANNED` | Account is banned |

### RoleName
| Value | Description |
|---|---|
| `SUPER_ADMIN` | Full system access |
| `ADMIN` | Admin access (inherits STAFF + CUSTOMER) |
| `STAFF` | Staff access (inherits CUSTOMER) |
| `CUSTOMER` | Regular customer |

**Role hierarchy (Spring Security):**
```
SUPER_ADMIN > ADMIN > STAFF > CUSTOMER
```

### Gender
| Value | Description |
|---|---|
| `MALE` | Male |
| `FEMALE` | Female |
| `OTHER` | Other |

### AddressType
| Value | Description |
|---|---|
| `SHIPPING` | Delivery address |
| `BILLING` | Billing address |

### ProductStatus
| Value | Description |
|---|---|
| `DRAFT` | Not visible to public |
| `PUBLISHED` | Visible and purchasable |
| `ARCHIVED` | No longer sold, hidden from storefront |

### ProductVariantStatus
| Value | Description |
|---|---|
| `ACTIVE` | Available for purchase |
| `INACTIVE` | Unavailable |

### CategoryStatus / BrandStatus
| Value | Description |
|---|---|
| `ACTIVE` | Visible |
| `INACTIVE` | Hidden |

### MediaType
| Value | Description |
|---|---|
| `IMAGE` | Product image |
| `VIDEO` | Product video |

### AttributeType
| Value | Description |
|---|---|
| `COLOR` | Color attribute |
| `SIZE` | Size attribute |
| `MATERIAL` | Material attribute |

### OrderStatus (state machine)
| Value | Transitions to | Description |
|---|---|---|
| `PENDING` | `AWAITING_PAYMENT`, `CANCELLED` | Just created |
| `AWAITING_PAYMENT` | `CONFIRMED`, `CANCELLED` | Waiting for payment |
| `CONFIRMED` | `PROCESSING`, `CANCELLED` | Payment confirmed |
| `PROCESSING` | `SHIPPED` | Being picked/packed |
| `SHIPPED` | `DELIVERED` | Handed to carrier |
| `DELIVERED` | `COMPLETED` | Delivered to customer |
| `COMPLETED` | `REFUNDED` | Order fulfilled |
| `CANCELLED` | _(terminal)_ | Cancelled |
| `REFUNDED` | _(terminal)_ | Refunded |

### PaymentMethod
| Value | Description |
|---|---|
| `COD` | Cash on delivery |
| `ONLINE` | Online gateway payment |

### PaymentStatus (on Order)
| Value | Description |
|---|---|
| `PENDING` | Not yet paid |
| `PAID` | Payment confirmed |
| `FAILED` | Payment failed |
| `REFUNDED` | Refunded |

### PaymentRecordStatus (on Payment entity)
| Value | Description |
|---|---|
| `PENDING` | Created, not initiated |
| `INITIATED` | Gateway request sent |
| `PAID` | Payment received |
| `FAILED` | Payment failed |
| `REFUNDED` | Full refund |
| `PARTIALLY_REFUNDED` | Partial refund |

### ShipmentStatus (state machine)
| Value | Transitions to | Description |
|---|---|---|
| `PENDING` | `IN_TRANSIT`, `FAILED` | Awaiting carrier pickup |
| `IN_TRANSIT` | `OUT_FOR_DELIVERY`, `FAILED` | With carrier |
| `OUT_FOR_DELIVERY` | `DELIVERED`, `FAILED` | Final delivery attempt |
| `DELIVERED` | _(terminal)_ | Delivered |
| `FAILED` | `RETURNED` | Delivery failed |
| `RETURNED` | _(terminal)_ | Returned to sender |

### InvoiceStatus (state machine)
| Value | Transitions to | Description |
|---|---|---|
| `ISSUED` | `PAID`, `VOIDED` | Issued to customer |
| `PAID` | _(terminal)_ | Payment confirmed |
| `VOIDED` | _(terminal)_ | Cancelled |

### StockMovementType
| Value | Description |
|---|---|
| `IMPORT` | Receive goods from supplier |
| `EXPORT` | Write-off / remove stock |
| `ADJUSTMENT` | Manual correction |
| `RETURN` | Customer return |

### WarehouseStatus
| Value | Description |
|---|---|
| `ACTIVE` | Operational |
| `INACTIVE` | Not in use |

### DiscountType
| Value | Description |
|---|---|
| `PERCENTAGE` | Percentage off (e.g., 20 = 20%) |
| `FIXED_AMOUNT` | Fixed amount off (e.g., 50000 VND) |

### PromotionScope
| Value | Description |
|---|---|
| `ALL` | Applies to all products |
| `CATEGORY` | Applies to specific categories |
| `BRAND` | Applies to specific brands |
| `PRODUCT` | Applies to specific products |

### ReviewStatus
| Value | Description |
|---|---|
| `PENDING` | Awaiting moderation |
| `APPROVED` | Visible on product page |
| `REJECTED` | Hidden, customer notified |

### NotificationType
| Value | Description |
|---|---|
| `ORDER_PLACED` | Order created |
| `ORDER_CONFIRMED` | Order confirmed |
| `ORDER_CANCELLED` | Order cancelled |
| `ORDER_SHIPPED` | Order shipped |
| `ORDER_DELIVERED` | Order delivered |
| `ORDER_COMPLETED` | Order completed |
| `REVIEW_SUBMITTED` | Review submitted |
| `REVIEW_APPROVED` | Review approved |
| `REVIEW_REJECTED` | Review rejected |
| `PAYMENT_RECEIVED` | Payment received |
| `PAYMENT_FAILED` | Payment failed |
| `VOUCHER_RECEIVED` | Voucher received |
| `SYSTEM` | System notification |

---

## 9. Validation Rules (Common)

- Email: valid format, max 255 chars
- Password: 8–64 chars, must contain at least 1 uppercase, 1 lowercase, 1 digit
- Phone: Vietnamese phone format (`@PhoneNumber` custom validator)
- Names: max 100 chars
- Text fields: size limits enforced per DTO (see individual endpoint docs)
- Monetary values: `DECIMAL(18,2)` — use `BigDecimal` with 2 decimal places
- Quantity: minimum 1 where applicable

Validation errors return HTTP 422 with `code: "VALIDATION_ERROR"` and a field-level `errors` array.

---

## 10. File Upload Convention

> **NOT IMPLEMENTED YET** — no file upload endpoints exist in current code.

When implemented, will use `multipart/form-data`.

---

## 11. Role / Permission Matrix Summary

| Endpoint Group | PUBLIC | CUSTOMER | STAFF | ADMIN | SUPER_ADMIN |
|---|:---:|:---:|:---:|:---:|:---:|
| auth/register, login, refresh | ✓ | ✓ | ✓ | ✓ | ✓ |
| auth/logout | — | ✓ | ✓ | ✓ | ✓ |
| GET products, categories, brands | ✓ | ✓ | ✓ | ✓ | ✓ |
| GET reviews/product/* | ✓ | ✓ | ✓ | ✓ | ✓ |
| /me, /addresses | — | ✓ | — | — | — |
| /cart, /orders, /payments (customer) | — | ✓ | — | — | — |
| /vouchers/validate | — | ✓ | — | — | — |
| /reviews (create/my) | — | ✓ | — | — | — |
| /notifications | — | ✓ | — | — | — |
| /shipments/order/* (customer) | — | ✓ | — | — | — |
| /invoices/order/* (customer) | — | ✓ | — | — | — |
| admin/* (read) | — | — | ✓ | ✓ | ✓ |
| admin/* (create/update) | — | — | — | ✓ | ✓ |
| admin/* (destructive/cancel) | — | — | — | ✓ | ✓ |
| admin/users (create) | — | — | — | ✓ | ✓ |
| /reviews/pending, moderate | — | — | ✓ | ✓ | ✓ |
| payment/callback | ✓ | ✓ | ✓ | ✓ | ✓ |

---

## 12. Missing / Not Yet Implemented

The following features are referenced in CLAUDE.md or domain design but **do not have API endpoints in the current codebase**:

| Feature | Status |
|---|---|
| Forgot password / Reset password | NOT IMPLEMENTED |
| Change password | NOT IMPLEMENTED |
| Wishlist | NOT IMPLEMENTED |
| Admin customer management (list/get/disable customers) | NOT IMPLEMENTED |
| Product media management (upload/delete images) | NOT IMPLEMENTED |
| Product attribute management (CRUD) | NOT IMPLEMENTED |
| Admin user list / get / disable | NOT IMPLEMENTED (only create exists) |
| Refund API | NOT IMPLEMENTED |
| Dashboard / reports | NOT IMPLEMENTED |
| CMS / banners | NOT IMPLEMENTED |
| Loyalty points | NOT IMPLEMENTED |
| Recommendation engine | NOT IMPLEMENTED |
