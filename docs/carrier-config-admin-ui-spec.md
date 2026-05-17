# Carrier Config Admin UI Spec

Spec nay mo ta admin UI production-ready cho carrier configuration, tap trung vao merchant/operator experience, khong expose raw JSON va khong buoc nguoi dung hieu metadata ky thuat noi bo.

Tai lieu nay phu thuoc vao:

- [carrier-config-production-roadmap.md](./carrier-config-production-roadmap.md)
- [carrier-config-be-next-phase-spec.md](./carrier-config-be-next-phase-spec.md)
- [admin-api-contract.md](./admin-api-contract.md)

---

## 1. Objective

Admin UI moi phai dat duoc:

1. merchant/operator co the ket noi AhaMove ma khong can nhap `configJson`
2. merchant/operator khong can thao tac voi `code`, `providerType`, raw auth details
3. flow co `test connection`
4. flow co `webhook setup wizard`
5. flow cho phep chon pickup source typed thay vi viet chuoi tuy y

---

## 2. Current UX Problems

Màn hinh hien tai co cac van de:

1. hien field ky thuat qua som:
   - `API Key`
   - `Secret Key`
   - `Webhook Secret`
   - `Base URL`
   - `Config JSON`
2. `Config JSON` la textarea raw, khong merchant-friendly
3. merchant co the khong hieu:
   - `groupServiceId`
   - `paymentMethod`
   - `pickupPhone`
   - `pickupShortAddress`
4. webhook setup dang tach roi:
   - backend luu secret mot noi
   - AhaMove portal bat merchant copy nhieu field
5. khong co step "test API key"

---

## 3. UX Principles

1. Khong hien raw JSON trong flow chinh.
2. Khong cho merchant tao `AHAMOVE` carrier catalog bang tay.
3. Mot provider built-in nhu AhaMove phai xuat hien nhu mot integration card co san.
4. Form phai dung business labels, khong dung ten field ky thuat neu khong can.
5. Secret la write-only.
6. Webhook setup duoc wizard hoa.
7. Error message phai theo nghiep vu.

---

## 4. Information Architecture

### 4.1 Navigation

De xuat doi trang hien tai tu "Carriers" CRUD-heavy thanh:

- `Shipping Providers`

Hoac neu giu route cu:

- van route `/carriers`
- nhung UI doi tu CRUD list sang provider integrations dashboard

### 4.2 Main page layout

Trang `Shipping Providers` hien:

- AhaMove
- GHN
- GHTK
- Manual

Moi provider card hien:

- logo
- display name
- connection status
- enabled/disabled
- capabilities
- last health check
- action primary:
  - `Connect`
  - `Configure`
  - `Retry`
  - `Disable`

---

## 5. Screen Specification

## 5.1 Shipping Providers Dashboard

### Purpose

Cho merchant/operator nhin tong quan cac provider san co va tinh trang ket noi.

### Data source

- `GET /api/v1/admin/carrier-providers` hoac list catalog equivalent
- `GET /api/v1/admin/carrier-integrations`

### Card content

- `Provider name`
- `Status badge`
  - `Not connected`
  - `Connected`
  - `Connection failed`
  - `Disabled`
- `Capabilities`
  - real-time quote
  - create shipment
  - tracking sync
  - webhook
- `Last checked`
- actions

### Actions

- `Connect`
- `Configure`
- `View webhook setup`
- `Disable`

### Empty states

Khong co empty state theo nghia "no provider", vi provider built-in phai co san.

---

## 5.2 AhaMove Integration Drawer/Page

### Purpose

Trang/chuot drawer chinh de cau hinh AhaMove theo form typed.

### Sections

#### Section A. Connection

Fields:

- `API Key`
- `Base URL`

Actions:

- `Test connection`
- `Save`

Behavior:

- `Base URL` default san:
  - `https://partner-apistg.ahamove.com` tren staging
- secret khong duoc prefill bang raw value
- neu da ton tai secret:
  - show badge `Saved`
  - input de trong
  - helper text: "Leave blank to keep existing key"

Validation:

- `API Key` required neu chua co key luu truoc do
- `Base URL` required
- `Base URL` phai la URL hop le

#### Section B. Account Information

Fields:

- `Account phone`
- `Brand name`

