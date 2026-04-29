# Admin API Contract

This document covers only the current admin API surface under `/api/v1/admin/**`.

Source of truth:
- `src/main/java/com/locnguyen/ecommerce/domains/admin/controller/*`
- `src/main/java/com/locnguyen/ecommerce/domains/notification/controller/AdminNotificationController.java`
- `src/main/java/com/locnguyen/ecommerce/domains/review/controller/AdminReviewController.java`
- related DTOs, services, and shared response types

Excluded on purpose:
- legacy admin-only review moderation routes under `/api/v1/reviews/**`
- all public/customer routes outside `/api/v1/admin/**`

Shared response, error, auth, enum, and pagination rules are defined in [api-common.md](./api-common.md).

---

## 1. Access model

- All routes here require `Authorization: Bearer <accessToken>`.
- URL-level access for `/api/v1/admin/**`: `STAFF`, `ADMIN`, or `SUPER_ADMIN`
- Some endpoints are stricter via `@PreAuthorize`.

Role summary by module:

- `ADMIN`, `SUPER_ADMIN` only:
  - admin products and variants
  - admin promotions
  - admin notifications broadcast
  - admin audit logs
  - admin users
  - admin order cancel
  - admin inventory mutations
  - admin warehouse mutations
  - admin voucher mutations
- `STAFF`, `ADMIN`, `SUPER_ADMIN`:
  - category CRUD
  - brand CRUD
  - inventory reads
  - warehouse reads
  - order reads and non-cancel transitions
  - payments
  - shipments
  - invoices
  - `/api/v1/admin/reviews`

---

## 2. Product

### DTOs

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
- `CreateProductRequest`
  - `name`: required, max 255
  - `slug`: required, max 255
  - `shortDescription`: optional, max 500
  - `description`: optional
  - `brandId`: optional
  - `categoryIds`: optional list of UUID
  - `status`: optional
  - `featured`: optional
- `UpdateProductRequest`
  - partial update version of the same fields

### Variant DTOs

- `VariantResponse`
  - `id`, `sku`, `barcode`, `variantName`
  - `basePrice`, `salePrice`, `compareAtPrice`
  - `weightGram`, `status`
  - `attributes`
- `AttributeResponse`
  - `name`, `value`
- `MediaResponse`
  - `id`, `mediaUrl`, `mediaType`, `sortOrder`, `primary`, `variantId`
- `CreateVariantRequest`
  - `sku`: required, max 100
  - `barcode`: optional, max 100
  - `variantName`: required, max 255
  - `basePrice`: required, positive
  - `salePrice`: optional, positive
  - `compareAtPrice`: optional, positive
  - `weightGram`: optional
  - `status`: optional
  - `attributes`: optional list of `AttributeRequest`
- `UpdateVariantRequest`
  - partial update version of the same fields
- `AttributeRequest`
  - `attributeName`: required, max 100
  - `value`: required, max 100

### GET `/api/v1/admin/products`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: list all products, including non-public statuses
- Filters:
  - `keyword`, `categoryId`, `brandId`, `status`, `minPrice`, `maxPrice`, `featured`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=createdAt,desc`
- Response:
  - `ApiResponse<PagedResponse<ProductListItemResponse>>`

### GET `/api/v1/admin/products/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: get full product detail without public status filtering
- Response:
  - `ApiResponse<ProductDetailResponse>`

### POST `/api/v1/admin/products`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create product
- Request body:
  - `CreateProductRequest`
- Current HTTP status:
  - `200 OK`
- Response:
  - `ApiResponse<ProductDetailResponse>`

### PATCH `/api/v1/admin/products/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update product
- Request body:
  - `UpdateProductRequest`
- Response:
  - `ApiResponse<ProductDetailResponse>`

### DELETE `/api/v1/admin/products/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete product
- Response:
  - `ApiResponse<Void>`

### GET `/api/v1/admin/products/{productId}/variants`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: list variants for a product
- Response:
  - `ApiResponse<List<VariantResponse>>`

