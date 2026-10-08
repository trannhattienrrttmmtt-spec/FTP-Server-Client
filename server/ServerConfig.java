package server;

import java.io.File;

/**
 * Lớp cấu hình các thông số hoạt động của FTP Server
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Thực hiện: Trần Nhật Tiến (MSSV: 2380602243)
 */
public class ServerConfig {
    // Cổng kênh điều khiển (Control Channel) mặc định
    public static final int DEFAULT_CONTROL_PORT = 2121;

    // Tên hiển thị của Server trong banner chào mừng
    public static final String SERVER_NAME = "HUTECH FTP Two-Channel Server (Group 11)";
    public static final String SERVER_VERSION = "1.0.0";

    // Thư mục lưu trữ gốc của FTP Server (Sandbox Root)
    public static final String DEFAULT_ROOT_DIR = "ftp_root";

    // Kích thước bộ đệm (Buffer Size) khi truyền stream nhị phân (8 KB)
    public static final int BUFFER_SIZE = 8192;

    // Thời gian chờ đọc dữ liệu trên Control Channel (2 phút)
    public static final int CONTROL_TIMEOUT_MS = 120000;

    // Thời gian chờ kết nối trên Data Channel (60 giây)
    public static final int DATA_TIMEOUT_MS = 60000;

    // Bộ ký tự mã hóa mặc định cho tin nhắn văn bản điều khiển
    public static final String CHARSET_NAME = "UTF-8";

    // Dải cổng dành cho kênh dữ liệu Passive Mode (50000 - 50100)
    // Nếu để 0 thì hệ điều hành sẽ tự cấp phát cổng ngẫu nhiên khả dụng
    public static final int PASSIVE_PORT_MIN = 50000;
    public static final int PASSIVE_PORT_MAX = 50100;

    /**
     * Lấy thư mục gốc đã chuẩn hóa
     */
    public static File getResolvedRootDir(String customPath) {
        String path = (customPath != null && !customPath.trim().isEmpty()) ? customPath : DEFAULT_ROOT_DIR;
        File dir = new File(path);
        if (!dir.isAbsolute()) {
            // Nếu là đường dẫn tương đối, lấy theo thư mục hiện tại của server
            dir = new File(System.getProperty("user.dir"), path);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
