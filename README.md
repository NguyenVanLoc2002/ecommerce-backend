# Fashion Shop Backend

Backend cho hệ thống bán quần áo đa nền tảng, phục vụ:

- Admin Web (ReactJS)
- Mobile App
- Hệ thống quản trị nội bộ trong tương lai

Project được xây dựng theo hướng **Modular Monolith** với **Java Spring Boot + MariaDB**, ưu tiên tốc độ phát triển giai đoạn đầu nhưng vẫn đảm bảo khả năng mở rộng về sau.

---

## 1. Project là gì

Đây là backend API cho một hệ thống thương mại điện tử thời trang, tập trung vào các nghiệp vụ:

- Đăng ký / đăng nhập / phân quyền
- Quản lý người dùng và khách hàng
- Quản lý danh mục sản phẩm
- Quản lý sản phẩm và biến thể
- Quản lý tồn kho theo biến thể
- Giỏ hàng
- Đơn hàng và chi tiết đơn hàng
- Thanh toán
- Voucher / promotion / coupon
- Giao hàng
- Hóa đơn
- Đánh giá sản phẩm
- Notification
- Admin dashboard và vận hành

---

## 2. Tech Stack

### Backend
- Java 17 hoặc 21
- Spring Boot 3.x
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- Spring Cache

### Database / Infra
- MariaDB
- Redis
- Flyway
- Docker / Docker Compose

### API Docs
- springdoc OpenAPI
- Swagger UI

### Utilities
- Lombok
- MapStruct

---

## 3. Kiến trúc tổng thể

Dự án áp dụng **Modular Monolith**.

### Nguyên tắc:
- Một codebase
- Một backend service chính
- Một database chính
- Chia rõ domain/module
- Hạn chế phụ thuộc chéo trực tiếp giữa các module

### Domain chính:
- auth
- user
- customer
- address
- category
- brand
- product
- product_variant
- inventory
- cart
- order
- payment
- promotion
- shipment
- invoice
- review
- notification
- admin

---

## 4. Cấu trúc thư mục dự kiến

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
│   ├── inventory
│   ├── cart
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

---

## 5. Chạy local thế nào

### 5.1. Yêu cầu cài đặt

Máy local cần có:

- JDK 17 hoặc 21
- Maven
- MariaDB
- Redis
- Git
- Docker (khuyến nghị)

### 5.2. Clone project

```bash
git clone <repository-url>
cd fashion-shop-backend
```

### 5.3. Tạo database

Ví dụ MariaDB:

```sql
CREATE DATABASE fashion_shop_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE fashion_shop_prod CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 5.4. Tạo file cấu hình môi trường

Project dùng profile Spring:

- dev
- prod

Các file cấu hình gợi ý:
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

### 5.5. Chạy local với Maven

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 6. Env cần gì

Dưới đây là cấu hình mẫu.

**application.properties**

```properties
# SERVER
server.port=8080

# APP
spring.application.name=fashion-shop-backend
spring.profiles.active=dev

# DATASOURCE
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver

# JPA
spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.format_sql=true

# FLYWAY
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# SWAGGER
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true

# LOGGING
logging.level.root=INFO
```

**application-dev.properties**

```properties
# SERVER
server.port=8080

# DATABASE
spring.datasource.url=jdbc:mariadb://localhost:3306/fashion_shop_dev
spring.datasource.username=root
spring.datasource.password=your_password

# REDIS
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JPA
spring.jpa.show-sql=true

# LOGGING
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.springframework.security=INFO

# CORS (list dạng CSV)
app.cors.allowed-origins=http://localhost:3000,http://localhost:5173,http://localhost:8081

# JWT
security.jwt.secret-key=your_dev_secret_key
security.jwt.access-token-expiration-ms=3600000
security.jwt.refresh-token-expiration-ms=2592000000
```

**application-prod.properties**

```properties
# SERVER
server.port=8080

# DATABASE (ENV VARIABLE)
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# REDIS
spring.data.redis.host=${REDIS_HOST}
spring.data.redis.port=${REDIS_PORT}

# JPA
spring.jpa.show-sql=false

# LOGGING
logging.level.root=INFO

# CORS
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}

