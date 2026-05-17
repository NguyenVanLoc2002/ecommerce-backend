# Carrier Config Backend Next Phase Spec

Spec nay chot pham vi implementation backend cho phase tiep theo cua carrier configuration theo huong production-ready, merchant-friendly, va backward-compatible.

Tai lieu nen duoc doc cung voi:

- [carrier-config-production-roadmap.md](./carrier-config-production-roadmap.md)
- [carrier-integration-flow.md](./carrier-integration-flow.md)
- [admin-api-contract.md](./admin-api-contract.md)
- source code trong `domains/carrier/**`, `domains/shipment/**`, `infrastructure/external/ahamove/**`

---

## 1. Objective

Muc tieu cua phase nay:

1. bo giao dien/config raw `configJson` khoi flow onboarding chinh
2. them API typed de admin UI cau hinh AhaMove ma khong can biet JSON
3. giu backward compatibility voi du lieu `configJson` da ton tai
4. bo sung workflow:
   - test connection
   - webhook setup
   - generate webhook token
5. chuan bi domain cho pickup location typed o phase sau

Phase nay chua can:

- xoa `configJson` khoi DB
- doi toan bo shipment create flow sang pickup-location domain moi
- rewrite provider catalog module toan dien

---

## 2. Non-Goals

Khong lam trong phase nay:

- khong bo schema cu ngay lap tuc
- khong edit existing Flyway migration
- khong xoa admin carrier CRUD cu neu no dang duoc repo hoac test su dung
- khong bat buoc implement service discovery API tu AhaMove neu docs chua ro
- khong refactor customer checkout flow

---

## 3. Current Problems To Solve

Backend hien tai co cac van de sau:

1. `UpdateCarrierConfigRequest` chi co:
   - `apiKey`
   - `secretKey`
   - `webhookSecret`
   - `baseUrl`
   - `enabled`
   - `configJson`
2. `configJson` dang chua:
   - `phone`
   - `brandName`
   - `pickupAddress`
   - `pickupShortAddress`
   - `pickupName`
   - `pickupPhone`
   - `pickupLat`
   - `pickupLng`
   - `groupServiceId`
   - `paymentMethod`
   - `groupRequests`
3. UI buoc merchant/operator phai hieu JSON va key noi bo
4. khong co API typed de test API key
5. khong co API wizard de setup webhook

---

## 4. Phase Scope

Phase nay se implement 4 cum thay doi:

1. typed integration request/response DTOs cho AhaMove
2. typed admin APIs cho onboarding/cau hinh AhaMove
3. persistence compatibility layer:
   - save typed fields
   - mirror vao `configJson` trong giai doan chuyen tiep
4. service logic:
   - test connection
   - webhook token generation
   - webhook setup response

---

## 5. Data Model Strategy

### 5.1 Keep current tables for this phase

Van giu:

- `carriers`
- `store_carrier_configs`

Van doc:

- `api_key_enc`
- `secret_key_enc`
- `webhook_secret_enc`
- `base_url`
- `enabled`
- `config_json`

### 5.2 Add typed columns in a new migration

Them migration moi, vi du:

- `V29__add_typed_ahamove_config_fields.sql`

De xuat cot them vao `store_carrier_configs`:

- `provider_account_phone` `VARCHAR(50) NULL`
- `provider_brand_name` `VARCHAR(200) NULL`
- `pickup_address` `VARCHAR(500) NULL`
- `pickup_short_address` `VARCHAR(255) NULL`
- `pickup_name` `VARCHAR(200) NULL`
- `pickup_phone` `VARCHAR(50) NULL`
- `pickup_lat` `DECIMAL(10,7) NULL`
- `pickup_lng` `DECIMAL(10,7) NULL`
- `default_service_code` `VARCHAR(100) NULL`
- `default_payment_method` `VARCHAR(50) NULL`
- `connection_status` `VARCHAR(50) NULL`
- `last_health_check_at` `DATETIME(6) NULL`
- `last_health_check_error` `VARCHAR(1000) NULL`

