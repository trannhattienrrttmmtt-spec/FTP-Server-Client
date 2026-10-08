package datachannel;

/**
 * Interface lắng nghe sự kiện tiến độ truyền tải dữ liệu trên Data Channel
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Phụ trách: Phan Thanh Trọng (MSSV: 2380602351)
 * Sử dụng bởi: Client (Nguyễn Anh Vũ) để cập nhật JProgressBar
 */
public interface DataTransferListener {
    /**
     * Gọi khi bắt đầu phiên truyền tải
     * @param fileName Tên tệp tin
     * @param totalBytes Tổng dung lượng tệp tin (bytes), -1 nếu chưa rõ
     */
    void onTransferStart(String fileName, long totalBytes);

    /**
     * Gọi liên tục khi từng khối dữ liệu được truyền qua socket
     * @param bytesTransferred Số bytes đã truyền được đến thời điểm hiện tại
     * @param totalBytes Tổng dung lượng tệp tin
     * @param percent Tỉ lệ phần trăm hoàn thành (0 - 100)
     */
    void onTransferProgress(long bytesTransferred, long totalBytes, int percent);

    /**
     * Gọi khi phiên truyền tải hoàn tất thành công
     * @param totalBytes Tổng số bytes đã truyền
     */
    void onTransferComplete(long totalBytes);

    /**
     * Gọi khi xảy ra lỗi trong quá trình truyền tải
     * @param ex Lỗi ngoại lệ phát sinh
     */
    void onTransferError(Exception ex);
}
