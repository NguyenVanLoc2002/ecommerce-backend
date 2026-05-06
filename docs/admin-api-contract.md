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
- Admin and staff users authenticate through the same public `POST /api/v1/auth/login` endpoint used by customers.
- The shared auth backend returns only the access token in the JSON response body for all user types.
- The shared auth backend stores the refresh token in an HttpOnly cookie for all user types.
- There is no separate admin login controller or separate admin refresh-token mechanism.
- URL-level access for `/api/v1/admin/**`: `STAFF`, `ADMIN`, or `SUPER_ADMIN`
- Some endpoints are stricter via `@PreAuthorize`.

Role summary by module:

- `ADMIN`, `SUPER_ADMIN` only:
  - admin products and variants
  - admin product attributes (and their values)
  - admin promotions
  - admin notifications broadcast
  - admin audit logs
  - admin users
  - admin customer mutations (`PATCH`/`DELETE` under `/api/v1/admin/customers`)
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
  - admin customer reads (`GET` under `/api/v1/admin/customers`)

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
  - `id`, `productId`, `sku`, `barcode`, `variantName`
  - `basePrice`, `salePrice`, `compareAtPrice`
  - `weightGram`, `status`
  - `attributes`: list of `VariantAttributeResponse`
- `VariantAttributeResponse`
  - `attributeId`, `attributeName`, `attributeCode`
  - `valueId`, `value`, `displayValue`
- `MediaResponse`
  - `id`, `mediaUrl`, `mediaType`, `sortOrder`, `primary`, `variantId`
- `CreateVariantRequest`
  - `sku`: optional, max 100. Server generates one when blank or `autoGenerateSku=true`.
  - `autoGenerateSku`: optional boolean
  - `barcode`: optional, max 100. Uniqueness enforced when present.
  - `autoGenerateBarcode`: optional boolean — server generates when true or barcode blank
  - `variantName`: optional, max 255. Server builds from selected attribute display values when blank or `autoGenerateVariantName=true`.
  - `autoGenerateVariantName`: optional boolean
  - `basePrice`: required, `>= 0`
  - `salePrice`: optional, `>= 0`, must be `<= basePrice`
  - `compareAtPrice`: optional, `>= 0`, must be `>= basePrice`
  - `weightGram`: optional, must be `> 0` when present
  - `status`: optional
  - `attributeValueIds`: set of `ProductAttributeValue` UUIDs (must be `VARIANT`-typed; at most one value per attribute)
- `UpdateVariantRequest`
  - partial update version of the same fields. If `attributeValueIds` is provided, the variant's attribute set is replaced.

### Variant validation rules

- `attributeValueIds` must reference existing values whose attribute is of type `VARIANT`. Sending a `DESCRIPTIVE` attribute value yields `VARIANT_ATTRIBUTE_INVALID`.
- A variant cannot select more than one value from the same attribute (`VARIANT_ATTRIBUTE_INVALID`).
- The same attribute-value combination cannot occur twice within the same product (`VARIANT_COMBINATION_DUPLICATE`).
- `basePrice` is required and must be `>= 0`. `salePrice <= basePrice`. `compareAtPrice >= basePrice`. Violations yield `VARIANT_INVALID_PRICE`.
- `weightGram` must be `> 0` when present (`VARIANT_INVALID_WEIGHT`).
- Manually supplied `sku` must be unique (`SKU_ALREADY_EXISTS`); manually supplied `barcode` must be unique (`BARCODE_ALREADY_EXISTS`).

### GET `/api/v1/admin/products`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: list all products, including non-public statuses
- Soft-delete behavior:
  - soft-deleted products are excluded by default; `isDeleted=true` returns
    soft-deleted only, `includeDeleted=true` returns both
