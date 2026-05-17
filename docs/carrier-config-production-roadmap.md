# Carrier Config Production Roadmap

Tai lieu nay de xuat thiet ke production-ready cho carrier configuration va admin UI onboarding, thay the cach tiep can hien tai dang expose qua nhieu chi tiet ky thuat nhu `code`, `providerType`, `configJson`, va raw webhook auth cho merchant/operator.

Nguon tham chieu chinh:

- source code hien tai trong `domains/carrier/**`, `domains/shipment/**`, `infrastructure/external/ahamove/**`
- [carrier-integration-flow.md](./carrier-integration-flow.md)
- [admin-api-contract.md](./admin-api-contract.md)
- AhaMove docs entry: https://developers.ahamove.com/docs/
- AhaMove staging portal/account screens da duoc doi chieu trong qua trinh phan tich

---

## 1. Problem Statement

Carrier config hien tai da chay duoc ve mat ky thuat, nhung chua phu hop production UX cho merchant/operator.

### 1.1 Van de cua thiet ke hien tai

- `Carrier` dang vua dong vai tro catalog noi bo, vua bi expose nhu mot object merchant phai tu tao/cap nhat.
- Merchant co the phai biet:
  - `code`
  - `providerType`
  - `baseUrl`
  - `configJson`
  - webhook auth details
- `configJson` dang giai quyet nhanh cho backend, nhung UX thuc te rat kem:
  - khach hang khong biet JSON la gi
  - rat de nhap sai key/value
  - khong co validate theo tung field nghiep vu
  - kho scale khi them provider moi
- Pickup information dang bi dat sai cho:
  - `CarrierConfig`
  thay vi dat vao store/warehouse/fulfillment location
- `Secret key` dang duoc show trong UI du backend AhaMove flow hien tai chua dung
- Webhook setup dang qua technical cho merchant:
  - URL
  - auth header
  - auth scheme
  - auth token

### 1.2 Tac dong xau neu giu nguyen

- onboarding provider cham va de sai
- support team phai can thiep thu cong
- kho mo rong sang GHN/GHTK/J&T vi moi provider se tiep tuc nhet them JSON
- kho phan quyen va audit tren tung field business
- kho tu dong hoa test connection / health check / wizard onboarding

---

## 2. Production Design Principles

Carrier config production-ready can tuan theo cac nguyen tac sau:

1. Merchant khong duoc bi buoc nhap JSON raw.
2. Merchant khong duoc bi buoc hieu metadata noi bo nhu `code` hay `providerType`.
3. Provider catalog phai la du lieu seed/noi bo cua he thong.
4. Store chi cau hinh "integration instance" cua provider, khong tao provider moi.
5. Pickup location phai thuoc domain store/warehouse/fulfillment, khong chon trong chuoi JSON.
6. Secret phai write-only, encrypted, co health check rieng.
7. Webhook onboarding phai theo wizard:
   - show callback URL
   - generate/copy token
   - test callback
8. Form phai validate theo field business, khong validate tren blob JSON.
9. Backward compatibility phai duoc giu trong rollout, khong pha config dang ton tai.

---

## 3. Target Architecture

### 3.1 Tach ro 3 lop du lieu

#### A. Provider Catalog

Day la catalog noi bo cua he thong, duoc seed san.

Vi du:

- `AHAMOVE`
- `GHN`
- `GHTK`
- `MANUAL`
- `MOCK`

Trach nhiem:

- xac dinh provider type
- logo/mac dinh
- display name mac dinh
- capability flags

Merchant khong phai tao moi catalog entry cho AhaMove.

#### B. Store Carrier Integration

Day la cau hinh "cua hang cua toi ket noi voi provider nao".

Trach nhiem:

- enable/disable provider cho store
- luu secret:
  - api key
  - webhook token/secret
  - secret key neu provider can
- luu default service mapping
- luu test/health status

#### C. Store Pickup Location / Fulfillment Location

Day la du lieu nghiep vu cua store, khong thuoc provider config.

Trach nhiem:

- dia chi lay hang
- pickup contact name
- pickup phone
- lat/lng neu co
- default warehouse/storefront/source location

Provider request khi create shipment se lay pickup info tu location nay, khong lay tu `configJson`.

---

## 4. Target Backend Model

### 4.1 Carrier catalog

