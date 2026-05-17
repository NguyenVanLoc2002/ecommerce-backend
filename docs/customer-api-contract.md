# Customer API Contract

This document covers the current public and customer-facing API surface outside `/api/v1/admin/**`.

Source of truth:
- public/customer controllers under `src/main/java/com/locnguyen/ecommerce/domains/*/controller`
- related DTOs, services, and shared response types

Explicitly excluded:
- all `/api/v1/admin/**` routes
- legacy admin-only moderation routes under `/api/v1/reviews/**`

Shared response, error, auth, enum, and pagination rules are defined in [api-common.md](./api-common.md).

---

## 1. Route scope and auth notes

### Included route families

- auth
- profile
- addresses
- product/category/brand browsing
- cart
- orders
- payments
- voucher validation
- shipments
- invoices
- reviews
- notifications

### Important current-code note

`POST /api/v1/payments/callback` is a **public** endpoint called server-to-server by the payment gateway. No `Authorization` header is needed. HMAC signature verification is a pending TODO — see `docs/security.md §10`.

### Legacy review moderation routes intentionally excluded

These non-admin paths exist in code but are admin/staff endpoints, not customer APIs:

- `GET /api/v1/reviews/pending`
- `GET /api/v1/reviews/{id}`
- `PATCH /api/v1/reviews/{id}/moderate`
- `DELETE /api/v1/reviews/{id}`

---

## 2. Auth

### DTOs

- `RegisterRequest`
  - `email`: required, valid email, max 255
  - `password`: required, 8-64 chars, at least one lowercase letter, one uppercase letter, and one digit
  - `firstName`: required, max 100
  - `lastName`: optional, max 100
  - `phoneNumber`: optional, `@PhoneNumber`
- `LoginRequest`
  - `email`: required, valid email
  - `password`: required
- `RefreshTokenRequest`
  - `refreshToken`: optional, deprecated fallback for refresh only
- `AuthResponse`
  - `user`
  - `tokens`
- `UserResponse`
  - `id`, `email`, `firstName`, `lastName`, `phoneNumber`
  - `status`, `roles`, `createdAt`
- `TokenResponse`
  - `accessToken`, `tokenType`, `expiresIn`

### Current auth contract

- Register and login both return `ApiResponse<AuthResponse>`.
- `AuthResponse.data.tokens` contains `accessToken`, `tokenType`, and `expiresIn`.
- Register and login set the refresh token in an HttpOnly cookie.
- Refresh reads the cookie by default and returns only a new access token in the JSON body.
- A JSON-body `refreshToken` fallback is still accepted temporarily for refresh and is deprecated.
- Logout revokes the current refresh session, clears the cookie, and blacklists the presented access token when valid.
- There is no password-change or password-reset endpoint in the current backend source.
- The same login endpoint is used for customer and non-customer accounts; authorization is role-based after login.

### Frontend contract notes

- Frontends must call login, refresh, and logout with `withCredentials: true`.
- Frontends must not store refresh tokens in LocalStorage, sessionStorage, or other JavaScript-accessible storage.
- Frontends may keep a temporary body-based refresh fallback only during migration and should remove it once all environments rely on the cookie flow.

### POST `/api/v1/auth/register`

- Access: public
- Description: create customer account and return auth payload
- Request body:
  - `RegisterRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<AuthResponse>`

### POST `/api/v1/auth/login`

- Access: public
- Description: login by email/password
- Request body:
  - `LoginRequest`
- Response:
  - `ApiResponse<AuthResponse>`
- Current response behavior:
  - returns `data.user`
  - returns `data.tokens.accessToken`
  - returns `data.tokens.tokenType`
  - returns `data.tokens.expiresIn`
- Cookie behavior:
  - sets the refresh-token cookie
  - cookie defaults: `HttpOnly`, `SameSite=Lax`, `Path=/api/v1/auth`
  - `Secure` is configurable and should be `true` in production

### POST `/api/v1/auth/refresh-token`

- Access: public
- Description: exchange refresh token for a new access token and rotated refresh cookie
- Request body:
  - `RefreshTokenRequest`
