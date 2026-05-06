# CLAUDE.md

## 1. Project Overview

Đây là backend cho hệ thống **Web bán quần áo đa nền tảng**, phục vụ:

- **Admin Web**: ReactJS
- **Customer Web**: ReactJS
- **Mobile App**: React Native
- **Backend API**: Java Spring Boot (triển khai trước)

Kiến trúc hiện tại ưu tiên theo hướng **Modular Monolith**, dễ phát triển nhanh trong giai đoạn đầu, nhưng vẫn có cấu trúc đủ tốt để tách module hoặc chuyển hướng microservices sau này nếu hệ thống tăng trưởng mạnh.

Project tập trung vào các nghiệp vụ cốt lõi của e-commerce thời trang:

- Authentication / Authorization
- User / Customer
- Product / Category / Brand / Collection
- Product Variant (size, màu, SKU)
- Inventory
- Cart
- Wishlist
- Order / OrderItem
- Payment / PaymentTransaction
- Promotion / Voucher / Coupon
- Shipment / Invoice
- Review / Rating
- Notification
- Admin management

---

## 2. Business Goal

Mục tiêu của hệ thống:

1. Cung cấp API ổn định cho Admin Web, Customer Web và Mobile App.
2. Hỗ trợ bán sản phẩm thời trang có **biến thể** như:
   - màu sắc
   - kích thước
   - chất liệu (nếu có)
3. Quản lý tồn kho theo từng biến thể.
4. Hỗ trợ giỏ hàng, đặt hàng, thanh toán, voucher, khuyến mãi.
5. Dễ mở rộng về sau:
   - nhiều kho
   - nhiều cổng thanh toán
   - nhiều chương trình khuyến mãi
   - loyalty / tích điểm
   - recommendation
   - report / analytics

---

## 3. Tech Stack

### Backend
- Java 17 hoặc 21
- Spring Boot 3.x
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- Spring Cache
- OpenAPI / Swagger
- Flyway

### Database
- MariaDB là database chính
- Redis cho cache / token / rate limit / OTP / dữ liệu tạm
- MongoDB **chưa bắt buộc** ở phase đầu

### Build Tool
- Maven

### Other
- Lombok
- MapStruct
- Docker / Docker Compose
- MinIO hoặc S3-compatible storage cho media sau này

---

## 4. Project Architecture Principles

### 4.1. Architecture style
Project theo hướng **Modular Monolith**:

- chung một codebase
- chung một database chính
- chia rõ domain/module
- hạn chế coupling trực tiếp giữa các module

### 4.2. Package structure guideline

```text
src/main/java/com/fit/fashion_shop
│
├── common
│   ├── config
│   ├── exception
│   ├── response
│   ├── security
│   ├── utils
│   ├── constants
│   ├── enums
│   ├── mapper
│   ├── validation
│   └── auditing
│
├── domains
│   ├── auth
│   ├── user
│   ├── customer
│   ├── address
│   ├── category
│   ├── brand
│   ├── product
│   ├── product_variant
│   ├── collection
│   ├── inventory
│   ├── cart
│   ├── wishlist
│   ├── order
│   ├── payment
│   ├── promotion
│   ├── shipment
│   ├── invoice
│   ├── review
│   ├── notification
│   └── admin
│
├── infrastructure
│   ├── external
│   ├── storage
│   ├── cache
│   └── messaging
│
└── FashionShopApplication.java
```

### 4.3. Layering trong mỗi module
Mỗi module nên có tối thiểu:

- controller
- service
- dto
- entity
- repository
- mapper
- specification hoặc query
- converter nếu cần
- enum nếu module có enum riêng

Ví dụ:

```text
domains/product
├── controller
├── service
├── dto
├── entity
├── repository
├── mapper
├── specification
└── enum
```

---

## 5. Core Business Modeling Rules

### 5.1. Product vs Variant
- **Product** là thực thể cha, mang ý nghĩa sản phẩm bán ra
- **ProductVariant** là đơn vị bán thực tế

Ví dụ:
- Product: Áo thun basic nam
- Variant 1: màu trắng, size M
- Variant 2: màu trắng, size L
- Variant 3: màu đen, size M

### 5.2. Inventory tracking
Tồn kho phải được quản lý theo variant, không theo product cha.