Giu `Carrier`, nhung doi role:

- chi dung cho system/provider catalog
- seed san cho provider phat hanh cung san pham
- merchant khong duoc tao AhaMove bang tay

Khuyen nghi bo sung:

- `isSystemManaged`
- `supportsRealtimeQuote`
- `supportsCreateShipment`
- `supportsCancelShipment`
- `supportsWebhook`
- `supportsManualFallback`

### 4.2 Store carrier integration

Thay vi de `store_carrier_configs` giu mot blob JSON tong hop, nen chuyen sang model ro rang hon.

Khuyen nghi tao hoac tien hoa bang:

`store_carrier_integrations`

Field de xuat:

- `id`
- `carrier_id`
- `store_id` hoac tenant key neu sau nay multi-store
- `enabled`
- `api_key_enc`
- `secret_key_enc`
- `webhook_secret_enc`
- `base_url`
- `connection_status`
- `last_health_check_at`
- `last_health_check_error`
- `provider_account_id`
- `provider_account_phone`
- `provider_brand_name`
- `default_service_code`
- `default_payment_method`
- `default_pickup_location_id`
- `provider_metadata_json`

`provider_metadata_json` chi giu metadata nho, khong giu field merchant phai nhap thu cong hang ngay.

### 4.3 Pickup location

Khuyen nghi bo sung bang/domain:

`store_pickup_locations`

Field de xuat:

- `id`
- `store_id`
- `name`
- `contact_name`
- `contact_phone`
- `address_line`
- `ward`
- `district`
- `city`
- `postal_code`
- `lat`
- `lng`
- `is_default`
- `active`

Neu repo da co `Warehouse` va warehouse da du thong tin pickup, co the tai su dung warehouse thay vi tao bang moi.

### 4.4 Backward compatibility

Trong giai doan chuyen doi:

- van doc `configJson` neu integration chua duoc migrate
- uu tien field typed moi truoc
- them migration data/backfill sau khi UI moi san sang

---

## 5. Target Backend API Design

### 5.1 Khong dung CRUD carrier catalog cho merchant onboarding

Thay vi merchant dung:

- `POST /api/v1/admin/carriers`
- `PATCH /api/v1/admin/carriers/{id}`

nen seed san `AHAMOVE` va cho merchant dung API integration-oriented.

### 5.2 API de xuat cho admin UI

#### Provider catalog read-only

- `GET /api/v1/admin/carrier-providers`
  - list provider san co trong he thong
  - tra capability flags

#### Store integration onboarding

- `GET /api/v1/admin/carrier-integrations`
- `GET /api/v1/admin/carrier-integrations/{providerCode}`
- `PUT /api/v1/admin/carrier-integrations/{providerCode}`
- `POST /api/v1/admin/carrier-integrations/{providerCode}/test-connection`
- `POST /api/v1/admin/carrier-integrations/{providerCode}/generate-webhook-token`
- `GET /api/v1/admin/carrier-integrations/{providerCode}/webhook-setup`

#### Pickup location management

Neu tai su dung warehouse:

- `GET /api/v1/admin/warehouses?active=true`

Neu tach module pickup:

- `GET /api/v1/admin/pickup-locations`
- `POST /api/v1/admin/pickup-locations`
- `PATCH /api/v1/admin/pickup-locations/{id}`
- `PATCH /api/v1/admin/pickup-locations/{id}/default`

### 5.3 AhaMove integration-specific request DTO

Khong gui `configJson` raw.

De xuat request typed:

`UpdateAhamoveIntegrationRequest`

Field:

- `apiKey`
- `secretKey`
- `baseUrl`
- `enabled`
- `defaultServiceCode`
- `defaultPaymentMethod`
- `defaultPickupLocationId`
- `webhookSecret`

`TestAhamoveConnectionRequest`

Field:

- `apiKey` optional neu muon test truoc khi save
- `baseUrl`

`AhamoveWebhookSetupResponse`

Field:

- `webhookUrl`
- `authHeader`
- `authScheme`
- `authTokenMasked`
- `copyableAuthToken` chi tra ngay sau generate, khong tra lai trong read API

### 5.4 Health check / connect flow

Sau khi merchant nhap API key:

1. backend goi auth/token provider
2. neu provider co profile/account endpoint:
   - fetch account phone
   - fetch brand/store/account metadata