- Response:
  - `ApiResponse<TokenResponse>`
- Transport behavior:
  - reads refresh token from HttpOnly cookie by default
  - temporarily supports `refreshToken` from request JSON body as a deprecated fallback
  - does not read from `Authorization`
- Rotation behavior:
  - rotates the refresh token cookie on every success
  - returns only `accessToken`, `tokenType`, and `expiresIn`
  - revokes the previously issued refresh session
  - rejects reused/mismatched refresh tokens with `401`

### POST `/api/v1/auth/logout`

- Access: public/idempotent
- Description: revoke current refresh session, clear refresh cookie, and blacklist current access token when supplied
- Response:
  - `ApiResponse<Void>`
- Current behavior:
  - accepts missing or expired access token without failing
  - blacklists the presented access token in Redis until access-token expiry when valid
  - revokes the matching refresh session when the refresh cookie is present
  - clears the refresh-token cookie

### Current auth storage model

- Refresh sessions are stored in Redis with TTL.
- Redis stores a SHA-256 hash of the refresh token, not the raw token.
- Refresh tokens carry session identity (`jti`) plus principal and family claims for server-side validation and revocation.
- `AuthService.revokeAllRefreshSessions(principalType, principalId)` is available for a future password-change integration.

### POST `/api/v1/auth/password/forgot`

- Access: public, no token required.
- Description: request a password-reset OTP. Always returns 200 to avoid leaking which emails are registered.
- Request body: `ForgotPasswordRequest { email }`.
- Response: `ApiResponse<Void>` (`code: SUCCESS`).
- Side effects (when the email belongs to an active account):
  - issues a 6-digit OTP, stored as a SHA-256 hash with a server pepper
  - dispatches the OTP via `EmailSender`
  - increments the per-target rate-limit counters (Redis)
- Errors: `OTP_RATE_LIMITED` (429) when the cooldown / window limit is exceeded.

### POST `/api/v1/auth/password/forgot/verify`

- Access: public.
- Description: verify the OTP and obtain a one-shot reset token.
- Request body: `VerifyOtpRequest { email, otp }`.
- Response: `ApiResponse<ResetTokenResponse { resetToken, expiresAt }>`.
- Errors: `OTP_INVALID`, `OTP_EXPIRED`, `OTP_USED`, `OTP_TOO_MANY_ATTEMPTS`.

### POST `/api/v1/auth/password/reset`

- Access: public.
- Description: consume the reset token, update the password, revoke all refresh sessions.
- Request body: `ResetPasswordRequest { resetToken, newPassword, confirmPassword }`.
- Password policy: min 8 chars, requires uppercase, lowercase, digit; cannot equal the current password.
- Response: `ApiResponse<Void>`.
- Errors: `RESET_TOKEN_INVALID`, `RESET_TOKEN_EXPIRED`, `PASSWORD_MISMATCH`, `PASSWORD_POLICY_VIOLATED`, `PASSWORD_REUSED`, `USER_NOT_FOUND`, `ACCOUNT_DISABLED`.

### POST `/api/v1/account/password/change`

- Access: authenticated (Bearer JWT).
- Description: authenticated change-password. Verifies the current password, applies the policy, and revokes all refresh sessions on success.
- Request body: `ChangePasswordRequest { currentPassword, newPassword, confirmPassword }`.
- Response: `ApiResponse<Void>`.
- Errors: `CURRENT_PASSWORD_INVALID`, `PASSWORD_MISMATCH`, `PASSWORD_POLICY_VIOLATED`, `PASSWORD_REUSED`.

### TODO / Future work

- Promote `app.security.refresh-token-body-fallback-enabled` to `false` in all environments and fully remove the body fallback in a later release.
- Enable `app.security.csrf-double-submit-enabled=true` once the front end echoes `X-XSRF-TOKEN`.
- Replace `LoggingEmailSender` with a real SMTP / SES provider before production rollout.

---

## 3. User profile

### DTOs

