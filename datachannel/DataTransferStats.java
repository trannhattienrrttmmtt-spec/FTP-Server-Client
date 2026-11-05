package datachannel;

import java.util.Locale;

public class DataTransferStats {
    private final String fileName;
    private final String direction;
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

    public String getFormattedSize() {
        if (totalBytes < 1024) {
            return totalBytes + " B";
        } else if (totalBytes < 1024 * 1024) {
            return String.format(Locale.ENGLISH, "%.2f KB (%d bytes)", totalBytes / 1024.0, totalBytes);
        } else {
            return String.format(Locale.ENGLISH, "%.2f MB (%d bytes)", totalBytes / (1024.0 * 1024.0), totalBytes);
        }
    }

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