- Filters:
  - `keyword`, `categoryId`, `brandId`, `status`, `minPrice`, `maxPrice`, `featured`
  - `isDeleted`, `includeDeleted`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=createdAt,desc`
- Keyword search:
  - When `keyword` is blank the standard JPA Specification is used.
  - When `keyword` has text MariaDB FULLTEXT is used:
    `MATCH(products.name, products.slug, products.search_text) AGAINST (? IN BOOLEAN MODE)`.
  - Search is case-insensitive and accent-insensitive (`Áo` → `ao`,
    `Đầm` → `dam`); reserved BOOLEAN MODE characters in user input are stripped.
  - Results are ordered by FULLTEXT relevance first, then by the requested
    `sort` (whitelisted: `createdAt`, `updatedAt`, `name`, `status`, `featured`).
  - `minPrice` / `maxPrice` must match the **same** variant; soft-deleted
    variants are excluded.
  - `products.search_text` is internal — never returned.
- Response:
  - `ApiResponse<PagedResponse<ProductListItemResponse>>`

### GET `/api/v1/admin/products/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: get full product detail without public status filtering
- Soft-delete behavior:
  - a soft-deleted product returns `PRODUCT_NOT_FOUND`
  - returned `categories[]` and `variants[]` exclude soft-deleted children
- Response:
  - `ApiResponse<ProductDetailResponse>`

### POST `/api/v1/admin/products`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create product
- Reference behavior:
  - `brandId` and every `categoryId` must reference active rows
- Request body:
  - `CreateProductRequest`
- Current HTTP status:
  - `200 OK`
- Response:
  - `ApiResponse<ProductDetailResponse>`

### PATCH `/api/v1/admin/products/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update product
- Reference behavior:
  - `brandId` and every `categoryId` must reference active rows
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
- Soft-delete behavior:
  - soft-deleted variants are excluded by default
- Filters:
  - `isDeleted`, `includeDeleted`
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

### POST `/api/v1/admin/products/search/reindex`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: rebuild `products.search_text` for every active product.
  Run once after deploying the FULLTEXT migration (V17) and any time a bulk
  data fix has bypassed the create/update service path. Processes products
  in batches of 200 inside per-batch transactions.
- Response:
  - `ApiResponse<ReindexResult>` where `ReindexResult = { totalProcessed }`

### DELETE `/api/v1/admin/products/{productId}/variants/{variantId}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete variant
- Response:
  - `ApiResponse<Void>`

---

## 2.b Product Attribute

Reusable attribute definitions used to build variant pickers and tag descriptive product traits.

### DTOs

- `ProductAttributeFilter`
  - `type`: optional `VARIANT` or `DESCRIPTIVE`
  - `keyword`: optional partial, case-insensitive match on `name` or `code`
  - `isDeleted`, `includeDeleted`
- `ProductAttributeResponse`
  - `id`, `name`, `code`, `type`
  - `values`: list of `ProductAttributeValueResponse`
  - `createdAt`, `updatedAt`
- `ProductAttributeValueResponse`
  - `id`, `value`, `displayValue`
- `CreateProductAttributeRequest`
  - `name`: required, max 100
  - `code`: required, max 50 — normalized to upper snake-case server-side
  - `type`: required (`VARIANT` or `DESCRIPTIVE`)
  - `values`: optional list of `CreateProductAttributeValueRequest`
- `UpdateProductAttributeRequest`
  - partial update of the same fields. When `values` is provided, the value set is **replaced**.
- `CreateProductAttributeValueRequest`
  - `value`: required, max 100
  - `displayValue`: optional, max 100

### GET `/api/v1/admin/product-attributes`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: list product attributes (paginated). Pass `type=VARIANT` to fetch the dataset used by the variant attribute picker.
- Soft-delete behavior:
  - soft-deleted attributes are excluded by default
  - each returned `values[]` list excludes soft-deleted attribute values
- Filters:
  - `type`, `keyword`, `isDeleted`, `includeDeleted`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=createdAt,desc`
- Response:
  - `ApiResponse<PagedResponse<ProductAttributeResponse>>`

### GET `/api/v1/admin/product-attributes/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: fetch one attribute
- Soft-delete behavior:
  - a soft-deleted attribute is treated as not found
- Errors:
  - `PRODUCT_ATTRIBUTE_NOT_FOUND` (404)
- Response:
  - `ApiResponse<ProductAttributeResponse>`

### POST `/api/v1/admin/product-attributes`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create a product attribute (optionally seed initial values)
- Uniqueness rule:
  - `code` remains reserved even if an older attribute with the same code was soft-deleted, because the database unique constraint still applies
- Request body:
  - `CreateProductAttributeRequest`
- Errors:
  - `PRODUCT_ATTRIBUTE_CODE_ALREADY_EXISTS` (409)
  - `PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS` (409) — duplicate value in request
- Response:
  - `ApiResponse<ProductAttributeResponse>`