- `UserProfileResponse`
  - `id`, `email`, `firstName`, `lastName`, `phoneNumber`
  - `status`, `roles`
  - `customerId`, `gender`, `birthDate`, `avatarUrl`, `loyaltyPoints`
  - `createdAt`
- `UpdateProfileRequest`
  - `firstName`: optional, max 100
  - `lastName`: optional, max 100
  - `phoneNumber`: optional, `@PhoneNumber`
  - `gender`: optional
  - `birthDate`: optional

### GET `/api/v1/me`

- Access: authenticated
- Description: get current user profile
- Response:
  - `ApiResponse<UserProfileResponse>`

### PATCH `/api/v1/me`

- Access: authenticated
- Description: partial update current profile
- Request body:
  - `UpdateProfileRequest`
- Response:
  - `ApiResponse<UserProfileResponse>`

---

## 4. Addresses

### DTOs

- `AddressResponse`
  - `id`, `receiverName`, `phoneNumber`
  - `streetAddress`, `ward`, `district`, `city`, `postalCode`
  - `addressType`, `isDefault`, `label`, `fullAddress`
  - `createdAt`
- `CreateAddressRequest`
  - `receiverName`: required, max 100
  - `phoneNumber`: required, `@PhoneNumber`
  - `streetAddress`: required, max 255
  - `ward`: required, max 100
  - `district`: required, max 100
  - `city`: required, max 100
  - `postalCode`: optional, max 20
  - `addressType`: required
  - `isDefault`: optional
  - `label`: optional, max 50
- `UpdateAddressRequest`
  - partial update version of:
  - `receiverName`, `phoneNumber`, `streetAddress`, `ward`, `district`, `city`, `postalCode`, `addressType`, `isDefault`, `label`

### GET `/api/v1/addresses`

- Access: authenticated customer flow
- Description: list my addresses
- Response:
  - `ApiResponse<List<AddressResponse>>`
- Ordering note:
  - default address first, then newest first

### GET `/api/v1/addresses/{id}`

- Access: authenticated customer flow
- Description: get one owned address
- Response:
  - `ApiResponse<AddressResponse>`

### POST `/api/v1/addresses`

- Access: authenticated customer flow
- Description: create address
- Request body:
  - `CreateAddressRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<AddressResponse>`

### PATCH `/api/v1/addresses/{id}`

- Access: authenticated customer flow
- Description: partial update address
- Request body:
  - `UpdateAddressRequest`
- Response:
  - `ApiResponse<AddressResponse>`

### DELETE `/api/v1/addresses/{id}`

- Access: authenticated customer flow
- Description: soft-delete address
- Response:
  - `ApiResponse<Void>`

---

## 5. Product browsing

### Category DTO

- `CategoryResponse`
  - `id`, `parentId`, `name`, `slug`, `description`, `imageUrl`
  - `status`, `sortOrder`, `createdAt`

### Brand DTO

- `BrandResponse`
  - `id`, `name`, `slug`, `logoUrl`, `description`
  - `sortOrder`, `status`, `createdAt`

### Product DTOs

- `ProductFilter`
  - `keyword`
  - `categoryId`
  - `brandId`
  - `status`
  - `minPrice`
  - `maxPrice`
  - `featured`
- `ProductListItemResponse`
  - `id`, `name`, `slug`, `shortDescription`, `thumbnailUrl`
  - `minPrice`, `maxPrice`
  - `status`, `featured`
  - `brandName`, `categoryNames`
  - `createdAt`
- `ProductDetailResponse`
  - `id`, `name`, `slug`, `shortDescription`, `description`
  - `status`, `featured`
  - `brand`, `categories`, `variants`, `media`
  - `createdAt`, `updatedAt`
- `VariantResponse`
  - `id`, `sku`, `barcode`, `variantName`
  - `basePrice`, `salePrice`, `compareAtPrice`
  - `weightGram`, `status`
  - `attributes`
- `AttributeResponse`
  - `name`, `value`
- `MediaResponse`
  - `id`, `mediaUrl`, `mediaType`, `sortOrder`, `primary`, `variantId`

### GET `/api/v1/categories`

- Access: public
- Description: list active categories
- Response:
  - `ApiResponse<List<CategoryResponse>>`

