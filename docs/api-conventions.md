# API Conventions

Tài liệu mô tả quy ước thiết kế API cho toàn bộ backend.

---

## 1. Mục tiêu

Mục tiêu của tài liệu này là đảm bảo toàn bộ API:
- nhất quán
- dễ dùng cho frontend/mobile
- dễ bảo trì
- dễ mở rộng version
- dễ debug khi tích hợp

---

## 2. Base URL

Tất cả API phải có prefix version:

```text
/api/v1
```

Ví dụ:

- `/api/v1/auth/login`
- `/api/v1/products`
- `/api/v1/orders`

---

## 3. Naming endpoint

### 3.1. Dùng danh từ số nhiều

Ưu tiên:

- `/products`
- `/orders`
- `/customers`
- `/vouchers`

### 3.2. Không dùng động từ trong path nếu không cần

**Tốt:**

- `POST /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `PATCH /api/v1/orders/{id}/status`

**Không tốt:**

- `/createOrder`
- `/getAllProducts`
- `/updateUserInfo`

### 3.3. Sub-resource rõ nghĩa

Ví dụ:

- `GET /api/v1/products/{id}/variants`
- `POST /api/v1/orders/{id}/cancel`
- `PATCH /api/v1/orders/{id}/status`

---

## 4. Response format chuẩn

### 4.1. Success response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {},
  "timestamp": "2026-04-06T10:00:00Z"
}
```

### 4.2. Error response

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Email is invalid"
    }
  ],
  "timestamp": "2026-04-06T10:00:00Z",
  "path": "/api/v1/auth/register"
}
```

### 4.3. Empty data response

Nếu endpoint không cần trả object cụ thể:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": null,
  "timestamp": "2026-04-06T10:00:00Z"
}
```

---

## 5. HTTP status code conventions

**Thành công**

- `200 OK`: lấy dữ liệu / update thành công
- `201 Created`: tạo mới thành công
- `204 No Content`: xử lý thành công nhưng không có body

**Lỗi client**

- `400 Bad Request`: request sai format hoặc thiếu param cơ bản
- `401 Unauthorized`: chưa xác thực
- `403 Forbidden`: không có quyền
- `404 Not Found`: không tìm thấy tài nguyên
- `409 Conflict`: trùng dữ liệu hoặc trạng thái conflict
- `422 Unprocessable Entity`: business validation fail

**Lỗi server**

- `500 Internal Server Error`
- `502 Bad Gateway`
- `503 Service Unavailable`

---

## 6. Business error codes

Nên chuẩn hóa code trong error response.

**Nhóm chung**
- `SUCCESS`
- `BAD_REQUEST`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `CONFLICT`
- `INTERNAL_SERVER_ERROR`

**Nhóm auth**
- `INVALID_CREDENTIALS`
- `TOKEN_EXPIRED`
- `TOKEN_INVALID`
- `REFRESH_TOKEN_INVALID`
- `ACCOUNT_DISABLED`

**Nhóm product**
- `PRODUCT_NOT_FOUND`
- `PRODUCT_VARIANT_NOT_FOUND`
- `PRODUCT_INACTIVE`
- `SKU_ALREADY_EXISTS`

**Nhóm inventory**
- `INVENTORY_NOT_ENOUGH`
- `VARIANT_OUT_OF_STOCK`
- `STOCK_RESERVATION_FAILED`

**Nhóm order**
- `ORDER_NOT_FOUND`
- `ORDER_STATUS_INVALID`
- `ORDER_CANNOT_CANCEL`
- `ORDER_CANNOT_COMPLETE`

**Nhóm payment**
- `PAYMENT_NOT_FOUND`
- `PAYMENT_FAILED`
- `PAYMENT_ALREADY_PROCESSED`
- `PAYMENT_CALLBACK_INVALID`