### PUT `/api/v1/admin/product-attributes/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update. If `values` is provided, the value set is replaced. Existing values that are still in use by variants cannot be removed.
- Soft-delete behavior:
  - the target attribute must be active; a soft-deleted attribute returns `PRODUCT_ATTRIBUTE_NOT_FOUND`
  - soft-deleted values are not returned in the response payload
- Uniqueness rule:
  - attribute `code` and per-attribute `value` uniqueness still consider soft-deleted rows reserved
- Request body:
  - `UpdateProductAttributeRequest`
- Errors:
  - `PRODUCT_ATTRIBUTE_NOT_FOUND` (404)
  - `PRODUCT_ATTRIBUTE_CODE_ALREADY_EXISTS` (409)
  - `PRODUCT_ATTRIBUTE_VALUE_IN_USE` (409)
- Response:
  - `ApiResponse<ProductAttributeResponse>`

### DELETE `/api/v1/admin/product-attributes/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete the attribute and soft-delete its values. There is no hard-delete behavior for product attributes or product attribute values through this API. Variant snapshots remain intact because `variant_attribute_values` join rows are not touched.
- Response:
  - `ApiResponse<Void>`

### POST `/api/v1/admin/product-attributes/{attributeId}/values`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: add a single value under an attribute
- Uniqueness rule:
  - a value remains reserved under the same attribute even if an older row with that value was soft-deleted
- Request body:
  - `CreateProductAttributeValueRequest`
- Errors:
  - `PRODUCT_ATTRIBUTE_NOT_FOUND` (404)
  - `PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS` (409)
- Response:
  - `ApiResponse<ProductAttributeValueResponse>`

### PUT `/api/v1/admin/product-attributes/{attributeId}/values/{valueId}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: update a single attribute value
- Soft-delete behavior:
  - the attribute and value must both be active; soft-deleted rows return `PRODUCT_ATTRIBUTE_VALUE_NOT_FOUND`
- Request body:
  - `CreateProductAttributeValueRequest`
- Errors:
  - `PRODUCT_ATTRIBUTE_VALUE_NOT_FOUND` (404)
  - `PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS` (409)
- Response:
  - `ApiResponse<ProductAttributeValueResponse>`

### DELETE `/api/v1/admin/product-attributes/{attributeId}/values/{valueId}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete a single value. Rejected with `PRODUCT_ATTRIBUTE_VALUE_IN_USE` if any active variant still references it. There is no hard-delete behavior for attribute values through this API.
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
  - `isDeleted`, `includeDeleted`
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
- Soft-delete behavior:
  - soft-deleted categories are excluded by default
- Filters:
  - `name`, `slug`, `parentId`, `status`, `isDeleted`, `includeDeleted`
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`
- Response:
  - `ApiResponse<PagedResponse<CategoryResponse>>`

### POST `/api/v1/admin/categories`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: create category
- Reference behavior:
  - `parentId`, when provided, must reference an active category
- Request body:
  - `CreateCategoryRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<CategoryResponse>`

### PATCH `/api/v1/admin/categories/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: partial update category
- Soft-delete behavior:
  - a soft-deleted category returns `CATEGORY_NOT_FOUND`
- Reference behavior:
  - `parentId`, when provided, must reference an active category
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
  - `isDeleted`, `includeDeleted`
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
- Soft-delete behavior:
  - soft-deleted brands are excluded by default
- Filters:
  - `name`, `status`, `isDeleted`, `includeDeleted`
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
- Soft-delete behavior:
  - a soft-deleted brand returns `BRAND_NOT_FOUND`
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
- Description: list warehouses
- Soft-delete behavior:
  - soft-deleted warehouses are excluded by default
- Filters:
  - `status`, `isDeleted`, `includeDeleted`
- Response:
  - `ApiResponse<List<WarehouseResponse>>`

### GET `/api/v1/admin/warehouses/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get warehouse by ID
- Soft-delete behavior:
  - a soft-deleted warehouse returns `WAREHOUSE_NOT_FOUND`
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

Manages **system users only** — STAFF, ADMIN, SUPER_ADMIN. CUSTOMER accounts are
created via the public `/auth/register` flow and managed under
`/api/v1/admin/customers` (see section 17). The list, detail, create and update
endpoints all reject CUSTOMER-only accounts:

- `GET /api/v1/admin/users` and `GET /api/v1/admin/users/{id}` exclude users
  whose only role is CUSTOMER (a system-role guard is applied via `UserSpecification`).
- `POST /api/v1/admin/users` and `PATCH /api/v1/admin/users/{id}` reject any
  request whose `roles` set contains `CUSTOMER`.
- `AdminUserFilter.role` only accepts STAFF / ADMIN / SUPER_ADMIN; passing
  `CUSTOMER` returns `BAD_REQUEST`.

### DTOs

- `AdminUserFilter`
  - `keyword`: free-text, matched against `email`, `firstName`, `lastName`, `phoneNumber`
  - `email`: partial, case-insensitive
  - `phoneNumber`: partial match
  - `status`: `UserStatus` (`ACTIVE`, `INACTIVE`, `LOCKED`)
  - `role`: `RoleName`, restricted to `STAFF`, `ADMIN`, `SUPER_ADMIN` — `CUSTOMER` is rejected
  - `isDeleted`: optional — `false` = active only (default), `true` = deleted only
  - `includeDeleted`: optional — `true` returns both active and deleted rows
- `CreateUserRequest`
  - `email`: required, valid email, max 255
  - `password`: required, 8-64 chars, at least one lowercase letter, one uppercase letter, and one digit
  - `firstName`: required, max 100
  - `lastName`: optional, max 100
  - `phoneNumber`: optional, `@PhoneNumber`
  - `roles`: required non-empty set of `RoleName`, restricted to `STAFF`, `ADMIN`, `SUPER_ADMIN`
- `UpdateUserRequest`
  - all fields optional — partial update
  - `firstName`: max 100
  - `lastName`: max 100
  - `phoneNumber`: `@PhoneNumber`; uniqueness enforced server-side
  - `status`: `UserStatus`
  - `roles`: replace assigned roles. Must be non-empty when provided. `CUSTOMER` is rejected.
  - **Not updatable** here: `email`, `password`. Update these via dedicated flows.
- `UserResponse`
  - `id`, `email`, `firstName`, `lastName`, `phoneNumber`
  - `status`, `roles`, `createdAt`
  - never exposes `passwordHash` or token fields

### Access

- All endpoints in this section require `ADMIN` or `SUPER_ADMIN`.
- `STAFF` and `CUSTOMER` are rejected with `403 FORBIDDEN`.

### Pagination & sorting

- Default `page = 0`, `size = 20` (`AppConstants.DEFAULT_PAGE_SIZE`), `sort = createdAt,desc`.
- Standard `page`, `size`, `sort` query params (see [api-common.md](./api-common.md)).

### Safety rules

The following are enforced by `AdminUserService` and return `403 FORBIDDEN`:

- A caller cannot delete or deactivate themselves.
- A caller cannot remove their own `SUPER_ADMIN` or sole `ADMIN` role (would lock them out).
- The last active `SUPER_ADMIN` cannot be deleted, deactivated, locked, or have the
  `SUPER_ADMIN` role removed.

### Soft-delete behaviour

- `DELETE` performs a soft delete (`SoftDeleteEntity`) and forces `status` to `INACTIVE`.
- Deleted users are filtered by Hibernate `@SQLRestriction` and cannot authenticate
  (login lookup uses `findByEmailAndDeletedFalse`). Access tokens still cached client-side
  become unusable on next authenticated request.

### Error codes

| Code | When |
| --- | --- |
| `USER_NOT_FOUND` | id does not exist, user is soft-deleted, or the user is CUSTOMER-only (treated as not-found here) |
| `EMAIL_ALREADY_EXISTS` | create with duplicate email |
| `PHONE_ALREADY_EXISTS` | create or update with duplicate phone |
| `VALIDATION_ERROR` | `roles` provided but empty, or `roles` contains no system role |
| `BAD_REQUEST` | unknown role names supplied, `roles` contains `CUSTOMER`, or `filter.role=CUSTOMER` |
| `FORBIDDEN` | safety rule violation (self-delete, last SUPER_ADMIN, etc.) or insufficient privileges |

### GET `/api/v1/admin/users`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: list system users with filter and pagination
- Query params:
  - `AdminUserFilter` fields (see DTOs)
  - `page`, `size`, `sort`
- Response:
  - `ApiResponse<PagedResponse<UserResponse>>`