### GET `/api/v1/categories/{id}`

- Access: public
- Description: get category by ID
- Response:
  - `ApiResponse<CategoryResponse>`

### GET `/api/v1/brands`

- Access: public
- Description: list active brands
- Response:
  - `ApiResponse<List<BrandResponse>>`

### GET `/api/v1/brands/{id}`

- Access: public
- Description: get brand by ID
- Response:
  - `ApiResponse<BrandResponse>`

### GET `/api/v1/products`

- Access: public
- Description: list published products
- Filters:
  - `keyword`, `categoryId`, `brandId`, `minPrice`, `maxPrice`, `featured`
  - `status` exists on the DTO but the service forces `PUBLISHED`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=createdAt,desc`
- Keyword search:
  - When `keyword` is blank the service uses the standard JPA Specification path.
  - When `keyword` has text the service runs a MariaDB FULLTEXT query against
    `MATCH(products.name, products.slug, products.search_text)` in BOOLEAN MODE.
  - Search is **case-insensitive** and **accent-insensitive** (`Áo` matches `ao`,
    `đầm` matches `dam`) — input is normalised through `SearchTextNormalizer`.
  - Results with a keyword are ordered by FULLTEXT relevance first, then by the
    requested `sort` (whitelisted columns: `createdAt`, `updatedAt`, `name`,
    `status`, `featured`).
  - `minPrice` / `maxPrice` must match the **same** variant; soft-deleted
    variants are ignored.
  - Soft-deleted products are always excluded from this public endpoint.
  - Internal column `products.search_text` is **never** returned in the response.
- Response:
  - `ApiResponse<PagedResponse<ProductListItemResponse>>`

### GET `/api/v1/products/{id}`

- Access: public
- Description: get published product detail
- Response:
  - `ApiResponse<ProductDetailResponse>`

---

## 6. Cart

### DTOs

- `AddCartItemRequest`
  - `variantId`: required
  - `quantity`: required, min 1
- `UpdateCartItemRequest`
  - `quantity`: required, min 1
- `CartResponse`
  - `id`, `items`, `totalItems`, `subTotal`, `updatedAt`
- `CartItemResponse`
  - `id`, `variantId`, `variantName`, `sku`
  - `productSlug`, `productName`
  - `unitPrice`, `salePrice`
  - `quantity`, `availableStock`, `lineTotal`
  - `createdAt`

### GET `/api/v1/cart`

- Access: authenticated customer flow
- Description: get or lazily create my active cart
- Response:
  - `ApiResponse<CartResponse>`

### POST `/api/v1/cart/items`

- Access: authenticated customer flow
- Description: add item to cart
- Request body:
  - `AddCartItemRequest`
- Response:
  - `ApiResponse<CartResponse>`

### PATCH `/api/v1/cart/items/{itemId}`

- Access: authenticated customer flow
- Description: update cart item quantity
- Request body:
  - `UpdateCartItemRequest`
- Response:
  - `ApiResponse<CartResponse>`

### DELETE `/api/v1/cart/items/{itemId}`

- Access: authenticated customer flow
- Description: remove one cart item
- Response:
  - `ApiResponse<CartResponse>`

### DELETE `/api/v1/cart`

- Access: authenticated customer flow
- Description: clear active cart
- Response:
  - `ApiResponse<Void>`

---

## 7. Order flow

### Order DTOs

- `CheckoutCarrierOptionResponse`
  - `id`, `code`, `name`, `providerType`
  - `logoUrl`, `description`
- `CreateOrderRequest`
  - `shippingAddressId`: required
  - `carrierId`: optional selected carrier ID for server-side shipping quote and shipment preference
  - `paymentMethod`: optional
  - `customerNote`: optional, max 500
  - `voucherCode`: optional
- `OrderFilter`
  - `status`
- `OrderListItemResponse`
  - `id`, `orderCode`, `status`, `paymentMethod`, `paymentStatus`
  - `totalItems`, `totalAmount`, `createdAt`
- `OrderResponse`
  - `id`, `orderCode`, `customerId`
  - `status`, `paymentMethod`, `paymentStatus`
  - `receiverName`, `receiverPhone`
  - `shippingStreet`, `shippingWard`, `shippingDistrict`, `shippingCity`, `shippingPostalCode`
  - `carrierId`, `carrierCode`, `carrierName`, `carrierProviderType`
  - `subTotal`, `discountAmount`, `shippingFee`, `totalAmount`
  - `voucherCode`, `customerNote`
  - `items`
  - `createdAt`
- `OrderPreviewResponse`
  - `carrierId`, `carrierCode`, `carrierName`, `carrierProviderType`
  - `shippingServiceName`
  - `paymentMethod`
  - `subTotal`, `discountAmount`, `shippingFee`, `totalAmount`
  - `voucherCode`, `customerNote`
- `OrderItemResponse`
  - `id`, `variantId`, `productName`, `variantName`, `sku`
  - `unitPrice`, `salePrice`, `quantity`, `lineTotal`

### GET `/api/v1/carriers/checkout-options`

- Access: authenticated customer flow
- Description: list active carriers currently available for customer checkout
- Response:
  - `ApiResponse<List<CheckoutCarrierOptionResponse>>`
- Current service behavior:
  - returns only carriers with `status = ACTIVE`
  - excludes provider types that do not have a registered provider bean
  - for real providers such as `AHAMOVE`, excludes carriers whose config is missing or disabled
  - response does not include secrets or fee quotes

### POST `/api/v1/orders/preview`

- Access: authenticated customer flow
- Description: preview checkout totals using the current active cart, selected shipping address, and optional selected carrier
- Request body:
  - `CreateOrderRequest`
- Response:
  - `ApiResponse<OrderPreviewResponse>`
- Current service behavior:
  - uses the customer's active cart and validates address ownership
  - defaults `paymentMethod` to `COD` when omitted
  - when `carrierId` is omitted:
    - `shippingFee = 0`
    - `totalAmount = subTotal - discountAmount`
  - when `carrierId` is provided:
    - resolves the carrier through `CarrierCheckoutService`
    - calculates shipping fee server-side via the provider abstraction
    - applies the quote to `shippingFee` and `totalAmount`
    - returns a carrier snapshot plus optional `shippingServiceName`
  - `voucherCode` is stored in the preview response, but discount calculation is currently not applied in service logic

### POST `/api/v1/orders`

- Access: authenticated customer flow
- Description: create order from active cart
- Required header: `Idempotency-Key: <unique-string>` (max 100 chars). Missing or blank key returns `400 IDEMPOTENCY_KEY_REQUIRED`. Same key + same payload returns the original order without re-executing checkout.
- Request body:
  - `CreateOrderRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<OrderResponse>`
- Current service behavior:
  - `paymentMethod` defaults to `COD` when omitted
  - `COD` orders start at `PENDING`
  - `ONLINE` orders start at `AWAITING_PAYMENT`
  - when `carrierId` is provided, the backend recalculates shipping fee server-side during checkout and persists the selected carrier snapshot on the order
  - when `carrierId` is omitted, the order is still created with `shippingFee = 0`
  - stock is reserved during order creation
  - `voucherCode` is stored on the order, but discount calculation is currently not applied in service logic
  - the persisted order snapshot includes `carrierId`, `carrierCode`, `carrierName`, and `carrierProviderType` when a carrier was selected

### GET `/api/v1/orders`

- Access: authenticated customer flow
- Description: list my orders
- Filters:
  - `status`
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
  - repository query orders by `createdAt DESC`
- Response:
  - `ApiResponse<PagedResponse<OrderListItemResponse>>`

### GET `/api/v1/orders/{id}`

- Access: authenticated customer flow
- Description: get one owned order
- Response:
  - `ApiResponse<OrderResponse>`

### POST `/api/v1/orders/my/{id}/cancel`

- Access: authenticated customer flow
- Description: cancel own order
- Response:
  - `ApiResponse<OrderResponse>`
- Current service behavior:
  - customer cancellation is allowed only while the order can transition to `CANCELLED`
  - service explicitly blocks `CONFIRMED`

---

## 8. Payment flow

### DTOs

- `InitPaymentRequest`
  - `provider`: optional
  - `returnUrl`: optional
- `PaymentCaptureRequest`
  - `provider`: required — provider name (e.g., `PAYPAL`), case-insensitive
  - `providerToken`: required — provider-assigned order token from the redirect URL (max 200 chars)
- `PaymentCallbackRequest`
  - `orderCode`: required
  - `status`: required string
  - `providerTxnId`: optional
  - `provider`: optional
  - `payload`: optional
- `PaymentResponse`
  - `id`, `orderId`, `orderCode`, `paymentCode`
  - `method`, `status`, `amount`, `paidAt`, `createdAt`
  - `paymentUrl` — web redirect URL for the payment gateway (null for COD, null after payment settled)
  - `deeplink` — mobile app deeplink (MoMo only; null for providers that don't support it)
  - `qrCodeUrl` — raw QR code data string to encode as a QR image (MoMo only; null for others)
  - `transactions`
- `TransactionResponse`
  - `id`, `transactionCode`, `status`, `amount`
  - `method`, `provider`, `providerTxnId`
  - `referenceType`, `referenceId`, `note`
  - `createdAt`

### GET `/api/v1/payments/order/{orderId}`

- Access: authenticated customer flow
- Description: get payment for an owned order
- Response:
  - `ApiResponse<PaymentResponse>`

### POST `/api/v1/payments/order/{orderId}/initiate`

- Access: authenticated customer flow
- Description: initiate or retry online payment
- Required header: `Idempotency-Key: <unique-string>` (max 100 chars). Missing or blank key returns `400 IDEMPOTENCY_KEY_REQUIRED`. Same key + same payload returns existing payment without re-initiating. After a FAILED payment, the same key retries the payment.
- Request body:
  - optional `InitPaymentRequest`
  - if the body is omitted, the controller creates an empty request object internally
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<PaymentResponse>`
  - `paymentUrl` is populated for all online providers
  - `deeplink` and `qrCodeUrl` are populated when using the MoMo provider
