package client;

/**
 * Đối tượng biểu diễn một phản hồi từ Server trên Control Channel.
 * Cú pháp: "<Mã 3 chữ số> <Thông điệp>" hoặc phản hồi nhiều dòng "<Mã>-...\r\n ... \r\n<Mã> ...".
 * Phụ trách: Nguyễn Anh Vũ (MSSV: 2380602582)
 */
public class FtpResponse {
    private final int code;
    private final String message;   // Thông điệp của dòng cuối cùng (không kèm mã)
    private final String fullText;  // Toàn bộ nội dung phản hồi (kể cả nhiều dòng)

    public FtpResponse(int code, String message, String fullText) {
        this.code = code;
        this.message = message;
        this.fullText = fullText;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public String getFullText() { return fullText; }

    /** 1xx - Đã chấp nhận, đang xử lý (ví dụ 150 mở Data Channel) */
    public boolean isPreliminary() { return code >= 100 && code < 200; }

    /** 2xx - Thành công */
    public boolean isSuccess() { return code >= 200 && code < 300; }

    /** 3xx - Cần thêm thông tin (ví dụ 331 cần mật khẩu) */
    public boolean isIntermediate() { return code >= 300 && code < 400; }

    /** 4xx / 5xx - Lỗi */
    public boolean isError() { return code >= 400; }

    @Override
    public String toString() { return fullText; }
}
