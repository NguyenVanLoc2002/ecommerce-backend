# Cấu hình tuyến dự kiến giao hàng

Mỗi tuyến gồm carrier, dịch vụ, kho, tỉnh và xã tùy chọn. Ngày bắt đầu được tính bao gồm. Ngày kết thúc không bao gồm. Tuyến theo xã ưu tiên hơn tuyến theo tỉnh. Trong cùng phạm vi, số priority nhỏ hơn được ưu tiên. Hai tuyến active cùng phạm vi và priority không thể chồng lấn. Sửa lịch hoặc điều kiện tuyến tạo revision cùng audit. Định danh tuyến được giữ khi chỉnh sửa.

Quyền quản trị được cấp theo UUID trong `app.shipping.delivery-rules.managers`, độc lập với quyền công bố chính sách và quản lý lịch. Khi kích hoạt, người có quyền phải có tham chiếu xác minh vùng phủ. Backend kiểm tra carrier và kho đang hoạt động, dịch vụ và kho khớp cấu hình carrier đang bật, cặp tỉnh/xã hợp lệ và lịch carrier hoạt động. Preview không thay thế kiểm tra báo giá trực tiếp.

| Phương thức | Dưới `/api/v1/admin/shipping/delivery-rules` | Dữ liệu |
| --- | --- | --- |
| GET | `?carrierId={id}&page=0` | 20 tuyến theo carrier |
| POST | `/` | `route` và `terms` |
| GET | `/{id}` | Tuyến cùng revision |
| PUT | `/{id}` | Revision hiện tại và terms. Xung đột trả 409 |
| GET | `/{id}/history?page=0` | Lịch sử, 10 revision mỗi trang |
| GET | `/calendar-options` | ID và tên lịch carrier đang hoạt động |
| POST | `/preview` | `destination` và `onDate`. Trả `estimate` có thể rỗng |

`route` gồm `carrierId`, `serviceCode`, `warehouseId`, `provinceCode`, `wardCode`. `terms` gồm `calendarId`, `minBusinessDays`, `maxBusinessDays`, `priority`, `effectiveFrom`, `effectiveUntil`, `active`, `guaranteed`, `coverageReference`. Preview trả `source=POLICY`, `referenceEvent=CARRIER_HANDOVER` và revision của tuyến lẫn lịch.

V61.2 tạo bảng tuyến và audit. Lookup dựa trên composite index từ carrier, kho, service và tỉnh. Preview thực hiện truy vấn xác minh địa chỉ và một truy vấn chọn tuyến qua join. Transaction khóa carrier khi ghi để bảo vệ quy tắc chồng lấn. Admin xử lý theo tỉnh/xã. Shipping Zone gộp nhiều tỉnh/xã và dịch vụ ngoài cấu hình hiện có cần contract vùng phục vụ phù hợp.

Nguồn preview chỉ dành cho quản trị viên. Checkout chưa lấy ETA từ cấu hình này cho đến khi luồng địa chỉ và khả năng carrier được xác minh.