- Current service behavior:
  - order must belong to the current customer
  - order payment method must be `ONLINE`
  - existing `PENDING` or `INITIATED` payment returns existing record
  - existing `FAILED` payment is retried
  - terminal processed states are rejected
- Supported providers:
  - `MOMO` — active when `app.payment.momo.enabled=true`; amount must be 1,000–50,000,000 VND
  - `PAYPAL` — active when `app.payment.paypal.enabled=true`; uses PayPal Orders API v2 sandbox. Returns PayPal approval URL as `paymentUrl`. Orders are VND; enable `app.payment.paypal.test-conversion-enabled=true` for sandbox VND→USD conversion. After initiation the frontend redirects to PayPal; upon return, call the capture endpoint below with the `?token=` param.
  - `MOCK` — dev/test only; active when `app.payment.mock.enabled=true`
- PayPal frontend routes:
  - Approval return: configured via `app.payment.paypal.return-url` (default `http://localhost:5173/payment/paypal/return`)
  - Cancellation: configured via `app.payment.paypal.cancel-url` (default `http://localhost:5173/payment/paypal/cancel`)

### POST `/api/v1/payments/order/{orderId}/capture`

- Access: authenticated customer flow
- Description: capture an authorized online payment after the customer returns from the provider. For PayPal, this must be called with the `token` query parameter from the PayPal redirect URL.
- Request body:
  - `PaymentCaptureRequest`
  - `provider`: e.g. `PAYPAL`
  - `providerToken`: the provider order ID (for PayPal, the `?token=` value from the redirect URL)
