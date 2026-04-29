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

`POST /api/v1/payments/callback` is **not** public in the current source because `SecurityConfig` does not whitelist it. It currently requires authentication even though the controller comment describes it as a gateway callback.

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
  - `refreshToken`: required
- `AuthResponse`
  - `user`
  - `tokens`
- `UserResponse`
  - `id`, `email`, `firstName`, `lastName`, `phoneNumber`
  - `status`, `roles`, `createdAt`
- `TokenResponse`
  - `accessToken`, `refreshToken`, `tokenType`, `expiresIn`

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

### POST `/api/v1/auth/refresh-token`

- Access: public
- Description: exchange refresh token for a new token pair
- Request body:
  - `RefreshTokenRequest`
- Response:
  - `ApiResponse<TokenResponse>`

### POST `/api/v1/auth/logout`

- Access: authenticated
- Description: blacklist current access token
- Response:
  - `ApiResponse<Void>`

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

- `CreateOrderRequest`
  - `shippingAddressId`: required
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
  - `subTotal`, `discountAmount`, `shippingFee`, `totalAmount`
  - `voucherCode`, `customerNote`
  - `items`
  - `createdAt`
- `OrderItemResponse`
  - `id`, `variantId`, `productName`, `variantName`, `sku`
  - `unitPrice`, `salePrice`, `quantity`, `lineTotal`

### POST `/api/v1/orders`

- Access: authenticated customer flow
- Description: create order from active cart
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
  - stock is reserved during order creation
  - `voucherCode` is stored on the order, but discount calculation is currently not applied in service logic

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
- `PaymentCallbackRequest`
  - `orderCode`: required
  - `status`: required string
  - `providerTxnId`: optional
  - `provider`: optional
  - `payload`: optional
- `PaymentResponse`
  - `id`, `orderId`, `orderCode`, `paymentCode`
  - `method`, `status`, `amount`, `paidAt`, `createdAt`
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
- Request body:
  - optional `InitPaymentRequest`
  - if the body is omitted, the controller creates an empty request object internally
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<PaymentResponse>`
- Current service behavior:
  - order must belong to the current customer
  - order payment method must be `ONLINE`
  - existing `PENDING` or `INITIATED` payment returns existing record
  - existing `FAILED` payment is retried
  - terminal processed states are rejected

### POST `/api/v1/payments/callback`

- Access: authenticated in the current source
- Description: payment callback endpoint
- Request body:
  - `PaymentCallbackRequest`
- Response:
  - `ApiResponse<PaymentResponse>`
- Current service behavior:
  - `status=SUCCESS` marks payment paid
  - any other status value is treated as failed
  - duplicate `providerTxnId` is idempotent

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
  - `carrier`, `trackingNumber`, `status`
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