# JWT
security.jwt.secret-key=${JWT_SECRET_KEY}
security.jwt.access-token-expiration-ms=3600000
security.jwt.refresh-token-expiration-ms=2592000000
```

---

## 7. DB thế nào

### MariaDB
MariaDB là database chính dùng cho toàn bộ transactional data:
- users, roles, customers, addresses
- categories, brands, products, product_variants
- inventories, carts, cart_items
- orders, order_items
- payments, payment_transactions
- promotions, vouchers, reviews

### Redis
Redis dùng cho:
- cache dữ liệu đọc nhiều
- token blacklist
- OTP / verification
- rate limit
- cart/session tạm thời nếu cần
- dữ liệu tính toán ngắn hạn

### MongoDB
Chưa bắt buộc trong phase đầu. Chỉ cân nhắc khi có nhu cầu:
- event log lớn
- search document riêng
- analytics phi cấu trúc
- recommendation data

---

## 8. Port nào

Mặc định local:

- **Backend API**: 8080
- **MariaDB**: 3306
- **Redis**: 6379

Ví dụ:
- Backend: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI docs: http://localhost:8080/v3/api-docs

---

## 9. Swagger ở đâu

Sau khi backend chạy local:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

---

## 10. Tài liệu trong project

- `CLAUDE.md`: mô tả project cho người và AI
- `docs/domain-overview.md`: mô tả domain và nghiệp vụ
- `docs/api-conventions.md`: quy ước API
- `docs/database-guidelines.md`: quy tắc database

**Khuyến nghị thêm:**
- `docs/security.md`
- `docs/order-lifecycle.md`
- `docs/inventory-lifecycle.md`
- `docs/deployment.md`

---

## 11. Quy ước phát triển

### 11.1. Không expose entity trực tiếp ra API
Luôn dùng DTO cho request/response.

### 11.2. Không nhét business logic vào controller
Controller chỉ nhận request và trả response.

### 11.3. Tất cả thay đổi schema đi qua Flyway
Không sửa tay database ở môi trường chung mà không có migration.

### 11.4. Tồn kho quản lý theo variant
Không quản lý tồn kho trực tiếp ở product cha.

### 11.5. Order item phải snapshot dữ liệu
Tránh bị thay đổi khi product đổi tên/giá về sau.

### 11.6. Tách payment và payment transaction
Để dễ audit và làm việc với nhiều payment flow.

---

## 12. Roadmap triển khai

### Phase 1 - MVP
- auth
- user/customer
- address
- category
- brand
- product
- product_variant
- inventory
- cart
- order
- order_item
- admin basic

### Phase 2
- payment gateway
- shipment
- invoice
- voucher
- promotion
- review

### Phase 3
- notification
- wishlist
- dashboard/report
- loyalty
- recommendation
- cms/banner

---

## 13. Cách build

**Maven**
```bash
./mvnw clean package
```

---

## 14. Chạy test

**Maven**
```bash
./mvnw test
```

---

## 15. Docker compose gợi ý cho local

```yaml
version: "3.9"

services:
  mariadb:
    image: mariadb:11
    container_name: fashion-shop-mariadb
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: fashion_shop_dev
    ports:
      - "3306:3306"
    volumes:
      - mariadb_data:/var/lib/mysql

  redis:
    image: redis:7
    container_name: fashion-shop-redis
    ports:
      - "6379:6379"

volumes:
  mariadb_data:
```

Chạy:

```bash
docker compose up -d
```

---

## 16. Branch & Commit Convention

### Branch types
- dev
- feat/
- bugfix/
- hotfix/
- release/
- refactor/

### Branch naming
```
{type}/{task_id}_{title}
```

Ví dụ:
- `feat/1001_product_variant_management`
- `bugfix/1042_fix_order_total_calculation`

### Commit message
```
{type}({module}): {title}

{description of the change}

{Fixes/Complete #issue_number}
```

Ví dụ:
```
feat(order): add create order API

Implement create order flow from cart items
Add validation for inventory and voucher application

Complete #1008
```

---

## 17. Gợi ý module ưu tiên code trước

Thứ tự khuyến nghị:

1. auth
2. role / permission
3. user / customer / address
4. category / brand
5. product / variant / media
6. inventory
7. cart
8. order / order_item
9. payment
10. promotion / voucher
11. shipment / invoice
12. review / notification

---

## 18. Chủ sở hữu hệ thống

Ví dụ:

- Backend team: ...
- Frontend admin team: ...
- Mobile team: ...
- DevOps: ...
- Product owner: ...