Co the them:

- `provider_account_id` `VARCHAR(100) NULL`

Khong can them `webhook_url` vao DB vi backend co the derive tu env/domain config.

### 5.3 Compatibility rule

Rule resolve config:

1. uu tien typed columns moi
2. neu typed columns null thi fallback sang `configJson`
3. neu `configJson` cung thieu thi fallback sang env properties neu logic hien tai dang cho phep

Phase nay chua xoa fallback cu.

---

## 6. Domain / Entity Changes

### 6.1 CarrierConfig entity

Cap nhat [CarrierConfig.java](/T:/Project/ecommerce-backend/src/main/java/com/locnguyen/ecommerce/domains/carrier/entity/CarrierConfig.java:1):

Them typed fields moi tu migration V29.

### 6.2 Typed AhaMove view model

Them class typed de doc/ghi config AhaMove:

- `AhamoveIntegrationSettings`

Field:

- `phone`
- `brandName`
- `pickupAddress`
- `pickupShortAddress`
- `pickupName`
- `pickupPhone`
- `pickupLat`
- `pickupLng`
- `defaultServiceCode`
- `defaultPaymentMethod`

Class nay dung trong service va mapper, khong expose entity truc tiep.

---

## 7. Admin API Design

Phase nay khong xoa admin carrier APIs cu, nhung them AhaMove-specific onboarding APIs de UI moi dung.

### 7.1 New endpoints

De xuat them vao `AdminCarrierController` hoac mot controller moi neu muon tach:

#### GET `/api/v1/admin/carriers/{id}/integration/ahamove`

Description:

- lay cau hinh typed cua carrier AhaMove

Response:

- `ApiResponse<AhamoveIntegrationResponse>`

#### PUT `/api/v1/admin/carriers/{id}/integration/ahamove`

Description:

- save typed AhaMove settings

Request:

- `UpdateAhamoveIntegrationRequest`

#### POST `/api/v1/admin/carriers/{id}/integration/ahamove/test-connection`

Description:

- validate API key/base URL va luu health check status

Request:

- `TestAhamoveConnectionRequest`

Response:

- `ApiResponse<AhamoveConnectionTestResponse>`

#### POST `/api/v1/admin/carriers/{id}/integration/ahamove/webhook-token`

Description:

- generate va save webhook token moi

Response:

- `ApiResponse<AhamoveWebhookTokenResponse>`

#### GET `/api/v1/admin/carriers/{id}/integration/ahamove/webhook-setup`

Description:

- tra thong tin setup webhook de UI render wizard

Response:

- `ApiResponse<AhamoveWebhookSetupResponse>`

### 7.2 DTOs

#### `UpdateAhamoveIntegrationRequest`

Fields:

- `apiKey`: optional write-only
- `secretKey`: optional write-only
- `baseUrl`: optional, required neu chua co
- `enabled`: optional
- `phone`: optional
- `brandName`: optional
- `pickupAddress`: required khi `enabled=true`
- `pickupShortAddress`: optional
- `pickupName`: optional
- `pickupPhone`: optional
- `pickupLat`: optional
- `pickupLng`: optional
- `defaultServiceCode`: required khi `enabled=true`
- `defaultPaymentMethod`: optional
- `webhookSecret`: optional write-only

#### `AhamoveIntegrationResponse`

Fields:

- `carrierId`
- `carrierCode`
- `carrierName`
- `enabled`
- `baseUrl`
- `hasApiKey`
- `hasSecretKey`
- `hasWebhookSecret`
- `phone`
- `brandName`
- `pickupAddress`
- `pickupShortAddress`
- `pickupName`
- `pickupPhone`
- `pickupLat`
- `pickupLng`
- `defaultServiceCode`
- `defaultPaymentMethod`
- `connectionStatus`
- `lastHealthCheckAt`
- `lastHealthCheckError`

#### `TestAhamoveConnectionRequest`

Fields:

- `apiKey`: optional
- `baseUrl`: optional
- `phone`: optional

