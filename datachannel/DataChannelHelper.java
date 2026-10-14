package datachannel;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Lớp tiện ích toàn diện hỗ trợ kênh truyền dữ liệu nhị phân (Data Channel)
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Phụ trách: Phan Thanh Trọng (MSSV: 2380602351)
 *
 * Các tính năng trọng tâm:
 * 1. Phân tích phản hồi PASV và thiết lập kết nối Socket dữ liệu riêng biệt.
 * 2. Xử lý truyền nhận file qua luồng byte nhị phân (STOR & RETR) với bộ đệm tối ưu.
 * 3. Báo cáo tiến độ và tốc độ truyền thời gian thực qua DataTransferListener.
 * 4. Xử lý lỗi toàn diện: mất kết nối, file không tồn tại, quyền truy cập, dọn dẹp file tạm.
 * 5. Tính toán Checksum SHA-256 xác thực tính toàn vẹn dữ liệu bit-level.
 * 6. Công cụ tạo file mẫu và kiểm thử hiệu năng truyền tải.
 */
public class DataChannelHelper {
    public static final int DEFAULT_BUFFER_SIZE = 8192;    // 8 KB (Mặc định chuẩn)
    public static final int LARGE_BUFFER_SIZE   = 65536;   // 64 KB (Tối ưu hóa file lớn)
    public static final int DEFAULT_TIMEOUT_MS  = 30000;   // 30 giây timeout kênh dữ liệu

    // =========================================================================
    // 1. XÂY DỰNG KÊNH DỮ LIỆU RIÊNG (PASSIVE MODE)
    // =========================================================================

    /**
     * Phân tích địa chỉ IP và Port từ phản hồi 227 của Server
     */
    public static PassiveModeInfo parsePassiveAddress(String pasvResponse) {
        return PassiveModeInfo.parse(pasvResponse);
    }

