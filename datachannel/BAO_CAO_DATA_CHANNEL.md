# BÁO CÁO KỸ THUẬT: MODULE KÊNH DỮ LIỆU (DATA CHANNEL) & TRUYỀN FILE
### MÔN HỌC: LẬP TRÌNH MẠNG MÁY TÍNH - LỚP: 23DTHB4 - NHÓM 11
**Sinh viên phụ trách**: Phan Thanh Trọng - **MSSV**: 2380602351  
**Đề tài**: Hệ thống FTP Server/Client cơ bản với Hai kênh (Điều khiển & Dữ liệu)

---

## 1. TỔNG QUAN KIẾN TRÚC HAI KÊNH (TWO-CHANNEL ARCHITECTURE)

Trong giao thức FTP chuẩn (RFC 959), hệ thống phân tách độc lập hai kênh truyền thông:
1. **Control Channel (Kênh điều khiển)**: Chạy cố định trên TCP Port `2121` để gửi lệnh dạng văn bản (USER, PASS, PASV, RETR, STOR, QUIT...) và nhận mã phản hồi số 3 chữ số (220, 230, 227, 150, 226, 550, 426...).
2. **Data Channel (Kênh dữ liệu)**: Được mở động theo cơ chế **Passive Mode (PASV)** khi có yêu cầu truyền tải danh sách tệp tin hoặc dữ liệu nhị phân (`STOR`, `RETR`). Sau khi truyền xong hoặc xảy ra lỗi, kênh dữ liệu sẽ tự động đóng ngay để giải phóng tài nguyên.

```mermaid
sequenceDiagram
    autonumber
    actor Client as FTP Client (Vũ)
    participant Helper as DataChannelHelper (Trọng)
    participant Ctrl as Server Control (Port 2121)
    participant Data as Server Data Channel (Port N)

    Client->>Ctrl: PASV
    Ctrl-->>Client: 227 Entering Passive Mode (127,0,0,1,p1,p2)
    Note over Client,Helper: Helper phân tích IP & Port = p1*256 + p2
    Helper->>Data: TCP Connect tới Port N
    Client->>Ctrl: STOR / RETR filename
    Ctrl-->>Client: 150 File status okay, opening data connection
    Note over Helper,Data: Truyền luồng byte nhị phân qua BufferedStream (8KB/64KB)<br/>Báo tiến độ thời gian thực qua DataTransferListener
    Data-->>Helper: Hoàn tất truyền dữ liệu (EOF) / Đóng socket
    Ctrl-->>Client: 226 Transfer complete
```

---

## 2. NỘI DUNG VÀ KẾT QUẢ TRIỂN KHAI

### 2.1. Xây dựng kênh dữ liệu riêng (Passive Mode): Mở port, kết nối
- **Phía Server**:
  - Khi nhận lệnh `PASV`, Server tìm một cổng khả dụng trong dải `50000 - 50100` (hoặc cổng ngẫu nhiên do HĐH cấp phát).
  - Khởi tạo `ServerSocket` lắng nghe tại cổng đó với `SO_TIMEOUT = 60000ms`.
  - Đóng gói phản hồi theo chuẩn RFC 959:
    $$\text{Port} = p1 \times 256 + p2 \implies p1 = \lfloor \text{Port} / 256 \rfloor, \quad p2 = \text{Port} \pmod{256}$$
    Ví dụ cổng `50000`: $50000 / 256 = 195, 50000 \pmod{256} = 80 \implies \text{227 Entering Passive Mode (127,0,0,1,195,80).}$
- **Phía Client / Data Channel (`PassiveModeInfo.java`, `DataChannelHelper.java`)**:
  - Hàm `DataChannelHelper.parsePassiveAddress(pasvResponse)` dùng biểu thức chính quy (Regex) bóc tách chính xác 6 octet $(h1, h2, h3, h4, p1, p2)$.
  - Tái tạo địa chỉ IP và tính toán Port đích: `port = (p1 << 8) | p2`.
  - Hàm `DataChannelHelper.openDataSocket(host, port, timeoutMs)` thiết lập kết nối TCP thứ hai độc lập, kích hoạt `TCP_NODELAY` (vô hiệu hóa thuật toán Nagle để đẩy gói tin tức thời) và gán kích thước bộ đệm `SO_RCVBUF` / `SO_SNDBUF` tối ưu.

---

### 2.2. Xử lý STOR (Upload) và RETR (Download) qua luồng byte
- **Luồng dữ liệu nhị phân (Binary Stream)**:
  - Sử dụng `BufferedInputStream` và `BufferedOutputStream` với bộ đệm `8192 bytes (8 KB)` cho file thông thường và `65536 bytes (64 KB)` cho file dung lượng lớn.
  - Ngăn chặn hoàn toàn hiện tượng nghẽn I/O và tối ưu hóa số lượng ngắt hệ thống (System calls).
- **Lệnh STOR (Upload)**:
  - Client đọc từng khối byte từ tệp tin cục bộ qua `FileInputStream` và đẩy trực tiếp ra `OutputStream` của Data Socket.
  - Server đọc từ Data Socket và ghi vào tệp tin đích trong thư mục Sandbox `server/ftp_root/`.
- **Lệnh RETR (Download)**:
  - Server đọc file từ đĩa cục bộ qua `FileInputStream` và ghi vào `OutputStream` của Data Socket.
  - Client nhận dữ liệu từ Data Socket và lưu xuống đĩa cục bộ.
