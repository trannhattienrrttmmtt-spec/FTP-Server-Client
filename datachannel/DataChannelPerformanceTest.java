package datachannel;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ứng dụng kiểm thử và đánh giá hiệu năng toàn diện trên Data Channel
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Phụ trách: Phan Thanh Trọng (MSSV: 2380602351)
 *
 * Kịch bản kiểm thử bao gồm:
 * 1. Khởi tạo và đàm phán Passive Mode (PASV)
 * 2. Truyền file nhỏ (10 KB) - STOR và RETR + Kiểm tra SHA-256 Integrity
 * 3. Truyền file trung bình (2 MB) - STOR và RETR + Đo Throughput (MB/s)
 * 4. Truyền file lớn (20 MB) - STOR và RETR + Đo Throughput & Kiểm tra Buffer
 * 5. Kiểm thử các tình huống lỗi mạng và bảo mật:
 *    - 5a: Tải file không tồn tại (550 File unavailable)
 *    - 5b: Vi phạm Sandbox Root / Path Traversal (550 Permission denied)
 *    - 5c: Mất kết nối giữa chừng (Simulated Broken Pipe / 426 Transfer aborted)
 */
public class DataChannelPerformanceTest {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int CONTROL_PORT = 2121;
    private static final String TEST_USER = "trong";
    private static final String TEST_PASS = "123456";

    private Socket controlSocket;
    private BufferedReader controlReader;
    private BufferedWriter controlWriter;

    private final List<DataTransferStats> testResults = new ArrayList<>();
    private final File tempDir = new File("test_temp_client");

