# ĐẶC TẢ GIAO THỨC TRUYỀN THÔNG (FTP TWO-CHANNEL PROTOCOL)

**Đề tài**: Hệ thống FTP Server/Client cơ bản với Hai kênh (Điều khiển và Dữ liệu)  
**Môn học**: Lập trình mạng máy tính - Lớp: **23DTHB4** - **Nhóm 11**  
**Thành viên thực hiện**:
- **Trần Nhật Tiến** - MSSV: 2380602243 *(Phụ trách Server & Thiết kế Giao thức)*
- **Nguyễn Anh Vũ** - MSSV: 2380602582 *(Phụ trách Client)*
- **Phan Thanh Trọng** - MSSV: 2380602351 *(Phụ trách Data Channel)*

---

## 1. TỔNG QUAN KIẾN TRÚC HAI KÊNH (TWO-CHANNEL ARCHITECTURE)

Hệ thống mô phỏng giao thức truyền tải tập tin FTP (File Transfer Protocol - chuẩn RFC 959) bằng cách phân tách rõ ràng hai kênh truyền thông độc lập trên nền giao thức truyền vận TCP/IP:

```
+-------------------------------------------------------------------+
|                           CLIENT (Vũ)                             |
+-------------------------------------------------------------------+
       | (1) Control Channel (TCP Port 2121)   | (2) Data Channel (TCP Port ngẫu nhiên)
       |     Gửi lệnh văn bản (USER, RETR...)  |     Truyền byte nhị phân (Binary stream)
       v                                       v
+-------------------------------------------------------------------+
|                           SERVER (Tiến)                           |
+-------------------------------------------------------------------+
```

1. **Kênh điều khiển (Control Channel)**:
   - **Giao thức**: TCP.
   - **Cổng mặc định**: `2121`.
   - **Vòng đời**: Duy trì kết nối cố định xuyên suốt phiên làm việc của Client.
   - **Định dạng dữ liệu**: Chuỗi văn bản ASCII / UTF-8, mỗi dòng lệnh hoặc phản hồi kết thúc bằng ký tự xuống dòng `\r\n` (CRLF).
   - **Nhiệm vụ**: Xác thực người dùng, gửi lệnh điều khiển và nhận mã phản hồi trạng thái từ Server.

2. **Kênh dữ liệu (Data Channel)**:
   - **Giao thức**: TCP.
   - **Cổng**: Cấp phát động trên Server theo cơ chế **Passive Mode (PASV)** trong dải `50000 - 50100` (hoặc cổng tự do của hệ điều hành).
   - **Vòng đời**: Chỉ mở khi có yêu cầu truyền tải dữ liệu (`LIST`, `RETR`, `STOR`) và tự động đóng ngay sau khi việc truyền tải hoàn tất.
   - **Định dạng dữ liệu**: Luồng byte nhị phân thô (Raw binary stream), không pha trộn lệnh hay phản hồi điều khiển.

---

## 2. BẢNG MÃ PHẢN HỒI CHUẨN (FTP RESPONSE CODES)

Mỗi phản hồi từ Server qua Control Channel đều tuân thủ cú pháp: `<Mã 3 chữ số> <Thông điệp>\r\n`.

| Mã phản hồi | Ý nghĩa chuẩn | Mô tả chi tiết trong hệ thống |
| :--- | :--- | :--- |
| **150** | File status okay | Bắt đầu mở kênh dữ liệu (chuẩn bị truyền danh sách/file) |
| **200** | Command okay | Lệnh thực hiện thành công (NOOP, TYPE...) |
| **211** | System status / Feature | Danh sách tính năng hỗ trợ (`FEAT`) |
| **213** | File status | Kích thước của tệp tin tính bằng bytes (`SIZE`) |
| **215** | System type | Hệ điều hành mô phỏng (`UNIX Type: L8`) |
| **220** | Service ready | Server sẵn sàng tiếp nhận khi Client vừa kết nối tới |
| **221** | Closing control connection | Đóng kết nối điều khiển khi Client gửi `QUIT` |
| **226** | Closing data connection | Hoàn tất truyền dữ liệu thành công qua Data Channel |
| **227** | Entering Passive Mode | Server mở cổng Passive Mode: `(h1,h2,h3,h4,p1,p2)` |
| **230** | User logged in | Đăng nhập thành công, cho phép thao tác tập tin |
| **250** | Requested file action okay | Thao tác đổi thư mục / xóa file thành công |
| **257** | Pathname created / Current dir | Đường dẫn thư mục hiện tại (`PWD`) hoặc vừa tạo (`MKD`) |
| **331** | User name okay, need password | Tên đăng nhập hợp lệ, yêu cầu gửi tiếp lệnh `PASS` |
| **425** | Can't open data connection | Chưa mở kênh dữ liệu (Client chưa gọi lệnh `PASV`) |
| **426** | Connection closed; transfer aborted | Mất kết nối hoặc truyền dữ liệu bị ngắt giữa chừng |
| **500** | Syntax error | Lệnh không hợp lệ hoặc Server không hỗ trợ |
| **501** | Syntax error in parameters | Tham số của lệnh không hợp lệ |
| **530** | Not logged in | Chưa đăng nhập hoặc sai thông tin xác thực |
| **550** | File unavailable | Tệp/thư mục không tồn tại hoặc bị từ chối quyền truy cập |

