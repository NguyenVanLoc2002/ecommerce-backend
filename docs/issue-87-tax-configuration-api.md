# VAT Configuration and Order Tax Estimate

The price model uses existing VAT-inclusive gross totals. When estimates are enabled, the order creation transaction snapshots effective tax-rate revision, rate date, original amounts, eligible merchandise allocation and separated shipping tax. For shipping the current source of truth is the amount charged to the customer. Existing checkout currently has no separate shipping discount field. Tax estimates do not modify the existing voucher, shipping fee, Order or Payment calculation.

## Admin APIs

The routes below use /api/v1/admin/tax. They require an active Admin or Super Admin account explicitly granted via app.tax.configurators UUIDs.

| Method | Path | Data |
| --- | --- | --- |
| GET | /categories | Tax category summaries and schedule revisions |
| POST | /categories | code, name, treatment (TAXABLE or NON_TAXABLE) |
| GET | /categories/{code} | Tax category with its current rate schedule |
| PUT | /categories/{code} | name, active |
| PUT | /categories/{code}/schedule | revision, rates [{fraction, effectiveFrom, effectiveUntil, active}] |
| GET, PUT | /assignments/{productId} | categoryCode, null for inherited shop default |
| GET | /audit/{subject}?before=0 | Latest 20 audit entries or previous entries before an audit ID |
| GET | /orders/{orderId}/estimate | currency, taxDate, gross, net, vat and immutable snapshot lines |

Rates are fractions (0.08 for 8%). Start is inclusive and end is exclusive. Use 2027-01-01 as the exclusive end for a period through 31/12/2026. A category row lock serializes schedule changes and protects old revisions from overwrites. Calculation checks both active category and exactly one effective rate. The contract keeps zero-rate and non-taxable treatments distinct.

## Rollout

- app.tax.order-estimates-enabled is false by default. Setting it to true requires complete configured coverage of the current date.
- app.tax.default-product-category defaults to STANDARD_REDUCIBLE when unset. Admin can override per Product. No per-SKU setup is necessary.
- app.tax.shipping-category must be assigned explicitly when a customer pays shipping.
- Default category identifiers are seeded. No effective rates, production public label or external invoice behavior are enabled.
- V62 is additive. Deploy in the agreed Flyway sequence after the #88 migrations.
- Each enabled estimate adds two indexed batch reads and one snapshot insert, plus the flush of already pending Order writes. Storefront list and detail reads are unaffected.
- The tax estimate is an interim record. It is not a final tax snapshot or an electronic invoice. Final transfer event, refund accounting, adjustment/replacement and production enablement require later validation.

The read-only `GET /orders/{orderId}/final` returns the separate final snapshot. The internal capture method requires an approved transfer event ID, timestamp and reference. Locking the Order serializes final capture. Replaying the same evidence returns the same snapshot. No application event is bound to this boundary pending confirmation. `app.tax.finalization-enabled` defaults to false.

## Dữ liệu kế toán và hóa đơn đã phát hành bên ngoài

Contract có thêm các đường dẫn dưới `/api/v1/admin/tax/invoices/{invoiceId}`:

- `GET /preparation` trả thông tin hóa đơn nội bộ, snapshot thuế chính thức và metadata hóa đơn đã ghi nhận, nếu có.
- `GET /preparation.csv` xuất dòng giá, chiết khấu, nhóm thuế, VAT và tổng từ snapshot chính thức.
- `PUT /external-record` lưu thông tin phát hành gồm `issuerReference`, `series`, `invoiceNumber`, `externalReference` và `issuedAt`. Retry cùng bằng chứng trả bản ghi đã có. Hóa đơn chỉ có một bản ghi gốc. Xung đột chứng từ trả 409.

Quyền xem và ghi nhận độc lập với quyền cấu hình thuế. Cấp UUID qua `app.tax.invoices.readers` và `app.tax.invoices.recorders`. Bật ghi nhận qua `app.tax.invoices.recording-enabled`, mặc định tắt. Phần này chuẩn bị và đối soát dữ liệu. Việc ghi metadata không phát hành chứng từ điện tử và không thay đổi trạng thái của hóa đơn nội bộ. Quy trình chứng từ điều chỉnh/thay thế và quyết toán refund chưa được kết nối.