Behavior:

- neu test connection fetch duoc data:
  - auto-fill
- van cho edit neu backend cho phep override

#### Section C. Default Pickup Source

Fields:

- `Default pickup location` dropdown

Secondary action:

- `Manage pickup locations`

Behavior:

- dropdown load tu API pickup locations/warehouses
- phai co 1 selected value khi enable provider

#### Section D. Default Shipment Settings

Fields:

- `Default service` dropdown
- `Default payment method` dropdown
- `Enable AhaMove for shipments` toggle

Behavior:

- `Default service` required khi enabled
- `Default payment method` optional voi sensible default

#### Section E. Webhook Setup

Read-only fields:

- `Webhook URL`
- `Authentication header`
- `Authentication scheme` neu co
- `Webhook token status`

Actions:

- `Generate new token`
- `Copy webhook URL`
- `Copy webhook token`

Optional:

- `Test webhook`

Help text:

- "Paste these values into AhaMove partner portal webhook settings."
- "Raw token is shown only once after generation."

---

## 6. Form Field Mapping

UI labels khuyen nghi:

- `API Key` -> `apiKey`
- `Base URL` -> `baseUrl`
- `Account phone` -> `phone`
- `Brand name` -> `brandName`
- `Pickup address` khong cho nhap text tu do neu da co pickup location domain
- `Default pickup location` -> `defaultPickupLocationId`
- `Default service` -> `defaultServiceCode`
- `Default payment method` -> `defaultPaymentMethod`
- `Webhook token` -> `webhookSecret` qua generate flow

Khong hien:

- `configJson`
- `providerType`
- editable `code` cho provider built-in
- `Secret key` neu backend/provider hien tai khong su dung

---

## 7. API Dependencies

Admin UI spec nay ky vong backend co cac endpoint sau:

- `GET /api/v1/admin/carrier-integrations/{providerCode}`
- `PUT /api/v1/admin/carrier-integrations/{providerCode}`
- `POST /api/v1/admin/carrier-integrations/{providerCode}/test-connection`
- `POST /api/v1/admin/carrier-integrations/{providerCode}/generate-webhook-token`
- `GET /api/v1/admin/carrier-integrations/{providerCode}/webhook-setup`
- `GET /api/v1/admin/pickup-locations`

Neu backend phase dau van expose theo `carrierId` thay vi `providerCode`, frontend co the map theo `carrierId`.

---

## 8. State Design

### 8.1 Page state

Cho moi provider card:

- `idle`
- `loading`
- `connected`
- `failed`
- `disabled`

### 8.2 Form state

Track rieng:

- initial values
- dirty state
- save loading
- test connection loading
- webhook token generation loading

### 8.3 Secret state

Khong bao gio luu raw token/apiKey lau hon muc can thiet trong client state.

Rules:

- raw webhook token chi giu tam de show/copy sau generate
- khi user close modal/drawer, xoa raw token khoi state

---

## 9. Validation and Error UX

### 9.1 Client validation

Validate truoc submit:

- required fields khi enabled
- URL syntax
- number format cho lat/lng neu field nay con xuat hien

### 9.2 Server error mapping

Map error backend thanh message de hieu:

- `CARRIER_CONFIG_MISSING`
  - "Thong tin cau hinh AhaMove chua day du."
- `CARRIER_REQUEST_FAILED`
  - "Khong the ket noi AhaMove. Vui long kiem tra API key va Base URL."
- `VALIDATION_ERROR`
  - show field-level mapping neu duoc

### 9.3 Test connection UX

Success:

- green badge `Connected`
- timestamp cap nhat

Failure:

- red badge `Connection failed`
- hien sanitized message
- giu lai gia tri form de user sua

---

## 10. Webhook Wizard UX

### 10.1 Goal

Merchant/operator khong can tu suy nghi:

- `Auth Header`
- `Auth Scheme`
- `Auth Body`

Chi can copy-paste cac gia tri backend dua ra.

### 10.2 Flow

1. User mo section `Webhook setup`
2. UI goi `GET webhook-setup`
3. UI hien:
   - webhook URL
   - header name
   - scheme neu co
4. User bam `Generate token`
5. UI goi `POST generate-webhook-token`
6. UI show modal success:
   - token raw
   - copy button
   - warning "shown once"
