# TRƯỜNG ĐẠI HỌC CÔNG NGHỆ TP. HCM (HUTECH)
## KHOA CÔNG NGHỆ THÔNG TIN

---

# ĐỒ ÁN MÔN HỌC: LẬP TRÌNH MẠNG MÁY TÍNH
## ĐỀ TÀI: HỆ THỐNG FTP SERVER/CLIENT CƠ BẢN VỚI HAI KÊNH (ĐIỀU KHIỂN VÀ DỮ LIỆU)

- **Ngành**: CÔNG NGHỆ THÔNG TIN
- **Giảng viên hướng dẫn**: ThS. Nguyễn Lê Văn
- **Nhóm sinh viên thực hiện (Nhóm 11 - Lớp 23DTHB4)**:
  1. **Trần Nhật Tiến** - MSSV: **2380602243** *(Trưởng nhóm)*
  2. **Phan Thanh Trọng** - MSSV: **2380602351**
  3. **Nguyễn Anh Vũ** - MSSV: **2380602582**
- **Thời gian**: TP. Hồ Chí Minh, Tháng 10/2026

---

## LỜI CAM ĐOAN

Nhóm sinh viên chúng em gồm: **Trần Nhật Tiến**, **Phan Thanh Trọng** và **Nguyễn Anh Vũ**, sinh viên lớp 23DTHB4, Khoa Công nghệ Thông tin - Trường Đại học Công nghệ TP.HCM (HUTECH), xin cam đoan đồ án môn học Lập trình mạng máy tính với đề tài **“HỆ THỐNG FTP SERVER/CLIENT CƠ BẢN VỚI HAI KÊNH (ĐIỀU KHIỂN VÀ DỮ LIỆU)”** là công trình nghiên cứu và cài đặt thực nghiệm độc lập của nhóm dưới sự hướng dẫn của **ThS. Nguyễn Lê Văn**.

Mọi số liệu, lược đồ, bảng biểu, thuật toán và mã nguồn trong đồ án đều hoàn toàn trung thực, có thể kiểm chứng độc lập trên hệ thống thực tế. Nhóm cam kết tuân thủ nghiêm ngặt quy chế chống đạo văn của Trường Đại học Công nghệ TP.HCM (HUTECH), các tài liệu tham khảo đều được trích dẫn nguồn gốc rõ ràng, minh bạch.

---

## LỜI CẢM ƠN

Nhóm xin chân thành cảm ơn **ThS. Nguyễn Lê Văn** đã tận tâm giảng dạy, truyền đạt kiến thức chuyên sâu về giao thức mạng TCP/IP, kỹ thuật lập trình Socket đa luồng trong Java và định hướng phương pháp nghiên cứu để nhóm hoàn thiện đồ án này. Nhóm cũng xin cảm ơn Ban Giám hiệu cùng quý Thầy Cô Khoa Công nghệ Thông tin - Đại học HUTECH đã tạo điều kiện học tập và thực hành tốt nhất.

---

## MỤC LỤC TỔNG QUAN

- **Chương 1. TỔNG QUAN**
  - 1.1. Giới thiệu đề tài
  - 1.2. Tính cấp thiết và lý do chọn đề tài
  - 1.3. Mục tiêu nghiên cứu, đối tượng và phạm vi
  - 1.4. Phân công nhiệm vụ và cấu trúc đồ án
- **Chương 2. CƠ SỞ LÝ THUYẾT VÀ PHƯƠNG PHÁP GIẢI QUYẾT**
  - 2.1. Mô hình Client - Server và tầng truyền vận TCP/IP
  - 2.2. Giao thức truyền tệp FTP (RFC 959) và Kiến trúc Hai kênh
  - 2.3. Đặc tả tập lệnh và bảng mã phản hồi chuẩn
  - 2.4. Kỹ thuật lập trình Socket đa luồng trong Java
  - 2.5. Cơ chế an toàn và phòng thủ bảo mật Sandbox Root
- **Chương 3. KẾT QUẢ THỰC NGHIỆM VÀ CÀI ĐẶT HỆ THỐNG**
  - 3.1. Thiết kế kiến trúc tổng thể hệ thống
  - 3.2. Cài đặt chi tiết Module Server (Sinh viên Trần Nhật Tiến)
  - 3.3. Cài đặt chi tiết Module Data Channel (Sinh viên Phan Thanh Trọng)
  - 3.4. Cài đặt chi tiết Module Client (Sinh viên Nguyễn Anh Vũ)
  - 3.5. Biểu đồ tuần tự (Sequence Diagrams) các luồng tương tác chính
  - 3.6. Đánh giá kết quả kiểm thử chức năng và hiệu năng thực tế