### POST `/api/v1/admin/products/{productId}/variants`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create variant for a product
- Request body:
  - `CreateVariantRequest`
- Current HTTP status:
  - `200 OK`
- Response:
  - `ApiResponse<VariantResponse>`

### PATCH `/api/v1/admin/products/{productId}/variants/{variantId}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update variant
- Request body:
  - `UpdateVariantRequest`
- Response:
  - `ApiResponse<VariantResponse>`

### DELETE `/api/v1/admin/products/{productId}/variants/{variantId}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete variant
- Response:
  - `ApiResponse<Void>`

---

## 3. Category

### DTOs

- `CategoryFilter`
  - `name`
  - `slug`
  - `parentId`
  - `status`
- `CategoryResponse`
  - `id`, `parentId`, `name`, `slug`, `description`, `imageUrl`
  - `status`, `sortOrder`, `createdAt`
- `CreateCategoryRequest`
  - `parentId`: optional
  - `name`: required, max 100
  - `slug`: required, max 255
  - `description`: optional
  - `imageUrl`: optional
  - `sortOrder`: optional
- `UpdateCategoryRequest`
  - partial update version of:
  - `parentId`, `name`, `slug`, `description`, `imageUrl`, `status`, `sortOrder`

### GET `/api/v1/admin/categories`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list categories
- Filters:
  - `name`, `slug`, `parentId`, `status`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`
- Response:
  - `ApiResponse<PagedResponse<CategoryResponse>>`

### POST `/api/v1/admin/categories`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: create category
- Request body:
  - `CreateCategoryRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<CategoryResponse>`

### PATCH `/api/v1/admin/categories/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: partial update category
- Request body:
  - `UpdateCategoryRequest`
- Response:
  - `ApiResponse<CategoryResponse>`

### DELETE `/api/v1/admin/categories/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete category
- Response:
  - `ApiResponse<Void>`

---

## 4. Brand

### DTOs

- `BrandFilter`
  - `name`
  - `status`
- `BrandResponse`
  - `id`, `name`, `slug`, `logoUrl`, `description`
  - `sortOrder`, `status`, `createdAt`
- `CreateBrandRequest`
  - `name`: required, max 100
  - `slug`: required, max 255
  - `logoUrl`: optional
  - `description`: optional
- `UpdateBrandRequest`
  - partial update version of:
  - `name`, `slug`, `logoUrl`, `description`, `status`

### GET `/api/v1/admin/brands`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list brands
- Filters:
  - `name`, `status`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=sortOrder,asc`
- Response:
  - `ApiResponse<PagedResponse<BrandResponse>>`

### POST `/api/v1/admin/brands`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: create brand
- Request body:
  - `CreateBrandRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<BrandResponse>`

### PATCH `/api/v1/admin/brands/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: partial update brand
- Request body:
  - `UpdateBrandRequest`
- Response:
  - `ApiResponse<BrandResponse>`

### DELETE `/api/v1/admin/brands/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete brand
- Response:
  - `ApiResponse<Void>`

---

## 5. Inventory

### DTOs

- `InventoryFilter`
  - `variantId`
  - `warehouseId`
  - `productId`
  - `sku`
  - `keyword`
  - `variantStatus`
  - `outOfStock`
  - `lowStock`
  - `lowStockThreshold`
- `InventoryResponse`
  - `id`, `variantId`, `variantName`, `sku`
  - `warehouseId`, `warehouseName`
  - `onHand`, `reserved`, `available`
  - `updatedAt`
- `StockFilter`
  - `variantId`
  - `warehouseId`
  - `movementType`
- `StockMovementResponse`
  - `id`, `variantId`, `variantName`, `sku`
  - `warehouseId`, `warehouseName`
  - `movementType`, `quantity`
  - `referenceType`, `referenceId`, `note`
  - `beforeOnHand`, `beforeReserved`, `beforeAvailable`
  - `afterOnHand`, `afterReserved`, `afterAvailable`
  - `createdBy`, `createdAt`