- Response:
  - `ApiResponse<PaymentResponse>`
- Current service behavior:
  - order must belong to the current customer
  - order payment method must be `ONLINE`
  - payment already `PAID` → returns existing record (idempotent)
  - payment status must be `INITIATED` or `PENDING` to be capturable
  - `providerToken` is verified against the stored `providerOrderId` to prevent substitution attacks
  - on `COMPLETED` capture → payment `PAID`, order `paymentStatus = PAID`
  - on failed capture → payment `FAILED`, order `paymentStatus = FAILED`
  - pessimistic row lock prevents duplicate concurrent captures

### POST `/api/v1/payments/callback`

- Access: **public** — called server-to-server by the payment gateway. No auth token required.
- Description: payment gateway callback endpoint. Idempotent on duplicate `providerTxnId`.
- Request body:
  - `PaymentCallbackRequest`
- Response:
  - `ApiResponse<PaymentResponse>`
- Current service behavior:
  - `status=SUCCESS` marks payment PAID, order.paymentStatus → PAID
  - any other status is treated as FAILED
  - duplicate `providerTxnId` → returns existing result, no side effect
  - stale callback cannot move a PAID/REFUNDED payment backward
- Security limitation: HMAC signature verification is not yet implemented. See `docs/security.md §10`.

### POST `/api/v1/webhooks/payment/PAYPAL`