### 5.3. Order item snapshot
OrderItem phải lưu snapshot dữ liệu tại thời điểm đặt hàng:
- product name
- variant name
- sku
- unit price
- quantity
- line total
- discount amount

Không phụ thuộc hoàn toàn vào dữ liệu product hiện tại.

### 5.4. Order address snapshot
Order phải lưu snapshot địa chỉ giao hàng tại thời điểm đặt hàng:
- recipient_name
- recipient_phone
- full address (street, ward, district, province)

Không lưu foreign key trỏ về bảng `addresses` vì customer có thể sửa/xóa địa chỉ sau khi đặt hàng.

### 5.5. Cart item price
`CartItem` **không** lưu giá cố định. Giá phải được tính lại từ `ProductVariant` tại thời điểm checkout.  
Nếu cần hiển thị giá trong giỏ, lấy từ variant hiện tại, không cache trong cart_items.

### 5.6. Payment design
Không chỉ có bảng `payments`. Bắt buộc nên có:
- `payments`
- `payment_transactions`

### 5.7. Promotion design
Phân biệt:
- **Promotion**: chương trình khuyến mãi
- **Voucher**: mã giảm giá
- **PromotionRule**: điều kiện áp dụng
- **VoucherUsage**: lịch sử sử dụng

### 5.8. Soft delete
Áp dụng soft delete cho các bảng cấu hình / dữ liệu quản trị:
- users
- products
- categories
- brands
- vouchers
- banners

Không lạm dụng soft delete cho các bảng log lớn.

### 5.9. Optimistic locking cho inventory
Bảng `inventories` phải có cột `version` (`@Version` của JPA) để tránh oversell khi concurrent requests.

---

## 6. Domain List

### Identity & Access
- auth
- users
- roles
- permissions
- refresh tokens

### Catalog
- categories
- brands
- products
- product_variants
- product_media
- product_attributes
- product_attribute_values
- collections
- collection_products

### Commerce
- carts
- cart_items
- wishlists
- wishlist_items
- orders
- order_items
- shipments
- invoices

### Inventory
- warehouses
- inventories
- stock_movements
- inventory_reservations

### Payment
- payments
- payment_transactions
- refunds

### Promotion
- promotions
- promotion_rules
- vouchers
- voucher_usages

### Engagement
- reviews
- notifications
- banners
- cms_pages

### Administration
- admin users
- audit logs
- dashboard/report queries

---

## 7. Coding Principles

### 7.1. General
- Code phải rõ ràng, dễ đọc, dễ bảo trì
- Ưu tiên naming chính xác hơn code ngắn
- Không viết business logic trực tiếp trong controller
- Không expose entity trực tiếp ra API

### 7.2. Controller
Controller chỉ nên:
- nhận request
- validate input
- gọi service
- trả response chuẩn

Không chứa business logic phức tạp.

### 7.3. Service
Service là nơi xử lý nghiệp vụ chính:
- validate business
- orchestration
- transaction boundary
- phối hợp nhiều repository/module

### 7.4. Repository
Repository chỉ truy cập dữ liệu. Không chứa business logic nặng.

### 7.5. DTO
Phân tách rõ:
- request DTO
- response DTO
- internal DTO nếu cần

### 7.6. Mapper
Dùng MapStruct hoặc mapper rõ ràng. Không nhét mapping lằng nhằng trong controller/service.

---

## 8. Validation Rules

### 8.1. DTO validation
Luôn validate ở request DTO bằng:
- `@NotNull`
- `@NotBlank`
- `@Size`
- `@Email`
- `@Positive`
- `@Min`
- `@Max`

### 8.2. Business validation
Ngoài annotation, business rule phải kiểm tra trong service:
- quantity không vượt tồn kho khả dụng
- voucher còn hạn
- salePrice không lớn hơn basePrice
- order không được cancel ở trạng thái không hợp lệ
- payment callback không được xử lý lặp

---

## 9. API Design Rules

### 9.1. Base path
Tất cả API dùng prefix:
```
/api/v1
```

### 9.2. Response wrapper
Tất cả response dùng chuẩn thống nhất:
- `success`
- `code`
- `message`
- `data`
- `timestamp`
- `path` (đối với lỗi)

### 9.3. Pagination
List API phải hỗ trợ:
- `page`
- `size`
- `sort`

### 9.4. Versioning
Breaking change phải tạo version mới:
- `/api/v2/...`