    /**
     * Mở kết nối Socket kênh dữ liệu đến Server tại cổng đã phân tích từ lệnh PASV
     *
     * @param host Địa chỉ IP host
     * @param port Cổng kênh dữ liệu Passive
     * @param timeoutMs Thời gian chờ kết nối tối đa
     * @return Socket kênh dữ liệu đã kết nối
     * @throws IOException khi không thể kết nối hoặc hết thời gian timeout
     */
    public static Socket openDataSocket(String host, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        // Cấu hình socket tối ưu cho truyền file
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true); // Giảm độ trễ gửi gói tin
        socket.setReceiveBufferSize(LARGE_BUFFER_SIZE);
        socket.setSendBufferSize(LARGE_BUFFER_SIZE);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        return socket;
    }

    // =========================================================================
    // 2. XỬ LÝ LỖI TRUY CẬP VÀ XÁC THỰC FILE
    // =========================================================================

    /**
     * Kiểm tra tính hợp lệ và quyền đọc của file trước khi Upload (STOR)
     */
    public static void validateFileForUpload(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Doi tuong File khong duoc null.");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("Tep tin khong ton tai tren dia: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IOException("Duong dan khong phai la tep tin (co the la thu muc): " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new SecurityException("Khong co quyen doc tep tin (Permission Denied): " + file.getAbsolutePath());
        }
    }

    /**
     * Kiểm tra tính hợp lệ và quyền ghi của thư mục trước khi Download (RETR)
     */
    public static void validateDirectoryForDownload(File destFile) throws IOException {
        if (destFile == null) {
            throw new IllegalArgumentException("Doi tuong DestFile khong duoc null.");
        }
        File parentDir = destFile.getParentFile();
        if (parentDir != null) {
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Khong the tao thu muc cha cho file: " + parentDir.getAbsolutePath());
            }
            if (!parentDir.canWrite()) {
                throw new SecurityException("Khong co quyen ghi vao thu muc (Permission Denied): " + parentDir.getAbsolutePath());
            }
        }
        if (destFile.exists() && !destFile.canWrite()) {
            throw new SecurityException("Tep dich da ton tai va chi co quyen doc (Read-only): " + destFile.getAbsolutePath());
        }
    }

    // =========================================================================
    // 3. XỬ LÝ STOR (UPLOAD) VÀ RETR (DOWNLOAD) QUA LUỒNG BYTE NHỊ PHÂN
    // =========================================================================

    /**
     * Tải file từ đĩa cục bộ lên Server qua Data Socket (STOR)
     * Đồng thời tính toán SHA-256 Checksum trực tiếp trong quá trình truyền
     *
     * @param dataSocket Socket dữ liệu đã kết nối
     * @param localFile File cục bộ cần upload
     * @param bufferSize Kích thước bộ đệm (8KB hoặc 64KB)
     * @param listener Callback báo tiến trình (có thể null)
     * @return DataTransferStats Thống kê hiệu năng (thời gian, tốc độ, hash SHA-256)
     */
    public static DataTransferStats uploadFile(Socket dataSocket, File localFile, int bufferSize,
                                              DataTransferListener listener) throws Exception {
        validateFileForUpload(localFile);
        long fileSize = localFile.length();
        String fileName = localFile.getName();
        DataTransferStats stats = new DataTransferStats(fileName, "UPLOAD");

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("He thong khong ho tro thuat toan SHA-256", e);
        }

        if (listener != null) {
            listener.onTransferStart(fileName, fileSize);
        }

        long startTime = System.currentTimeMillis();
        long totalBytesSent = 0;
        int bufSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        byte[] buffer = new byte[bufSize];

        try (FileInputStream fis = new FileInputStream(localFile);
             BufferedInputStream bis = new BufferedInputStream(fis, bufSize);
             OutputStream rawOut = dataSocket.getOutputStream();
             DigestOutputStream dos = new DigestOutputStream(rawOut, md);
             BufferedOutputStream bos = new BufferedOutputStream(dos, bufSize)) {

            int bytesRead;
            long lastProgressUpdate = startTime;

            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;

                long now = System.currentTimeMillis();
                // Cập nhật tiến độ sau mỗi 50ms hoặc khi đọc xong để tránh quá tải UI
                if (listener != null && (now - lastProgressUpdate >= 50 || totalBytesSent == fileSize)) {
                    int percent = (fileSize > 0) ? (int) ((totalBytesSent * 100) / fileSize) : -1;
                    double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
                    double currentSpeedKBps = (totalBytesSent / elapsedSec) / 1024.0;
                    listener.onTransferProgress(totalBytesSent, fileSize, percent, currentSpeedKBps);
                    lastProgressUpdate = now;
                }
            }

            bos.flush();
            long elapsedTimeMs = System.currentTimeMillis() - startTime;
            String sha256 = bytesToHex(dos.getMessageDigest().digest());

            stats.recordSuccess(totalBytesSent, elapsedTimeMs, sha256);

            if (listener != null) {
                listener.onTransferComplete(totalBytesSent, elapsedTimeMs, stats.getSpeedKBps());
            }

            return stats;
        } catch (SocketException | SocketTimeoutException se) {
            stats.recordError("Loi socket mat ket noi giua chung: " + se.getMessage());
            if (listener != null) listener.onTransferError(se);
            throw se;
        } catch (Exception e) {
            stats.recordError("Loi khi upload: " + e.getMessage());
            if (listener != null) listener.onTransferError(e);
            throw e;
        } finally {
            closeQuietly(dataSocket);
        }
    }

    /**
     * Tải file từ Server về đĩa cục bộ qua Data Socket (RETR)
     * Sử dụng cơ chế file tạm `.part` để chống hỏng file nếu mất kết nối giữa chừng
     *
     * @param dataSocket Socket dữ liệu đã kết nối
     * @param destFile File đích cục bộ lưu file
     * @param expectedSize Tổng dung lượng dự kiến từ Server (-1 nếu không rõ)
     * @param bufferSize Kích thước bộ đệm (8KB hoặc 64KB)
     * @param listener Callback báo tiến trình (có thể null)
     * @return DataTransferStats Thống kê hiệu năng
     */
    public static DataTransferStats downloadFile(Socket dataSocket, File destFile, long expectedSize,
                                                int bufferSize, DataTransferListener listener) throws Exception {
        validateDirectoryForDownload(destFile);
        String fileName = destFile.getName();
        DataTransferStats stats = new DataTransferStats(fileName, "DOWNLOAD");

        // Sử dụng file tạm .part để tránh ghi đè làm hỏng file cũ khi tải lỗi
        File tempFile = new File(destFile.getParentFile(), destFile.getName() + ".part");

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("He thong khong ho tro thuat toan SHA-256", e);
        }

        if (listener != null) {
            listener.onTransferStart(fileName, expectedSize);
        }

        long startTime = System.currentTimeMillis();
        long totalBytesReceived = 0;
        int bufSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        byte[] buffer = new byte[bufSize];

        try (InputStream rawIn = dataSocket.getInputStream();
             DigestInputStream dis = new DigestInputStream(rawIn, md);
             BufferedInputStream bis = new BufferedInputStream(dis, bufSize);
             FileOutputStream fos = new FileOutputStream(tempFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, bufSize)) {

            int bytesRead;
            long lastProgressUpdate = startTime;

            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesReceived += bytesRead;

                long now = System.currentTimeMillis();
                if (listener != null && (now - lastProgressUpdate >= 50 || (expectedSize > 0 && totalBytesReceived == expectedSize))) {
                    int percent = (expectedSize > 0) ? (int) ((totalBytesReceived * 100) / expectedSize) : -1;
                    double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
                    double currentSpeedKBps = (totalBytesReceived / elapsedSec) / 1024.0;
                    listener.onTransferProgress(totalBytesReceived, expectedSize, percent, currentSpeedKBps);
                    lastProgressUpdate = now;
                }
            }

            bos.flush();
            long elapsedTimeMs = System.currentTimeMillis() - startTime;
            String sha256 = bytesToHex(dis.getMessageDigest().digest());

            // Đóng streams trước khi đổi tên file tạm
            bos.close();
            fos.close();

            // Đổi tên file tạm .part thành file chính thức một cách an toàn (Atomic Move nếu OS hỗ trợ)
            Files.move(tempFile.toPath(), destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            stats.recordSuccess(totalBytesReceived, elapsedTimeMs, sha256);

            if (listener != null) {
                listener.onTransferComplete(totalBytesReceived, elapsedTimeMs, stats.getSpeedKBps());
            }

            return stats;
        } catch (Exception e) {
            // Khi xảy ra bất kỳ lỗi gì (mất kết nối giữa chừng, timeout), xóa file tạm ngay để không rác đĩa
            if (tempFile.exists()) {
                tempFile.delete();
            }
            stats.recordError("Loi truyen file giua chung: " + e.getMessage());
            if (listener != null) listener.onTransferError(e);
            throw e;
        } finally {
            closeQuietly(dataSocket);
        }
    }

    /**
     * Phương thức sao chép luồng tổng quát tương thích ngược
     */
    public static long transfer(InputStream in, OutputStream out, long totalSize, DataTransferListener listener) throws Exception {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long totalBytesRead = 0;
        int bytesRead;
        long startTime = System.currentTimeMillis();

        if (listener != null) {
            listener.onTransferStart("", totalSize);
        }

        try {
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (listener != null && totalSize > 0) {
                    int percent = (int) ((totalBytesRead * 100) / totalSize);
                    double elapsedSec = Math.max(0.001, (System.currentTimeMillis() - startTime) / 1000.0);
                    double speedKBps = (totalBytesRead / elapsedSec) / 1024.0;
                    listener.onTransferProgress(totalBytesRead, totalSize, percent, speedKBps);
                }
            }
            out.flush();
            if (listener != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                double avgSpeed = (totalBytesRead / Math.max(0.001, elapsed / 1000.0)) / 1024.0;
                listener.onTransferComplete(totalBytesRead, elapsed, avgSpeed);
            }
            return totalBytesRead;
        } catch (Exception e) {
            if (listener != null) {
                listener.onTransferError(e);
            }
            throw e;
        }
    }

    // =========================================================================
    // 4. KIỂM TRA TÍNH TOÀN VẸN (SHA-256 CHECKSUM)
    // =========================================================================

    /**
     * Tính toán mã băm SHA-256 của một tệp tin trên đĩa
     */
    public static String calculateFileSHA256(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis, LARGE_BUFFER_SIZE)) {
                byte[] buffer = new byte[LARGE_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("He thong khong ho tro thuat toan SHA-256", e);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // =========================================================================
    // 5. CÔNG CỤ TẠO FILE DUMMY PHỤC VỤ TEST HIỆU NĂNG
    // =========================================================================

    /**
     * Tạo một tệp tin dữ liệu ngẫu nhiên với kích thước chỉ định để kiểm thử tốc độ truyền
     */
    public static void createDummyFile(File file, long sizeBytes) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        byte[] pattern = new byte[DEFAULT_BUFFER_SIZE];
        Random rnd = new Random(42); // Seed cố định để dữ liệu nhất quán
        rnd.nextBytes(pattern);

        long written = 0;
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, LARGE_BUFFER_SIZE)) {
            while (written < sizeBytes) {
                int toWrite = (int) Math.min(pattern.length, sizeBytes - written);
                bos.write(pattern, 0, toWrite);
                written += toWrite;
            }
            bos.flush();
        }
    }

    // =========================================================================
    // 6. GIẢI PHÓNG TÀI NGUYÊN AN TOÀN
    // =========================================================================

    /**
     * Đóng an toàn các luồng hoặc socket mà không phát sinh ngoại lệ không mong muốn
     */
    public static void closeQuietly(AutoCloseable... closeables) {
        if (closeables == null) return;
        for (AutoCloseable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
