# Customer App API Contract

> **Audience:** `customer-app` (React Native)
> **Generated from source code on 2026-04-19.**
> See `api-common.md` for shared conventions (response envelope, auth, enums, pagination).

---

## Overview

- **Base URL:** `/api/v1`
- **Auth:** `Authorization: Bearer <access_token>` where noted; public endpoints require no header.
- **Allowed roles for protected endpoints:** `CUSTOMER` (unless stated otherwise)
- **Role hierarchy:** `SUPER_ADMIN > ADMIN > STAFF > CUSTOMER`

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [User Profile](#2-user-profile)
3. [Address Management](#3-address-management)
4. [Catalog — Categories](#4-catalog--categories)
5. [Catalog — Brands](#5-catalog--brands)
6. [Catalog — Products](#6-catalog--products)
7. [Cart](#7-cart)
8. [Orders](#8-orders)
9. [Payment](#9-payment)
10. [Vouchers](#10-vouchers)
11. [Shipment Tracking](#11-shipment-tracking)
12. [Invoices](#12-invoices)
13. [Reviews](#13-reviews)
14. [Notifications](#14-notifications)
15. [Enum Dictionaries](#15-enum-dictionaries)
16. [Error Code Reference](#16-error-code-reference)
17. [Missing / Incomplete Endpoints](#17-missing--incomplete-endpoints)

---

## 1. Authentication

### 1.1 Register

- **Module:** Auth
- **Endpoint:** `POST /api/v1/auth/register`
- **Description:** Creates a customer account. Returns user profile + JWT tokens (auto-login).
- **Auth:** Public
- **Allowed roles:** —

#### Request Body

```json
{
  "email": "customer@example.com",
  "password": "Password123",
  "firstName": "Nguyen",
  "lastName": "Van Loc",
  "phoneNumber": "0912345678"
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `email` | string | ✓ | No | valid email, max 255 | Login identifier |
| `password` | string | ✓ | No | 8–64 chars, ≥1 upper, ≥1 lower, ≥1 digit | Plain text — hashed server-side |
| `firstName` | string | ✓ | No | max 100 | First name |
| `lastName` | string | — | Yes | max 100 | Last name |
| `phoneNumber` | string | — | Yes | Vietnamese phone format | E.g. `0912345678` |

#### Response (201 Created)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Created successfully",
  "data": {
    "user": {
      "id": 1,
      "email": "customer@example.com",
      "firstName": "Nguyen",
      "lastName": "Van Loc",
      "phoneNumber": "0912345678",
      "status": "ACTIVE",
      "roles": ["CUSTOMER"],
      "createdAt": "2026-04-19T08:00:00"
    },
    "tokens": {
      "accessToken": "eyJhbGci...",
      "refreshToken": "eyJhbGci...",
      "tokenType": "Bearer",
      "expiresIn": 3600
    }
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Description |
|---|---|---|
| `data.user` | object | User identity — see `UserResponse` |
| `data.tokens.accessToken` | string | Short-lived JWT — put in `Authorization: Bearer` |
| `data.tokens.refreshToken` | string | Long-lived JWT — store securely, use to refresh |
| `data.tokens.tokenType` | string | Always `"Bearer"` |
| `data.tokens.expiresIn` | number | Access token TTL in seconds |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 409 | `EMAIL_ALREADY_EXISTS` | Email already registered |
| 409 | `PHONE_ALREADY_EXISTS` | Phone already registered |
| 422 | `VALIDATION_ERROR` | Field validation failure |

```json
{
  "success": false,
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Email is already registered",
  "timestamp": "2026-04-19T08:00:00",
  "path": "/api/v1/auth/register"
}
```

#### Notes

- Role `CUSTOMER` is always assigned automatically — cannot be overridden here.
- A `Customer` profile record is created alongside the `User` for business data (gender, birthDate, etc.).

---

### 1.2 Login

- **Module:** Auth
- **Endpoint:** `POST /api/v1/auth/login`
- **Description:** Authenticates with email/password. Returns JWT tokens.
- **Auth:** Public
- **Allowed roles:** —

#### Request Body

```json
{
  "email": "customer@example.com",
  "password": "Password123"
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `email` | string | ✓ | No | valid email | Login identifier |
| `password` | string | ✓ | No | not blank | Plain text password |

#### Response (200 OK)

Same structure as Register `data` field — `AuthResponse` with `user` + `tokens`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | Wrong email or password |
| 403 | `ACCOUNT_DISABLED` | Account is INACTIVE or LOCKED |
| 422 | `VALIDATION_ERROR` | Field validation failure |

---

### 1.3 Refresh Token

- **Module:** Auth
- **Endpoint:** `POST /api/v1/auth/refresh-token`
- **Description:** Exchanges a valid refresh token for a new access + refresh token pair.
- **Auth:** Public
- **Allowed roles:** —

#### Request Body

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

| Field | Type | Required | Nullable | Validation |
|---|---|:---:|:---:|---|
| `refreshToken` | string | ✓ | No | not blank |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 401 | `REFRESH_TOKEN_INVALID` | Token is expired or invalid |

#### Notes

- The old refresh token is **not explicitly revoked** in the current implementation. Store only the latest pair.

---

### 1.4 Logout

- **Module:** Auth
- **Endpoint:** `POST /api/v1/auth/logout`
- **Description:** Invalidates the current access token by adding it to a Redis blacklist.
- **Auth:** Bearer Token
- **Allowed roles:** Any authenticated user

#### Request

No body. Pass the access token in the `Authorization` header.

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": null,
  "timestamp": "2026-04-19T08:00:00"
}
```

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 401 | `TOKEN_INVALID` | Authorization header missing or malformed |
| 401 | `TOKEN_EXPIRED` | Token already expired |

---

## 2. User Profile

### 2.1 Get My Profile

- **Module:** User
- **Endpoint:** `GET /api/v1/me`
- **Description:** Returns combined auth identity and customer profile for the authenticated user.
- **Auth:** Bearer Token
- **Allowed roles:** Any authenticated user
- **Ownership rule:** Returns data for the token owner only.

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "id": 1,
    "email": "customer@example.com",
    "firstName": "Nguyen",
    "lastName": "Van Loc",
    "phoneNumber": "0912345678",
    "status": "ACTIVE",
    "roles": ["CUSTOMER"],
    "customerId": 1,
    "gender": "MALE",
    "birthDate": "2000-01-15",
    "avatarUrl": null,
    "loyaltyPoints": 0,
    "createdAt": "2026-04-19T08:00:00"
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | User (auth) ID |
| `email` | string | No | Login email |
| `firstName` | string | No | First name |
| `lastName` | string | Yes | Last name |
| `phoneNumber` | string | Yes | Phone number |
| `status` | string | No | `UserStatus` enum |
| `roles` | string[] | No | Assigned roles |
| `customerId` | number | Yes | Customer profile ID (null for admin/staff) |
| `gender` | string | Yes | `Gender` enum |
| `birthDate` | string | Yes | ISO date `YYYY-MM-DD` |
| `avatarUrl` | string | Yes | Profile picture URL |
| `loyaltyPoints` | number | Yes | Current points balance |
| `createdAt` | string | No | ISO datetime |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 401 | `UNAUTHORIZED` | Not authenticated |

---

### 2.2 Update My Profile

- **Module:** User
- **Endpoint:** `PATCH /api/v1/me`
- **Description:** Partially updates profile. Only provided (non-null) fields are applied. Updates both `User` (name, phone) and `Customer` (gender, birthDate) records.
- **Auth:** Bearer Token
- **Allowed roles:** Any authenticated user
- **Ownership rule:** Updates the token owner's profile only.

#### Request Body

All fields are optional. Send only the fields to change.

```json
{
  "firstName": "Nguyen",
  "lastName": "Van Loc",
  "phoneNumber": "0912345678",
  "gender": "MALE",
  "birthDate": "2000-01-15"
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `firstName` | string | — | Yes | max 100 | First name |
| `lastName` | string | — | Yes | max 100 | Last name |
| `phoneNumber` | string | — | Yes | Vietnamese phone format | Phone number |
| `gender` | string | — | Yes | `Gender` enum | `MALE`, `FEMALE`, `OTHER` |
| `birthDate` | string | — | Yes | ISO date `YYYY-MM-DD` | Date of birth |

#### Response (200 OK)

Same structure as Get My Profile — returns the updated `UserProfileResponse`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 401 | `UNAUTHORIZED` | Not authenticated |
| 409 | `PHONE_ALREADY_EXISTS` | Phone already used by another account |
| 422 | `VALIDATION_ERROR` | Field validation failure |

---

## 3. Address Management

All address endpoints require `Authorization: Bearer <token>`. Addresses are scoped to the authenticated user — a user cannot access another user's addresses.

### 3.1 List My Addresses

- **Endpoint:** `GET /api/v1/addresses`
- **Description:** Returns all addresses for the current user, sorted by default first then newest.
- **Auth:** Bearer Token

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": [
    {
      "id": 1,
      "receiverName": "Nguyen Van Loc",
      "phoneNumber": "0912345678",
      "streetAddress": "123 Nguyen Hue, Ben Nghe",
      "ward": "Phuong Ben Nghe",
      "district": "Quan 1",
      "city": "TP. Ho Chi Minh",
      "postalCode": "700000",
      "addressType": "SHIPPING",
      "isDefault": true,
      "label": "Home",
      "fullAddress": "123 Nguyen Hue, Ben Nghe, Phuong Ben Nghe, Quan 1, TP. Ho Chi Minh",
      "createdAt": "2026-04-19T08:00:00"
    }
  ],
  "timestamp": "2026-04-19T08:00:00"
}
```

`data` is an array of `AddressResponse`.

---

### 3.2 Get Address by ID

- **Endpoint:** `GET /api/v1/addresses/{id}`
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if address does not belong to the current user.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `id` | number | Address ID |

#### Response (200 OK)

`data` is a single `AddressResponse` object (same fields as list item above).

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `ADDRESS_NOT_FOUND` | Address not found or not owned by current user |

---

### 3.3 Create Address

- **Endpoint:** `POST /api/v1/addresses`
- **Auth:** Bearer Token

#### Request Body

```json
{
  "receiverName": "Nguyen Van Loc",
  "phoneNumber": "0912345678",
  "streetAddress": "123 Nguyen Hue, Ben Nghe",
  "ward": "Phuong Ben Nghe",
  "district": "Quan 1",
  "city": "TP. Ho Chi Minh",
  "postalCode": "700000",
  "addressType": "SHIPPING",
  "isDefault": false,
  "label": "Home"
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `receiverName` | string | ✓ | No | max 100 | Recipient name |
| `phoneNumber` | string | ✓ | No | Vietnamese phone format | Recipient phone |
| `streetAddress` | string | ✓ | No | max 255 | Street + building details |
| `ward` | string | ✓ | No | max 100 | Ward (phường/xã) |
| `district` | string | ✓ | No | max 100 | District (quận/huyện) |
| `city` | string | ✓ | No | max 100 | City / Province |
| `postalCode` | string | — | Yes | max 20 | Postal code |
| `addressType` | string | ✓ | No | `AddressType` enum | `SHIPPING`, `BILLING`, `BOTH` |
| `isDefault` | boolean | — | Yes | — | Set as default; clears previous default |
| `label` | string | — | Yes | max 50 | Label e.g. `Home`, `Office` |

#### Response (201 Created)

`data` is the created `AddressResponse`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 401 | `UNAUTHORIZED` | Not authenticated |
| 422 | `VALIDATION_ERROR` | Field validation failure |

#### Notes

- If `isDefault = true`, any previously-default address is automatically unset.

---

### 3.4 Update Address

- **Endpoint:** `PATCH /api/v1/addresses/{id}`
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if address does not belong to current user.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `id` | number | Address ID |

#### Request Body

All fields optional. Only provided fields are applied.

```json
{
  "streetAddress": "456 Le Loi, Ben Thanh",
  "ward": "Phuong Ben Thanh",
  "isDefault": true
}
```

Same field definitions as Create — all optional here.

#### Response (200 OK)

`data` is the updated `AddressResponse`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `ADDRESS_NOT_FOUND` | Address not found or not owned |
| 422 | `VALIDATION_ERROR` | Field validation failure |

---

### 3.5 Delete Address

- **Endpoint:** `DELETE /api/v1/addresses/{id}`
- **Description:** Soft-deletes the address. Keeps it in the database for order history references.
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if address does not belong to current user.

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": null,
  "timestamp": "2026-04-19T08:00:00"
}
```

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `ADDRESS_NOT_FOUND` | Address not found or not owned |

---

## 4. Catalog — Categories

### 4.1 List Active Categories

- **Endpoint:** `GET /api/v1/categories`
- **Description:** Returns all ACTIVE categories.
- **Auth:** Public

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": [
    {
      "id": 1,
      "parentId": null,
      "name": "Áo",
      "slug": "ao",
      "description": "Các loại áo",
      "imageUrl": "https://...",
      "status": "ACTIVE",
      "sortOrder": 1,
      "createdAt": "2026-04-01T00:00:00"
    }
  ],
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Category ID |
| `parentId` | number | Yes | Parent category ID (null = root) |
| `name` | string | No | Display name |
| `slug` | string | No | URL-friendly identifier |
| `description` | string | Yes | Category description |
| `imageUrl` | string | Yes | Category image |
| `status` | string | No | `CategoryStatus` enum |
| `sortOrder` | number | Yes | Display order |
| `createdAt` | string | No | ISO datetime |

---

### 4.2 Get Category by ID

- **Endpoint:** `GET /api/v1/categories/{id}`
- **Auth:** Public

#### Response (200 OK)

`data` is a single `CategoryResponse`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `CATEGORY_NOT_FOUND` | Category not found |

---

## 5. Catalog — Brands

### 5.1 List Active Brands

- **Endpoint:** `GET /api/v1/brands`
- **Description:** Returns all ACTIVE brands.
- **Auth:** Public

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": [
    {
      "id": 1,
      "name": "Nike",
      "slug": "nike",
      "logoUrl": "https://...",
      "description": "Just do it",
      "status": "ACTIVE",
      "createdAt": "2026-04-01T00:00:00"
    }
  ],
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Brand ID |
| `name` | string | No | Brand name |
| `slug` | string | No | URL-friendly identifier |
| `logoUrl` | string | Yes | Brand logo URL |
| `description` | string | Yes | Brand description |
| `status` | string | No | `BrandStatus` enum |
| `createdAt` | string | No | ISO datetime |

---

### 5.2 Get Brand by ID

- **Endpoint:** `GET /api/v1/brands/{id}`
- **Auth:** Public

#### Response (200 OK)

`data` is a single `BrandResponse`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `BRAND_NOT_FOUND` | Brand not found |

---

## 6. Catalog — Products

### 6.1 List Published Products

- **Endpoint:** `GET /api/v1/products`
- **Description:** Returns published products with optional filtering. Paginated.
- **Auth:** Public

#### Query Parameters

| Param | Type | Required | Description |
|---|---|:---:|---|
| `keyword` | string | — | Search in product name |
| `categoryId` | number | — | Filter by category |
| `brandId` | number | — | Filter by brand |
| `status` | string | — | `ProductStatus` — typically `PUBLISHED` for app |
| `minPrice` | decimal | — | Minimum variant price |
| `maxPrice` | decimal | — | Maximum variant price |
| `featured` | boolean | — | Show only featured products |
| `page` | number | — | Page number (0-based, default 0) |
| `size` | number | — | Page size (default: see `AppConstants.DEFAULT_PAGE_SIZE`) |
| `sort` | string | — | Sort field + direction e.g. `createdAt,desc` |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "items": [
      {
        "id": 1,
        "name": "Áo thun basic nam",
        "slug": "ao-thun-basic-nam",
        "shortDescription": "Áo thun cotton 100%",
        "thumbnailUrl": "https://...",
        "minPrice": 150000.00,
        "maxPrice": 200000.00,
        "status": "PUBLISHED",
        "featured": false,
        "brandName": "Nike",
        "categoryNames": ["Áo", "Áo Thun"],
        "createdAt": "2026-04-01T00:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 100,
    "totalPages": 5,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Product ID |
| `name` | string | No | Product name |
| `slug` | string | No | URL slug |
| `shortDescription` | string | Yes | Short summary |
| `thumbnailUrl` | string | Yes | Primary image URL |
| `minPrice` | decimal | Yes | Lowest variant price |
| `maxPrice` | decimal | Yes | Highest variant price |
| `status` | string | No | `ProductStatus` enum |
| `featured` | boolean | No | Featured flag |
| `brandName` | string | Yes | Brand name |
| `categoryNames` | string[] | Yes | List of category names |
| `createdAt` | string | No | ISO datetime |

---

### 6.2 Get Product Detail

- **Endpoint:** `GET /api/v1/products/{id}`
- **Description:** Returns full product detail with variants and media. Only published products visible to customers (service-enforced).
- **Auth:** Public

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `id` | number | Product ID |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "id": 1,
    "name": "Áo thun basic nam",
    "slug": "ao-thun-basic-nam",
    "shortDescription": "Áo thun cotton 100%",
    "description": "<p>Mô tả chi tiết...</p>",
    "status": "PUBLISHED",
    "featured": false,
    "brand": {
      "id": 1,
      "name": "Nike",
      "slug": "nike",
      "logoUrl": "https://...",
      "description": null,
      "status": "ACTIVE",
      "createdAt": "2026-04-01T00:00:00"
    },
    "categories": [
      {
        "id": 1,
        "parentId": null,
        "name": "Áo",
        "slug": "ao",
        "description": null,
        "imageUrl": null,
        "status": "ACTIVE",
        "sortOrder": 1,
        "createdAt": "2026-04-01T00:00:00"
      }
    ],
    "variants": [
      {
        "id": 1,
        "sku": "SKU-001",
        "barcode": null,
        "variantName": "Trắng / M",
        "basePrice": 200000.00,
        "salePrice": 150000.00,
        "compareAtPrice": null,
        "weightGram": 200,
        "status": "ACTIVE",
        "attributes": [
          { "name": "Color", "value": "Trắng" },
          { "name": "Size", "value": "M" }
        ]
      }
    ],
    "media": [
      {
        "id": 1,
        "mediaUrl": "https://...",
        "mediaType": "IMAGE",
        "sortOrder": 1,
        "primary": true,
        "variantId": null
      }
    ],
    "createdAt": "2026-04-01T00:00:00",
    "updatedAt": "2026-04-19T08:00:00"
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

**Variant fields:**

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Variant ID |
| `sku` | string | No | Stock-Keeping Unit |
| `barcode` | string | Yes | Barcode |
| `variantName` | string | No | Computed name e.g. `"Trắng / M"` |
| `basePrice` | decimal | No | Regular price |
| `salePrice` | decimal | Yes | Sale price (null = not on sale) |
| `compareAtPrice` | decimal | Yes | Original price shown crossed-out |
| `weightGram` | number | Yes | Weight in grams |
| `status` | string | No | `ProductVariantStatus` enum |
| `attributes` | array | No | Key-value attribute pairs |

**Media fields:**

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Media ID |
| `mediaUrl` | string | No | URL of image/video |
| `mediaType` | string | No | `MediaType` enum (`IMAGE`, `VIDEO`) |
| `sortOrder` | number | Yes | Display order |
| `primary` | boolean | No | Is this the primary/thumbnail media? |
| `variantId` | number | Yes | If linked to a specific variant |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `PRODUCT_NOT_FOUND` | Product not found or not published |

---

## 7. Cart

All cart endpoints require `Authorization: Bearer <token>`. Cart is scoped to the authenticated customer.

### 7.1 Get My Cart

- **Endpoint:** `GET /api/v1/cart`
- **Auth:** Bearer Token

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "id": 1,
    "items": [
      {
        "id": 10,
        "variantId": 1,
        "variantName": "Trắng / M",
        "sku": "SKU-001",
        "productSlug": "ao-thun-basic-nam",
        "productName": "Áo thun basic nam",
        "unitPrice": 200000.00,
        "salePrice": 150000.00,
        "quantity": 2,
        "availableStock": 50,
        "lineTotal": 300000.00,
        "createdAt": "2026-04-19T08:00:00"
      }
    ],
    "totalItems": 2,
    "subTotal": 300000.00,
    "updatedAt": "2026-04-19T08:00:00"
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Cart ID |
| `items` | array | No | Cart items |
| `totalItems` | number | No | Total quantity across all items |
| `subTotal` | decimal | No | Sum of all line totals |
| `updatedAt` | string | No | Last modification time |

**Cart item fields:**

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Cart item ID |
| `variantId` | number | No | Variant ID |
| `variantName` | string | Yes | E.g. `"Trắng / M"` |
| `sku` | string | Yes | Variant SKU |
| `productSlug` | string | Yes | Product slug for navigation |
| `productName` | string | Yes | Product name |
| `unitPrice` | decimal | Yes | Base price |
| `salePrice` | decimal | Yes | Sale price (null = no discount) |
| `quantity` | number | No | Quantity in cart |
| `availableStock` | number | No | Current available stock |
| `lineTotal` | decimal | Yes | quantity × effective price |
| `createdAt` | string | Yes | When item was added |

---

### 7.2 Add Item to Cart

- **Endpoint:** `POST /api/v1/cart/items`
- **Auth:** Bearer Token

#### Request Body

```json
{
  "variantId": 1,
  "quantity": 2
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `variantId` | number | ✓ | No | not null | Variant to add |
| `quantity` | number | ✓ | No | ≥ 1 | Quantity to add |

#### Response (200 OK)

Returns the updated `CartResponse` (same as Get My Cart).

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `PRODUCT_VARIANT_NOT_FOUND` | Variant does not exist |
| 422 | `PRODUCT_VARIANT_INACTIVE` | Variant is inactive |
| 422 | `INVENTORY_NOT_ENOUGH` | Requested quantity exceeds stock |
| 422 | `VALIDATION_ERROR` | Field validation failure |

---

### 7.3 Update Cart Item Quantity

- **Endpoint:** `PATCH /api/v1/cart/items/{itemId}`
- **Auth:** Bearer Token

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `itemId` | number | Cart item ID |

#### Request Body

```json
{
  "quantity": 3
}
```

| Field | Type | Required | Nullable | Validation |
|---|---|:---:|:---:|---|
| `quantity` | number | ✓ | No | ≥ 1 |

#### Response (200 OK)

Returns the updated `CartResponse`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `CART_ITEM_NOT_FOUND` | Item not found or not in this user's cart |
| 422 | `INVENTORY_NOT_ENOUGH` | Quantity exceeds available stock |

---

### 7.4 Remove Item from Cart

- **Endpoint:** `DELETE /api/v1/cart/items/{itemId}`
- **Auth:** Bearer Token

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `itemId` | number | Cart item ID |

#### Response (200 OK)

Returns the updated `CartResponse` (item removed).

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `CART_ITEM_NOT_FOUND` | Item not found |

---

### 7.5 Clear Cart

- **Endpoint:** `DELETE /api/v1/cart`
- **Description:** Removes all items from the cart.
- **Auth:** Bearer Token

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": null,
  "timestamp": "2026-04-19T08:00:00"
}
```

---

## 8. Orders

All order endpoints require `Authorization: Bearer <token>` and `CUSTOMER` role.

### 8.1 Create Order (Checkout)

- **Endpoint:** `POST /api/v1/orders`
- **Description:** Creates an order from the current cart. Reserves inventory, applies voucher if provided, clears the cart on success.
- **Auth:** Bearer Token

#### Request Body

```json
{
  "shippingAddressId": 1,
  "paymentMethod": "COD",
  "customerNote": "Giao giờ hành chính",
  "voucherCode": "SALE20"
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `shippingAddressId` | number | ✓ | No | not null | ID of an existing address owned by the user |
| `paymentMethod` | string | — | Yes | `COD` or `ONLINE` | Defaults to `COD` if omitted |
| `customerNote` | string | — | Yes | max 500 | Delivery instructions |
| `voucherCode` | string | — | Yes | — | Voucher code to apply |

#### Response (201 Created)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Created successfully",
  "data": {
    "id": 100,
    "orderCode": "ORD20260419000001",
    "customerId": 1,
    "status": "PENDING",
    "paymentMethod": "COD",
    "paymentStatus": "PENDING",
    "receiverName": "Nguyen Van Loc",
    "receiverPhone": "0912345678",
    "shippingStreet": "123 Nguyen Hue, Ben Nghe",
    "shippingWard": "Phuong Ben Nghe",
    "shippingDistrict": "Quan 1",
    "shippingCity": "TP. Ho Chi Minh",
    "shippingPostalCode": "700000",
    "subTotal": 300000.00,
    "discountAmount": 30000.00,
    "shippingFee": 30000.00,
    "totalAmount": 300000.00,
    "voucherCode": "SALE20",
    "customerNote": "Giao giờ hành chính",
    "items": [
      {
        "id": 1,
        "variantId": 1,
        "productName": "Áo thun basic nam",
        "variantName": "Trắng / M",
        "sku": "SKU-001",
        "unitPrice": 200000.00,
        "salePrice": 150000.00,
        "quantity": 2,
        "lineTotal": 300000.00
      }
    ],
    "createdAt": "2026-04-19T08:00:00"
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

**Order fields:**

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Order ID |
| `orderCode` | string | No | Human-readable order code |
| `customerId` | number | No | Customer ID |
| `status` | string | No | `OrderStatus` enum |
| `paymentMethod` | string | No | `PaymentMethod` enum |
| `paymentStatus` | string | No | `PaymentStatus` enum |
| `receiverName` | string | No | Shipping address receiver |
| `receiverPhone` | string | No | Receiver phone |
| `shippingStreet` | string | No | Shipping street address (snapshot) |
| `shippingWard` | string | No | Shipping ward |
| `shippingDistrict` | string | No | Shipping district |
| `shippingCity` | string | No | Shipping city |
| `shippingPostalCode` | string | Yes | Shipping postal code |
| `subTotal` | decimal | No | Sum of item line totals |
| `discountAmount` | decimal | No | Voucher/promotion discount |
| `shippingFee` | decimal | No | Shipping fee |
| `totalAmount` | decimal | No | Final amount to pay |
| `voucherCode` | string | Yes | Applied voucher code |
| `customerNote` | string | Yes | Delivery note |
| `items` | array | No | Order items (snapshot) |
| `createdAt` | string | No | ISO datetime |

**Order item fields:**

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Order item ID |
| `variantId` | number | No | Variant ID |
| `productName` | string | No | Product name (snapshot) |
| `variantName` | string | No | Variant name (snapshot) |
| `sku` | string | No | SKU (snapshot) |
| `unitPrice` | decimal | No | Base price at checkout |
| `salePrice` | decimal | Yes | Sale price at checkout |
| `quantity` | number | No | Quantity ordered |
| `lineTotal` | decimal | No | Total for this line |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `ADDRESS_NOT_FOUND` | Shipping address not found or not owned |
| 422 | `ORDER_EMPTY` | Cart is empty |
| 422 | `INVENTORY_NOT_ENOUGH` | Stock insufficient for one or more items |
| 422 | `VOUCHER_INVALID` | Voucher is invalid or inactive |
| 422 | `VOUCHER_EXPIRED` | Voucher has expired |
| 422 | `VOUCHER_MIN_ORDER_NOT_MET` | Cart total below voucher minimum |
| 422 | `VOUCHER_USAGE_LIMIT_EXCEEDED` | Voucher usage limit exhausted |
| 422 | `VOUCHER_USER_LIMIT_EXCEEDED` | Per-user limit for voucher reached |
| 422 | `STOCK_RESERVATION_FAILED` | Transient reservation failure — retry |

#### Notes

- Shipping address must belong to the authenticated customer.
- Inventory is reserved atomically. If any item fails, the entire order is rejected.
- Voucher usage is recorded on order creation; validated discount is reflected in `discountAmount`.
- Cart is cleared after successful order creation.

---

### 8.2 List My Orders

- **Endpoint:** `GET /api/v1/orders`
- **Description:** Returns the customer's orders, paginated, newest first. Supports optional status filter via `OrderFilter`.
- **Auth:** Bearer Token

#### Query Parameters

| Param | Type | Description |
|---|---|---|
| `status` | string | Optional. Filter by order status (e.g., `PENDING`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `COMPLETED`, `CANCELLED`) |
| `page` | number | Page number (0-based, default 0) |
| `size` | number | Page size (default: `AppConstants.DEFAULT_PAGE_SIZE`) |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "items": [
      {
        "id": 100,
        "orderCode": "ORD20260419000001",
        "status": "PENDING",
        "paymentMethod": "COD",
        "paymentStatus": "PENDING",
        "totalItems": 2,
        "totalAmount": 300000.00,
        "createdAt": "2026-04-19T08:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 5,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

List items are `OrderListItemResponse` — lightweight summaries without line items.

---

### 8.3 Get My Order by ID

- **Endpoint:** `GET /api/v1/orders/{id}`
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if order does not belong to current customer.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `id` | number | Order ID |

#### Response (200 OK)

`data` is a full `OrderResponse` (same as Create Order response).

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `ORDER_NOT_FOUND` | Order not found or not owned |

---

### 8.4 Cancel My Order

- **Endpoint:** `POST /api/v1/orders/my/{id}/cancel`
- **Description:** Customer-initiated cancellation. Only allowed in `PENDING` or `AWAITING_PAYMENT` statuses.
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if order does not belong to current customer.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `id` | number | Order ID |

#### Response (200 OK)

`data` is the updated `OrderResponse` with `status: "CANCELLED"`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `ORDER_NOT_FOUND` | Order not found or not owned |
| 422 | `ORDER_CANNOT_CANCEL` | Order status does not allow cancellation |

#### Notes

- Only `PENDING` and `AWAITING_PAYMENT` orders can be cancelled by the customer.
- Cancellation releases any reserved inventory.

---

## 9. Payment

### 9.1 Get Payment for My Order

- **Endpoint:** `GET /api/v1/payments/order/{orderId}`
- **Description:** Returns the payment record and transaction history for the customer's order.
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if the order does not belong to the current customer.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `orderId` | number | Order ID |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "id": 1,
    "orderId": 100,
    "orderCode": "ORD20260419000001",
    "paymentCode": "PAY20260419000001",
    "method": "ONLINE",
    "status": "PAID",
    "amount": 300000.00,
    "paidAt": "2026-04-19T09:00:00",
    "transactions": [
      {
        "id": 1,
        "transactionCode": "TXN20260419000001",
        "status": "SUCCESS",
        "amount": 300000.00,
        "method": "ONLINE",
        "provider": "VNPAY",
        "providerTxnId": "TXN_GW_12345",
        "referenceType": "PAYMENT",
        "referenceId": "1",
        "note": null,
        "createdAt": "2026-04-19T09:00:00"
      }
    ],
    "createdAt": "2026-04-19T08:30:00"
  },
  "timestamp": "2026-04-19T09:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Payment record ID |
| `orderId` | number | No | Associated order ID |
| `orderCode` | string | No | Order code |
| `paymentCode` | string | No | Payment code |
| `method` | string | No | `PaymentMethod` enum |
| `status` | string | No | `PaymentRecordStatus` enum |
| `amount` | decimal | No | Payment amount |
| `paidAt` | string | Yes | When payment was confirmed |
| `transactions` | array | Yes | Individual transaction attempts |
| `createdAt` | string | No | ISO datetime |

**Transaction fields:**

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Transaction ID |
| `transactionCode` | string | No | Internal transaction code |
| `status` | string | No | `TransactionStatus` enum |
| `amount` | decimal | No | Transaction amount |
| `method` | string | Yes | Payment method |
| `provider` | string | Yes | Gateway provider name |
| `providerTxnId` | string | Yes | Provider's transaction ID |
| `referenceType` | string | Yes | Type of referenced entity |
| `referenceId` | string | Yes | ID of referenced entity |
| `note` | string | Yes | Additional note |
| `createdAt` | string | No | ISO datetime |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `PAYMENT_NOT_FOUND` | No payment for this order |
| 404 | `ORDER_NOT_FOUND` | Order not found or not owned |

---

### 9.2 Initiate Online Payment

- **Endpoint:** `POST /api/v1/payments/order/{orderId}/initiate`
- **Description:** Creates a payment record and returns payment details. Idempotent — calling again for an in-flight payment returns the existing record. Calling after a `FAILED` payment retries.
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if order not owned by current customer.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `orderId` | number | Order ID |

#### Request Body (Optional)

```json
{
  "provider": "vnpay",
  "returnUrl": "https://example.com/payment/callback"
}
```

| Field | Type | Required | Nullable | Description |
|---|---|:---:|:---:|---|
| `provider` | string | — | Yes | Payment gateway identifier (for future gateway integration) |
| `returnUrl` | string | — | Yes | Redirect URL after payment (for future redirect flow) |

Body may be omitted entirely (body is marked `required = false` in the controller).

#### Response (201 Created)

`data` is a `PaymentResponse` with `status: "INITIATED"`.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `ORDER_NOT_FOUND` | Order not found or not owned |
| 409 | `PAYMENT_ALREADY_PROCESSED` | Payment already in `PAID` state |

---

### 9.3 Payment Gateway Callback

- **Endpoint:** `POST /api/v1/payments/callback`
- **Description:** Called by the payment gateway to notify the result. No authentication required. Idempotent on duplicate `providerTxnId`.
- **Auth:** None (called by external gateway)
- **Audience:** Payment gateway — NOT called by the mobile app directly.

#### Request Body

```json
{
  "orderCode": "ORD20260419000001",
  "status": "SUCCESS",
  "providerTxnId": "TXN_GW_12345",
  "provider": "VNPAY",
  "payload": "{\"raw\":\"gateway_data\"}"
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `orderCode` | string | ✓ | No | not blank | Order code |
| `status` | string | ✓ | No | `SUCCESS` or `FAILED` | Payment result |
| `providerTxnId` | string | — | Yes | — | Gateway's transaction ID |
| `provider` | string | — | Yes | — | Gateway identifier |
| `payload` | string | — | Yes | — | Raw gateway callback JSON |

#### Response (200 OK)

`data` is a `PaymentResponse` with updated status.

---

## 10. Vouchers

### 10.1 Validate Voucher

- **Endpoint:** `POST /api/v1/vouchers/{code}/validate`
- **Description:** Validates a voucher code and previews the discount for the given order context. Does NOT record usage — safe to call during checkout preview before order submission.
- **Auth:** Bearer Token

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `code` | string | Voucher code |

#### Request Body

```json
{
  "orderAmount": 300000.00,
  "productIds": [1, 2],
  "categoryIds": [1],
  "brandIds": [1]
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `orderAmount` | decimal | ✓ | No | > 0, max 16 integer digits, 2 fraction digits | Cart subtotal before discount |
| `productIds` | number[] | — | Yes | — | Product IDs in cart (needed for PRODUCT-scope vouchers) |
| `categoryIds` | number[] | — | Yes | — | Category IDs of cart products (for CATEGORY-scope) |
| `brandIds` | number[] | — | Yes | — | Brand IDs of cart products (for BRAND-scope) |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "voucherCode": "SALE20",
    "promotionName": "Flash Sale April",
    "discountType": "PERCENTAGE",
    "discountValue": 10.00,
    "discountAmount": 30000.00,
    "orderAmount": 300000.00,
    "finalAmount": 270000.00
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `voucherCode` | string | No | Applied voucher code |
| `promotionName` | string | No | Promotion name |
| `discountType` | string | No | `DiscountType` enum |
| `discountValue` | decimal | No | Percentage or fixed amount value |
| `discountAmount` | decimal | No | Calculated monetary discount |
| `orderAmount` | decimal | No | Input order amount |
| `finalAmount` | decimal | No | `orderAmount − discountAmount` (min 0) |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `VOUCHER_NOT_FOUND` | Voucher code does not exist |
| 422 | `VOUCHER_INVALID` | Voucher is inactive or not started |
| 422 | `VOUCHER_EXPIRED` | Voucher past end date |
| 422 | `VOUCHER_MIN_ORDER_NOT_MET` | `orderAmount` below minimum |
| 422 | `VOUCHER_USAGE_LIMIT_EXCEEDED` | Global usage limit reached |
| 422 | `VOUCHER_USER_LIMIT_EXCEEDED` | Per-user limit reached |
| 422 | `VOUCHER_NOT_APPLICABLE` | Scope mismatch — products/categories/brands don't match |

#### Notes

- Always call this before order submission to show the customer the discounted price.
- Pass the actual `productIds`, `categoryIds`, and `brandIds` from the cart to support scope-restricted vouchers.

---

## 11. Shipment Tracking

### 11.1 Get Shipment for My Order

- **Endpoint:** `GET /api/v1/shipments/order/{orderId}`
- **Description:** Returns the shipment record and full tracking event timeline.
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if the order does not belong to the current customer.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `orderId` | number | Order ID |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "id": 1,
    "orderId": 100,
    "orderCode": "ORD20260419000001",
    "shipmentCode": "SHP20260419000001",
    "carrier": "Giao Hàng Nhanh",
    "trackingNumber": "GHN1234567",
    "status": "IN_TRANSIT",
    "estimatedDeliveryDate": "2026-04-22",
    "deliveredAt": null,
    "shippingFee": 30000.00,
    "note": null,
    "events": [
      {
        "id": 1,
        "status": "PENDING",
        "location": "Hà Nội",
        "description": "Đơn hàng đã được tiếp nhận",
        "eventTime": "2026-04-19T10:00:00"
      },
      {
        "id": 2,
        "status": "IN_TRANSIT",
        "location": "TP. Ho Chi Minh",
        "description": "Đang vận chuyển",
        "eventTime": "2026-04-20T14:00:00"
      }
    ],
    "createdAt": "2026-04-19T09:00:00",
    "updatedAt": "2026-04-20T14:00:00"
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Shipment ID |
| `orderId` | number | No | Associated order ID |
| `orderCode` | string | No | Order code |
| `shipmentCode` | string | No | Shipment code |
| `carrier` | string | Yes | Carrier name |
| `trackingNumber` | string | Yes | External tracking number |
| `status` | string | No | `ShipmentStatus` enum |
| `estimatedDeliveryDate` | string | Yes | Expected delivery date `YYYY-MM-DD` |
| `deliveredAt` | string | Yes | Actual delivery timestamp |
| `shippingFee` | decimal | Yes | Shipping fee |
| `note` | string | Yes | Internal note |
| `events` | array | Yes | Chronological tracking events |

**Tracking event fields:**

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Event ID |
| `status` | string | No | `ShipmentStatus` at this event |
| `location` | string | Yes | Physical location |
| `description` | string | Yes | Human-readable event description |
| `eventTime` | string | No | ISO datetime of event |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `SHIPMENT_NOT_FOUND` | No shipment yet for this order |
| 404 | `ORDER_NOT_FOUND` | Order not found or not owned |

---

## 12. Invoices

### 12.1 Get Invoice for My Order

- **Endpoint:** `GET /api/v1/invoices/order/{orderId}`
- **Description:** Returns the full invoice for printing or display.
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if the order does not belong to the current customer.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `orderId` | number | Order ID |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "id": 1,
    "invoiceCode": "INV20260419000001",
    "status": "PAID",
    "issuedAt": "2026-04-19T09:00:00",
    "dueDate": "2026-04-26",
    "notes": null,
    "orderId": 100,
    "orderCode": "ORD20260419000001",
    "paymentMethod": "ONLINE",
    "paymentStatus": "PAID",
    "paidAt": "2026-04-19T09:05:00",
    "customerName": "Nguyen Van Loc",
    "customerEmail": "customer@example.com",
    "customerPhone": "0912345678",
    "billingStreet": "123 Nguyen Hue, Ben Nghe",
    "billingWard": "Phuong Ben Nghe",
    "billingDistrict": "Quan 1",
    "billingCity": "TP. Ho Chi Minh",
    "billingPostalCode": "700000",
    "items": [
      {
        "variantId": 1,
        "productName": "Áo thun basic nam",
        "variantName": "Trắng / M",
        "sku": "SKU-001",
        "unitPrice": 200000.00,
        "salePrice": 150000.00,
        "effectivePrice": 150000.00,
        "quantity": 2,
        "lineTotal": 300000.00
      }
    ],
    "subTotal": 300000.00,
    "discountAmount": 30000.00,
    "shippingFee": 30000.00,
    "totalAmount": 300000.00,
    "voucherCode": "SALE20",
    "createdAt": "2026-04-19T09:00:00"
  },
  "timestamp": "2026-04-19T09:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Invoice ID |
| `invoiceCode` | string | No | Invoice code |
| `status` | string | No | `InvoiceStatus` enum |
| `issuedAt` | string | No | Issue timestamp |
| `dueDate` | string | Yes | Due date `YYYY-MM-DD` |
| `notes` | string | Yes | Invoice notes |
| `orderId` | number | No | Associated order ID |
| `orderCode` | string | No | Order code |
| `paymentMethod` | string | No | Payment method |
| `paymentStatus` | string | No | Payment status |
| `paidAt` | string | Yes | Payment confirmation time |
| `customerName` | string | No | Customer name (snapshot) |
| `customerEmail` | string | No | Customer email (snapshot) |
| `customerPhone` | string | Yes | Customer phone (snapshot) |
| `billingStreet` | string | Yes | Billing address (snapshot) |
| `billingWard` | string | Yes | Billing ward |
| `billingDistrict` | string | Yes | Billing district |
| `billingCity` | string | Yes | Billing city |
| `billingPostalCode` | string | Yes | Billing postal code |
| `items` | array | Yes | Line items |
| `subTotal` | decimal | No | Items subtotal |
| `discountAmount` | decimal | No | Discount applied |
| `shippingFee` | decimal | No | Shipping fee |
| `totalAmount` | decimal | No | Final total |
| `voucherCode` | string | Yes | Applied voucher |
| `createdAt` | string | No | ISO datetime |

**Invoice item fields:**

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `variantId` | number | No | Variant ID |
| `productName` | string | No | Product name (snapshot) |
| `variantName` | string | No | Variant name (snapshot) |
| `sku` | string | No | SKU (snapshot) |
| `unitPrice` | decimal | No | Base price |
| `salePrice` | decimal | Yes | Sale price (null = full price) |
| `effectivePrice` | decimal | No | Actual price paid per unit |
| `quantity` | number | No | Quantity |
| `lineTotal` | decimal | No | Line total |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `INVOICE_NOT_FOUND` | No invoice for this order |
| 404 | `ORDER_NOT_FOUND` | Order not found or not owned |

---

## 13. Reviews

### 13.1 Submit a Review

- **Endpoint:** `POST /api/v1/reviews`
- **Description:** Submits a product review tied to a completed order item. One review per order item.
- **Auth:** Bearer Token
- **Allowed roles:** `CUSTOMER`

#### Request Body

```json
{
  "orderItemId": 1,
  "rating": 5,
  "comment": "Sản phẩm tốt, đúng mô tả!"
}
```

| Field | Type | Required | Nullable | Validation | Description |
|---|---|:---:|:---:|---|---|
| `orderItemId` | number | ✓ | No | not null | Must be from a COMPLETED order owned by the user |
| `rating` | number | ✓ | No | 1–5 | Star rating |
| `comment` | string | — | Yes | max 2000 | Review text |

#### Response (201 Created)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Created successfully",
  "data": {
    "id": 1,
    "customerId": 1,
    "customerName": "Nguyen Van Loc",
    "productId": 1,
    "productName": "Áo thun basic nam",
    "variantId": 1,
    "variantName": "Trắng / M",
    "sku": "SKU-001",
    "orderItemId": 1,
    "rating": 5,
    "comment": "Sản phẩm tốt, đúng mô tả!",
    "status": "PENDING",
    "adminNote": null,
    "moderatedAt": null,
    "moderatedBy": null,
    "createdAt": "2026-04-19T08:00:00",
    "updatedAt": "2026-04-19T08:00:00"
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Review ID |
| `customerId` | number | No | Reviewer's customer ID |
| `customerName` | string | No | Reviewer's name |
| `productId` | number | No | Product ID |
| `productName` | string | No | Product name |
| `variantId` | number | No | Variant ID |
| `variantName` | string | No | Variant name |
| `sku` | string | No | Variant SKU |
| `orderItemId` | number | No | Source order item ID |
| `rating` | number | No | 1–5 rating |
| `comment` | string | Yes | Review text |
| `status` | string | No | `ReviewStatus` enum |
| `adminNote` | string | Yes | Moderation note from admin |
| `moderatedAt` | string | Yes | When moderated |
| `moderatedBy` | string | Yes | Who moderated |
| `createdAt` | string | No | ISO datetime |
| `updatedAt` | string | No | ISO datetime |

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `ORDER_NOT_FOUND` | Order item not found or not owned |
| 409 | `REVIEW_ALREADY_EXISTS` | Review already submitted for this order item |
| 422 | `REVIEW_NOT_ELIGIBLE` | Order not in COMPLETED status |

#### Notes

- Newly submitted reviews have `status: "PENDING"` and are not visible on the product page until approved by a moderator.

---

### 13.2 Get My Reviews

- **Endpoint:** `GET /api/v1/reviews/my`
- **Description:** Returns all reviews submitted by the current customer.
- **Auth:** Bearer Token
- **Allowed roles:** `CUSTOMER`

#### Query Parameters

| Param | Type | Description |
|---|---|---|
| `page` | number | Page number (0-based) |
| `size` | number | Page size |

#### Response (200 OK)

Paginated list of `ReviewResponse` objects.

---

### 13.3 Get Product Reviews (Public)

- **Endpoint:** `GET /api/v1/reviews/product/{productId}`
- **Description:** Returns approved reviews for a product. No authentication required.
- **Auth:** Public

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `productId` | number | Product ID |

#### Query Parameters

| Param | Type | Description |
|---|---|---|
| `minRating` | number | Optional. Minimum rating (1–5, inclusive) |
| `maxRating` | number | Optional. Maximum rating (1–5, inclusive) |
| `page` | number | Page number (0-based) |
| `size` | number | Page size |

#### Response (200 OK)

Paginated list of `ReviewResponse` objects with `status: "APPROVED"`.

---

## 14. Notifications

All notification endpoints require `Authorization: Bearer <token>` and `CUSTOMER` role.

### 14.1 Get My Notifications

- **Endpoint:** `GET /api/v1/notifications`
- **Auth:** Bearer Token

#### Query Parameters

| Param | Type | Description |
|---|---|---|
| `page` | number | Page number (0-based) |
| `size` | number | Page size |

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "items": [
      {
        "id": 1,
        "type": "ORDER_PLACED",
        "title": "Đặt hàng thành công",
        "body": "Đơn hàng ORD20260419000001 đã được đặt",
        "referenceId": 100,
        "referenceType": "ORDER",
        "read": false,
        "readAt": null,
        "createdAt": "2026-04-19T08:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 5,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

| Field | Type | Nullable | Description |
|---|---|:---:|---|
| `id` | number | No | Notification ID |
| `type` | string | No | `NotificationType` enum |
| `title` | string | No | Notification title |
| `body` | string | No | Notification body text |
| `referenceId` | number | Yes | ID of the referenced entity (e.g., orderId) |
| `referenceType` | string | Yes | Type of referenced entity (e.g., `"ORDER"`) |
| `read` | boolean | No | Whether notification has been read |
| `readAt` | string | Yes | When notification was read |
| `createdAt` | string | No | ISO datetime |

---

### 14.2 Get Unread Count

- **Endpoint:** `GET /api/v1/notifications/unread-count`
- **Auth:** Bearer Token

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": {
    "count": 3
  },
  "timestamp": "2026-04-19T08:00:00"
}
```

---

### 14.3 Mark Notification as Read

- **Endpoint:** `PATCH /api/v1/notifications/{id}/read`
- **Auth:** Bearer Token
- **Ownership rule:** Returns 404 if notification does not belong to current customer.

#### Path Parameters

| Param | Type | Description |
|---|---|---|
| `id` | number | Notification ID |

#### Response (200 OK)

`data` is the updated `NotificationResponse` with `read: true` and `readAt` set.

#### Response (ERROR)

| HTTP | ErrorCode | Trigger |
|---|---|---|
| 404 | `NOTIFICATION_NOT_FOUND` | Notification not found or not owned |

---

### 14.4 Mark All Notifications as Read

- **Endpoint:** `PATCH /api/v1/notifications/read-all`
- **Auth:** Bearer Token

#### Response (200 OK)

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request processed successfully",
  "data": null,
  "timestamp": "2026-04-19T08:00:00"
}
```

---

## 15. Enum Dictionaries

### UserStatus

| Value | Description |
|---|---|
| `ACTIVE` | Account active — can log in |
| `INACTIVE` | Deactivated — cannot log in |
| `LOCKED` | Locked — cannot log in |

### Gender

| Value | Description |
|---|---|
| `MALE` | Male |
| `FEMALE` | Female |
| `OTHER` | Other / Prefer not to say |

### AddressType

| Value | Description |
|---|---|
| `SHIPPING` | Delivery address for orders |
| `BILLING` | Invoice / receipt address |
| `BOTH` | Both shipping and billing |

### ProductStatus (filter only — app sees PUBLISHED only)

| Value | Description |
|---|---|
| `DRAFT` | Not visible to customers |
| `PUBLISHED` | Live on storefront |
| `ARCHIVED` | No longer sold |

### ProductVariantStatus

| Value | Description |
|---|---|
| `ACTIVE` | Available for purchase |
| `INACTIVE` | Unavailable |

### OrderStatus (state machine)

| Value | Description | Customer can cancel? |
|---|---|:---:|
| `PENDING` | Order placed, awaiting confirmation | ✓ |
| `AWAITING_PAYMENT` | Awaiting online payment | ✓ |
| `CONFIRMED` | Confirmed by staff | — |
| `PROCESSING` | Being packed/prepared | — |
| `SHIPPED` | Handed to carrier | — |
| `DELIVERED` | Delivered to customer | — |
| `COMPLETED` | Order completed | — |
| `CANCELLED` | Cancelled (terminal) | — |
| `REFUNDED` | Refunded (terminal) | — |

**Valid transitions:**

```
PENDING          → AWAITING_PAYMENT, CANCELLED
AWAITING_PAYMENT → CONFIRMED, CANCELLED
CONFIRMED        → PROCESSING, CANCELLED
PROCESSING       → SHIPPED
SHIPPED          → DELIVERED
DELIVERED        → COMPLETED
COMPLETED        → REFUNDED
```

### PaymentMethod

| Value | Description |
|---|---|
| `COD` | Cash on delivery |
| `ONLINE` | Online payment gateway |

### PaymentStatus (on Order)

| Value | Description |
|---|---|
| `PENDING` | Not yet paid |
| `PAID` | Payment confirmed |
| `FAILED` | Payment failed |
| `REFUNDED` | Payment refunded |

### PaymentRecordStatus (on Payment record)

| Value | Description |
|---|---|
| `PENDING` | Created, not yet initiated |
| `INITIATED` | Payment initiated with gateway |
| `PAID` | Confirmed paid |
| `FAILED` | Failed |
| `REFUNDED` | Full refund |
| `PARTIALLY_REFUNDED` | Partial refund |

### TransactionStatus

| Value | Description |
|---|---|
| `INITIATED` | Transaction started |
| `SUCCESS` | Successful |
| `FAILED` | Failed |
| `REFUNDED` | Refunded |
| `CANCELLED` | Cancelled |

### ShipmentStatus (state machine)

| Value | Description | Terminal? |
|---|---|:---:|
| `PENDING` | Awaiting pickup | — |
| `IN_TRANSIT` | In transit | — |
| `OUT_FOR_DELIVERY` | Out for delivery | — |
| `DELIVERED` | Delivered | ✓ |
| `FAILED` | Delivery failed | — |
| `RETURNED` | Returned to sender | ✓ |

### InvoiceStatus

| Value | Description |
|---|---|
| `ISSUED` | Invoice generated |
| `PAID` | Payment confirmed (terminal) |
| `VOIDED` | Cancelled (terminal) |

### ReviewStatus

| Value | Description |
|---|---|
| `PENDING` | Awaiting moderation — not visible publicly |
| `APPROVED` | Visible on product page |
| `REJECTED` | Hidden — customer notified |

### NotificationType

| Value | Trigger |
|---|---|
| `ORDER_PLACED` | Order successfully placed |
| `ORDER_CONFIRMED` | Order confirmed by staff |
| `ORDER_CANCELLED` | Order cancelled |
| `ORDER_SHIPPED` | Order handed to carrier |
| `ORDER_DELIVERED` | Order delivered |
| `ORDER_COMPLETED` | Order marked complete |
| `REVIEW_SUBMITTED` | Review submitted |
| `REVIEW_APPROVED` | Review approved |
| `REVIEW_REJECTED` | Review rejected |
| `PAYMENT_RECEIVED` | Payment confirmed |
| `PAYMENT_FAILED` | Payment failed |
| `VOUCHER_RECEIVED` | Voucher issued to customer |
| `SYSTEM` | System-level notification |

### DiscountType (for voucher response)

| Value | Description |
|---|---|
| `PERCENTAGE` | Percentage off (e.g., 10%) |
| `FIXED_AMOUNT` | Fixed amount off (e.g., 50,000 VND) |

---

## 16. Error Code Reference

| ErrorCode | HTTP | When |
|---|:---:|---|
| `INVALID_CREDENTIALS` | 401 | Wrong email/password on login |
| `TOKEN_EXPIRED` | 401 | Access token expired |
| `TOKEN_INVALID` | 401 | Malformed or blacklisted token |
| `REFRESH_TOKEN_INVALID` | 401 | Refresh token expired or invalid |
| `TOKEN_BLACKLISTED` | 401 | Token explicitly invalidated (after logout) |
| `ACCOUNT_DISABLED` | 403 | Account is INACTIVE or LOCKED |
| `FORBIDDEN` | 403 | Role insufficient for this action |
| `EMAIL_ALREADY_EXISTS` | 409 | Email taken (register) |
| `PHONE_ALREADY_EXISTS` | 409 | Phone taken |
| `USER_NOT_FOUND` | 404 | User lookup failed |
| `CUSTOMER_NOT_FOUND` | 404 | Customer profile not found |
| `ADDRESS_NOT_FOUND` | 404 | Address not found or not owned |
| `PRODUCT_NOT_FOUND` | 404 | Product not found or not published |
| `PRODUCT_INACTIVE` | 422 | Product not published |
| `PRODUCT_VARIANT_NOT_FOUND` | 404 | Variant not found |
| `PRODUCT_VARIANT_INACTIVE` | 422 | Variant not available |
| `INVENTORY_NOT_ENOUGH` | 422 | Insufficient stock |
| `VARIANT_OUT_OF_STOCK` | 422 | Variant has zero stock |
| `STOCK_RESERVATION_FAILED` | 422 | Reservation failed — retry |
| `CART_NOT_FOUND` | 404 | Cart not found |
| `CART_ITEM_NOT_FOUND` | 404 | Cart item not found |
| `ORDER_NOT_FOUND` | 404 | Order not found or not owned |
| `ORDER_EMPTY` | 422 | Attempt to checkout empty cart |
| `ORDER_CANNOT_CANCEL` | 422 | Status doesn't allow cancellation |
| `ORDER_STATUS_INVALID` | 422 | Invalid status transition |
| `PAYMENT_NOT_FOUND` | 404 | No payment record |
| `PAYMENT_FAILED` | 422 | Payment processing failed |
| `PAYMENT_ALREADY_PROCESSED` | 409 | Payment already PAID |
| `VOUCHER_NOT_FOUND` | 404 | Voucher code unknown |
| `VOUCHER_INVALID` | 422 | Voucher inactive or not yet started |
| `VOUCHER_EXPIRED` | 422 | Voucher past end date |
| `VOUCHER_MIN_ORDER_NOT_MET` | 422 | Cart below minimum order value |
| `VOUCHER_USAGE_LIMIT_EXCEEDED` | 422 | Global limit exhausted |
| `VOUCHER_USER_LIMIT_EXCEEDED` | 422 | Per-user limit reached |
| `VOUCHER_NOT_APPLICABLE` | 422 | Scope mismatch |
| `SHIPMENT_NOT_FOUND` | 404 | Shipment not yet created for order |
| `INVOICE_NOT_FOUND` | 404 | Invoice not yet created for order |
| `REVIEW_NOT_FOUND` | 404 | Review not found |
| `REVIEW_NOT_ELIGIBLE` | 422 | Order not COMPLETED or not owned |
| `REVIEW_ALREADY_EXISTS` | 409 | Already reviewed this order item |
| `NOTIFICATION_NOT_FOUND` | 404 | Notification not found or not owned |
| `VALIDATION_ERROR` | 422 | Request field validation failure |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server error |

---

## 17. Missing / Incomplete Endpoints

The following capabilities are expected by a typical customer app but are **not found in the current backend code**:

### High Priority

| # | Feature | Notes |
|---|---|---|
| 1 | `POST /auth/forgot-password` | Password reset by email — NOT FOUND IN CODE |
| 2 | `POST /auth/reset-password` | Apply reset token to set new password — NOT FOUND IN CODE |
| 3 | `POST /auth/change-password` | Authenticated password change — NOT FOUND IN CODE |
| 4 | `GET /products/{slug}` | Lookup product by slug (app typically uses slug in URLs) — NOT FOUND IN CODE |
| 5 | Avatar upload | `PATCH /me/avatar` or `POST /me/avatar` — `avatarUrl` field in profile has no upload endpoint |

### Medium Priority

| # | Feature | Notes |
|---|---|---|
| 6 | `GET /promotions` | List active promotions visible to customers — NOT FOUND IN CODE |
| 7 | Wishlist | `GET/POST/DELETE /wishlist` — NOT FOUND IN CODE (referenced in domain list) |
| 8 | `GET /me/loyalty-points` | Loyalty points history — field exists in profile but no dedicated endpoint |

### Low Priority / Future

| # | Feature | Notes |
|---|---|---|
| 11 | `GET /products/search` | Dedicated search endpoint (vs. filter on list) — NOT FOUND IN CODE |
| 12 | `GET /collections` / `GET /banners` | CMS content for homepage — NOT FOUND IN CODE |
| 13 | Push notification token registration | No endpoint to register device push token — NOT FOUND IN CODE |
| 14 | OAuth2 / Social login | Google/Facebook sign-in — NOT FOUND IN CODE |
| 15 | COD payment confirmation | No customer endpoint to confirm COD receipt — admin-only flow |