### 9.5. Auth
Dùng Bearer JWT:
```
Authorization: Bearer <token>
```

### 9.6. Payment callback / Webhook
Endpoint nhận callback từ payment gateway phải:
- có prefix riêng: `/api/v1/webhooks/payment/{provider}`
- validate chữ ký (signature/HMAC) trước khi xử lý
- trả `200 OK` nhanh, xử lý nghiệp vụ async hoặc sau đó
- idempotent: không xử lý lặp cùng transaction

---

## 10. Error Handling Rules

Dùng global exception handling.

Phân loại lỗi:
- validation error
- auth error
- permission error
- not found
- conflict
- business rule violation
- internal server error

Ví dụ business errors:
- `PRODUCT_NOT_FOUND`
- `VARIANT_NOT_FOUND`
- `INVENTORY_NOT_ENOUGH`
- `VOUCHER_INVALID`
- `ORDER_STATUS_INVALID`
- `PAYMENT_FAILED`

---

## 11. Database Rules

### 11.1. Database chính
MariaDB là nguồn dữ liệu chính.

### 11.2. Migration
Mọi thay đổi schema phải đi qua Flyway migration.  
Không dùng `ddl-auto=create` hoặc `ddl-auto=update` trên production.

### 11.3. Money
Tất cả cột tiền tệ dùng:
```sql
DECIMAL(18,2)
```

### 11.4. Key strategy
Khuyến nghị:
- `BIGINT` auto increment cho PK nội bộ
- `code` business riêng cho hiển thị công khai

Ví dụ:
- `ORD202604060001`
- `INV202604060010`

### 11.5. Foreign key
Phải có foreign key cho dữ liệu lõi. Không cascade delete bừa bãi.

### 11.6. Optimistic locking
Bảng có khả năng concurrent update cao phải có `version BIGINT`:
- `inventories`

---

## 12. Caching Rules

### 12.1. Dùng Redis làm cache layer
Spring Cache (`@Cacheable`, `@CacheEvict`) kết hợp Redis.

### 12.2. Những gì nên cache
- danh sách category (ít thay đổi, đọc nhiều)
- danh sách brand (ít thay đổi, đọc nhiều)
- product detail (cache ngắn, evict khi update)
- banner/homepage config

### 12.3. Những gì không nên cache
- cart (mỗi user khác nhau, thay đổi thường xuyên)
- order (trạng thái thay đổi liên tục)
- inventory (số lượng thay đổi liên tục, không cache giá trị tồn kho)

### 12.4. TTL
Định nghĩa TTL rõ ràng cho từng cache key. Không để TTL vô hạn.

### 12.5. Evict
Khi admin cập nhật product/category/brand phải evict cache liên quan ngay lập tức.

---

## 13. Security Principles
- JWT access token ngắn hạn
- refresh token dài hạn
- password hash bằng BCrypt hoặc Argon2
- role-based access control
- CORS cấu hình rõ theo môi trường
- validate input nghiêm ngặt
- hạn chế upload file nguy hiểm
- không log thông tin nhạy cảm
- không trả stacktrace ra ngoài API production
- OTP lưu trong Redis với TTL, không trong DB
- rate limit cho auth endpoint, payment endpoint

---

### 13.1. Current Auth Implementation

- `POST /api/v1/auth/register` and `POST /api/v1/auth/login` return only the access token in the JSON response body and set the refresh token in an HttpOnly cookie.
- `POST /api/v1/auth/refresh-token` reads the refresh token from the cookie and still accepts a deprecated request-body fallback.
- Redis is used for both access-token blacklist on logout and refresh-session storage with TTL.
- Refresh sessions store a SHA-256 token hash, not the raw refresh token.
- Logout revokes the refresh session, clears the cookie, and blacklists the presented access token when valid.
- There is no implemented password-change/reset endpoint yet, but `AuthService.revokeAllRefreshSessions(...)` is available as the integration point.

### 13.2. Recommended Target

- access token in response body
- refresh token only in `HttpOnly` + `Secure` + `SameSite` cookie
- refresh session stored server-side in Redis with TTL, or in DB if Redis session storage is not ready
- server stores token hash, not raw refresh token
- logout revokes refresh session and clears cookie
- password change revokes all active refresh sessions

---

## 14. Environment Profiles