- `AdjustStockRequest`
  - `variantId`: required
  - `warehouseId`: required
  - `quantity`: required, positive
  - `movementType`: required
  - `note`: optional, max 500
- `ReserveStockRequest`
  - `variantId`: required
  - `warehouseId`: required
  - `quantity`: required, positive
  - `referenceType`: optional
  - `referenceId`: optional
  - `expiresAt`: optional

### GET `/api/v1/admin/inventories`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list inventory records
- Filters:
  - `variantId`, `warehouseId`, `productId`, `sku`, `keyword`, `variantStatus`, `outOfStock`, `lowStock`, `lowStockThreshold`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=updatedAt,desc`
- Response:
  - `ApiResponse<PagedResponse<InventoryResponse>>`

### GET `/api/v1/admin/inventories/variant/{variantId}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: inventory levels for one variant across warehouses
- Response:
  - `ApiResponse<List<InventoryResponse>>`

### GET `/api/v1/admin/inventories/warehouse/{warehouseId}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: inventory levels inside one warehouse
- Response:
  - `ApiResponse<List<InventoryResponse>>`

### GET `/api/v1/admin/inventories/variant/{variantId}/warehouse/{warehouseId}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: inventory detail for one variant in one warehouse
- Response:
  - `ApiResponse<InventoryResponse>`

### GET `/api/v1/admin/inventories/movements`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: stock movement history
- Filters:
  - `variantId`, `warehouseId`, `movementType`
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
  - repository query orders by `createdAt DESC`
- Response:
  - `ApiResponse<PagedResponse<StockMovementResponse>>`

### POST `/api/v1/admin/inventories/adjust`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: adjust/import/export/return stock
- Request body:
  - `AdjustStockRequest`
- Response:
  - `ApiResponse<StockMovementResponse>`

### POST `/api/v1/admin/inventories/reserve`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create manual reservation record
- Request body:
  - `ReserveStockRequest`
- Response:
  - `ApiResponse<InventoryReservation>`
- Stable scalar fields on the current returned entity:
  - `id`
  - `referenceType`
  - `referenceId`
  - `quantity`
  - `status`
  - `expiresAt`
  - `createdAt`
  - `createdBy`
  - `updatedAt`
  - `updatedBy`
- Note:
  - the controller returns the JPA entity directly, not a dedicated DTO
  - nested `variant` and `warehouse` objects should not be treated as a stable contract

### POST `/api/v1/admin/inventories/release`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: release reservations by reference
- Request params:
  - `referenceType`
  - `referenceId`
- Response:
  - `ApiResponse<Void>`

---

## 6. Warehouse

### DTOs

- `WarehouseResponse`
  - `id`, `name`, `code`, `location`, `status`, `createdAt`
- `CreateWarehouseRequest`
  - `name`: required, max 100
  - `code`: required, max 50, pattern `^[A-Za-z0-9_-]+$`
  - `location`: optional, max 255
- `UpdateWarehouseRequest`
  - partial update version of:
  - `name`, `location`, `status`

### GET `/api/v1/admin/warehouses`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list active warehouses
- Response:
  - `ApiResponse<List<WarehouseResponse>>`

### GET `/api/v1/admin/warehouses/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get warehouse by ID
- Response:
  - `ApiResponse<WarehouseResponse>`

### POST `/api/v1/admin/warehouses`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create warehouse
- Request body:
  - `CreateWarehouseRequest`
- Current HTTP status:
  - `200 OK`
- Response:
  - `ApiResponse<WarehouseResponse>`

### PATCH `/api/v1/admin/warehouses/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update warehouse
- Request body:
  - `UpdateWarehouseRequest`
- Response:
  - `ApiResponse<WarehouseResponse>`

### DELETE `/api/v1/admin/warehouses/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete warehouse
- Response:
  - `ApiResponse<Void>`

