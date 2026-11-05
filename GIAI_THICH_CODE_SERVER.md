# Giải thích mã nguồn FTP Server

Tài liệu này giải thích phần server của dự án FTP hai kênh. Mã nguồn chính nằm trong thư mục `server`, viết bằng Java, dùng TCP socket và có hai cách quản trị: dòng lệnh (CLI) hoặc giao diện Java Swing.

## 1. Kiến trúc và luồng hoạt động

Server nhận kết nối, xác thực tài khoản và thao tác tệp trong thư mục Sandbox Root. Nó dùng hai kết nối TCP:

| Kênh | Chức năng | Vòng đời |
|---|---|---|
| Control Channel | Trao đổi lệnh FTP và mã phản hồi | Giữ mở suốt phiên |
| Data Channel | Truyền danh sách thư mục hoặc byte của file | Mở riêng cho mỗi thao tác dữ liệu rồi đóng |

Cổng điều khiển mặc định là `2121`. Kênh dữ liệu chạy Passive Mode: server thử các cổng `50000–50100`, hoặc nhờ hệ điều hành chọn cổng nếu dải đó bận.

Luồng chính: `FtpServer` mở cổng và chờ kết nối; mỗi client được giao cho một `ClientHandler`; handler xử lý lệnh tuần tự trong phiên; lệnh dữ liệu sử dụng `PASV` để mở `PassiveDataConnection`; cuối phiên các socket được đóng. Các client khác nhau chạy đồng thời trong cached thread pool.

## 2. Giải thích từng tệp

### FtpServer.java — khởi chạy và quản lý server

Đây là lớp mạng chính.

- Constructor lưu cổng, chuẩn hóa thư mục root qua `ServerConfig.getResolvedRootDir()`, nhận hoặc tạo `UserManager` và khởi tạo cached thread pool.
- `start()` in banner, mở `ServerSocket`, ghi log, đăng ký shutdown hook và (ở CLI) chạy luồng đọc lệnh quản trị. Vòng `accept()` chờ client mới, cấp số thứ tự, tạo `ClientHandler` rồi giao cho thread pool.
- `stop()` đánh dấu server dừng, đóng socket lắng nghe để thoát khỏi `accept()` và gọi `shutdownNow()`.
- `runAdminConsole()` đọc lệnh `status`, `users`, `adduser <user> <pass>`, `help`, `stop` hoặc `exit`.
- `printStatus()`, `printUsers()` và `printAdminHelp()` hiển thị trạng thái, tài khoản và trợ giúp. Lưu ý `printUsers()` in cả mật khẩu.
- `main(args)` đọc tham số tùy chọn: cổng, rồi thư mục root; nếu không có thì dùng cấu hình mặc định.
- Các getter cung cấp trạng thái, cổng, root, user manager, tổng kết nối và thời điểm khởi động cho GUI.

`clientCounter` đếm tổng số client từng được chấp nhận từ lúc server chạy, không phải số kết nối hiện còn hoạt động.

### ClientHandler.java — một phiên FTP

Mỗi đối tượng triển khai `Runnable` và xử lý một client. Nó nắm control socket, root, user manager, thư mục hiện tại, trạng thái đăng nhập và kênh dữ liệu passive.

