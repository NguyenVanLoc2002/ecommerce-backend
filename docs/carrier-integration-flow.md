# Carrier Integration Flow

Tai lieu nay mo ta luong carrier/shipment theo implementation backend hien tai, bao gom:

- carrier catalog va carrier config
- manual shipment flow
- provider-based shipment flow
- AhaMove production integration flow
- webhook flow
- luu y backward compatibility va van hanh production

Nguon tham chieu chinh trong code:

- `src/main/java/com/locnguyen/ecommerce/domains/carrier/**`
- `src/main/java/com/locnguyen/ecommerce/domains/shipment/**`
- `src/main/java/com/locnguyen/ecommerce/infrastructure/external/ahamove/**`
- `src/main/resources/db/migration/V26__create_carrier_infrastructure.sql`
- `src/main/resources/db/migration/V27__add_ahamove_provider_support.sql`
- `src/main/resources/db/migration/V29__add_typed_ahamove_config_fields.sql`

---

## 1. Muc tieu

He thong ho tro 2 kieu shipment:

- manual shipment: admin tu nhap `carrier`, `trackingNumber`, khong goi provider that
- provider-backed shipment: shipment gan voi `Carrier` va duoc tao/sync/huy thong qua `CarrierProvider`

Kien truc hien tai dat ra 4 muc tieu:

- khong hard-code logic provider vao `ShipmentServiceImpl`
- giu backward compatibility voi shipment manual cu
- cho phep nhieu provider cung dung chung mot abstraction
- tach biet carrier config, shipment lifecycle, va webhook processing

---

## 2. Thanh phan chinh

### 2.1 Carrier catalog

`Carrier` luu metadata noi bo:

- `code`
- `name`
- `providerType`
- `status`
- `logoUrl`
- `description`

Y nghia:

- `code` la ma on dinh de he thong nhan biet provider, vi du `AHAMOVE`
- `name` la ten hien thi
- `providerType` quyet dinh provider implementation duoc registry resolve

### 2.2 Carrier config

`CarrierConfig` luu cau hinh per-carrier:

- secrets encrypted:
  - `apiKeyEnc`
  - `secretKeyEnc`
  - `webhookSecretEnc`
- shared fields:
  - `baseUrl`
  - `enabled`
- typed AhaMove fields:
  - `providerAccountId`
  - `providerAccountPhone`
  - `providerBrandName`
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
- legacy compatibility:
  - `configJson`

Production note:

- typed fields la nguon chinh cho admin production flow
- `configJson` chi con la legacy mirror de runtime/provider code cu van hoat dong

### 2.3 Provider abstraction

Interface dung chung:

- `CarrierProvider`
  - `calculateRate(...)`
  - `createShipment(...)`
  - `cancelShipment(...)`
  - `getTracking(...)`
  - `mapStatus(...)`

Registry:

- `CarrierProviderRegistry`

Provider hien co:

- `ManualCarrierProvider`
- `MockCarrierProvider`
- `AhamoveCarrierProvider`

### 2.4 Shipment domain

`Shipment` giu ca du lieu noi bo lan du lieu provider:

- `carrier`
- `carrierEntity`
- `carrierShipmentId`
- `trackingNumber`
- `providerStatus`
- `providerTrackingUrl`
- `shippingFee`

### 2.5 Webhook persistence

Webhook log duoc luu o:

- `carrier_webhook_logs`

Muc dich:

- audit
- idempotency
- debug callback loi

---

## 3. Dieu kien truoc khi dung provider-backed flow

Truoc khi tao shipment qua provider that, can dam bao:

1. Co `Carrier` voi `providerType` dung, vi du `AHAMOVE`.
2. Co `CarrierConfig` tuong ung.
3. `CarrierConfig.enabled = true`.
4. Secret nhay cam da duoc ma hoa truoc khi luu.
5. Provider bean da duoc register trong `CarrierProviderRegistry`.
6. Don hang dang o trang thai cho phep tao shipment.

Neu thieu, service fail fast bang `AppException`.

---

## 4. Production admin flow cho AhaMove config

Day la luong moi de thay the viec admin phai nhap `configJson` tay.

### 4.1 Lay typed config

Admin goi:

- `GET /api/v1/admin/carriers/{id}/integration/ahamove`

Backend tra ve:

- secret flags: `hasApiKey`, `hasSecretKey`, `hasWebhookSecret`
- typed account/pickup/service fields
- health fields:
  - `connectionStatus`
  - `lastHealthCheckAt`
  - `lastHealthCheckError`
- `maskedWebhookToken`

### 4.2 Luu typed config

Admin goi:

- `PUT /api/v1/admin/carriers/{id}/integration/ahamove`

Request typed fields:

- `apiKey`, `secretKey`, `webhookSecret`
- `baseUrl`, `enabled`
- `phone`, `brandName`
- `pickupAddress`, `pickupShortAddress`, `pickupName`, `pickupPhone`
- `pickupLat`, `pickupLng`
- `defaultServiceCode`, `defaultPaymentMethod`

Backend xu ly step by step:

1. Resolve `Carrier` va validate provider phai la `AHAMOVE`.
2. Tao `CarrierConfig` neu chua co.
3. Encrypt secret neu request co truyen moi.
4. Persist typed fields vao dedicated columns.
5. Validate effective config neu `enabled=true`.
6. Build lai `configJson` mirror tu typed fields.
7. Save config.
8. Ghi audit log.

Production note:

- admin UI production nen dung endpoint typed nay
- `PUT /api/v1/admin/carriers/{id}/config` van ton tai de tuong thich nguoc, nhung khong con la primary flow

### 4.3 Test connection

Admin goi:

- `POST /api/v1/admin/carriers/{id}/integration/ahamove/test-connection`

Request co the override tam:

- `apiKey`
- `baseUrl`
- `phone`

Backend xu ly:

1. Copy persisted config thanh effective config.
2. Apply override neu request co gui.
3. Resolve effective config bang `AhamoveConfigResolver`.
4. Goi `AhamoveClient.verifyConnection(...)`.
5. Neu thanh cong:
   - cap nhat `connectionStatus = CONNECTED`
   - set `lastHealthCheckAt`
   - xoa `lastHealthCheckError`
6. Neu that bai:
   - cap nhat `connectionStatus = FAILED`
   - set `lastHealthCheckAt`
   - luu `lastHealthCheckError` da sanitize

### 4.4 Rotate webhook token

Admin goi:

- `POST /api/v1/admin/carriers/{id}/integration/ahamove/webhook-token`

Backend:

1. Generate random token an toan.
2. Encrypt token va luu vao `webhookSecretEnc`.
3. Build lai legacy `configJson` mirror neu can.
4. Tra raw token dung 1 lan trong response.

### 4.5 Lay huong dan webhook setup

Admin goi:

- `GET /api/v1/admin/carriers/{id}/integration/ahamove/webhook-setup`

Backend dung:

- `app.carrier.webhook-public-base-url`

de build:

- `https://<public-base-url>/api/v1/shipments/webhook/ahamove`

Response hien thi:

- `webhookUrl`
- `authHeader = X-Webhook-Token`
- `maskedWebhookToken`
- danh sach instruction de paste vao AhaMove portal

---

## 5. Cac cach resolve AhaMove config khi runtime

Flow resolve config hien tai:

1. Doc `CarrierConfig` tu DB.
2. Giai ma secret neu co.
3. Uu tien typed fields moi tren `CarrierConfig`.
4. Fallback sang legacy `configJson`.
5. Cuoi cung moi fallback sang `AhamoveProperties` neu DB dang thieu mot so field bootstrap.

Thu tu uu tien nay giup:

- production config luon uu tien DB
- khong buoc phai dep het legacy ngay lap tuc
- van giu duoc runtime cho implementation da ton tai

---

## 6. Manual shipment flow

Flow nay duoc giu nguyen de tuong thich nguoc.

Step by step:

1. Admin goi API tao shipment.
2. Request khong truyen `carrierId`, chi truyen `carrier` dang text.
3. `ShipmentServiceImpl.createShipment(...)` validate order.
4. Service tao `Shipment`.
5. `configureCarrier(...)` vao nhanh manual:
   - set `shipment.carrier`
   - khong resolve provider registry
6. Neu request co `trackingNumber`, luu truc tiep.
7. Shipment duoc save.

---

## 7. Provider-based shipment flow

Step by step:

1. Admin goi API tao shipment.
2. Request truyen `carrierId`, hoac order da co carrier snapshot tu customer checkout.
3. `ShipmentServiceImpl` resolve `Carrier`.
4. `CarrierProviderRegistry` tra ve provider theo `providerType`.
5. Service build provider request tu order/shipment.
6. Provider goi API carrier that.
7. Ket qua provider duoc map ve:
   - `carrierShipmentId`
   - `trackingNumber`
   - `providerTrackingUrl`
   - `providerStatus`
   - `shippingFee`
   - internal `ShipmentStatus`
8. Shipment event noi bo duoc tao neu can.
9. Shipment duoc save.

Luu y:

- `ShipmentServiceImpl` khong biet chi tiet AhaMove
- moi logic dac thu provider phai nam trong provider implementation

---

## 8. AhaMove flow cu the

### 8.1 Tinh phi

1. Resolve effective config.
2. Validate:
   - `baseUrl`
   - `apiKey`
   - `phone`
   - `pickupAddress`
   - du lieu nguoi nhan
3. Map request noi bo sang estimate request cua AhaMove.
4. Goi estimate API.
5. Map response ve `ShippingRateResult`.

### 8.2 Tao order tren AhaMove

1. Resolve config.
2. Map shipment + order sang create-order request.
3. Goi AhaMove create order API.
4. Nhan ve:
   - `orderId`
   - `trackingNumber` neu co
   - `sharedLink` neu co
   - `rawStatus`
5. Map ket qua ve `ShippingOrderResult`.
6. Persist vao shipment.

### 8.3 Sync tracking

