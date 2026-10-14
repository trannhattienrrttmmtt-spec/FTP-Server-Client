# MODULE DATACHANNEL (KÊNH TRUYỀN DỮ LIỆU)

**Phụ trách phát triển**: Phan Thanh Trọng (MSSV: 2380602351)  
**Môn học**: Lập trình mạng máy tính - Lớp: 23DTHB4 - Nhóm 11  
**Mục tiêu**: Xây dựng module dùng chung (Shared Component) quản lý kênh truyền nhị phân (Binary Stream) cho Kênh dữ liệu (Data Channel).  
**Trạng thái**: **ĐÃ HOÀN THÀNH 100%**  

---

## 1. Cấu trúc tệp tin trong Module

- `DataTransferListener.java`: Interface callback thông báo tiến trình truyền tải, tỉ lệ %, và tốc độ thời gian thực (`speedKBps`).
- `DataTransferStats.java`: Lớp thu thập và định dạng số liệu hiệu năng (dung lượng, thời gian ms, tốc độ MB/s, mã băm SHA-256).
- `PassiveModeInfo.java`: Lớp phân tích phản hồi 227 của lệnh `PASV`, trích xuất IP và tính toán cổng dữ liệu `(p1 << 8) | p2`.
- `DataChannelHelper.java`: Lớp tiện ích cốt lõi xử lý truyền nhận file qua luồng byte nhị phân (`BufferedInputStream`, `BufferedOutputStream`), kiểm tra quyền truy cập, dọn dẹp file tạm `.part` khi mất kết nối, và tính toán SHA-256 checksum.
- `DataChannelPerformanceTest.java`: Ứng dụng benchmark độc lập kiểm thử hiệu năng truyền file (10KB, 2MB, 20MB) và kiểm thử các ca lỗi.
- `BAO_CAO_DATA_CHANNEL.md`: Báo cáo kỹ thuật chi tiết với bảng biểu số liệu thực nghiệm.

---

## 2. Hướng dẫn Biên dịch và Chạy Kiểm thử Hiệu năng

### Cách 1: Chạy file Batch tự động (Windows 1-Click)
Nhấp đúp chuột vào file:
```text
ChayTestHieuNang.bat
```

### Cách 2: Chạy qua Command Line (Terminal)
1. **Biên dịch**:
   ```powershell
   javac -encoding UTF-8 -d bin datachannel\*.java server\*.java client\*.java
   ```
2. **Khởi động Server** (nếu chưa chạy):
   ```powershell
   java "-Dfile.encoding=UTF-8" -cp bin server.FtpServer 2121 "server\ftp_root"
   ```
3. **Chạy Benchmark Java**:
   ```powershell
   java "-Dfile.encoding=UTF-8" -cp bin datachannel.DataChannelPerformanceTest
   ```
4. **Chạy Benchmark Python**:
   ```powershell
   python test_datachannel_performance.py
   ```