- Access: **public** — called server-to-server by PayPal. No auth token required.
- Description: PayPal webhook notification receiver. Verifies the signature using PayPal's Webhooks API v1 before processing. Idempotent on duplicate `providerTxnId`. Returns `HTTP 200` as required by PayPal.
- Required headers (all passed by PayPal):
  - `PAYPAL-AUTH-ALGO`, `PAYPAL-CERT-URL`, `PAYPAL-TRANSMISSION-ID`, `PAYPAL-TRANSMISSION-SIG`, `PAYPAL-TRANSMISSION-TIME`
- Supported event types:
  - `PAYMENT.CAPTURE.COMPLETED` → marks payment PAID (if not already)
  - `PAYMENT.CAPTURE.DENIED` / `PAYMENT.CAPTURE.DECLINED` → marks payment FAILED
  - Other events (e.g., `CHECKOUT.ORDER.APPROVED`) → logged and ignored
- Security: signature verified via PayPal's `/v1/notifications/verify-webhook-signature` API; invalid signatures are rejected before any DB mutation.

### POST `/api/v1/webhooks/payment/{provider}`

- Access: **public** — called server-to-server by non-PayPal gateways (MoMo, VNPay, etc.).
- Description: generic gateway IPN/webhook receiver. Signature is verified internally via the registered provider. Returns `HTTP 204 No Content` as required by MoMo IPN spec.
- Required header: `X-Signature: <signature-value>`

---

## 9. Voucher validation

### DTOs

- `ValidateVoucherRequest`
  - `orderAmount`: required, decimal >= 0.01
  - `productIds`: optional list
  - `categoryIds`: optional list
  - `brandIds`: optional list
- `ValidateVoucherResponse`
  - `voucherCode`, `promotionName`
  - `discountType`, `discountValue`
  - `discountAmount`, `orderAmount`, `finalAmount`

### POST `/api/v1/vouchers/{code}/validate`

- Access: authenticated customer flow
- Description: validate voucher and preview discount
- Request body:
  - `ValidateVoucherRequest`
- Response:
  - `ApiResponse<ValidateVoucherResponse>`
- Note:
  - this endpoint previews discount only
  - it does not record voucher usage

---

## 10. Shipment tracking

### DTOs

- `ShipmentResponse`
  - `id`, `orderId`, `orderCode`, `shipmentCode`
  - `carrierId`, `carrierCode`, `carrierProviderType`
  - `carrier`, `carrierShipmentId`, `trackingNumber`
  - `providerStatus`, `providerTrackingUrl`, `status`
  - `estimatedDeliveryDate`, `deliveredAt`
  - `shippingFee`, `note`
  - `events`
  - `createdAt`, `updatedAt`
- `ShipmentEventResponse`
  - `id`, `status`, `location`, `description`, `eventTime`

### GET `/api/v1/shipments/order/{orderId}`

- Access: authenticated customer flow
- Description: get shipment for an owned order
- Response:
  - `ApiResponse<ShipmentResponse>`

### POST `/api/v1/shipments/webhook/ahamove`

