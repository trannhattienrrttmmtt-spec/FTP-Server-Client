# MODULE DATACHANNEL (KÊNH TRUYỀN DỮ LIỆU)

**Phụ trách phát triển**: Phan Thanh Trọng (MSSV: 2380602351)  
**Môn học**: Lập trình mạng máy tính - Lớp: 23DTHB4 - Nhóm 11  
**Mục tiêu**: Xây dựng module dùng chung (Shared Component) quản lý kênh truyền nhị phân (Binary Stream) cho Kênh dữ liệu (Data Channel).

---

## 1. Nhiệm vụ và Trách nhiệm
Theo thiết kế kiến trúc Hai kênh của đề tài:
- **Kênh điều khiển (Control Channel)**: Truyền lệnh văn bản qua port 2121 (Tiến phụ trách ở Server, Vũ phụ trách ở Client).
- **Kênh dữ liệu (Data Channel)**: Là một kết nối TCP riêng biệt được mở theo cơ chế Passive Mode khi có yêu cầu truyền file hoặc lấy danh sách thư mục (Trọng phụ trách module dùng chung).

## 2. Các chức năng cần hoàn thiện
1. **Quản lý luồng nhị phân (Stream Transfer)**:
   - Truyền file từ máy cục bộ ra socket (`sendStream` / `upload`).
   - Nhận file từ socket ghi vào máy cục bộ (`receiveStream` / `download`).
   - Tối ưu hóa kích thước Buffer (`8192` bytes) để truyền nhanh và ổn định.
2. **Cơ chế Callback thông báo tiến độ (Progress Callback)**:
   - Cung cấp interface `DataTransferListener` để khi mỗi chunk dữ liệu được gửi/nhận, sẽ phát sự kiện `onProgress(bytesTransferred, totalBytes, percent)`.
   - Giúp phía Client (Vũ) dễ dàng gắn vào thanh `JProgressBar` trên giao diện người dùng.
3. **Xử lý ngắt kết nối và giải phóng tài nguyên**:
   - Tự động `flush` và `close` socket dữ liệu khi truyền xong.
   - Bắt các lỗi `SocketTimeoutException`, `IOException` khi đường truyền mạng bị gián đoạn.

## 3. Cấu trúc gợi ý
- `DataTransferListener.java`: Interface định nghĩa các hàm callback `onStart`, `onProgress`, `onComplete`, `onError`.
- `DataChannelHelper.java`: Lớp chứa các static method hoặc instance xử lý việc đọc/ghi dữ liệu từ luồng này sang luồng khác kèm trigger listener.
