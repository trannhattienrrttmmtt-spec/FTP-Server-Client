# MODULE CLIENT (GIAO DIỆN & KẾT NỐI)

**Phụ trách phát triển**: Nguyễn Anh Vũ (MSSV: 2380602582)  
**Môn học**: Lập trình mạng máy tính - Lớp: 23DTHB4 - Nhóm 11  
**Công nghệ sử dụng**: Java Swing UI, Socket client (`java.net.Socket`).

---

## 1. Mục tiêu và Yêu cầu chức năng (Theo phiếu đăng ký)
- [ ] **Giao diện người dùng (Swing UI)**:
  - Khung đăng nhập: Nhập IP Server, Port (mặc định `2121`), Username, Password.
  - Bảng danh sách tệp tin/thư mục: Hiển thị tên file, dung lượng, ngày sửa đổi (`JTable` hoặc `JList`).
  - Thanh địa chỉ thư mục hiện tại: Hiển thị đường dẫn (ví dụ: `/`, `/tailieu`) và nút "Lên thư mục cha" (CDUP).
  - Các nút tác vụ: "Tải lên" (Upload), "Tải xuống" (Download), "Làm mới" (Refresh), "Xóa" (Delete), "Tạo thư mục" (New Folder).
  - Thanh tiến trình: `JProgressBar` hiển thị % khi truyền tải file.
  - Khung Log: `JTextArea` hiển thị các lệnh gửi đi và mã phản hồi từ Server (220, 331, 230, 227, 150, 226, ...).

- [ ] **Xử lý mạng phía Client**:
  - Kết nối đến Control Socket của Server (cổng `2121`).
  - Gửi lệnh văn bản và đọc mã phản hồi từ Server theo quy chuẩn tại `PROTOCOL.md`.
  - Cơ chế Passive Mode:
    1. Gửi lệnh `PASV`.
    2. Phân tích chuỗi phản hồi `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)` để tính IP và Data Port (`port = p1 * 256 + p2`).
    3. Mở kết nối Socket thứ hai đến Data Port vừa nhận được.
    4. Gửi lệnh truyền tải (`LIST`, `RETR`, hoặc `STOR`) qua Control Channel.
    5. Đọc/ghi luồng byte nhị phân qua Data Socket (phối hợp sử dụng class `datachannel/` do Trọng phụ trách).
    6. Đóng Data Socket và nhận phản hồi `226 Transfer complete` từ Control Channel.

---

## 2. Các lớp gợi ý xây dựng
1. `ClientApp.java` (hoặc `ClientGui.java`): Khởi tạo và bố cục giao diện đồ họa Java Swing.
2. `FtpClientController.java`: Quản lý kết nối Socket điều khiển, xử lý logic gửi nhận lệnh FTP.
3. `FileTransferTask.java`: Dùng `SwingWorker` để truyền file ngầm trong background thread, tránh đơ/lag giao diện và cập nhật `JProgressBar`.