---

## 7. Order

### DTOs

- `OrderAdminFilter`
  - `customerId`
  - `status`
  - `paymentStatus`
- `AdminOrderListItemResponse`
  - `id`, `orderCode`
  - `customerId`, `customerName`, `customerEmail`
  - `status`, `paymentMethod`, `paymentStatus`
  - `totalItems`, `totalAmount`
  - `createdAt`
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

### GET `/api/v1/admin/orders`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list orders
- Filters:
  - `customerId`, `status`, `paymentStatus`
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
  - repository query orders by `createdAt DESC`
- Response:
  - `ApiResponse<PagedResponse<AdminOrderListItemResponse>>`

### GET `/api/v1/admin/orders/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get order by ID
- Response:
  - `ApiResponse<OrderResponse>`

### GET `/api/v1/admin/orders/code/{orderCode}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get order by order code
- Response:
  - `ApiResponse<OrderResponse>`

### POST `/api/v1/admin/orders/{id}/confirm`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: confirm order
- Status transition note:
  - `PENDING -> CONFIRMED`
  - `AWAITING_PAYMENT -> CONFIRMED`
  - online orders must already be paid
- Response:
  - `ApiResponse<OrderResponse>`

### POST `/api/v1/admin/orders/{id}/process`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: move order to processing
- Status transition note:
  - `CONFIRMED -> PROCESSING`
- Response:
  - `ApiResponse<OrderResponse>`

### POST `/api/v1/admin/orders/{id}/deliver`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: mark order delivered
- Status transition note:
  - `SHIPPED -> DELIVERED`
- Response:
  - `ApiResponse<OrderResponse>`

### POST `/api/v1/admin/orders/{id}/complete`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: complete order and commit reserved stock
- Status transition note:
  - `DELIVERED -> COMPLETED`
- Response:
  - `ApiResponse<OrderResponse>`

### POST `/api/v1/admin/orders/{id}/cancel`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: cancel order and release reservations
- Response:
  - `ApiResponse<OrderResponse>`

---

## 8. Payment

### DTOs

- `PaymentFilter`
  - `method`
  - `status`
  - `orderCode`
  - `dateFrom`
  - `dateTo`
- `PaymentResponse`
  - `id`, `orderId`, `orderCode`, `paymentCode`
  - `method`, `status`, `amount`, `paidAt`, `createdAt`
  - `transactions`
- `TransactionResponse`
  - `id`, `transactionCode`, `status`, `amount`
  - `method`, `provider`, `providerTxnId`
  - `referenceType`, `referenceId`, `note`
  - `createdAt`

### GET `/api/v1/admin/payments`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list payments
- Filters:
  - `method`, `status`, `orderCode`, `dateFrom`, `dateTo`
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
- Response:
  - `ApiResponse<PagedResponse<PaymentResponse>>`
- List payload note:
  - list mapping omits `transactions`

### GET `/api/v1/admin/payments/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get payment by ID
- Response:
  - `ApiResponse<PaymentResponse>`

### GET `/api/v1/admin/payments/code/{code}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get payment by payment code
- Response:
  - `ApiResponse<PaymentResponse>`

### GET `/api/v1/admin/payments/order/{orderId}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get payment by order ID
- Response:
  - `ApiResponse<PaymentResponse>`

### POST `/api/v1/admin/payments/order/{orderId}/complete`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: mark COD payment as received
- Response:
  - `ApiResponse<PaymentResponse>`

### GET `/api/v1/admin/payments/{id}/transactions`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list transaction trail for a payment
- Response:
  - `ApiResponse<List<TransactionResponse>>`

---

## 9. Shipment

### DTOs

- `ShipmentFilter`
  - `orderId`
  - `orderCode`
  - `carrier`
  - `status`
  - `dateFrom`
  - `dateTo`