7. UI hien checklist:
   - Step 1: open AhaMove portal
   - Step 2: paste Webhook URL
   - Step 3: paste Auth Header
   - Step 4: paste Auth Token
   - Step 5: save in portal

### 10.3 Optional enhancement

Them button:

- `Mark webhook as configured`
- `Test callback later`

---

## 11. Pickup Location UX

Neu phase tiep theo da co pickup location domain:

- UI khong cho nhap `pickupAddress` trong AhaMove form nua
- UI thay bang:
  - dropdown pickup locations
  - preview panel:
    - contact name
    - phone
    - full address

Neu backend phase truoc chua xong pickup domain:

- tam thoi co the render sub-form typed:
  - `Pickup address`
  - `Pickup short address`
  - `Pickup contact name`
  - `Pickup contact phone`
  - `Pickup latitude`
  - `Pickup longitude`

Nhung van khong duoc render raw JSON.

---

## 12. Table/List Behavior

Neu van giu trang list carrier:

- provider built-in row khong cho xoa
- provider built-in khong cho sua `code`
- row action:
  - `Configure`
  - `Webhook setup`
  - `Disable`

Khong de merchant thay action:

- `Create carrier`

cho case AhaMove/GHN/GHTK built-in.

Neu can giu cho internal ops:

- an action bang role hoac feature flag

---

## 13. Accessibility

UI moi phai:

- support keyboard navigation trong drawer/modal
- labels ro rang cho secret fields
- copy buttons co accessible label
- status badges co text, khong chi mau sac
- validation messages gan voi field dung

---

## 14. Responsive Behavior

### Desktop

- dashboard cards hien 2-3 cot
- configuration mo drawer ben phai hoac page detail

### Tablet/mobile

- card 1 cot
- configuration dung full-screen dialog/page
- webhook setup section co sticky copy actions neu can

---

## 15. Analytics / Audit Suggestions

Neu admin frontend da co event tracking, nen ghi nhan:

- provider configuration opened
- test connection clicked
- test connection success/failure
- webhook token generated
- config saved

Khong bao gio gui raw secret/token vao analytics.

---

## 16. Testing Scope For Admin UI

### Unit/component tests

Them hoac cap nhat test cho:

1. provider card render dung state
2. AhaMove config form render typed fields, khong render raw JSON textarea
3. test-connection mutation call dung endpoint
4. save form gui typed payload dung shape
5. generate webhook token hien raw token mot lan
6. close modal clears raw token state
7. pickup location dropdown renders selected option
8. disable provider toggle flow

### Integration tests

1. connect AhaMove happy path
2. failed API key path
3. reopen config after save -> secret inputs blank but status badges still hien `Saved`
4. webhook setup copy flow

### E2E preferred flow

1. vao `Shipping Providers`
2. mo `AhaMove`
3. nhap API key
4. test connection success
5. chon pickup location
6. chon default service
7. save
8. generate webhook token

---

## 17. Acceptance Criteria

Admin UI spec nay duoc xem la dat khi:

1. merchant/operator co the connect AhaMove ma khong phai nhap JSON
2. khong can hieu `code`, `providerType`, `configJson`
3. co `Test connection` ro rang
4. co `Webhook setup` wizard ro rang
5. secret la write-only
6. provider status/health check duoc hien thi
7. pickup source duoc chon bang field typed

---

## 18. Rollout Notes

### Phase UI-1

- giu route cu
- doi modal config AhaMove thanh structured form
- an `configJson` va field ky thuat khoi UI

### Phase UI-2

- them dashboard provider cards
- giam su phu thuoc vao CRUD carrier list

### Phase UI-3

- tich hop pickup location module/day du
- them health and retry UX

---

## 19. Final Recommendation

UI production cho carrier config khong nen la "mot modal generic cho moi provider" voi:

- `code`
- `name`
- `providerType`
- `configJson`

Huong dung la:

- built-in provider cards
- form typed theo tung provider
- onboarding wizard
- webhook setup co copy-paste instructions
- pickup source chon tu du lieu cua hang/he thong

Spec nay duoc viet de frontend co the implement ma khong phai doan y backend hay duy tri UX mang tinh ky thuat noi bo.
