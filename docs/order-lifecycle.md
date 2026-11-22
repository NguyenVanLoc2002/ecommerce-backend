# Order Lifecycle

Order/customer snapshot, carrier-cost and durable provider-attempt design is tracked separately in [Order Management Contract and Safe Migration Proposal](order-management-contracts-proposal.md). That proposal does not change the state machine or payment/shipping policies described here.

Tài liệu mô tả vòng đời đơn hàng và rule chuyển trạng thái.

---

## 1. Order Status

```text
PENDING
AWAITING_PAYMENT
CONFIRMED
PROCESSING
SHIPPED
DELIVERED
COMPLETED
CANCELLED
RETURN_REQUESTED
RETURNING
RETURNED
REFUNDED
```

---

## 2. State Machine

Dưới đây là bảng chuyển trạng thái hợp lệ:

| Trạng thái hiện tại  | Trạng thái chuyển tiếp hợp lệ               | Ai thực hiện         |
|----------------------|---------------------------------------------|----------------------|
| **PENDING**          | AWAITING_PAYMENT, CONFIRMED (COD), CANCELLED | System / Customer / Admin (COD confirmation only) |
| **AWAITING_PAYMENT** | CONFIRMED, CANCELLED                        | Webhook / Admin (online payment paid) |
| **CONFIRMED**        | PROCESSING, CANCELLED                       | Admin / Staff        |
| **PROCESSING**       | SHIPPED                                     | Admin / Staff        |
| **SHIPPED**          | DELIVERED                                   | Admin / Staff        |
| **DELIVERED**        | COMPLETED, RETURN_REQUESTED                 | Customer / System    |
| **COMPLETED**        | RETURN_REQUESTED, REFUNDED                  | Customer / Admin     |
| **RETURN_REQUESTED** | RETURNING, CANCELLED (reject return)        | Admin / Staff        |
| **RETURNING**        | RETURNED                                    | Admin / Staff        |
| **RETURNED**         | REFUNDED                                    | Admin / Staff        |

**Lưu ý**: Không cho phép chuyển ngược hoặc bỏ qua trạng thái ngoài bảng trên.

---

## 3. Rule quan trọng

❗ **Không được phép**:

- Chuyển trạng thái ngược về trạng thái trước (ngoại trừ theo flow return)
- Skip qua các trạng thái trung gian
- Chuyển trạng thái khi điều kiện payment chưa thỏa mãn

---

## 4. Payment dependency

`CONFIRMED` chỉ được chuyển khi thỏa mãn điều kiện sau:

| Payment Method     | Điều kiện                          |
|--------------------|------------------------------------|
| COD                | Luôn cho phép confirm              |
| Online Payment     | payment_status = PAID              |

Quy tắc này áp dụng cho mọi request backend có thể đưa đơn online vào khâu xử lý hoặc vận chuyển, gồm `processOrder`, tạo shipment và cập nhật shipment sang `IN_TRANSIT`, `OUT_FOR_DELIVERY` hoặc `DELIVERED`. COD vẫn được giao khi payment `PENDING`; trạng thái giao hàng không tự đánh dấu đã thu COD.

---

## 5. Inventory dependency

| Sự kiện                  | Hành động với Inventory                |
|--------------------------|----------------------------------------|
| Tạo order / Checkout     | Reserve stock                          |
| Cancel order             | Release reserved stock                 |
| Complete order           | Commit stock (giảm on_hand)            |
| Return accepted          | Cộng lại on_hand (nếu hàng còn dùng được) |

---

## 6. COD Payment Flow

1. Customer chọn COD → tạo order `PENDING`
2. Admin/Staff xác nhận đơn → `CONFIRMED` (payment vẫn `PENDING`)
3. Staff đóng hàng → `PROCESSING`
4. Giao hàng → `SHIPPED`
5. Giao thành công → `DELIVERED`
6. Sau N ngày không khiếu nại → `COMPLETED`
7. Chỉ thao tác xác nhận thu tiền COD mới ghi nhận payment `PAID`; payment transaction lưu `referenceType=COD_COLLECTION` và actor audit.

---

## 7. Online Payment Flow

1. Customer checkout online → tạo order `AWAITING_PAYMENT` + tạo payment `INITIATED`
2. Chuyển hướng payment gateway
3. Gateway callback success → payment `PAID` → order `CONFIRMED`
4. Gateway callback fail / timeout → payment `FAILED` → order `CANCELLED`, release stock

Admin chỉ xác nhận trực tiếp đơn COD `PENDING`. Đơn online phải ở `AWAITING_PAYMENT` và có `paymentStatus=PAID` mới được xác nhận; online chưa thanh toán không thể confirm hoặc fulfillment. Xác nhận đơn COD không đánh dấu payment thành `PAID`; thu tiền vẫn là thao tác riêng.

---

## 8. Return / Refund Flow

1. Customer yêu cầu trả hàng → order `RETURN_REQUESTED`
2. Admin duyệt → `RETURNING` (gửi hướng dẫn trả hàng cho customer)
3. Nhận hàng trả về → `RETURNED`
4. Tạo yêu cầu hoàn tiền → refund `PENDING`; chỉ sau xác nhận chuyển tiền mới cập nhật payment summary.

**Partial refund**: Khi đã xác nhận hoàn một phần, payment và order payment summary là `PARTIALLY_REFUNDED`; khi xác nhận hoàn đủ mới là `REFUNDED`. Refund đang chờ vẫn giữ chỗ số dư nhưng không được tính là tiền đã hoàn.