Có 2 profile chính:
- `dev`
- `prod`

**dev**:
- log chi tiết hơn
- swagger enabled
- có thể seed data
- có thể bật SQL log khi cần

**prod**:
- log an toàn
- swagger có thể restricted
- secrets lấy từ env / secret manager
- tối ưu connection pool / cache / rate limit

---

## 15. Logging & Audit

### 15.1. Logging
Cần log đủ các nhóm:
- request id / trace id
- auth failures
- payment callback
- inventory changes
- order status changes
- admin critical actions

### 15.2. Audit log
Những hành động admin quan trọng cần audit:
- tạo/sửa/xóa product
- chỉnh tồn kho
- đổi trạng thái đơn
- tạo/sửa voucher
- hoàn tiền
- khóa user

---

## 16. Testing Strategy

### 16.1. Unit test
Ưu tiên cho:
- service business logic
- validator
- promotion calculation
- inventory check
- order lifecycle transitions

### 16.2. Integration test
Ưu tiên cho:
- repository queries
- API auth flow
- create order flow
- payment callback flow
- apply voucher flow

### 16.3. Test data
Có thể chuẩn bị seed data cho:
- roles
- admin account
- categories cơ bản
- brands cơ bản
- sample products

---

## 17. Branching & Commit Conventions

### Branch types
- `dev`: branch chính của team
- `feat/`: feature mới
- `bugfix/`: sửa lỗi
- `hotfix/`: fix gấp production
- `release/`: phát hành
- `refactor/`: tái cấu trúc

### Branch naming convention
```
{type}/{task_id}_{title}
```

Ví dụ:
- `feat/1001_product_variant_management`
- `bugfix/1042_fix_order_total_calculation`
- `refactor/1088_cleanup_auth_module`

### Commit message convention
```
{type}({module}): {title}

{description of the change}

{Fixes/Complete #issue_number}
```

Ví dụ:
```
feat(product): add product variant creation API

Implement create variant flow with size/color combination validation
Add DTO, service, repository, mapper and controller endpoint

Complete #1001
```

---

## 18. Development Priorities

### Phase 1 - MVP Backend
- auth
- user/customer
- address
- category
- brand
- product
- product variant
- inventory
- cart
- order
- order item
- admin basic

### Phase 2
- payment gateway
- shipment
- invoice
- voucher
- promotion
- review
- wishlist
- collection

### Phase 3
- notification
- cms/banner
- dashboard/report
- loyalty
- recommendation

---

## 19. AI Collaboration Instructions

Khi AI hỗ trợ code trong project này, cần tuân thủ:

1. Không tự ý thay đổi kiến trúc chung nếu chưa có lý do rõ ràng.
2. Không thêm dependency nặng nếu không thật sự cần.
3. Không sửa migration cũ đã dùng ở shared environment.
4. Luôn ưu tiên giữ tính nhất quán package, naming, response format.
5. Khi tạo API mới phải đồng thời:
    - tạo DTO request/response
    - service
    - repository nếu cần
    - controller
    - validation
    - OpenAPI annotation
6. Khi tạo entity mới phải nghĩ tới:
    - audit fields
    - soft delete
    - index
    - unique constraints
    - foreign keys
    - version (nếu entity có concurrent update)
7. Khi sửa business flow quan trọng phải kiểm tra ảnh hưởng:
    - order total
    - inventory
    - payment status
    - voucher usage
8. Không viết code "tạm chạy được"; phải ưu tiên code rõ trách nhiệm, dễ mở rộng.

---

## 20. What AI should do when implementing a feature

Khi triển khai một tính năng mới, AI nên làm theo trình tự:

1. Hiểu domain và business rule
2. Xác định module liên quan
3. Xác định entity / DTO / API cần thêm
4. Xác định migration cần thêm
5. Xác định validation và error cases
6. Xác định test cases quan trọng
7. Implement theo từng lớp rõ ràng
8. Review ảnh hưởng tới module khác
9. Viết hoặc cập nhật docs nếu cần

---

## 21. Out of Scope for Early Phase

Những phần chưa ưu tiên ở giai đoạn đầu:
- microservices
- event sourcing
- CQRS đầy đủ
- real-time recommendation engine
- complex search engine
- multi-tenant
- multi-currency phức tạp
- international tax engine

Có thể bổ sung sau nếu business thực sự cần.