- **Chương 4. KẾT LUẬN VÀ KIẾN NGHỊ**
  - 4.1. Đánh giá kết quả đạt được
  - 4.2. Những đóng góp và bài học kinh nghiệm
  - 4.3. Các hạn chế còn tồn tại
  - 4.4. Đề xuất và hướng phát triển tương lai
- **TÀI LIỆU THAM KHẢO**
- **PHỤ LỤC**

---

## DANH MỤC TỪ VIẾT TẮT

| Viết tắt | Thuật ngữ tiếng Anh | Ý nghĩa tiếng Việt |
| :--- | :--- | :--- |
| **FTP** | File Transfer Protocol | Giao thức truyền tải tệp tin (RFC 959) |
| **TCP** | Transmission Control Protocol | Giao thức điều khiển truyền vận tin cậy có hướng kết nối |
| **IP** | Internet Protocol | Giao thức liên mạng định tuyến gói tin |
| **PASV** | Passive Mode | Chế độ truyền dữ liệu thụ động |
| **RFC** | Request for Comments | Tập hợp tài liệu chuẩn kỹ thuật của IETF |
| **LAN** | Local Area Network | Mạng máy tính cục bộ |
| **GUI** | Graphical User Interface | Giao diện đồ họa người dùng |
| **CLI** | Command Line Interface | Giao diện dòng lệnh văn bản |
| **SHA-256** | Secure Hash Algorithm 256-bit | Thuật toán băm kiểm tra tính toàn vẹn |
| **NAT** | Network Address Translation | Cơ chế biên dịch địa chỉ mạng |
| **CRLF** | Carriage Return Line Feed | Ký tự kết thúc dòng điều khiển (`\r\n`) |

---

## CHƯƠNG 1. TỔNG QUAN

### 1.1. Giới thiệu đề tài
FTP (RFC 959) là một trong những giao thức tầng ứng dụng kinh điển nhất của Internet. Điểm ưu việt cốt lõi của FTP là tách biệt Kênh điều khiển (Control Channel) và Kênh dữ liệu (Data Channel). Đề tài xây dựng một hệ thống FTP độc lập bằng Java, đáp ứng nhu cầu chia sẻ và truyền tải dữ liệu an toàn, tốc độ cao.

### 1.2. Tính cấp thiết và lý do chọn đề tài
Môn học Lập trình mạng máy tính đòi hỏi sinh viên nắm vững nguyên lý TCP/IP, lập trình Socket và xử lý bất đồng bộ. Việc tự tay thiết kế và lập trình hệ thống FTP hai kênh giúp sinh viên làm chủ các kỹ thuật:
- Đàm phán cổng động trong Passive Mode (PASV).
- Quản lý đa luồng (Multi-threading) bằng `ExecutorService`.
- Tối ưu luồng I/O với kỹ thuật đệm nhị phân (Buffer Stream).
- Phòng thủ chống lỗ hổng Directory Traversal bằng kỹ thuật Sandbox Root.

### 1.3. Phân công nhiệm vụ (Nhóm 11 - 23DTHB4)
- **Trần Nhật Tiến (MSSV: 2380602243 - Trưởng nhóm)**: Phụ trách toàn bộ Module Server, xử lý lệnh, Passive Data Connection, Sandbox Security, PROTOCOL.md.
- **Phan Thanh Trọng (MSSV: 2380602351)**: Phụ trách Module Data Channel dùng chung, tối ưu buffer stream, Listener báo tiến độ, bộ test hiệu năng tự động (Java & Python).
- **Nguyễn Anh Vũ (MSSV: 2380602582)**: Phụ trách Module Client đồ họa Java Swing (JTable, JProgressBar, Log console) và Client dòng lệnh Console.

---

## CHƯƠNG 2. CƠ SỞ LÝ THUYẾT VÀ PHƯƠNG PHÁP GIẢI QUYẾT

### 2.1. So sánh Active Mode và Passive Mode
- **Active Mode**: Client tự mở cổng dữ liệu và gửi lệnh `PORT` bảo Server kết nối về. Chế độ này thường bị chặn bởi Tường lửa (Firewall) hoặc router NAT phía Client.
- **Passive Mode (Nhóm lựa chọn)**: Client gửi lệnh `PASV`, Server tự mở cổng dữ liệu trong dải `50000 - 50100` và trả về `(h1,h2,h3,h4,p1,p2)`. Client là bên chủ động kết nối chiều ra (Outbound), do đó vượt qua Firewall/NAT dễ dàng.

