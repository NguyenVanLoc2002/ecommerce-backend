# Lịch vận hành kho và đơn vị vận chuyển

Các lịch dùng múi giờ `Asia/Ho_Chi_Minh`. Mỗi lịch có tên, mục đích, ngày làm việc, giờ mở, giờ đóng, cut-off, danh sách ngày nghỉ và trạng thái. Hai mục đích là `WAREHOUSE_PROCESSING` và `CARRIER_TRANSPORT`. Mục đích đã chọn được giữ qua các lần sửa để tránh đổi ý nghĩa của cấu hình tại các consumer. Mỗi thay đổi tạo revision mới và lưu bản chụp audit cùng giao dịch.

Mọi thao tác cần tài khoản Admin đang hoạt động được cấp UUID trong `app.shipping.calendars.managers`. Quyền này độc lập với quyền công bố chính sách giao hàng. Migration V61.1 tạo bảng lịch và lịch sử. Lịch vận hành thực tế do người có quyền cấu hình sau khi xác nhận với Ops.

| Phương thức | Đường dẫn dưới `/api/v1/admin/shipping/calendars` | Dữ liệu |
| --- | --- | --- |
| GET | `?page=0` | Tóm tắt tối đa 20 lịch |
| POST | `/` | Tạo lịch với `name`, `purpose`, `workdays`, `opensAt`, `closesAt`, `cutoffAt`, `holidays`, `active` |
| GET | `/{id}` | Nội dung lịch, revision, timezone |
| PUT | `/{id}` | `revision` hiện tại và `calendar` mới. Revision cũ trả 409 |
| GET | `/{id}/history?page=0` | Tối đa 10 revision cùng người thực hiện và thời điểm |
| POST | `/{id}/preview-processing` | `eligibleAt` là timestamp ISO 8601. Trả `processingStartsAt` |
| POST | `/{id}/preview-delivery` | `handoverDate`, `minBusinessDays`, `maxBusinessDays`. Trả ngày `earliest` và `latest` |

Ngày nghỉ có dạng `{date,name}` và không được trùng. Thứ trong tuần dùng enum ISO từ `MONDAY` đến `SUNDAY`. Các giờ nhận dạng `HH:mm:ss`. Cut-off sau giờ mở cửa và không muộn hơn giờ đóng cửa.

Xem trước xử lý tính từ thời điểm đơn đủ điều kiện đầu vào. Đơn từ giờ cut-off hoặc vào ngày nghỉ bắt đầu ở giờ mở cửa của ngày làm việc tiếp theo. Dự báo giao nhận tính ngày làm việc theo lịch carrier từ ngày bàn giao. Khoảng 0 ngày kết thúc trong ngày bàn giao. Hai API preview chỉ dùng cho quản trị viên kiểm tra cấu hình. Dữ liệu này chỉ trở thành ETA checkout khi có tuyến và dịch vụ đã được xác minh.