---

## 3. DANH SÁCH LỆNH ĐIỀU KHIỂN (COMMAND SET)

| Lệnh | Tham số | Mô tả chức năng | Yêu cầu đăng nhập? |
| :--- | :--- | :--- | :---: |
| `USER` | `<username>` | Gửi tên tài khoản người dùng | Không |
| `PASS` | `<password>` | Gửi mật khẩu xác thực | Không |
| `PWD` | *(Không)* | Lấy đường dẫn thư mục hiện tại trên Server | Có |
| `CWD` | `<path>` | Chuyển đổi thư mục làm việc | Có |
| `CDUP`| *(Không)* | Chuyển lên thư mục cha (cấp trên) | Có |
| `PASV`| *(Không)* | Yêu cầu Server mở kênh dữ liệu Passive Mode | Có |
| `LIST`| `[path]` | Liệt kê danh sách tệp/thư mục chi tiết | Có (cần PASV) |
| `NLST`| `[path]` | Liệt kê danh sách chỉ gồm tên tệp tin | Có (cần PASV) |
| `RETR`| `<filename>` | Tải tệp tin từ Server về Client (Download) | Có (cần PASV) |
| `STOR`| `<filename>` | Tải tệp tin từ Client lên Server (Upload) | Có (cần PASV) |
| `DELE`| `<filename>` | Xóa tệp tin trên Server | Có |
| `MKD` | `<dirname>` | Tạo thư mục mới trên Server | Có |
| `RMD` | `<dirname>` | Xóa thư mục rỗng trên Server | Có |
| `SIZE`| `<filename>` | Xem kích thước tệp tin (bytes) | Có |
| `TYPE`| `<A / I>` | Thiết lập kiểu truyền (A: ASCII, I: Binary) | Có |
| `NOOP`| *(Không)* | Lệnh giữ kết nối (No Operation) | Có |
| `HELP`| *(Không)* | Hiển thị hướng dẫn các lệnh hỗ trợ | Không |
| `QUIT`| *(Không)* | Đóng phiên làm việc và ngắt kết nối | Không |

---

## 4. CHI TIẾT CƠ CHẾ PASSIVE MODE (PASV)

Cơ chế Passive Mode được thiết kế đặc biệt nhằm tránh xung đột với tường lửa (Firewall) hoặc NAT phía Client:

1. **Client gửi**: `PASV\r\n`
2. **Server xử lý**:
   - Mở một `ServerSocket` trên cổng khả dụng (ví dụ: `50024`).
   - Lấy địa chỉ IP (ví dụ: `127.0.0.1` -> `127, 0, 0, 1`).
   - Tính toán cổng:
     $$\text{port} = p1 \times 256 + p2 \implies p1 = \lfloor\text{port} / 256\rfloor,\ p2 = \text{port} \pmod{256}$$
     Ví dụ cổng `50024`: $50024 / 256 = 195$, $50024 \pmod{256} = 104$.
3. **Server phản hồi**:
   `227 Entering Passive Mode (127,0,0,1,195,104).\r\n`