1. Resolve config.
2. Goi AhaMove order-info API.
3. Map provider status sang internal status.
4. Build `ShipmentProviderUpdate`.
5. Goi `ShipmentService.applyProviderUpdate(...)`.

### 8.4 Huy shipment

1. Resolve config.
2. Goi AhaMove cancel API bang provider order id, hoac fallback theo tracking number neu can.
3. Neu thanh cong:
   - update shipment noi bo
   - set `providerStatus`
   - ghi shipment event

---

## 9. Webhook inbound flow

Webhook hien co cho AhaMove:

- `POST /api/v1/shipments/webhook/ahamove`

Route nay la public trong `SecurityConfig`, nhung van phai verify token truoc moi DB mutation.

Step by step:

1. Provider gui `POST` toi webhook endpoint.
2. Controller nhan raw body va raw headers.
3. `AhamoveWebhookService.receiveWebhook(...)` duoc goi.
4. Service resolve carrier `AHAMOVE` va `CarrierConfig`.
5. `AhamoveConfigResolver` resolve config de lay webhook token.
6. Verify token truoc moi update nghiep vu.
7. Parse payload.
8. Tao `eventKey` deterministic.
9. Kiem tra `carrier_webhook_logs` de chong duplicate.
10. Resolve shipment theo:
    - `carrierShipmentId`
    - fallback `trackingNumber`
11. Map provider status sang internal status.
12. Goi `shipmentService.applyProviderUpdate(...)`.
13. Cap nhat webhook log processed/error.

Token hien duoc chap nhan tu:

- `X-Webhook-Token`
- `apikey`
- `Authorization: Bearer <token>`

---

## 10. Status mapping cua AhaMove

Mapping hien tai:

| AhaMove status | Internal `ShipmentStatus` |
| --- | --- |
| `IDLE` | `PENDING` |
| `ASSIGNING` | `PENDING` |
| `ACCEPTED` | `PICKING` |
| `BOARDED` | `PICKING` |
| `IN_PROCESS` | `IN_TRANSIT` |
| `COMPLETING` | `OUT_FOR_DELIVERY` |
| `COMPLETED` | `DELIVERED` |
| `CANCELLED` | `FAILED` |
| `FAILED` | `FAILED` |
| `IN_RETURN` | `RETURNED` |
| `RETURNED` | `RETURNED` |

Rules:

- unknown status khong duoc lam crash processing
- khong duoc move shipment backward trong lifecycle
- `DELIVERED` duoc coi la terminal state

---

## 11. Persistence

Shipment persistence giu:

- `carrierId`
- `carrier`
- `carrierShipmentId`
- `trackingNumber`
- `providerStatus`
- `providerTrackingUrl`
- `shippingFee`

Webhook persistence giu:

- `carrier_code`
- `provider_order_id`
- `tracking_number`
- `event_type`
- `event_key`
- `sanitized_payload`
- `sanitized_headers`
- `processed`
- `error_message`

Carrier config persistence giu:

- encrypted secrets
- typed AhaMove fields
- health check fields
- legacy `configJson` mirror

---

## 12. Luu y production

### 12.1 Bao mat

- khong commit real credentials
- khong log api key, token, decrypted secret
- sanitize thong diep loi truoc khi persist hoac tra ve admin UI
- webhook token phai verify truoc moi business mutation

### 12.2 Backward compatibility

- manual shipment flow cu van duoc giu
- field `carrier` dang string van duoc support
- generic `/config` endpoint van ton tai
- provider runtime cu van co the doc `configJson` mirror

### 12.3 Gioi han domain hien tai

Day la gioi han nghiep vu quan trong:

- he thong chua co model pickup location rieng cho store/warehouse
- hien tai pickup info dang nam trong `CarrierConfig`
- neu sau nay co nhieu kho/nhieu diem lay hang, can tach pickup source khoi carrier config
- khong duoc fake toa do neu nghiep vu khong co du lieu that

### 12.4 Cac bien moi truong lien quan

- `app.carrier.config-encryption-key`
- `app.carrier.webhook-public-base-url`
- `app.carrier.ahamove.enabled`
- `app.carrier.ahamove.base-url`
- `app.carrier.ahamove.api-key`
- `app.carrier.ahamove.phone`
- `app.carrier.ahamove.brand-name`
- `app.carrier.ahamove.webhook-token`

Production khuyen nghi:

- secret that nen uu tien encrypted DB config hoac secret manager
- env property chi nen giu vai tro bootstrap/fallback

---

## 13. Checklist van hanh

Truoc khi bat production flow cho AhaMove:

1. Carrier `AHAMOVE` da ton tai va `status=ACTIVE`.
2. Typed AhaMove integration da du config day du.
3. `CarrierConfig.enabled = true`.
4. Da test connection thanh cong.
5. Da rotate webhook token.
6. `app.carrier.webhook-public-base-url` da duoc set dung domain public.
7. Da cau hinh webhook URL/token tren AhaMove portal.
8. Da test duplicate webhook.
9. Da test timeout/5xx cua provider.
10. Da test manual shipment flow cu khong bi anh huong.