- `run()` đặt timeout Control Channel 120 giây, tạo reader/writer UTF-8, gửi lời chào `220`, rồi đọc từng dòng lệnh. Lệnh được ghi log và chuyển cho `handleCommand()`. Khi timeout, lỗi socket hoặc client ngắt, khối `finally` gọi `closeSession()`.
- `handleCommand()` tách lệnh và tham số, chuyển lệnh thành chữ hoa và điều phối bằng `switch`. Các bí danh `XPWD`, `XCWD`, `XCUP`, `XMKD`, `XRMD` tương ứng với lệnh chuẩn; lệnh chưa hỗ trợ trả `500`.
- `USER <username>` lưu tên chờ xác thực, đặt trạng thái đăng nhập về false và trả `331`.
- `PASS <password>` gọi `UserManager.authenticate()`. Thành công thì đưa thư mục hiện tại về root và trả `230`; thất bại trả `530`.
- `checkLogin()` chặn đa số lệnh thao tác tệp nếu chưa đăng nhập.
- `PWD`/`XPWD` trả đường dẫn ảo hiện tại bằng `257`.
- `CWD`/`XCWD` chuyển thư mục nếu đường dẫn là thư mục hợp lệ trong sandbox. `CDUP`/`XCUP` đi lên thư mục cha, nhưng không cho vượt root.
- `PASV` đóng listener cũ, tạo listener mới và gửi địa chỉ/cổng trong phản hồi `227`.
- `LIST [path]` gửi danh sách chi tiết (quyền Unix mô phỏng, kích thước, ngày sửa, tên). `NLST` chỉ gửi tên. Nếu path LIST/NLST không hợp lệ, code dùng thư mục hiện tại.
- `RETR <file>` kiểm tra đăng nhập, tham số, PASV, sự tồn tại, loại file và quyền đọc; gửi `150`, truyền file, rồi `226` khi hoàn tất.
- `STOR <file>` kiểm tra đăng nhập, tham số, PASV và quyền ghi; nhận file qua data socket và trả `226` khi hoàn tất.
- `DELE <file>` xóa file; `MKD <dir>` tạo thư mục (có thể tạo các cấp cha); `RMD <dir>` xóa thư mục rỗng nhưng từ chối xóa root; `SIZE <file>` trả số byte bằng `213`.
- `TYPE A/I` phản hồi thành công nhưng không lưu trạng thái kiểu truyền; dữ liệu vẫn truyền byte thô.
- `SYST` trả thông tin hệ thống; `FEAT` liệt kê PASV, SIZE, UTF8; `NOOP` phản hồi kiểm tra kết nối; `HELP` gửi trợ giúp; `QUIT` trả `221` và kết thúc phiên.
- `resolvePath()` ghép đường dẫn tương đối vào thư mục hiện tại, hoặc diễn giải path bắt đầu bằng `/` hay `\` từ root FTP. `isSafePath()` kiểm tra canonical path; `getVirtualPath()` đổi lại thành đường dẫn ảo.
- `sendResponse()` tạo phản hồi một dòng; `sendRawResponse()` ghi log, thêm CRLF và gửi qua control socket. `closePassiveConnection()` đóng data listener; `closeSession()` đóng các luồng và socket của phiên.

### PassiveDataConnection.java — truyền trên Data Channel

Quản lý listener passive và socket dữ liệu của một phiên.

- Constructor gọi `openPassiveServerSocket()`, thử từng cổng từ cấu hình, bind vào địa chỉ local của control socket và đặt timeout 60 giây. Nếu tất cả cổng trong dải bận, hệ điều hành chọn cổng trống.
- `getPassiveResponseString()` chuyển IPv4 thành bốn số h1–h4, tách cổng thành `p1 = port / 256` và `p2 = port % 256`, sau đó dựng phản hồi `227`.
- `acceptConnection()` đợi client nối vào Data Channel; quá timeout thì ném lỗi socket.
- `sendTextData()` gửi text UTF-8 cho LIST/NLST.
- `sendFile()` đọc file theo buffer 8 KB, gửi từng khối byte và đếm tổng byte.
- `receiveFile()` ghi dữ liệu vào file tạm `<tên>.part`. Truyền xong mới xóa file đích cũ và đổi tên file tạm thành file đích; nếu lỗi thì cố gắng xóa file tạm.
- `close()` đóng data socket và listener; `isOpen()` kiểm tra listener.

Kết thúc truyền được nhận biết khi phía gửi đóng luồng và phía nhận đọc tới EOF. Dùng buffer giúp truyền file lớn mà không đọc toàn bộ file vào RAM.

### UserManager.java — tài khoản và xác thực

Lưu tài khoản trong `ConcurrentHashMap` để nhiều thread có thể truy cập. Tài khoản mặc định là `tien/123456`, `vu/123456`, `trong/123456`, `admin/admin123`, `test/test` và `anonymous/anonymous`.

`userExists()` và `authenticate()` trim/chuyển username thành chữ thường. Riêng `anonymous` được chấp nhận với bất kỳ mật khẩu nào. `addUser()` thêm hoặc cập nhật, `removeUser()` xóa, `getAllUsers()` trả map chỉ đọc. Server không phân quyền theo vai trò: tài khoản đã đăng nhập có cùng tập lệnh thao tác.

### ServerConfig.java — cấu hình

Chứa các hằng số: cổng mặc định 2121, root mặc định `ftp_root`, buffer 8192 byte, timeout điều khiển 120000 ms, timeout dữ liệu 60000 ms, UTF-8 và dải PASV 50000–50100. `getResolvedRootDir()` chọn đường dẫn cấu hình hoặc mặc định, biến đường dẫn tương đối thành đường dẫn tính từ `user.dir`, tạo thư mục nếu chưa tồn tại.

### FtpResponseCode.java — mã phản hồi

Định nghĩa các mã FTP dùng chung như 150, 200, 220, 221, 226, 227, 230, 331, 425, 426, 500, 501, 530 và 550. Các handler dùng hằng số này để gửi trạng thái nhất quán.

### ServerLogger.java — nhật ký

Ghi nối tiếp vào `server.log` trong thư mục làm việc hiện tại và in ra console. Mỗi dòng có thời gian, mức log và nhãn client. Các mức gồm INFO, WARN, ERROR, DEBUG, CLIENT_IN, CLIENT_OUT. `logRecv()`/`logSend()` ghi lệnh nhận/phản hồi gửi; `setUiLogListener()` chuyển log sang GUI. Khóa đồng bộ bảo vệ lúc nhiều thread cùng ghi.

### ServerApp.java — giao diện quản trị Swing

Giao diện điều khiển server, không thay thế phần xử lý giao thức. Nó tạo `FtpServer` trong thread nền và dùng cùng `UserManager`.

- Constructor dựng giao diện, nối listener log, nạp bảng user/tệp, dò IP LAN và bật timer cập nhật dashboard mỗi giây. Server tự khởi động khi mở GUI.
- `buildUi()` tạo cấu hình cổng/root, dashboard, bảng tài khoản, bảng file root, khung log và ô lệnh admin.
- `wireEvents()` nối sự kiện chọn root, mở thư mục, start/stop, quản lý user, làm mới file và xóa log.
- `startServer()` kiểm tra cổng 1–65535, khóa trường cấu hình, tạo `FtpServer(..., userManager, false)` để tắt console input, rồi chạy server trong thread nền. `stopServer()` dừng ở thread riêng; `updateStoppedUiState()` mở lại nút/trường cấu hình.
- `updateDashboardMetrics()` hiển thị tổng lượt kết nối và uptime.
- `executeAdminCommand()` xử lý status, users, adduser, clear, start, stop, help.
- `appendLogAsync()` thêm log trên Swing Event Dispatch Thread, tự cuộn nếu được bật và làm mới bảng file khi log chứa 226 hoặc 250.
- `refreshRootFiles()` liệt kê mục trực tiếp bên dưới root và tính số file/thư mục, tổng byte của file ở cấp này.
- `onExit()` dừng timer/server và thoát ứng dụng. `detectLanIp()` dò IPv4 LAN; `formatSize()` định dạng dung lượng.
- Các lớp con vẽ nút, nhãn trạng thái và icon; `UserTableModel` và `RootFileTableModel` cấp dữ liệu cho JTable.
- `main(args)` đọc cổng/root tùy chọn và mở JFrame trên Swing event thread.

### Các file chạy Windows

- `server/BienDich.bat` chuyển console sang UTF-8, về thư mục dự án, tạo `bin` nếu cần và biên dịch `server\*.java`.
- `server/ChayServer.bat` biên dịch nếu thiếu class chính, sau đó chạy `server.FtpServer` với cổng 2121 và root `server\ftp_root`.
- `server/ChayServerGUI.bat` biên dịch datachannel/server/client nếu thiếu GUI class, sau đó chạy `server.ServerApp`.

## 3. Mã phản hồi thường gặp

| Mã | Ý nghĩa |
|---:|---|
| 150 | Chuẩn bị truyền dữ liệu |
| 200 | Lệnh thành công |
| 211 | Tính năng FEAT |
| 213 | Kích thước file |
| 215 | Loại hệ thống |
| 220 | Server sẵn sàng |
| 221 | Đóng phiên điều khiển |
| 226 | Truyền dữ liệu hoàn tất |
| 227 | Địa chỉ/cổng Passive |
| 230 | Đăng nhập thành công |
| 250 | Thao tác file/thư mục thành công |
| 257 | Đường dẫn hiện tại hoặc thư mục vừa tạo |
| 331 | Cần mật khẩu |
| 425 | Data Channel chưa sẵn sàng |
| 426 | Truyền dữ liệu lỗi/gián đoạn |
| 500/501 | Lệnh/tham số sai |
| 530 | Chưa đăng nhập hoặc xác thực thất bại |
| 550 | File/thư mục không có hoặc thao tác bị từ chối |

## 4. Ví dụ tải file

```text
Client -> Control: USER tien
Server -> Control: 331 ...
Client -> Control: PASS 123456
Server -> Control: 230 ...
Client -> Control: PASV
Server -> Control: 227 Entering Passive Mode (...)
Client -> Data:    kết nối tới IP/cổng trong phản hồi 227
Client -> Control: RETR welcome.txt
Server -> Control: 150 ...
Server -> Data:    gửi các khối byte của welcome.txt
Server -> Control: 226 ...
```

Upload STOR theo luồng tương tự nhưng client gửi byte cho server, server ghi qua file tạm .part.

## 5. Biên dịch và chạy

Từ thư mục gốc dự án:

```powershell
javac -encoding UTF-8 -d bin server\*.java
java -Dfile.encoding=UTF-8 -cp bin server.FtpServer 2121 "server\ftp_root"
```

Chạy GUI quản trị:

```powershell
javac -encoding UTF-8 -d bin datachannel\*.java server\*.java client\*.java
java -Dfile.encoding=UTF-8 -cp bin server.ServerApp 2121 "server\ftp_root"
```

## 6. Lưu ý về mã hiện tại

- Đây là FTP trên TCP không có TLS: username, password và dữ liệu truyền không được mã hóa.
- Lệnh nhận được ghi nguyên văn vào log, vì vậy PASS có thể xuất hiện trong `server.log`. Lệnh CLI `users` in password; bảng GUI cũng hiển thị mật khẩu.
- Mật khẩu mẫu lưu dạng văn bản trong bộ nhớ; anonymous được chấp nhận với password bất kỳ. Tài khoản không được lưu sau khi server tắt.
- Kiểm tra sandbox dùng `canonicalPath.startsWith(canonicalRoot)`. So sánh tiền tố có thể nhầm đường dẫn anh em có cùng phần đầu tên; nên kiểm tra ranh giới thư mục hoặc dùng `Path.startsWith` để tăng cường bảo vệ.
- `TYPE A` chỉ được xác nhận, không chuyển đổi newline hay lưu kiểu truyền; file vẫn truyền byte thô.
- Phản hồi PASV được dựng theo IPv4; môi trường IPv6 cần xử lý riêng.
- Quyền Unix trong LIST là chuỗi mô phỏng, không phải quyền hệ thống thật.
- GUI chỉ hiển thị các mục trực tiếp dưới root, không chỉnh sửa nội dung file.

## 7. Sơ đồ trách nhiệm

```text
ServerApp (GUI quản trị) ─┐
                          ├─> FtpServer ─> ClientHandler (mỗi client)
CLI: FtpServer.main() ────┘                     ├─ UserManager
                                                ├─ ServerConfig
                                                ├─ ServerLogger
                                                └─ PassiveDataConnection ─> Data Socket
```

Tóm lại: FtpServer nhận kết nối và quản lý vòng đời; ClientHandler thực thi lệnh và giữ trạng thái phiên; PassiveDataConnection truyền danh sách/file; các lớp khác phụ trách cấu hình, mã phản hồi, tài khoản, log và giao diện.
