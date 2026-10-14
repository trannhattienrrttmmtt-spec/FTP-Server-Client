package datachannel;

/**
 * Interface lắng nghe sự kiện tiến độ truyền tải dữ liệu trên Data Channel
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Phụ trách: Phan Thanh Trọng (MSSV: 2380602351)
 * Sử dụng bởi: Client (Nguyễn Anh Vũ) và bộ test hiệu năng truyền file
 */
public interface DataTransferListener {
    /**
     * Gọi khi bắt đầu phiên truyền tải
     * @param fileName Tên tệp tin
     * @param totalBytes Tổng dung lượng tệp tin (bytes), -1 nếu chưa rõ
     */
    void onTransferStart(String fileName, long totalBytes);

    /**
     * Gọi liên tục khi từng khối dữ liệu (buffer chunk) được truyền qua socket
     * @param bytesTransferred Số bytes đã truyền được đến thời điểm hiện tại
     * @param totalBytes Tổng dung lượng tệp tin (-1 nếu không xác định)
     * @param percent Tỉ lệ phần trăm hoàn thành (0 - 100)
     */
    void onTransferProgress(long bytesTransferred, long totalBytes, int percent);

    /**
     * Phiên bản nâng cao có thêm thông số tốc độ truyền tải thời gian thực
     * @param bytesTransferred Số bytes đã truyền được
     * @param totalBytes Tổng dung lượng tệp tin
     * @param percent Tỉ lệ phần trăm hoàn thành (0 - 100)
     * @param speedKBps Tốc độ truyền tức thời (KB/s)
     */
    default void onTransferProgress(long bytesTransferred, long totalBytes, int percent, double speedKBps) {
        onTransferProgress(bytesTransferred, totalBytes, percent);
    }

    /**
     * Gọi khi phiên truyền tải hoàn tất thành công
     * @param totalBytes Tổng số bytes đã truyền
     */
    void onTransferComplete(long totalBytes);

    /**
     * Phiên bản nâng cao khi hoàn tất có thêm thống kê thời gian và tốc độ trung bình
     * @param totalBytes Tổng số bytes đã truyền
     * @param elapsedTimeMs Tổng thời gian truyền (milliseconds)
     * @param averageSpeedKBps Tốc độ truyền trung bình (KB/s)
     */
    default void onTransferComplete(long totalBytes, long elapsedTimeMs, double averageSpeedKBps) {
        onTransferComplete(totalBytes);
    }

    /**
     * Gọi khi xảy ra lỗi trong quá trình truyền tải (mất kết nối, timeout, IO...)
     * @param ex Lỗi ngoại lệ phát sinh
     */
    void onTransferError(Exception ex);
}