Neu request khong gui field nao thi service test tren config dang luu.

#### `AhamoveConnectionTestResponse`

Fields:

- `success`
- `status`
- `message`
- `resolvedBaseUrl`
- `resolvedPhone`
- `providerAccountPhone` optional
- `providerBrandName` optional

#### `AhamoveWebhookTokenResponse`

Fields:

- `token`
- `maskedToken`
- `generatedAt`

Luu y:

- chi tra raw token ngay luc moi generate
- khong tra raw token trong get integration response thong thuong

#### `AhamoveWebhookSetupResponse`

Fields:

- `webhookUrl`
- `authHeader`
- `authScheme`
- `hasWebhookToken`
- `maskedWebhookToken`
- `instructions`

Mac dinh:

- `authHeader = X-Webhook-Token`
- `authScheme = null`

---

## 8. Service Design

### 8.1 New service methods

Them vao `CarrierService` hoac tach `AhamoveIntegrationService` neu muon clean separation.

De xuat methods:

- `AhamoveIntegrationResponse getAhamoveIntegration(UUID carrierId)`
- `AhamoveIntegrationResponse updateAhamoveIntegration(UUID carrierId, UpdateAhamoveIntegrationRequest request)`
- `AhamoveConnectionTestResponse testAhamoveConnection(UUID carrierId, TestAhamoveConnectionRequest request)`
- `AhamoveWebhookTokenResponse generateAhamoveWebhookToken(UUID carrierId)`
- `AhamoveWebhookSetupResponse getAhamoveWebhookSetup(UUID carrierId)`

### 8.2 Update flow

Khi `updateAhamoveIntegration(...)`:

1. validate carrier ton tai va `providerType = AHAMOVE`
2. load/create `CarrierConfig`
3. update write-only secrets neu request co gui
4. update typed columns
5. normalize base URL
6. validate field business:
   - pickupAddress required neu enable provider
   - defaultServiceCode required neu enable provider
   - phone/pickupPhone required theo logic mapper hien tai
7. mirror typed values vao `configJson` legacy de compatibility
8. save config
9. return typed response

### 8.3 Legacy mirror strategy

Trong phase nay, backend se continue serialize typed settings vao `configJson`.

Vi du internal method:

- `String buildLegacyAhamoveConfigJson(CarrierConfig config)`

Muc dich:

- `AhamoveConfigResolver` cu va cac path cu van chay
- rollback an toan neu UI moi chua rollout het

### 8.4 Connection test flow

`testAhamoveConnection(...)`:

1. resolve effective config:
   - request overrides > saved config
2. validate co `baseUrl`, `apiKey`
3. goi `AhamoveClient.authenticate(...)`
4. neu thanh cong:
   - `connectionStatus = CONNECTED`
   - `lastHealthCheckAt = now`
   - `lastHealthCheckError = null`
5. neu that bai:
   - `connectionStatus = FAILED`
   - `lastHealthCheckAt = now`
   - `lastHealthCheckError = sanitized message`

Neu co profile/account endpoint kha dung trong docs/provider:

- fetch them account data
- fill `providerAccountPhone`, `providerBrandName`, `providerAccountId`

Neu khong co:

- chi validate duoc auth

### 8.5 Webhook token generation

`generateAhamoveWebhookToken(...)`:

1. tao token random dai, vi du 32-48 bytes base64url
2. encrypt va save vao `webhook_secret_enc`
3. return raw token mot lan duy nhat

### 8.6 Webhook setup response

`getAhamoveWebhookSetup(...)`:

1. build callback URL tu application config
2. tra `authHeader = X-Webhook-Token`
3. tra `maskedWebhookToken`
4. tra instruction strings de UI co the hien thi wizard

Can them application property typed:

- `app.public-base-url`

hoac provider-specific:

- `app.carrier.webhook-public-base-url`

De backend derive:

- `{publicBaseUrl}/api/v1/shipments/webhook/ahamove`

---

## 9. Resolver Refactor

### 9.1 AhamoveConfigResolver