- `ShipmentResponse`
  - `id`, `orderId`, `orderCode`, `shipmentCode`
  - `carrier`, `trackingNumber`, `status`
  - `estimatedDeliveryDate`, `deliveredAt`
  - `shippingFee`, `note`
  - `events`
  - `createdAt`, `updatedAt`
- `ShipmentEventResponse`
  - `id`, `status`, `location`, `description`, `eventTime`
- `CreateShipmentRequest`
  - `orderId`: required
  - `carrier`: required, max 100
  - `trackingNumber`: optional, max 200
  - `estimatedDeliveryDate`: optional
  - `shippingFee`: optional, decimal >= 0
  - `note`: optional, max 500
- `UpdateShipmentRequest`
  - partial update version of:
  - `carrier`, `trackingNumber`, `estimatedDeliveryDate`, `shippingFee`, `note`
- `UpdateShipmentStatusRequest`
  - `status`: required
  - `location`: optional, max 255
  - `description`: required, max 500
  - `eventTime`: optional

### POST `/api/v1/admin/shipments`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: create shipment for an order
- Request body:
  - `CreateShipmentRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<ShipmentResponse>`

### GET `/api/v1/admin/shipments/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get shipment by ID
- Response:
  - `ApiResponse<ShipmentResponse>`

### GET `/api/v1/admin/shipments/order/{orderId}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get shipment by order ID
- Response:
  - `ApiResponse<ShipmentResponse>`

### GET `/api/v1/admin/shipments`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list shipments
- Filters:
  - `orderId`, `orderCode`, `carrier`, `status`, `dateFrom`, `dateTo`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=createdAt,asc`
- Response:
  - `ApiResponse<PagedResponse<ShipmentResponse>>`
- List payload note:
  - list mapping omits `events`

### PATCH `/api/v1/admin/shipments/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: partial update shipment details
- Request body:
  - `UpdateShipmentRequest`
- Response:
  - `ApiResponse<ShipmentResponse>`

### PATCH `/api/v1/admin/shipments/{id}/status`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: advance shipment status and record event
- Request body:
  - `UpdateShipmentStatusRequest`
- Response:
  - `ApiResponse<ShipmentResponse>`

---

## 10. Invoice

### DTOs

- `InvoiceFilter`
  - `invoiceCode`
  - `orderCode`
  - `status`
  - `dateFrom`
  - `dateTo`
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
- `UpdateInvoiceStatusRequest`
  - `status`: required
  - `notes`: optional, max 1000

### POST `/api/v1/admin/invoices/order/{orderId}/generate`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: generate invoice for an order
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<InvoiceResponse>`

### GET `/api/v1/admin/invoices/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get invoice by ID
- Response:
  - `ApiResponse<InvoiceResponse>`

### GET `/api/v1/admin/invoices/order/{orderId}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get invoice by order ID
- Response:
  - `ApiResponse<InvoiceResponse>`

### GET `/api/v1/admin/invoices/code/{invoiceCode}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get invoice by invoice code
- Response:
  - `ApiResponse<InvoiceResponse>`