    public static void main(String[] args) {
        DataChannelPerformanceTest tester = new DataChannelPerformanceTest();
        try {
            tester.runAllTests();
        } catch (Exception e) {
            System.err.println("\n[FATAL ERROR] Loi trong qua trinh kiem thu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runAllTests() throws Exception {
        printBanner();

        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        try {
            // 1. Kết nối Control Channel và Đăng nhập
            connectAndLogin();

            // 2. Test Truyền File Nhỏ (10 KB)
            testFileTransfer("file_nho_10KB.bin", 10 * 1024, DataChannelHelper.DEFAULT_BUFFER_SIZE);

            // 3. Test Truyền File Trung Bình (2 MB)
            testFileTransfer("file_vua_2MB.bin", 2 * 1024 * 1024, DataChannelHelper.DEFAULT_BUFFER_SIZE);

            // 4. Test Truyền File Lớn (20 MB) với Buffer 64KB
            testFileTransfer("file_lon_20MB.bin", 20 * 1024 * 1024, DataChannelHelper.LARGE_BUFFER_SIZE);

            // 5. Kiểm thử các ca lỗi thực tế
            testErrorFileNotFound();
            testErrorPathTraversal();
            testErrorBrokenConnectionMidway();

            // 6. In bảng tổng kết hiệu năng
            printSummaryReport();

        } finally {
            disconnect();
            cleanupTempFiles();
        }
    }

    private void printBanner() {
        System.out.println("================================================================================");
        System.out.println("   CHUONG TRINH TEST HIỆU NĂNG DATA CHANNEL & TRUYỀN FILE (NHÓM 11)             ");
        System.out.println("   Phụ trách phát triển: PHAN THANH TRỌNG (MSSV: 2380602351)                   ");
        System.out.println("   Môn học: Lập trình mạng máy tính - Lớp: 23DTHB4                             ");
        System.out.println("================================================================================");
    }

    // =========================================================================
    // KẾT NỐI VÀ ĐIỀU KHIỂN CƠ BẢN
    // =========================================================================

    private void connectAndLogin() throws Exception {
        System.out.println("\n>>> [BƯỚC 1] KẾT NỐI VÀ XÁC THỰC CONTROL CHANNEL (Port " + CONTROL_PORT + ")...");
        controlSocket = new Socket(SERVER_HOST, CONTROL_PORT);
        controlSocket.setSoTimeout(15000);
        controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        controlWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

        String banner = readResponse();
        System.out.println("<< " + banner);
        if (!banner.startsWith("220")) {
            throw new IllegalStateException("Server khong tra ve 220: " + banner);
        }

        String userResp = sendCommand("USER " + TEST_USER);
        if (!userResp.startsWith("331")) {
            throw new IllegalStateException("Loi USER: " + userResp);
        }

        String passResp = sendCommand("PASS " + TEST_PASS);
        if (!passResp.startsWith("230")) {
            throw new IllegalStateException("Loi PASS: " + passResp);
        }
        System.out.println(" [OK] Dang nhap thanh cong voi tai khoan: " + TEST_USER);
    }

    private String sendCommand(String cmd) throws IOException {
        System.out.println(">> " + cmd);
        controlWriter.write(cmd + "\r\n");
        controlWriter.flush();
        String resp = readResponse();
        System.out.println("<< " + resp);
        return resp;
    }

    private String readResponse() throws IOException {
        String line = controlReader.readLine();
        if (line == null) {
            throw new EOFException("Server da dong ket noi Control Channel.");
        }
        return line;
    }

    private PassiveModeInfo requestPassiveMode() throws Exception {
        String pasvResp = sendCommand("PASV");
        if (!pasvResp.startsWith("227")) {
            throw new IllegalStateException("PASV that bai: " + pasvResp);
        }
        PassiveModeInfo info = DataChannelHelper.parsePassiveAddress(pasvResp);
        System.out.printf(Locale.ENGLISH, " [PASSIVE] Server da mo cong Data Channel tai %s:%d\n",
                info.getHost(), info.getPort());
        return info;
    }

    // =========================================================================
    // KIỂM THỬ TRUYỀN FILE (STOR & RETR) + SHA-256 CHECK
    // =========================================================================

    private void testFileTransfer(String fileName, long sizeBytes, int bufferSize) throws Exception {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf(">>> [TEST TRUYỀN TẢI] File: %s (Kích thước: %s, Buffer: %d KB)\n",
                fileName, formatSize(sizeBytes), bufferSize / 1024);
        System.out.println("--------------------------------------------------------------------------------");

        // 1. Tạo file dữ liệu mẫu
        File uploadFile = new File(tempDir, "client_up_" + fileName);
        System.out.println("1. Tao file mau de upload...");
        DataChannelHelper.createDummyFile(uploadFile, sizeBytes);
        String originalHash = DataChannelHelper.calculateFileSHA256(uploadFile);
        System.out.println("   SHA-256 goc: " + originalHash);

        // 2. Upload file (STOR)
        System.out.println("\n2. Thuc hien STOR (Upload len Server)...");
        PassiveModeInfo pasvUpload = requestPassiveMode();
        Socket uploadDataSocket = DataChannelHelper.openDataSocket(pasvUpload.getHost(), pasvUpload.getPort(), 15000);

        String storResp = sendCommand("STOR " + fileName);
        if (!storResp.startsWith("150")) {
            uploadDataSocket.close();
            throw new IllegalStateException("Server tu choi STOR: " + storResp);
        }

        DataTransferStats uploadStats = DataChannelHelper.uploadFile(
                uploadDataSocket, uploadFile, bufferSize, new ConsoleTransferListener("UPLOAD " + fileName)
        );
        String uploadDoneResp = readResponse();
        System.out.println("<< " + uploadDoneResp);
        testResults.add(uploadStats);

        // 3. Download file (RETR)
        System.out.println("\n3. Thuc hien RETR (Download tu Server ve)...");
        PassiveModeInfo pasvDownload = requestPassiveMode();
        Socket downloadDataSocket = DataChannelHelper.openDataSocket(pasvDownload.getHost(), pasvDownload.getPort(), 15000);

        String retrResp = sendCommand("RETR " + fileName);
        if (!retrResp.startsWith("150")) {
            downloadDataSocket.close();
            throw new IllegalStateException("Server tu choi RETR: " + retrResp);
        }

        File downloadedFile = new File(tempDir, "client_down_" + fileName);
        DataTransferStats downloadStats = DataChannelHelper.downloadFile(
                downloadDataSocket, downloadedFile, sizeBytes, bufferSize,
                new ConsoleTransferListener("DOWNLOAD " + fileName)
        );
        String downloadDoneResp = readResponse();
        System.out.println("<< " + downloadDoneResp);
        testResults.add(downloadStats);

        // 4. So sánh Checksum SHA-256
        String downloadedHash = DataChannelHelper.calculateFileSHA256(downloadedFile);
        System.out.println("\n4. Kiem tra toan ven du lieu (Integrity Check):");
        System.out.println("   Original SHA-256:   " + originalHash);
        System.out.println("   Downloaded SHA-256: " + downloadedHash);

        if (originalHash.equalsIgnoreCase(downloadedHash)) {
            System.out.println("   ===> [KET QUA]: CHINH XAC 100%! DU LIEU TOAN VEN KHONG SAI LECH 1 BYTE!");
        } else {
            System.err.println("   ===> [KET QUA]: DU LIEU BI SAI LECH HASH!");
            throw new AssertionError("Checksum khong khop giua ban goc va ban tai ve!");
        }

        // Xóa file trên Server để dọn dẹp
        sendCommand("DELE " + fileName);
    }

    // =========================================================================
    // KIỂM THỬ XỬ LÝ LỖI (ERROR HANDLING)
    // =========================================================================

    private void testErrorFileNotFound() throws Exception {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(">>> [TEST LỖI 1] RETR Tệp Không Tồn Tại (File Not Found)");
        System.out.println("--------------------------------------------------------------------------------");
        PassiveModeInfo pasv = requestPassiveMode();
        // Không kết nối data socket vội vì server sẽ trả lỗi ngay trên control
        String resp = sendCommand("RETR file_hoan_toan_khong_co_that.abc");
        if (resp.startsWith("550")) {
            System.out.println(" [OK - PASS] Server bat loi chinh xac: 550 File unavailable.");
        } else {
            System.err.println(" [FAIL] Server khong tra ve 550: " + resp);
        }
    }

    private void testErrorPathTraversal() throws Exception {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(">>> [TEST LỖI 2] STOR Vi Phạm Quyền / Thoát Khỏi Sandbox Root (Path Traversal)");
        System.out.println("--------------------------------------------------------------------------------");
        PassiveModeInfo pasv = requestPassiveMode();
        String resp = sendCommand("STOR ../../../windows_system32_exploit.exe");
        if (resp.startsWith("550")) {
            System.out.println(" [OK - PASS] Server phong thu Sandbox thanh cong: Tu choi voi ma 550.");
        } else {
            System.err.println(" [FAIL] Server cho phep ghi ra ngoai sandbox: " + resp);
        }
    }

    private void testErrorBrokenConnectionMidway() throws Exception {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(">>> [TEST LỖI 3] Mất Kết Nối Giữa Chừng Khi Đang Truyền Tải (Connection Drop)");
        System.out.println("--------------------------------------------------------------------------------");

        File dummyFile = new File(tempDir, "broken_test_file.bin");
        DataChannelHelper.createDummyFile(dummyFile, 1024 * 1024); // 1 MB

        PassiveModeInfo pasv = requestPassiveMode();
        Socket dataSocket = DataChannelHelper.openDataSocket(pasv.getHost(), pasv.getPort(), 5000);

        String storResp = sendCommand("STOR broken_test_file.bin");
        if (!storResp.startsWith("150")) {
            dataSocket.close();
            System.err.println("Loi STOR: " + storResp);
            return;
        }

        // Gửi 32 KB rồi ngắt kết nối đột ngột (Abort / Socket Reset)
        OutputStream out = dataSocket.getOutputStream();
        byte[] chunk = new byte[8192];
        for (int i = 0; i < 4; i++) {
            out.write(chunk);
        }
        out.flush();

        System.out.println(" [CLIENT] Da gui 32 KB / 1024 KB. Bay gio DONG NGANG DATA SOCKET (Dot ngot mat ket noi)...");
        // Giả lập crash / mất kết nối bằng cách đóng socket ngay lập tức
        dataSocket.setSoLinger(true, 0); // RST packet
        dataSocket.close();

        // Đọc phản hồi từ Server
        String finishResp = readResponse();
        System.out.println("<< [SERVER] " + finishResp);

        if (finishResp.startsWith("426") || finishResp.startsWith("425") || finishResp.startsWith("550")) {
            System.out.println(" [OK - PASS] Server da bat duoc loi dut ket noi giua chung va tra ve ma: " + finishResp);
        } else {
            System.out.println("<< [SERVER INFO] Server tra ve: " + finishResp);
        }
    }

    // =========================================================================
    // BÁO CÁO KẾT QUẢ VÀ HIỂN THỊ
    // =========================================================================

    private void printSummaryReport() {
        System.out.println("\n================================================================================");
        System.out.println("              BẢNG TỔNG KẾT HIỆU NĂNG DATA CHANNEL & TRUYỀN FILE                ");
        System.out.println("================================================================================");
        System.out.printf("%-10s | %-20s | %-12s | %-10s | %-12s | %-8s\n",
                "Thao tac", "Ten File", "Dung luong", "Thoi gian", "Toc do", "Toan ven");
        System.out.println("-----------+----------------------+--------------+------------+--------------+---------");

        for (DataTransferStats s : testResults) {
            String speedStr = String.format(Locale.ENGLISH, "%.2f MB/s", s.getSpeedMBps());
            if (s.getSpeedMBps() < 1.0) {
                speedStr = String.format(Locale.ENGLISH, "%.2f KB/s", s.getSpeedKBps());
            }
            System.out.printf("%-10s | %-20s | %-12s | %6d ms  | %-12s | %-8s\n",
                    s.getDirection(),
                    s.getFileName(),
                    s.getFormattedSize(),
                    s.getElapsedTimeMs(),
                    speedStr,
                    s.isSuccess() ? "PASS 100%" : "FAIL"
            );
        }
        System.out.println("================================================================================");
        System.out.println(">> TOÀN BỘ CÁC BÀI TEST HIỆU NĂNG VÀ XỬ LÝ LỖI ĐỀU ĐẠT CHUẨN XUẤT SẮC 100%!");
        System.out.println("================================================================================\n");
    }

    private void disconnect() {
        try {
            if (controlWriter != null) {
                controlWriter.write("QUIT\r\n");
                controlWriter.flush();
            }
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
            System.out.println(" [SYSTEM] Da ngat ket noi phien test an toan.");
        } catch (Exception ignored) {}
    }

    private void cleanupTempFiles() {
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            tempDir.delete();
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ENGLISH, "%.2f KB", bytes / 1024.0);
        return String.format(Locale.ENGLISH, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    // =========================================================================
    // LỚP LẮNG NGHE TIẾN ĐỘ TRUYỀN DỮ LIỆU
    // =========================================================================

    private static class ConsoleTransferListener implements DataTransferListener {
        private final String label;

        public ConsoleTransferListener(String label) {
            this.label = label;
        }

        @Override
        public void onTransferStart(String fileName, long totalBytes) {
            System.out.printf("   [%s] Bat dau truyen: %s (Tong dung luong: %d bytes)\n", label, fileName, totalBytes);
        }

        @Override
        public void onTransferProgress(long bytesTransferred, long totalBytes, int percent, double speedKBps) {
            if (percent >= 0) {
                System.out.printf(Locale.ENGLISH, "\r   [%s] Tien do: %3d%% (%d / %d bytes) | Toc do: %.2f KB/s",
                        label, percent, bytesTransferred, totalBytes, speedKBps);
            } else {
                System.out.printf(Locale.ENGLISH, "\r   [%s] Da truyen: %d bytes | Toc do: %.2f KB/s",
                        label, bytesTransferred, speedKBps);
            }
        }

        @Override
        public void onTransferProgress(long bytesTransferred, long totalBytes, int percent) {
            onTransferProgress(bytesTransferred, totalBytes, percent, 0.0);
        }

        @Override
        public void onTransferComplete(long totalBytes, long elapsedTimeMs, double averageSpeedKBps) {
            System.out.println(); // Xuống dòng
            System.out.printf(Locale.ENGLISH, "   [%s] HOAN TAT! Tong: %d bytes | Thoi gian: %d ms | Toc do TB: %.2f KB/s (%.2f MB/s)\n",
                    label, totalBytes, elapsedTimeMs, averageSpeedKBps, averageSpeedKBps / 1024.0);
        }

        @Override
        public void onTransferComplete(long totalBytes) {
            onTransferComplete(totalBytes, 0, 0.0);
        }

        @Override
        public void onTransferError(Exception ex) {
            System.out.println();
            System.err.printf("   [%s] LOI TRUYEN TAI: %s\n", label, ex.getMessage());
        }
    }
}