### GET `/api/v1/admin/users/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: get a single system user by id
- Soft-delete behaviour: a soft-deleted user returns `USER_NOT_FOUND`
- Response:
  - `ApiResponse<UserResponse>`

### POST `/api/v1/admin/users`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: create a system user with explicit role assignment
- Request body:
  - `CreateUserRequest`
- Current HTTP status:
  - `201 Created`
- Response:
  - `ApiResponse<UserResponse>`

### PATCH `/api/v1/admin/users/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update — only provided fields are applied
- Request body:
  - `UpdateUserRequest`
- Response:
  - `ApiResponse<UserResponse>`

### DELETE `/api/v1/admin/users/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete and deactivate the user (see soft-delete behaviour above)
- Response:
  - `ApiResponse<Void>` with `data = null`

---

## 12. Promotion

### DTOs

- `PromotionFilter`
  - `name`
  - `scope`
  - `active`
  - `dateFrom`
  - `dateTo`
  - `isDeleted`, `includeDeleted`
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
- Soft-delete behavior:
  - a soft-deleted promotion returns `PROMOTION_NOT_FOUND`
- Response:
  - `ApiResponse<PromotionResponse>`

### GET `/api/v1/admin/promotions`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: list promotions
- Soft-delete behavior:
  - soft-deleted promotions are excluded by default
- Filters:
  - `name`, `scope`, `active`, `dateFrom`, `dateTo`, `isDeleted`, `includeDeleted`
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
  - `isDeleted`, `includeDeleted`
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
- Soft-delete behavior:
  - a soft-deleted voucher returns `VOUCHER_NOT_FOUND`
- Response:
  - `ApiResponse<VoucherResponse>`

### GET `/api/v1/admin/vouchers/code/{code}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get voucher by code
- Soft-delete behavior:
  - a soft-deleted voucher returns `VOUCHER_NOT_FOUND`
- Response:
  - `ApiResponse<VoucherResponse>`

### GET `/api/v1/admin/vouchers`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list vouchers
- Soft-delete behavior:
  - soft-deleted vouchers are excluded by default
- Filters:
  - `code`, `promotionId`, `active`, `dateFrom`, `dateTo`, `isDeleted`, `includeDeleted`
- Pagination:
  - `page`, `size`, `sort`
  - controller default: `size=20`
- Response:
  - `ApiResponse<PagedResponse<VoucherResponse>>`

### GET `/api/v1/admin/vouchers/{id}/usages`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get voucher usage history
- Soft-delete behavior:
  - a soft-deleted voucher returns `VOUCHER_NOT_FOUND`
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
  - `isDeleted`, `includeDeleted`
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
- Soft-delete behavior:
  - soft-deleted reviews are excluded by default
- Filters:
  - `status`, `productId`, `customerId`, `minRating`, `maxRating`, `isDeleted`, `includeDeleted`
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
- Soft-delete behavior:
  - a soft-deleted review returns `REVIEW_NOT_FOUND`
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

---

## 17. Customer

Manages **customer profiles only**. The list, detail, update, status and delete
endpoints all query `Customer` joined with the linked `User` — they never
operate on the `users` table directly. System users (STAFF/ADMIN/SUPER_ADMIN)
are managed under `/api/v1/admin/users` (see section 11).

Customer APIs cannot assign STAFF / ADMIN / SUPER_ADMIN roles — role assignment
is intentionally outside the surface of this module.

### DTOs

- `AdminCustomerFilter`
  - `keyword`: free-text, matched against `email`, `firstName`, `lastName`, `phoneNumber` of the linked user
  - `email`: partial, case-insensitive on linked user's email
  - `phoneNumber`: partial match on linked user's phone
  - `status`: `UserStatus` (`ACTIVE`, `INACTIVE`, `LOCKED`) — applied to the linked user
  - `gender`: `Gender` (`MALE`, `FEMALE`, `OTHER`)
  - `minLoyaltyPoints`: optional, inclusive lower bound
  - `maxLoyaltyPoints`: optional, inclusive upper bound
  - `dateFrom`: optional ISO date — inclusive lower bound on `Customer.createdAt`
  - `dateTo`: optional ISO date — inclusive upper bound on `Customer.createdAt`
  - `isDeleted`: optional — `false` = active only (default), `true` = deleted only
  - `includeDeleted`: optional — `true` returns both active and deleted rows