4. **Client xử lý**:
   - Dùng Regex hoặc tách chuỗi để lấy 6 số trong ngoặc: $h1, h2, h3, h4, p1, p2$.
   - Tính cổng kết nối: $\text{Port} = p1 \times 256 + p2$.
   - Khởi tạo kết nối Socket thứ hai: `new Socket("127.0.0.1", 50024)`.

---

## 5. KỊCH BẢN LUỒNG HOẠT ĐỘNG (INTERACTION FLOWS)

### 5.1. Luồng Xác thực (Authentication)
```text
Client                                             Server (Port 2121)
  |                                                        |
  | -------- TCP Connect --------------------------------> |
  | <------- 220 Service ready for new user -------------- |
  |                                                        |
  | -------- USER tien ----------------------------------> |
  | <------- 331 User name okay, need password ----------- |
  |                                                        |
  | -------- PASS 123456 --------------------------------> |
  | <------- 230 User logged in, proceed ----------------- |
```

### 5.2. Luồng Lấy danh sách tệp tin (LIST)
```text
Client                                             Server (Control: 2121)      Server (Data: Port N)
  |                                                        |                              |
  | -------- PASV ---------------------------------------> |                              |
  | <------- 227 Entering Passive Mode (127,0,0,1,p1,p2) - |                              |
  |                                                                                       |
  | -------- TCP Connect tới Port N ----------------------------------------------------> |
  | -------- LIST ---------------------------------------> |                              |
  | <------- 150 Opening BINARY mode data connection ----- |                              |
  |                                                        |                              |
  | <======= Truyền danh sách file dạng text qua Data Socket ============================ |
  |                                                        |                              |
  | -------- Đóng Data Socket ----------------------------------------------------------x |
  | <------- 226 Closing data connection ----------------- |                              |
```

### 5.3. Luồng Tải tệp xuống Client (Download - RETR)
```text
Client                                             Server (Control: 2121)      Server (Data: Port N)
  |                                                        |                              |
  | -------- PASV ---------------------------------------> |                              |
  | <------- 227 Entering Passive Mode (127,0,0,1,p1,p2) - |                              |
  |                                                                                       |
  | -------- TCP Connect tới Port N ----------------------------------------------------> |
  | -------- RETR tailieu.pdf ---------------------------> |                              |
  | <------- 150 Opening BINARY mode (tailieu.pdf, 1024B) -|                              |
  |                                                        |                              |
  | <======= Stream từng khối byte (8KB) qua Data Socket ================================ |
  |          (Client cập nhật thanh ProgressBar)           |                              |
  |                                                        |                              |
  | -------- Đóng Data Socket ----------------------------------------------------------x |
  | <------- 226 Transfer complete ----------------------- |                              |
```

### 5.4. Luồng Tải tệp lên Server (Upload - STOR)
```text
Client                                             Server (Control: 2121)      Server (Data: Port N)
  |                                                        |                              |
  | -------- PASV ---------------------------------------> |                              |
  | <------- 227 Entering Passive Mode (127,0,0,1,p1,p2) - |                              |
  |                                                                                       |
  | -------- TCP Connect tới Port N ----------------------------------------------------> |
  | -------- STOR tailieu_moi.docx ----------------------> |                              |
  | <------- 150 Ok to send data ------------------------- |                              |
  |                                                        |                              |
  | =======> Gửi từng khối byte (8KB) qua Data Socket =================================== |
  |          (Server ghi dữ liệu vào tệp tin đích)         |                              |
  |                                                        |                              |
  | -------- Đóng Data Socket ----------------------------------------------------------x |
  | <------- 226 Transfer complete ----------------------- |                              |
```

---

## 6. CƠ CHẾ BẢO MẬT & CHỐNG TẤN CÔNG ĐƯỜNG DẪN (PATH TRAVERSAL)

Server áp dụng kỹ thuật **Sandbox Root** để cô lập hoàn toàn người dùng bên trong thư mục `server/ftp_root/`:
- Mọi đường dẫn từ Client (`CWD`, `RETR`, `STOR`, `DELE`) đều được giải quyết qua `File.getCanonicalPath()`.
- Nếu đường dẫn chuẩn hóa không bắt đầu bằng thư mục gốc `rootDir.getCanonicalPath()`, Server lập tức từ chối với mã lỗi `550 Requested action not taken` nhằm ngăn chặn các hành vi đọc trộm hoặc ghi đè file hệ thống bên ngoài vùng cho phép.