**Nhóm promotion**
- `VOUCHER_INVALID`
- `VOUCHER_EXPIRED`
- `VOUCHER_USAGE_LIMIT_EXCEEDED`
- `VOUCHER_NOT_APPLICABLE`

---

## 7. Pagination convention

### 7.1. Query params chuẩn

Tất cả list API nên hỗ trợ:

- `page`
- `size`
- `sort`

Ví dụ:
```
GET /api/v1/products?page=0&size=20&sort=createdAt,desc
```

### 7.2. Paginated response chuẩn

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

### 7.3. Default page size

Khuyến nghị:
- default `page=0`
- default `size=20`
- max `size=100`

---

## 8. Sorting convention

### 8.1. Format

```
sort=field,direction
```

Ví dụ:
- `sort=createdAt,desc`
- `sort=price,asc`

### 8.2. Nhiều sort

Có thể hỗ trợ:
```
sort=price,asc&sort=createdAt,desc
```

### 8.3. Field hợp lệ

Chỉ cho sort trên whitelist field hợp lệ. Không cho sort tùy ý mọi field.

---

## 9. Filtering convention

### 9.1. Product filters

Ví dụ:
```
GET /api/v1/products?keyword=shirt&categoryId=1&brandId=2&minPrice=100000&maxPrice=500000&inStock=true
```

Các filter gợi ý: `keyword`, `categoryId`, `brandId`, `status`, `minPrice`, `maxPrice`, `color`, `size`, `inStock`, `isFeatured`

### 9.2. Order filters

Ví dụ:
```
GET /api/v1/orders?status=CONFIRMED&paymentStatus=PAID&fromDate=2026-04-01&toDate=2026-04-30
```

Các filter gợi ý: `orderCode`, `customerId`, `status`, `paymentStatus`, `shipmentStatus`, `fromDate`, `toDate`

### 9.3. Customer filters

- `keyword`
- `email`
- `phone`
- `status`
- `fromDate`
- `toDate`

---

## 10. Auth header convention

Dùng Bearer token:

```
Authorization: Bearer <access_token>
```

### 10.1. Refresh token

Có thể dùng:
- request body
- httpOnly cookie

**Khuyến nghị production:**
- access token ngắn hạn
- refresh token dài hạn
- refresh token rotation

---

## 11. Idempotency convention

Áp dụng cho các endpoint dễ bị gọi lặp:
- payment callback
- create order từ external request
- create payment request
- refund request

### 11.1. Cách xử lý

- idempotency key
- unique business code
- external transaction reference
- unique constraint ở database

---

## 12. Validation convention

### 12.1. DTO validation

Request DTO phải validate bằng annotation:
`@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Positive`, `@Min`, `@Max`, custom validator nếu cần.

### 12.2. Business validation

Bắt buộc kiểm tra thêm ở service:
- variant có tồn tại không
- quantity có hợp lệ không
- tồn kho có đủ không
- voucher còn hiệu lực không
- trạng thái order có cho phép chuyển không