3. luu:
   - `provider_account_id`
   - `provider_account_phone`
   - `provider_brand_name`
4. cap nhat:
   - `connection_status = CONNECTED` hoac `FAILED`

Neu AhaMove khong expose du account profile:

- van phai co `test-connection` de xac nhan API key hop le
- auto-fill duoc gi thi auto-fill
- phan con lai lay tu store profile/pickup location noi bo

---

## 6. Shipment Creation Design

### 6.1 Pickup source phai lay tu domain location

Khi `createShipment(...)`:

1. resolve carrier integration
2. resolve `defaultPickupLocationId`
3. load pickup location
4. map pickup location vao request provider

Khong nen dung:

- `pickupAddress`
- `pickupPhone`
- `pickupName`

tu `configJson` ve lau dai.

### 6.2 Service selection

Merchant/operator nen chon tu dropdown typed:

- `BIKE`
- `EXPRESS`
- `VAN`
- ...

Khong nhap tay `groupServiceId` trong JSON.

Neu provider support service discovery API:

- sync service catalog
- luu service code/display name capability

Neu chua support:

- backend maintain enum/config static cho tung provider

### 6.3 Webhook verification

Webhook flow giu nguyen ve nguyen tac:

- public endpoint
- verify token/signature truoc mutate DB
- idempotent
- log sanitized payload

Nhung UI onboarding phai don gian hon:

- merchant chi copy callback URL
- merchant chi copy webhook token
- khong can tu hieu raw auth mechanics

---

## 7. Target Admin UI Design

### 7.1 Bo cuc moi

Khong hien "carrier catalog CRUD" nhu mot trang merchant phai tu tao provider.

Thay vao do:

#### Page 1: Shipping Providers

Card/list san:

- AhaMove
- GHN
- GHTK
- Manual

Moi card hien:

- status: connected / not connected / disabled
- capability
- last checked
- action:
  - connect
  - configure
  - disable

### 7.2 AhaMove configuration drawer/page

Section 1: Connection

- `API key`
- `Base URL`
- button `Test connection`
- ket qua:
  - Connected
  - Invalid API key
  - Timeout

Section 2: Provider account info

- `Account phone`
- `Brand name`

Neu fetch duoc tu provider:

- read-only hoac auto-filled

Neu khong fetch duoc:

- editable, nhung la field form binh thuong

Section 3: Default pickup source

- dropdown `Default pickup location`
- link `Manage pickup locations`

Section 4: Default shipment behavior

- dropdown `Default service`
- dropdown `Default payment method`
- toggle `Enable provider`

Section 5: Webhook setup

- readonly `Webhook URL`
- button `Generate token`
- copyable token
- short setup instructions
- optional `Test webhook`

### 7.3 Khong show raw JSON

Admin UI production khong show:

- `configJson` textarea
- `providerType` raw enum cho merchant
- `code` editable field cho provider built-in
- `Secret key` neu provider flow hien tai khong dung

### 7.4 UX validation

Validate tung field business:

- `API key` required
- `Base URL` valid URL
- `Default pickup location` required khi provider can create shipment
- `Default service` required

Inline error phai business-oriented:

- "Vui long chon dia chi lay hang mac dinh"
- "API key AhaMove khong hop le"

Khong dung error nhu:

- "configJson invalid"

---

## 8. Migration Strategy

### Phase 0: Freeze current contract assumptions

- danh dau `configJson` la legacy
- khong mo rong them key moi vao JSON neu khong bat buoc

### Phase 1: Backend typed fields + compatibility

- them typed integration fields / pickup location reference
- van support read fallback tu `configJson`

### Phase 2: Admin UI structured form

- an `configJson`
- render typed form
- save typed request
- backend tu map sang field moi
- co the tam thoi serialize song song vao `configJson` de rollback an toan

### Phase 3: Data backfill

- viet migration script/backfill job:
  - parse `configJson`
  - map sang typed columns
  - tao pickup location mac dinh neu can

### Phase 4: Shipment flow switch-over

- `AhamoveMapper` doc pickup tu location domain
- bo phu thuoc vao `pickupAddress` trong `configJson`

### Phase 5: Cleanup

- remove raw JSON from UI hoan toan
- giam fallback legacy
- sau khi on dinh moi xem xet remove phu thuoc `configJson`

---

