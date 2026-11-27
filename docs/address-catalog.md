# Danh mục địa chỉ Việt Nam hai cấp

Nguồn tham chiếu là [Quyết định 19/2025/QĐ-TTg](https://vanban.chinhphu.vn/?classid=0&docid=214409&pageid=27160) và các thay đổi sau đó do Cục Thống kê công bố. Mã tỉnh giữ 2 chữ số, mã xã giữ 5 chữ số, cả hai lưu và truyền dạng chuỗi. Schema bổ sung sẵn cho bản tải danh mục được xác minh, gồm các bảng address_provinces và address_wards. Chưa seed dữ liệu một phần từ tài liệu hoặc ghép mã của nhà vận chuyển vào danh mục.

| API | Response data |
| --- | --- |
| GET /api/v1/locations/provinces | Danh sách tỉnh hoạt động, mỗi bản ghi có code và name. |
| GET /api/v1/locations/provinces/{provinceCode}/wards | Danh sách xã hoạt động thuộc tỉnh với code, name và provinceCode. Tỉnh không hợp lệ trả 404. |

Dùng AddressCatalog.resolve với cặp mã để lấy tên chính thức và xác minh quan hệ bằng một query có index. Khi chuyển đổi luồng ghi địa chỉ, phân biệt lệnh chọn địa chỉ hiện tại với đọc snapshot đơn hàng và hóa đơn. Snapshot giữ mã và tên ở thời điểm ghi nhận, không tra ngược danh mục để hiển thị lịch sử.

Trước cutover cần nạp đủ danh mục hiện hành có nguồn và phiên bản xác minh được. Migration tạo schema chưa thay đổi API ghi địa chỉ, địa chỉ khách hàng, luồng báo giá hoặc snapshot đơn hàng. Các API ghi và consumer cũ được đổi đồng bộ ở bước cutover, sau khi kiểm tra carrier có hỗ trợ địa chỉ hai cấp.

## Nạp danh mục có nguồn xác minh

Chuẩn bị hai tệp UTF-8. Tệp tỉnh có header `code,name`, tệp xã có header `code,province_code,name`. Giữ mã dưới dạng chuỗi kể cả các số 0 đầu. Dùng `scripts/make_address_catalog_sql.py` với `--provinces`, `--wards`, `--expected-provinces` và `--expected-wards` lấy từ nguồn cùng phiên bản. Công cụ kiểm tra số lượng, cấu trúc mã, trùng lặp và quan hệ cha/con rồi ghi SQL ra stdout cùng SHA-256 của hai tệp.

Đối chiếu SQL với nguồn và thử trên DB cô lập trước. Tùy chọn `--deactivate-absent` chỉ dành cho bản danh mục thay thế đầy đủ đã xác minh. Các mã được vô hiệu hóa vẫn giữ trong danh mục và có thể được tra lại bằng snapshot đã phát sinh.