### 12.3. Validation error format

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": [
    {
      "field": "quantity",
      "message": "quantity must be greater than 0"
    },
    {
      "field": "variantId",
      "message": "variantId is required"
    }
  ],
  "timestamp": "2026-04-06T10:00:00Z",
  "path": "/api/v1/cart/items"
}
```

---

## 13. Date time convention

### 13.1. Backend timezone

Khuyến nghị backend thống nhất dùng **UTC** cho timestamp hệ thống.

### 13.2. Response format

Dùng **ISO-8601**:
```json
{
  "createdAt": "2026-04-06T10:00:00Z"
}
```

### 13.3. Date-only fields

Các field chỉ có ngày (`birthDate`, `startDate`, `endDate`) nên dùng:
```json
{
  "birthDate": "2026-04-06"
}
```

### 13.4. Không dùng format tùy biến nếu không cần

Tránh: `06/04/2026`, `04-06-2026`

---

## 14. Versioning convention

### 14.1. Version theo URL

```
/api/v1
/api/v2
```

### 14.2. Khi nào tạo version mới

Tạo v2 nếu có **breaking change**:
- đổi contract response/request
- bỏ field cũ
- thay đổi semantics endpoint

### 14.3. Non-breaking changes

Có thể giữ nguyên version nếu:
- thêm field response không ảnh hưởng client cũ
- thêm endpoint mới
- thêm optional param

---

## 15. REST method convention

- **GET**: Lấy dữ liệu
- **POST**: Tạo mới hoặc thao tác không thuần CRUD
- **PUT**: Thay thế toàn bộ tài nguyên (ít dùng)
- **PATCH**: Update một phần
- **DELETE**: Xóa hoặc soft delete

---

## 16. File upload convention

### 16.1. Request type

Dùng `multipart/form-data`

### 16.2. Response ví dụ

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "File uploaded successfully",
  "data": {
    "fileName": "shirt-black-front.jpg",
    "fileUrl": "https://cdn.example.com/products/shirt-black-front.jpg",
    "contentType": "image/jpeg",
    "size": 183927
  },
  "timestamp": "2026-04-06T10:00:00Z"
}
```

### 16.3. Security

- kiểm tra content type
- kiểm tra kích thước file
- sanitize filename
- không cho upload file thực thi nguy hiểm

---

## 17. Error handling convention

### 17.1. Global exception handler

Tất cả exception phải đi qua handler chung.

### 17.2. Không trả stacktrace ở production

Không được lộ:
- stacktrace
- SQL query
- internal server path chi tiết nhạy cảm

### 17.3. Log đầy đủ nội bộ

Phía server vẫn phải log đủ để debug.

---

## 18. API cho order status transition

Không nên dùng endpoint chung chung kiểu `PATCH /api/v1/orders/{id}` cho các trạng thái quan trọng.

Nên cân nhắc:

- `POST /api/v1/orders/{id}/confirm`
- `POST /api/v1/orders/{id}/cancel`
- `POST /api/v1/orders/{id}/complete`

Hoặc vẫn dùng `PATCH /api/v1/orders/{id}/status` nhưng phải validate **state machine** chặt chẽ.

---

## 19. Swagger/OpenAPI convention

Mỗi endpoint cần có:
- `summary`
- `description`
- request schema
- response schema
- auth requirement
- possible error cases

---

## 20. Search convention

Khuyến nghị phase đầu:
- Dùng chung với list endpoint + filter `keyword`
- Ví dụ: `GET /api/v1/products?keyword=ao+thun`

---

## 21. Security convention cho admin API

**Prefix:**
```
/api/v1/admin/...
```

Yêu cầu:
- role `ADMIN` hoặc `STAFF`
- audit log cho các thao tác nhạy cảm

---

## 22. Public vs protected API

**Public API:**
- auth register/login
- product listing, product detail
- category listing, brand listing

**Protected customer API:**
- cart, address, checkout, order history, review

**Protected admin API:**
- product management, inventory management, order management, voucher management, reporting

---

## 23. Example endpoint groups

**Auth**
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh-token`
- `POST /api/v1/auth/logout`

**Products**
- `GET /api/v1/products`
- `GET /api/v1/products/{id}`
- `GET /api/v1/products/{id}/variants`

**Cart**
- `GET /api/v1/cart`
- `POST /api/v1/cart/items`
- `PATCH /api/v1/cart/items/{id}`
- `DELETE /api/v1/cart/items/{id}`

**Orders**
- `POST /api/v1/orders`
- `GET /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `POST /api/v1/orders/{id}/cancel`

**Admin**
- `POST /api/v1/admin/products`
- `PATCH /api/v1/admin/products/{id}`
- `PATCH /api/v1/admin/orders/{id}/status`
- `POST /api/v1/admin/inventories/adjust`

---

## 24. Contract stability rule