- `AdminCustomerResponse`
  - `id` (customerId), `userId`, `email`
  - `firstName`, `lastName`, `phoneNumber`
  - `status` (from linked user), `gender`, `birthDate`, `avatarUrl`, `loyaltyPoints`
  - `createdAt`, `updatedAt` (from `Customer`)
  - never exposes the user's `passwordHash`
- `UpdateCustomerRequest` — all fields optional, partial update
  - `firstName`, `lastName`: max 100; applied to `User`
  - `phoneNumber`: `@PhoneNumber`; applied to `User`; uniqueness enforced
  - `gender`, `birthDate`: applied to `Customer`
  - `avatarUrl`: max 500; applied to `Customer`; blank string clears the avatar
  - **Not updatable** here: `email`, `password`, `status`, `roles`. Use `/status` for status, `admin/users` for system roles.
- `UpdateCustomerStatusRequest`
  - `status`: required `UserStatus` — propagated to the linked `User.status`

### Access

- `GET` endpoints: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- `PATCH` and `DELETE`: `ADMIN`, `SUPER_ADMIN`
- All endpoints require `Authorization: Bearer <accessToken>`

### Pagination & sorting

- Default `page = 0`, `size = 20` (`AppConstants.DEFAULT_PAGE_SIZE`), `sort = createdAt,desc` on `Customer.createdAt`.
- Standard `page`, `size`, `sort` query params (see [api-common.md](./api-common.md)).

### Soft-delete behaviour

- `DELETE` is a soft delete only — there is no hard-delete path.
- Both `Customer` and the linked `User` rows are marked deleted, and the user's
  status is forced to `INACTIVE` so the account cannot authenticate.
- Deleted customers are filtered out by Hibernate `@SQLRestriction`. Pass
  `includeDeleted=true` (or `isDeleted=true`) on the list endpoint to surface
  them.
- Historical orders, reviews, payments, invoices, shipments and audit log rows
  remain intact at the database level — they continue to reference the
  customer/user PKs.

### Error codes

| Code | When |
| --- | --- |
| `CUSTOMER_NOT_FOUND` | id does not exist or customer is soft-deleted |
| `USER_NOT_FOUND` | linked user is missing (defensive — should not happen) |
| `PHONE_ALREADY_EXISTS` | update with a phone number that belongs to another active user |
| `VALIDATION_ERROR` | DTO field violation (`@Size`, `@PhoneNumber`, `@NotNull` on status) |
| `BAD_REQUEST` | malformed payload |
| `FORBIDDEN` | insufficient privileges |

### GET `/api/v1/admin/customers`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: list customer profiles with filter and pagination
- Soft-delete behavior:
  - soft-deleted customers are excluded by default
- Filters:
  - `AdminCustomerFilter` fields (see DTOs)
- Pagination:
  - `page`, `size`, `sort`
  - default: `size=20`, `sort=createdAt,desc`
- Response:
  - `ApiResponse<PagedResponse<AdminCustomerResponse>>`

### GET `/api/v1/admin/customers/{id}`

- Access: `STAFF`, `ADMIN`, `SUPER_ADMIN`
- Description: get a single customer profile by id
- Soft-delete behaviour: a soft-deleted customer returns `CUSTOMER_NOT_FOUND`
- Response:
  - `ApiResponse<AdminCustomerResponse>`

### PATCH `/api/v1/admin/customers/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: partial update of customer profile fields. Updates `User` for
  `firstName` / `lastName` / `phoneNumber`, and `Customer` for `gender` /
  `birthDate` / `avatarUrl`. Roles cannot be changed here.
- Request body:
  - `UpdateCustomerRequest`
- Response:
  - `ApiResponse<AdminCustomerResponse>`

### PATCH `/api/v1/admin/customers/{id}/status`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: change the customer's account status. The status is applied to
  the linked `User.status`; `INACTIVE` and `LOCKED` immediately prevent login.
- Request body:
  - `UpdateCustomerStatusRequest`
- Response:
  - `ApiResponse<AdminCustomerResponse>`

### DELETE `/api/v1/admin/customers/{id}`

- Access: `ADMIN`, `SUPER_ADMIN`
- Description: soft-delete the customer profile and the linked user account
  (see soft-delete behaviour above)
- Response:
  - `ApiResponse<Void>` with `data = null`