### GET `/api/v1/admin/invoices`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list invoices
- Filters:
  - `invoiceCode`, `orderCode`, `status`, `dateFrom`, `dateTo`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=issuedAt,desc`
- Response:
  - `ApiResponse<PagedResponse<InvoiceResponse>>`
- List payload note:
  - list mapping includes a reduced invoice shape and omits `items`

### PATCH `/api/v1/admin/invoices/{id}/status`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: update invoice status
- Request body:
  - `UpdateInvoiceStatusRequest`
- Response:
  - `ApiResponse<InvoiceResponse>`

---

## 11. User

### DTOs

- `CreateUserRequest`
  - `email`: required, valid email, max 255
  - `password`: required, 8-64 chars, at least one lowercase letter, one uppercase letter, and one digit
  - `firstName`: required, max 100
  - `lastName`: optional, max 100
  - `phoneNumber`: optional, `@PhoneNumber`
  - `roles`: required non-empty set of `RoleName`
- `UserResponse`
  - `id`, `email`, `firstName`, `lastName`, `phoneNumber`
  - `status`, `roles`, `createdAt`

### POST `/api/v1/admin/users`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create system user
- Request body:
  - `CreateUserRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<UserResponse>`

---

## 12. Promotion

### DTOs

- `PromotionFilter`
  - `name`
  - `scope`
  - `active`
  - `dateFrom`
  - `dateTo`
- `PromotionResponse`
  - `id`, `name`, `description`
  - `discountType`, `discountValue`, `maxDiscountAmount`, `minimumOrderAmount`
  - `scope`, `startDate`, `endDate`
  - `active`, `usageLimit`, `usageCount`
  - `rules`
  - `createdAt`, `updatedAt`
- `PromotionRuleResponse`
  - `id`, `ruleType`, `ruleValue`, `description`
- `CreatePromotionRequest`
  - `name`: required, max 200
  - `description`: optional, max 2000
  - `discountType`: required
  - `discountValue`: required, decimal >= 0.01
  - `maxDiscountAmount`: optional, decimal >= 0.01
  - `minimumOrderAmount`: optional, decimal >= 0
  - `scope`: required
  - `startDate`: required
  - `endDate`: required
  - `usageLimit`: optional, min 1
- `UpdatePromotionRequest`
  - partial update version of:
  - `name`, `description`, `discountValue`, `maxDiscountAmount`, `minimumOrderAmount`, `startDate`, `endDate`, `active`, `usageLimit`
- `AddRuleRequest`
  - `ruleType`: required
  - `ruleValue`: required, max 500
  - `description`: optional, max 255

### POST `/api/v1/admin/promotions`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create promotion
- Request body:
  - `CreatePromotionRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<PromotionResponse>`

### GET `/api/v1/admin/promotions/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: get promotion by ID
- Response:
  - `ApiResponse<PromotionResponse>`

### GET `/api/v1/admin/promotions`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: list promotions
- Filters:
  - `name`, `scope`, `active`, `dateFrom`, `dateTo`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=createdAt,asc`
- Response:
  - `ApiResponse<PagedResponse<PromotionResponse>>`

### PATCH `/api/v1/admin/promotions/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update promotion
- Request body:
  - `UpdatePromotionRequest`
- Response:
  - `ApiResponse<PromotionResponse>`

### DELETE `/api/v1/admin/promotions/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete promotion
- Current HTTP status:
  - `204 No Content`
- Response:
  - treat as empty body

### POST `/api/v1/admin/promotions/{id}/rules`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: add rule to promotion
- Request body:
  - `AddRuleRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<PromotionResponse>`

### DELETE `/api/v1/admin/promotions/{id}/rules/{ruleId}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: remove rule from promotion
- Current HTTP status:
  - `204 No Content`
- Response:
  - treat as empty body

---

## 13. Voucher

### DTOs

- `VoucherFilter`
  - `code`
  - `promotionId`
  - `active`
  - `dateFrom`
  - `dateTo`
- `VoucherResponse`
  - `id`, `code`, `promotionId`, `promotionName`
  - `discountType`, `discountValue`, `maxDiscountAmount`, `minimumOrderAmount`
  - `usageLimit`, `usageCount`, `usageLimitPerUser`
  - `startDate`, `endDate`, `active`, `createdAt`
- `VoucherUsageResponse`
  - `id`, `voucherId`, `voucherCode`, `customerId`, `orderId`, `discountAmount`, `usedAt`
- `CreateVoucherRequest`
  - `code`: optional, max 100
  - `promotionId`: required
  - `usageLimit`: optional, min 1
  - `usageLimitPerUser`: optional, min 1
  - `startDate`: required
  - `endDate`: required
- `UpdateVoucherRequest`
  - partial update version of:
  - `usageLimit`, `usageLimitPerUser`, `startDate`, `endDate`, `active`