Khi frontend/mobile đã dùng API:
- Không đổi field name tùy tiện
- Không đổi meaning của field
- Không bỏ field nếu chưa có deprecation plan
- Mọi thay đổi breaking phải version hóa rõ ràng

---

## 25. List API — Filter, Pageable, and Naming Rules

Áp dụng cho **tất cả** endpoint trả danh sách phân trang.

### 25.1. Tất cả list API phải hỗ trợ filter

Mọi `GET` endpoint trả `PagedResponse<T>` phải nhận một Filter DTO làm query parameter.

```java
// Controller signature chuẩn
@GetMapping
public ApiResponse<PagedResponse<XxxResponse>> getXxxs(
        XxxFilter filter,
        @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
    return ApiResponse.success(xxxService.getXxxs(filter, pageable));
}
```

### 25.2. Pageable chuẩn — dùng Spring Web

**Bắt buộc** dùng `@PageableDefault` thay cho `@RequestParam int page/size/sort/direction` thủ công.

```java
// Đúng
@PageableDefault(size = 20, sort = "createdAt") Pageable pageable

// Sai — KHÔNG làm
@RequestParam(defaultValue = "0") int page,
@RequestParam(defaultValue = "20") int size,
@RequestParam(defaultValue = "createdAt") String sort,
@RequestParam(defaultValue = "desc") String direction
```

Client dùng cú pháp Spring chuẩn:
```
GET /api/v1/admin/invoices?sort=issuedAt,desc&page=0&size=20
```

### 25.3. Sử dụng JPA Specification cho JPA modules

Nếu repository extends `JpaSpecificationExecutor<T>`, phải tạo `XxxSpecification` để xử lý filter.

```java
// Specification pattern chuẩn
public class XxxSpecification {
    private XxxSpecification() {}

    public static Specification<Xxx> withFilter(XxxFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // add predicates based on non-null filter fields
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

**Không dùng Specification cho JDBC/native query repositories** — giữ nguyên query method.

### 25.4. Naming convention cho service methods

Phương thức service trả danh sách phân trang **phải** đặt tên theo pattern `get{Domain}s(filter, pageable)`:

| Không tốt | Tốt |
|-----------|-----|
| `listInvoices()` | `getInvoices()` |
| `listShipments()` | `getShipments()` |
| `listPromotions()` | `getPromotions()` |
| `listVouchers()` | `getVouchers()` |
| `listPayments()` | `getPayments()` |
| `getAll()` | `getProducts()` |

### 25.5. Controller method naming

```java
// Đúng
public ApiResponse<PagedResponse<OrderResponse>> getOrders(...)
public ApiResponse<PagedResponse<InvoiceResponse>> getInvoices(...)

// Sai
public ApiResponse<PagedResponse<OrderResponse>> list(...)
public ApiResponse<PagedResponse<InvoiceResponse>> listInvoices(...)
```

### 25.6. Các modules đã áp dụng Specification

| Module | Filter DTO | Specification |
|--------|-----------|---------------|
| product | `ProductFilter` | `ProductSpecification` |
| inventory | `InventoryFilter` | `InventorySpecification` |
| invoice | `InvoiceFilter` | `InvoiceSpecification` |
| payment | `PaymentFilter` | `PaymentSpecification` |
| shipment | `ShipmentFilter` | `ShipmentSpecification` |
| promotion | `PromotionFilter` | `PromotionSpecification` |
| voucher | `VoucherFilter` | `VoucherSpecification` |
| review | `ReviewFilter` | `ReviewSpecification` |

### 25.7. Modules không dùng Specification (JPQL custom query)

| Module | Lý do |
|--------|-------|
| order (customer) | Dùng `@Query` JPQL với JOIN FETCH — không thay đổi repository |
| order (admin) | Dùng `@Query` JPQL với JOIN FETCH customer + user |
| stock movement | Dùng `@Query` JPQL |

---