## 9. Concrete Roadmap

## Phase A - Production UX Stabilization

Muc tieu: merchant khong phai nhap JSON nua, nhung backend van backward-compatible.

### Backend

- them DTO typed cho AhaMove integration update
- them API `test-connection`
- them API `webhook-setup`
- them API `generate-webhook-token`
- them typed validation
- giu fallback `configJson`

### Admin UI

- thay textarea JSON bang form typed
- an `code`, `providerType`, `secretKey` neu khong can
- them `Test connection`
- them `Webhook setup` section

### Acceptance

- merchant co the connect AhaMove ma khong phai viet JSON
- shipment create flow van chay
- config cu van doc duoc

## Phase B - Domain Correction

Muc tieu: dua pickup info ve dung domain.

### Backend

- tao `pickup_locations` hoac tai su dung `warehouses`
- them `default_pickup_location_id` vao integration
- `AhamoveMapper` lay pickup tu location

### Admin UI

- them pickup location selector
- them CRUD pickup location neu repo chua co module tuong duong

### Acceptance

- pickup info khong con nam o `configJson`
- co the thay doi pickup location ma khong dong vao provider secret

## Phase C - Provider Onboarding and Operations

Muc tieu: onboarding thuc su production-grade.

### Backend

- health status
- scheduled recheck optional
- capability flags
- masked secret status

### Admin UI

- providers dashboard
- connected/not connected/error states
- retry/test actions

### Acceptance

- support/ops co the biet integration dang healthy hay khong
- merchant khong can nho thong so ky thuat

## Phase D - Legacy Cleanup

Muc tieu: bo no ky thuat cu.

### Backend

- giam phu thuoc `configJson`
- bo field khong dung trong UI/API

### Admin UI

- remove legacy labels
- remove hidden migration fallback code

### Acceptance

- config moi typed 100%
- no raw JSON path in normal operations

---

## 10. Data Model Recommendation Summary

### Nen giu

- `Carrier` nhu system catalog
- encrypted secret storage
- provider registry abstraction
- webhook logs
- backward-compatible shipment manual flow

### Nen doi

- merchant khong tao/edit provider catalog built-in
- pickup location khong dat trong `configJson`
- service config khong nhap raw JSON
- webhook setup khong expose qua nhieu auth mechanics

### Nen bo khoi UI merchant

- `code`
- `providerType`
- raw `configJson`
- `secretKey` neu provider khong dung
- raw auth body/header mechanics neu co the suy ra tu provider preset

---

## 11. Risks and Mitigations

### Risk 1: Config hien tai dang nam trong `configJson`

Mitigation:

- fallback read during migration
- backfill typed columns

### Risk 2: Chua co pickup location domain day du

Mitigation:

- tam thoi map vao `Warehouse`
- neu warehouse chua du field, mo rong warehouse thay vi nhoi lai vao provider JSON

### Risk 3: Provider docs/API khong cho fetch day du account profile

Mitigation:

- test-connection van validate duoc API key
- account phone/brandName co the auto-fill mot phan va cho edit phan con lai

### Risk 4: Merchant da quen flow cu

Mitigation:

- rollout theo wizard moi
- giu fallback backend
- support import config cu

---

## 12. Recommended Next Implementation Order

Thu tu khuyen nghi de lam that:

1. Seed/fix provider catalog built-in
2. Thiet ke API typed cho AhaMove integration
3. An `configJson` khoi admin UI, thay bang structured form
4. Them `test-connection` + webhook setup wizard
5. Dua pickup location ve warehouse/pickup domain
6. Refactor `AhamoveMapper` bo doc pickup tu `configJson`
7. Backfill va cleanup legacy config

---

## 13. Final Recommendation

Khong nen tiep tuc coi `configJson` la giao dien cau hinh production cho merchant.

Huong production dung la:

- provider catalog duoc system quan ly
- merchant chi "ket noi provider"
- secret duoc luu an toan va test connection duoc
- pickup location thuoc domain store/warehouse
- webhook setup duoc wizard hoa
- UI merchant khong bao gio phai viet JSON raw

Roadmap toi uu cho repo hien tai la:

- Phase A ngay lap tuc de sua UX ma khong pha backend
- Phase B va C de dua mo hinh ve dung domain
- Phase D de don legacy sau khi van hanh on dinh