---

## 9. API design

Theo API convention:

```text
POST /api/v1/orders/{id}/confirm
POST /api/v1/orders/{id}/cancel
POST /api/v1/orders/{id}/complete
POST /api/v1/orders/{id}/request-return
POST /api/v1/admin/orders/{id}/approve-return
POST /api/v1/admin/orders/{id}/mark-returned
POST /api/v1/admin/orders/{id}/refund
```

---

## 10. Business validation

- `ORDER_NOT_FOUND`
- `ORDER_STATUS_INVALID`
- `ORDER_CANNOT_CANCEL`
- `ORDER_CANNOT_RETURN` (ví dụ: quá thời hạn trả hàng)
- `PAYMENT_ALREADY_REFUNDED`

---

## 11. Transaction boundary

Service phải đảm bảo **3 thao tác** nằm trong cùng một transaction:

- Update order status
- Update inventory
- Update payment (nếu có)

---

## 12. Order expiry / Auto-cancel

Đơn online `AWAITING_PAYMENT` giữ hàng tối đa **30 phút** kể từ khi checkout tạo reservation.
Đơn COD `PENDING` có **24 giờ** để Admin xác nhận. Hết thời hạn chờ tương ứng,
job hủy đơn chưa thanh toán/chưa xác nhận, chuyển payment chưa thu sang `CANCELLED`
và release toàn bộ reservation trong
cùng giao dịch theo từng Order. Callback, webhook và job cùng khóa Order trước
khi đổi Payment hoặc reservation.

Thanh toán online được xác nhận trước hạn giữ nguyên reservation cho fulfillment;
thời hạn checkout không còn áp dụng sau đó. Đơn COD được xác nhận cũng giữ nguyên
reservation, không tự nhả theo thời hạn chờ Admin. Mọi đường fulfillment phải
kiểm tra đủ reservation trước khi tiến hành.

Nếu tiền đến sau hạn hoặc sau khi đơn bị hủy, ghi Payment `PAID` cùng giao dịch
`LATE_SUCCESS_AFTER_CANCEL`, giữ Order `CANCELLED` và báo Admin đối soát, hoàn tiền
thủ công. Không tự mở lại đơn hoặc tự giữ hàng. Hoàn tiền tự động cần một quy trình
riêng sau khi cổng thanh toán và nghiệp vụ đối soát hỗ trợ.

Scheduled job quét định kỳ; tại biên 30 phút, callback kiểm tra trực tiếp
reservation để không phụ thuộc độ trễ của job.

---

## 13. Audit log

Ghi log tất cả các hành động quan trọng:

- Thay đổi trạng thái đơn hàng (bao gồm actor)
- Hủy đơn hàng
- Yêu cầu trả hàng
- Hoàn tiền (refund)

---

## 14. Idempotency (Phase 3)

### Checkout (`POST /api/v1/orders`)

- Requires `Idempotency-Key` header.
- The PROCESSING record is committed in a `REQUIRES_NEW` transaction **before** the cart lock is acquired, so concurrent requests with the same key see the in-progress status and receive `409 IDEMPOTENCY_REQUEST_IN_PROGRESS` instead of racing through checkout.
- On COMPLETED replay: the existing order is returned without re-running checkout or touching inventory.
- On FAILED: checkout is retried automatically when the same key is resent (CHECKOUT is a retryable action).

### Order state-change actions (confirm, cancel, complete, return)

These actions are idempotent via the **state machine** — sending the same state-change twice results in `ORDER_STATUS_INVALID` on the second call (the first already moved the order forward). No client-side `Idempotency-Key` is required for admin actions.

### Payment callback

- Protected via `providerTxnId` uniqueness (DB constraint + application check), not `Idempotency-Key`.
- Duplicate SUCCESS callback: no-op, returns existing PAID payment.
- Stale FAILED callback after payment is PAID: rejected.
- Payment callback and order/shipment state changes serialize on the Order row, then the Payment row where both are needed.
- A successful callback after cancellation records the received funds as PAID but leaves the Order CANCELLED. Its transaction is marked `LATE_SUCCESS_AFTER_CANCEL` for reconciliation; it never reopens fulfillment.
- Cancelling an already-paid online order is rejected unless a separate refund flow is performed.

---

## 15. Anti-pattern cần tránh

❌ Update trực tiếp `status` trong repository hoặc DB  
❌ Bỏ qua validation state machine  
❌ Không đồng bộ inventory khi thay đổi trạng thái  
❌ Không ghi lại actor (ai đã thay đổi trạng thái)  
❌ Xử lý payment callback lặp lại (phải idempotent)

---

## 15. Future improvement

- Triển khai State Machine Engine (Spring StateMachine)
- Sử dụng Event-driven architecture (Kafka)
- Áp dụng Saga pattern cho payment + inventory
- Tự động gửi thông báo theo từng bước chuyển trạng thái


### Shipment milestone reconciliation (Issue #67)

See [carrier lifecycle and exception contract](carrier-integration-flow.md#shipment-milestone-reconciliation-issue-67).
Shipment responses add nullable `attention` (DELIVERY_FAILED, CARRIER_CANCELLED,
RETURNED_HANDLING), available to Admin and the owning Customer. Booking does not
mark the order SHIPPED; pickup does. COD collection remains a separate action.