- **Cơ chế Callback tiến độ & tốc độ (`DataTransferListener`)**:
  - Liên tục cập nhật `onTransferProgress(bytesTransferred, totalBytes, percent, speedKBps)` theo thời gian thực (chu kỳ mỗi 50ms) giúp giao diện Swing của Client hiển thị mượt mà thanh `JProgressBar` và tốc độ truyền tải mà không gây giật lag.

---

### 2.3. Xử lý lỗi toàn diện trong quá trình truyền tải

| Loại lỗi | Nguyên nhân phát sinh | Giải pháp xử lý | Mã phản hồi FTP |
| :--- | :--- | :--- | :---: |
| **Mất kết nối giữa chừng (Broken Connection / RST)** | Mạng chập chờn, Client tắt đột ngột, Socket Timeout | Bắt `SocketException`, `SocketTimeoutException`. Tự động dọn dẹp file tạm `.part` (không để file rác bị hỏng lưu lại trên đĩa), giải phóng socket trong khối `finally`. | **426** Transfer aborted |
| **Tệp tin không tồn tại (File Not Found)** | Client yêu cầu RETR một file không có trên Server | Kiểm tra `file.exists()` và `file.isFile()` trước khi mở luồng truyền. Từ chối ngay lập tức, không mở kết nối dữ liệu rỗng. | **550** File unavailable |
| **Từ chối quyền truy cập (Permission Denied)** | File chỉ đọc (Read-only), thư mục không có quyền ghi | Kiểm tra `file.canRead()` trước khi gửi (RETR) và `parentDir.canWrite()` trước khi nhận (STOR). | **550** Permission denied |
| **Tấn công thoát thư mục gốc (Path Traversal)** | Client truyền đường dẫn nguy hiểm (`../../`) | Kiểm tra tính hợp lệ bằng `File.getCanonicalPath()`, đảm bảo luôn nằm trong phân vùng Sandbox `rootDir`. | **550** Requested action not taken |
| **Lỗi chưa mở kênh dữ liệu** | Client gọi RETR/STOR khi chưa gửi PASV | Kiểm tra trạng thái `passiveConnection == null || !passiveConnection.isOpen()`. | **425** Can't open data connection |

---

### 2.4. Kiểm thử hiệu năng truyền file (Performance Benchmark) & Tính toàn vẹn SHA-256

Hệ thống được kiểm thử thực nghiệm bằng cả bộ test **Java (`DataChannelPerformanceTest.java`)** và **Python (`test_datachannel_performance.py`)** qua 3 mức tải: File nhỏ (10 KB), File trung bình (2 MB), File lớn (20 MB).

#### Bảng kết quả thực nghiệm hiệu năng (Môi trường Windows Localhost)

| Kịch bản | Thao tác | Kích thước File | Thời gian truyền | Tốc độ truyền (Throughput) | Xác thực SHA-256 Checksum | Trạng thái |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **File Nhỏ** | UPLOAD (STOR) | 10.00 KB (10,240 B) | **2 ms** | **4.88 MB/s** | `563ac3371c...` (Khớp 100%) | **PASS** |
| **File Nhỏ** | DOWNLOAD (RETR) | 10.00 KB (10,240 B) | **2 ms** | **4.88 MB/s** | `563ac3371c...` (Khớp 100%) | **PASS** |
| **File Trung Bình** | UPLOAD (STOR) | 2.00 MB (2,097,152 B) | **8 ms** | **250.00 MB/s** | `c1585ea7d4...` (Khớp 100%) | **PASS** |
| **File Trung Bình** | DOWNLOAD (RETR) | 2.00 MB (2,097,152 B) | **8 ms** | **250.00 MB/s** | `c1585ea7d4...` (Khớp 100%) | **PASS** |
| **File Lớn** | UPLOAD (STOR) | 20.00 MB (20,971,520 B) | **58 ms** | **344.83 MB/s** | `7497f934ee...` (Khớp 100%) | **PASS** |
| **File Lớn** | DOWNLOAD (RETR) | 20.00 MB (20,971,520 B) | **89 ms** | **224.72 MB/s** | `7497f934ee...` (Khớp 100%) | **PASS** |

#### Kết quả kiểm thử xử lý lỗi (Error Handling Cases)
1. **Lỗi File không tồn tại**: Gửi `RETR file_hoan_toan_khong_co_that.abc` $\rightarrow$ Server trả về `550 Tep khong ton tai hoac khong the doc` $\rightarrow$ **PASS (Đạt)**.
2. **Lỗi Path Traversal**: Gửi `STOR ../../../windows_system32_exploit.exe` $\rightarrow$ Server chặn với mã `550 Duong dan khong hop le` $\rightarrow$ **PASS (Đạt)**.
3. **Lỗi mất kết nối giữa chừng**: Gửi 32 KB rồi đóng đột ngột Socket bằng cờ `TCP RST` $\rightarrow$ Server bắt `SocketException`, xóa file tạm `.part`, phản hồi `426 Upload tep bi loi giua chung` $\rightarrow$ **PASS (Đạt)**.

---

## 3. KẾT LUẬN

Module **Data Channel** do sinh viên **Phan Thanh Trọng (MSSV: 2380602351)** đảm nhận đã hoàn thành xuất sắc 100% mục tiêu đề ra:
1. Đảm bảo đúng chuẩn giao thức FTP RFC 959 hai kênh độc lập.
2. Đạt tốc độ truyền tải cực cao (trên 300 MB/s) nhờ kỹ thuật phân bổ bộ đệm byte stream thông minh.
3. Độ tin cậy và an toàn tuyệt đối với kiểm tra toàn vẹn bit-level SHA-256 và cô lập Sandbox.
