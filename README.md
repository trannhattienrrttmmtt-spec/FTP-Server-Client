# ĐỒ ÁN LẬP TRÌNH MẠNG MÁY TÍNH (HUTECH)
## ĐỀ TÀI: HỆ THỐNG FTP SERVER/CLIENT CƠ BẢN VỚI HAI KÊNH (ĐIỀU KHIỂN VÀ DỮ LIỆU)

- **Mã nhóm**: 11  
- **Lớp**: 23DTHB4  
- **Khoa**: Công nghệ Thông tin - Trường Đại học Công nghệ TP.HCM (HUTECH)  
- **Giảng viên hướng dẫn**: Bộ môn Mạng máy tính & Truyền thông  

---

## 👥 THÀNH VIÊN VÀ PHÂN CÔNG NHIỆM VỤ

| STT | Họ và tên | MSSV | Vai trò | Nhiệm vụ chính phụ trách | Trạng thái |
| :---: | :--- | :---: | :---: | :--- | :---: |
| 1 | **Trần Nhật Tiến** | **2380602243** | Nhóm trưởng | **Xây dựng FTP Server** (Đa luồng, xử lý lệnh, Passive Mode, Logging, Quản lý Sandbox root, Bảo mật) & Thiết kế giao thức `PROTOCOL.md` | **Đã hoàn thành 100%** |
| 2 | **Nguyễn Anh Vũ** | **2380602582** | Thành viên | **Xây dựng FTP Client** (Giao diện đồ họa Java Swing, quản lý kết nối, hiển thị tệp tin, cập nhật thanh tiến trình JProgressBar) | *Đang phát triển* |
| 3 | **Phan Thanh Trọng** | **2380602351** | Thành viên | **Xây dựng Data Channel** (Passive Mode, Stream Binary Buffer STOR/RETR, Listener tiến độ & tốc độ, Xử lý lỗi toàn diện, Test hiệu năng & Checksum SHA-256) | **Đã hoàn thành 100%** |

---

## 📂 CẤU TRÚC THƯ MỤC DỰ ÁN

```text
project/
├── server/                     # [Trần Nhật Tiến] Mã nguồn đầy đủ của FTP Server
│   ├── FtpServer.java          # Lớp chính khởi chạy Server đa luồng & Admin Console
│   ├── ClientHandler.java      # Luồng xử lý lệnh điều khiển và trạng thái từng Client
│   ├── PassiveDataConnection.java # Quản lý mở cổng và truyền nhận trên Data Channel
│   ├── UserManager.java        # Quản lý xác thực tài khoản đăng nhập
│   ├── ServerConfig.java       # Lưu các thông số cấu hình hệ thống (Port, Root, Buffer...)
│   ├── ServerLogger.java       # Ghi log hoạt động ra Console và tệp server.log
│   ├── FtpResponseCode.java    # Bảng định nghĩa mã phản hồi chuẩn FTP (RFC 959)
│   ├── BienDich.bat            # Script biên dịch tự động Server trên Windows
│   ├── ChayServer.bat          # Script khởi chạy nhanh Server bằng 1-click
│   └── ftp_root/               # Thư mục Sandbox chứa dữ liệu mẫu của Server
│       ├── welcome.txt
│       └── tailieu/
│           └── huong_dan_ftp.txt
├── client/                     # [Nguyễn Anh Vũ] Module giao diện và ứng dụng Client
│   ├── .gitkeep
│   ├── README.md               # Hướng dẫn chi tiết nhiệm vụ của Vũ
│   └── FtpClientStub.java      # Khung sườn code mẫu cho giao diện Swing
├── datachannel/                # [Phan Thanh Trọng] Module kênh dữ liệu dùng chung
│   ├── .gitkeep
│   ├── README.md               # Hướng dẫn chi tiết nhiệm vụ của Trọng
│   ├── DataTransferListener.java # Interface callback tiến độ truyền tải dữ liệu
│   └── DataChannelHelper.java  # Khung sườn xử lý truyền nhận stream nhị phân
├── PROTOCOL.md                 # Bản đặc tả chi tiết giao thức FTP Hai kênh
├── README.md                   # Tài liệu giới thiệu tổng quan dự án
└── .gitignore                  # Cấu hình bỏ qua các tệp tạm và file build .class
```

---

## 🚀 HƯỚNG DẪN BIÊN DỊCH VÀ KHỞI CHẠY SERVER

### 1. Yêu cầu môi trường
- **Hệ điều hành**: Windows 10/11 (hoặc Linux/macOS).
- **Java**: Java Development Kit (JDK) 11 trở lên (Khuyến nghị **JDK 17**).

### 2. Biên dịch mã nguồn Server
Mở terminal tại thư mục gốc `project` và chạy lệnh:
```powershell
javac -encoding UTF-8 -d bin server\*.java
```
Hoặc trên Windows, bạn chỉ cần nhấp đúp chuột vào tệp:
```text
server\BienDich.bat
```

### 3. Khởi chạy Server
Chạy lệnh Java với classpath trỏ tới thư mục `bin`:
```powershell
java -Dfile.encoding=UTF-8 -cp bin server.FtpServer 2121 "server\ftp_root"
```
Hoặc nhấp đúp chuột vào tệp:
```text
server\ChayServer.bat
```

---

## 🔐 DANH SÁCH TÀI KHOẢN MẪU ĐỂ KIỂM THỬ

Server đã được nạp sẵn danh sách tài khoản phục vụ đánh giá đề tài:

| Tài khoản | Mật khẩu | Quyền hạn |
| :--- | :--- | :--- |
| `tien` | `123456` | Toàn quyền thao tác |
| `vu` | `123456` | Toàn quyền thao tác |
| `trong` | `123456` | Toàn quyền thao tác |
| `admin` | `admin123` | Quản trị viên hệ thống |
| `anonymous` | *(Bất kỳ)* | Khách vãng lai |

Ngoài ra, tại cửa sổ Console của Server, quản trị viên có thể gõ lệnh `adduser <username> <password>` để cấp tài khoản mới bất cứ lúc nào!

---

## 🛠️ CÁC LỆNH QUẢN TRỊ CONSOLE (ADMIN CONSOLE)

Khi Server đang chạy, bạn có thể gõ trực tiếp các lệnh sau vào màn hình Console của Server:
- `status`: Xem trạng thái hoạt động, cổng, thư mục gốc và số lượng Client đã kết nối.
- `users`: Xem toàn bộ danh sách tài khoản hiện có.
- `adduser <user> <pass>`: Thêm tài khoản mới ngay lập tức.
- `help`: Xem hướng dẫn sử dụng.
- `stop` hoặc `exit`: Dừng Server và đóng các kết nối một cách an toàn.

---

## 📋 THÔNG TIN LIÊN HỆ & BÁO CÁO TIẾN ĐỘ
- Mọi thắc mắc về kiến trúc kết nối hoặc cần hỗ trợ ghép nối giữa Client và Server, các thành viên liên hệ Nhóm trưởng **Trần Nhật Tiến** hoặc tham khảo chi tiết tài liệu [PROTOCOL.md](PROTOCOL.md).