Refactor [AhamoveConfigResolver.java](/T:/Project/ecommerce-backend/src/main/java/com/locnguyen/ecommerce/infrastructure/external/ahamove/AhamoveConfigResolver.java:1):

Resolve order moi:

1. typed columns
2. legacy `configJson`
3. env properties fallback

Muc tieu:

- typed fields tro thanh primary source of truth
- `configJson` chi con la compatibility layer

### 9.2 Secret rules

- `apiKeyEnc`, `secretKeyEnc`, `webhookSecretEnc` van la nguon truth cho secrets
- khong log decrypted value
- responses chi tra boolean flags/ masked values

---

## 10. Validation Rules

### 10.1 Business validation

Khi `enabled=true`:

- `baseUrl` required
- `apiKey` hoac stored apiKey required
- `phone` required
- `pickupAddress` required
- `pickupPhone` required
- `defaultServiceCode` required

### 10.2 Soft validation

Optional:

- `brandName`
- `pickupShortAddress`
- `pickupName`
- `pickupLat`
- `pickupLng`
- `defaultPaymentMethod`

### 10.3 Error handling

Reuse `AppException/ErrorCode`.

Them moi neu can:

- `CARRIER_PROVIDER_INVALID`
- `CARRIER_CONNECTION_TEST_FAILED`
- `CARRIER_WEBHOOK_TOKEN_MISSING`

Neu khong can, dung `VALIDATION_ERROR`, `CARRIER_CONFIG_MISSING`, `CARRIER_REQUEST_FAILED`.

---

## 11. Security Requirements

1. khong return raw secrets trong get APIs
2. raw webhook token chi tra ngay sau generate
3. redact token/api key trong logs
4. sanitize health-check error messages
5. khong cho test-connection ghi de decrypted secret ra response

---

## 12. Testing Scope

### 12.1 Unit tests

Them test cho:

- `AhamoveIntegrationServiceTest` hoac `CarrierServiceTest`
  - get typed integration response
  - update typed integration -> save typed columns
  - update typed integration -> mirror legacy `configJson`
  - enable config but missing pickupAddress -> fail
  - test connection success
  - test connection failure
  - generate webhook token stores encrypted secret
  - webhook setup response builds correct URL

### 12.2 Controller tests

Them test cho:

- `AdminCarrierControllerTest` hoac controller moi
  - `GET /integration/ahamove`
  - `PUT /integration/ahamove`
  - `POST /integration/ahamove/test-connection`
  - `POST /integration/ahamove/webhook-token`
  - `GET /integration/ahamove/webhook-setup`

### 12.3 Compatibility tests

Them regression test:

- typed columns null + legacy `configJson` exists -> resolver van doc duoc
- typed columns co gia tri -> typed override legacy JSON

### 12.4 Validation commands

Chay:

- `.\mvnw.cmd -DskipTests compile`
- `.\mvnw.cmd test`
- `.\mvnw.cmd verify`

---

## 13. Implementation Steps

1. them migration V29 cho typed columns
2. cap nhat `CarrierConfig` entity
3. them DTOs typed cho AhaMove integration
4. them service methods moi
5. refactor `AhamoveConfigResolver`
6. them connection test implementation
7. them webhook token / setup implementation
8. them controller endpoints
9. them tests
10. update docs/contracts

---

## 14. Acceptance Criteria

Phase backend nay duoc xem la xong khi:

1. admin UI co the save AhaMove config ma khong gui `configJson`
2. backend persist typed fields va van mirror legacy JSON
3. co API `test-connection`
4. co API `generate-webhook-token`
5. co API `webhook-setup`
6. `AhamoveConfigResolver` uu tien typed fields
7. full test suite pass

---

## 15. Follow-up After This Phase

Phase tiep theo sau spec nay:

1. dua pickup location ve warehouse/pickup-location domain thuc su
2. bo pickup fields khoi `store_carrier_configs`
3. bo phu thuoc `configJson` trong provider mapper
4. giam pham vi CRUD carrier catalog cho merchant-facing flows