### 2.2. Công thức tính toán cổng Passive Mode (PASV)
Server gửi: `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`
- $h1, h2, h3, h4$ là 4 octet địa chỉ IP của Server.
- $p1, p2$ là 2 byte biểu diễn số hiệu cổng 16-bit:
$$\text{Data\_Port} = p1 \times 256 + p2 = (p1 \ll 8) \mid p2$$
Phía Server tính toán:
$$p1 = \lfloor \text{Data\_Port} / 256 \rfloor, \quad p2 = \text{Data\_Port} \pmod{256}$$

### 2.3. Bảng tập lệnh điều khiển
- Quản lý phiên: `USER`, `PASS`, `QUIT`, `NOOP`.
- Điều hướng: `PWD`, `CWD`, `CDUP`.
- Kênh dữ liệu: `PASV`, `LIST`, `RETR` (Download), `STOR` (Upload).
- Quản lý tệp: `DELE`, `MKD`, `RMD`, `SIZE`, `TYPE`.

### 2.4. Kỹ thuật Sandbox Root chống tấn công Path Traversal
Trước khi cho phép thao tác bất kỳ tệp tin nào, Server giải quyết đường dẫn chuẩn hóa tuyệt đối:
$$\text{canonicalTarget.startsWith(canonicalRoot)}$$
Nếu đường dẫn nằm ngoài thư mục `ftp_root`, Server lập tức từ chối với mã lỗi `550 Permission denied`.

---

## CHƯƠNG 3. KẾT QUẢ THỰC NGHIỆM VÀ CÀI ĐẶT

### 3.1. Kết quả hiệu năng truyền tải trên Data Channel (Buffer 8KB / 64KB)

| Thao tác | Tệp tin thử nghiệm | Dung lượng | Thời gian (ms) | Thông lượng (MB/s) | Toàn vẹn SHA-256 |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **UPLOAD** | `file_nho_10KB.bin` | 10.00 KB | 3 ms | 3.26 MB/s | **Khớp 100%** |
| **DOWNLOAD** | `file_nho_10KB.bin` | 10.00 KB | 2 ms | 4.88 MB/s | **Khớp 100%** |
| **UPLOAD** | `file_vua_2MB.bin` | 2.00 MB | 16 ms | 125.00 MB/s | **Khớp 100%** |
| **DOWNLOAD** | `file_vua_2MB.bin` | 2.00 MB | 30 ms | 66.67 MB/s | **Khớp 100%** |
| **UPLOAD** | `file_lon_20MB.bin` | 20.00 MB | 51 ms | 392.16 MB/s | **Khớp 100%** |
| **DOWNLOAD** | `file_lon_20MB.bin` | 20.00 MB | 167 ms | 119.76 MB/s | **Khớp 100%** |

### 3.2. Kết quả kiểm thử phòng thủ và ngoại lệ mạng
1. **Truy vấn tệp không tồn tại**: `RETR khong_ton_tai.abc` -> Server trả về `550 File unavailable` chính xác.
2. **Tấn công leo thang thư mục**: `STOR ../../../hack.exe` -> Bị chặn đứng với `550 Permission denied`.
3. **Mất kết nối giữa chừng (Broken Socket)**: Server bắt ngoại lệ `IOException`, giải phóng cổng và gửi `426 Transfer aborted`.

---

## CHƯƠNG 4. KẾT LUẬN VÀ KIẾN NGHỊ

- **Kết quả đạt được**: Hoàn thành 100% yêu cầu đồ án, chạy ổn định cả trên localhost lẫn 2 máy tính qua mạng Wi-Fi LAN.
- **Hướng phát triển**: Bổ sung mã hóa FTPS (TLS/SSL) theo RFC 4217, hỗ trợ tiếp tục truyền tải dở dang (`REST`) và lưu trữ tài khoản vào Database.

---

## TÀI LIỆU THAM KHẢO

1. Postel, J. & Reynolds, J. (1985). *File Transfer Protocol (FTP)*. RFC 959, IETF.
2. Kurose, J.F. & Ross, K.W. (2021). *Computer Networking: A Top-Down Approach (8th Edition)*. Pearson.
3. Harold, E.R. (2013). *Java Network Programming (4th Edition)*. O'Reilly Media.
4. Schildt, H. (2022). *Java: The Complete Reference (12th Edition)*. McGraw-Hill Education.
5. Oracle Corporation (2023). *Java Platform Standard Edition Documentation (JDK 17 API Specification)*.
