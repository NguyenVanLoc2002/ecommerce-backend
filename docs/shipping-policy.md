# Chính sách giao hàng

Backend quản lý nội dung dạng văn bản thuần theo phiên bản. Admin tạo bản nháp, cập nhật và gửi công bố, hoặc lưu trữ. Mỗi phiên bản đã công bố chỉ đọc. Mỗi thay đổi ghi lịch sử đầy đủ trong cùng transaction với dữ liệu chính, gồm UUID người thao tác và thời điểm UTC. Thao tác với revision cũ trả 409. Lịch sử và danh sách phân trang 20 bản ghi.

## API

| Method | Path dưới /api/v1 | Dữ liệu |
| --- | --- | --- |
| GET | /policies/shipping | Phiên bản có hiệu lực với version, title, content, effectiveFrom, publishedAt. 404 nếu chưa có bản hiệu lực. |
| GET | /admin/policies/shipping/capabilities | canEdit, canPublish, publicationEnabled |
| GET | /admin/policies/shipping?page=0 | items, hasNext |
| GET | /admin/policies/shipping/{version} | Nội dung, status, revision và publishedBy |
| POST | /admin/policies/shipping | title, content, effectiveFrom |
| PUT | /admin/policies/shipping/{version} | revision, draft gồm title, content, effectiveFrom |
| POST | /admin/policies/shipping/{version}/publish | revision |
| POST | /admin/policies/shipping/{version}/archive | revision |
| GET | /admin/policies/shipping/{version}/history?page=0 | items, hasNext |

Request sử dụng effectiveFrom là timestamp ISO 8601. Công bố ngay tối đa từ thời điểm thao tác. Khi đến ngày hiệu lực của một phiên bản thay thế, phiên bản đó thắng theo effectiveFrom rồi version. Lưu trữ phiên bản đang hiệu lực làm nội dung biến mất và không làm bản cũ hiện lại. Hủy phiên bản được lên lịch trước ngày hiệu lực sẽ giữ nội dung hiện hành.

## Phân quyền và rollout

- `app.shipping.policy.editors` và `app.shipping.policy.publishers` là hai tập UUID độc lập. Tài khoản đồng thời cần vai trò Admin phù hợp và trạng thái active.
- `app.shipping.policy.publication-enabled` mặc định false. Quyền chỉnh sửa bản nháp có thể cấp trước khi bật công bố.
- Môi trường phát triển dùng nội dung đã duyệt làm fixture, không seed nội dung lên môi trường vận hành.
- Customer Web tải nội dung khi mở khu vực giao nhận trên PDP hoặc truy cập `/policies/shipping`. Nội dung được render dạng văn bản, không thực thi HTML. API không chạy thêm query trong PLP hoặc PDP.
- Tính phí, ETA và nghiệp vụ đổi trả tiếp tục đi qua contract hiện có. Chính sách này cung cấp nội dung công bố, không tính lại phí vận chuyển.