### GET `/api/v1/admin/vouchers/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get voucher by ID
- Response:
  - `ApiResponse<VoucherResponse>`

### GET `/api/v1/admin/vouchers/code/{code}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get voucher by code
- Response:
  - `ApiResponse<VoucherResponse>`

### GET `/api/v1/admin/vouchers`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list vouchers
- Filters:
  - `code`, `promotionId`, `active`, `dateFrom`, `dateTo`
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
- Response:
  - `ApiResponse<PagedResponse<VoucherResponse>>`

### GET `/api/v1/admin/vouchers/{id}/usages`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get voucher usage history
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
- Response:
  - `ApiResponse<PagedResponse<VoucherUsageResponse>>`

### POST `/api/v1/admin/vouchers`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create voucher
- Request body:
  - `CreateVoucherRequest`
- Current HTTP status:
  - `200 OK`
- Response:
  - `ApiResponse<VoucherResponse>`

### PATCH `/api/v1/admin/vouchers/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update voucher
- Request body:
  - `UpdateVoucherRequest`
- Response:
  - `ApiResponse<VoucherResponse>`

### DELETE `/api/v1/admin/vouchers/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete voucher
- Response:
  - `ApiResponse<Void>`

---

## 14. Review

This file only documents the current admin-prefixed review API.

Excluded from this file:

- `GET /api/v1/reviews/pending`
- `GET /api/v1/reviews/{id}`
- `PATCH /api/v1/reviews/{id}/moderate`
- `DELETE /api/v1/reviews/{id}`

Those legacy moderation endpoints exist in code but are not under `/api/v1/admin/**`.

### DTOs

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
- `UpdateReviewStatusRequest`
  - `status`: required
  - `adminNote`: optional, max 500

### GET `/api/v1/admin/reviews`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list reviews across all statuses
- Filters:
  - `status`, `productId`, `customerId`, `minRating`, `maxRating`
- Pagination and sorting:
  - `page`
  - `size`
  - `sort`
  - `direction`
  - defaults: `page=0`, `size=20`, `sort=createdAt`, `direction=desc`
- Response:
  - `ApiResponse<PagedResponse<ReviewResponse>>`

### GET `/api/v1/admin/reviews/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get review by ID
- Response:
  - `ApiResponse<ReviewResponse>`

### PATCH `/api/v1/admin/reviews/{id}/status`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: approve or reject review
- Request body:
  - `UpdateReviewStatusRequest`
- Response:
  - `ApiResponse<ReviewResponse>`

---

## 15. Notification

### DTOs

- `BroadcastNotificationRequest`
  - `type`: required
  - `title`: required, max 255
  - `message`: required, max 5000
  - `referenceType`: optional, max 50
  - `referenceId`: optional, max 100
  - `customerIds`: optional list of UUID; empty/null means all customers

### POST `/api/v1/admin/notifications/broadcast`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: broadcast notification to selected or all customers
- Request body:
  - `BroadcastNotificationRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<Map<String, Integer>>`
  - response data shape:
    - `sent`
- Note:
  - `referenceId` is parsed as a UUID string by the service
  - invalid UUID text is accepted by the request DTO but stored as `null`

---

## 16. Audit

### DTOs

- `AuditLogFilter`
  - `entityType`
  - `entityId`
  - `action`
  - `actor`
  - `fromDate`
  - `toDate`
- `AuditLogResponse`
  - `id`, `action`
  - `entityType`, `entityId`
  - `actor`, `ipAddress`, `requestId`
  - `details`
  - `createdAt`

### GET `/api/v1/admin/audit-logs`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: list audit log entries
- Filters:
  - `entityType`, `entityId`, `action`, `actor`, `fromDate`, `toDate`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=createdAt,desc`
- Response:
  - `ApiResponse<PagedResponse<AuditLogResponse>>`

### GET `/api/v1/admin/audit-logs/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: get one audit log entry
- Response:
  - `ApiResponse<AuditLogResponse>`