- Access: public server-to-server endpoint
- Description: AhaMove shipment status callback receiver. Verifies the configured shared webhook token before any shipment mutation, ignores duplicate events idempotently, and stores sanitized webhook logs for valid requests.
- Current HTTP status:
  - `204 No Content`
- Security:
  - accepts one of `X-Webhook-Token`, `apikey`, or `Authorization: Bearer <token>`
  - invalid token/signature is rejected with `CARRIER_WEBHOOK_SIGNATURE_INVALID`

---

## 11. Invoice access

### DTOs

- `InvoiceResponse`
  - `id`, `invoiceCode`, `status`, `issuedAt`, `dueDate`, `notes`
  - `orderId`, `orderCode`, `paymentMethod`, `paymentStatus`, `paidAt`
  - `customerName`, `customerEmail`, `customerPhone`
  - `billingStreet`, `billingWard`, `billingDistrict`, `billingCity`, `billingPostalCode`
  - `items`
  - `subTotal`, `discountAmount`, `shippingFee`, `totalAmount`, `voucherCode`
  - `createdAt`
- `InvoiceItemResponse`
  - `variantId`, `productName`, `variantName`, `sku`
  - `unitPrice`, `salePrice`, `effectivePrice`
  - `quantity`, `lineTotal`

### GET `/api/v1/invoices/order/{orderId}`

- Access: authenticated customer flow
- Description: get invoice for an owned order
- Response:
  - `ApiResponse<InvoiceResponse>`

---

## 12. Reviews

### DTOs

- `CreateReviewRequest`
  - `orderItemId`: required
  - `rating`: required, 1 to 5
  - `comment`: optional, max 2000
- `ReviewFilter`
  - `status`
  - `productId`
  - `customerId`
  - `minRating`
  - `maxRating`
- `ReviewResponse`
  - `id`
  - `customerId`, `customerName`
  - `productId`, `productName`
  - `variantId`, `variantName`, `sku`
  - `orderItemId`
  - `rating`, `comment`
  - `status`, `adminNote`, `moderatedAt`, `moderatedBy`
  - `createdAt`, `updatedAt`

### POST `/api/v1/reviews`

- Access: `CUSTOMER` role
- Description: create review for an owned completed order item
- Request body:
  - `CreateReviewRequest`
- Current HTTP status:
  - `200 OK`
- Response:
  - `ApiResponse<ReviewResponse>`
- Current service behavior:
  - order item must belong to the current customer
  - parent order must be `COMPLETED`
  - one review per order item
  - new reviews start as `PENDING`

### GET `/api/v1/reviews/my`

- Access: `CUSTOMER` role
- Description: list my reviews
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
  - repository query orders by `createdAt DESC`
- Response:
  - `ApiResponse<PagedResponse<ReviewResponse>>`

### GET `/api/v1/reviews/product/{productId}`

- Access: public
- Description: list approved reviews for one product
- Filters:
  - practical filters: `minRating`, `maxRating`
  - the service overrides:
    - `productId` from the path
    - `status=APPROVED`
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
- Response:
  - `ApiResponse<PagedResponse<ReviewResponse>>`

---

## 13. Notifications

### DTOs

- `NotificationResponse`
  - `id`, `type`, `title`, `body`
  - `referenceId`, `referenceType`
  - `read`, `readAt`, `createdAt`
- `UnreadCountResponse`
  - `count`

### GET `/api/v1/notifications`

- Access: `CUSTOMER` role
- Description: list my notifications
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
  - repository query orders by `createdAt DESC`
- Response:
  - `ApiResponse<PagedResponse<NotificationResponse>>`

### GET `/api/v1/notifications/unread-count`

- Access: `CUSTOMER` role
- Description: get unread count
- Response:
  - `ApiResponse<UnreadCountResponse>`

### PATCH `/api/v1/notifications/{id}/read`

- Access: `CUSTOMER` role
- Description: mark one notification as read
- Response:
  - `ApiResponse<NotificationResponse>`

### PATCH `/api/v1/notifications/read-all`

- Access: `CUSTOMER` role
- Description: mark all notifications as read
- Response:
  - `ApiResponse<Void>`
