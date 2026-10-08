package datachannel;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lớp tiện ích hỗ trợ truyền nhận dữ liệu qua Socket Data Channel
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Phụ trách: Phan Thanh Trọng (MSSV: 2380602351)
 *
 * Ghi chú: Phan Thanh Trọng sẽ hoàn thiện các phương thức truyền stream bên dưới
 * kết hợp với DataTransferListener để báo tiến trình cho Client UI.
 */
public class DataChannelHelper {
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Copy dữ liệu từ InputStream sang OutputStream có thông báo tiến độ
     * TODO (Trọng): Triển khai vòng lặp đọc buffer và gọi listener.onTransferProgress(...)
     */
    public static long transfer(InputStream in, OutputStream out, long totalSize, DataTransferListener listener) throws Exception {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long totalBytesRead = 0;
        int bytesRead;

        if (listener != null) {
            listener.onTransferStart("", totalSize);
        }

        try {
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (listener != null && totalSize > 0) {
                    int percent = (int) ((totalBytesRead * 100) / totalSize);
                    listener.onTransferProgress(totalBytesRead, totalSize, percent);
                }
            }
            out.flush();
            if (listener != null) {
                listener.onTransferComplete(totalBytesRead);
            }
            return totalBytesRead;
        } catch (Exception e) {
            if (listener != null) {
                listener.onTransferError(e);
            }
            throw e;
        }
    }
}
