package datachannel;

import java.util.Locale;

/**
 * Lưu trữ và định dạng các thông số thống kê hiệu năng truyền tải dữ liệu
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Phụ trách: Phan Thanh Trọng (MSSV: 2380602351)
 */
public class DataTransferStats {
    private final String fileName;
    private final String direction; // "UPLOAD" (STOR) hoặc "DOWNLOAD" (RETR)
    private long totalBytes;
    private long elapsedTimeMs;
    private double speedKBps;
    private double speedMBps;
    private String sha256Checksum;
    private boolean success;
    private String errorMessage;

    public DataTransferStats(String fileName, String direction) {
        this.fileName = fileName;
        this.direction = direction;
        this.success = false;
        this.sha256Checksum = "";
    }

    public void recordSuccess(long totalBytes, long elapsedTimeMs, String sha256Checksum) {
        this.totalBytes = totalBytes;
        this.elapsedTimeMs = Math.max(1, elapsedTimeMs);
        this.sha256Checksum = sha256Checksum;
        this.success = true;

        // Tính toán tốc độ truyền
        // bytes / (ms / 1000) = bytes/s -> / 1024 = KB/s -> / 1024 = MB/s
        double seconds = this.elapsedTimeMs / 1000.0;
        double bytesPerSec = this.totalBytes / seconds;
        this.speedKBps = bytesPerSec / 1024.0;
        this.speedMBps = this.speedKBps / 1024.0;
    }

    public void recordError(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDirection() {
        return direction;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public double getSpeedKBps() {
        return speedKBps;
    }

    public double getSpeedMBps() {
        return speedMBps;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Định dạng kích thước dữ liệu theo dạng người đọc dễ hiểu (B, KB, MB)
     */
    public String getFormattedSize() {
        if (totalBytes < 1024) {
            return totalBytes + " B";
        } else if (totalBytes < 1024 * 1024) {
            return String.format(Locale.ENGLISH, "%.2f KB (%d bytes)", totalBytes / 1024.0, totalBytes);
        } else {
            return String.format(Locale.ENGLISH, "%.2f MB (%d bytes)", totalBytes / (1024.0 * 1024.0), totalBytes);
        }
    }

    /**
     * Xuất tóm tắt kết quả kiểm thử dạng chuỗi rõ ràng
     */
    public String getFormattedReport() {
        if (!success) {
            return String.format(Locale.ENGLISH,
                    "[%s] %s -> THAT BAI: %s",
                    direction, fileName, errorMessage);
        }
        return String.format(Locale.ENGLISH,
                "[%s] %s | Dung luong: %s | Thoi gian: %d ms | Toc do: %.2f MB/s (%.2f KB/s) | SHA-256: %s",
                direction, fileName, getFormattedSize(), elapsedTimeMs, speedMBps, speedKBps,
                sha256Checksum.isEmpty() ? "N/A" : sha256Checksum.substring(0, Math.min(12, sha256Checksum.length())) + "..."
        );
    }

    @Override
    public String toString() {
        return getFormattedReport();
    }
}
