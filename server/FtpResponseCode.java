package server;

/**
 * Định nghĩa các mã phản hồi chuẩn FTP (RFC 959)
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Thực hiện: Trần Nhật Tiến (MSSV: 2380602243)
 */
public class FtpResponseCode {
    // 1xx - Thông tin sơ bộ, chuẩn bị thực hiện hành động
    public static final int FILE_STATUS_OK_OPENING_DATA_CONNECTION = 150; // Opening BINARY mode data connection

    // 2xx - Lệnh thực hiện thành công
    public static final int COMMAND_OK = 200;                             // Command okay
    public static final int SYSTEM_STATUS = 211;                          // System status
    public static final int FILE_STATUS_SIZE = 213;                       // File status / size
    public static final int SYSTEM_TYPE = 215;                            // 215 UNIX Type: L8
    public static final int SERVICE_READY = 220;                          // Service ready for new user
    public static final int SERVICE_CLOSING_CONTROL = 221;                // Goodbye / closing connection
    public static final int CLOSING_DATA_CONNECTION = 226;                // Transfer complete
    public static final int ENTERING_PASSIVE_MODE = 227;                  // Entering Passive Mode (h1,h2,h3,h4,p1,p2)
    public static final int USER_LOGGED_IN = 230;                         // User logged in, proceed
    public static final int FILE_ACTION_OK = 250;                         // Requested file action okay, completed
    public static final int PATHNAME_CREATED = 257;                       // "PATH" created or current dir

    // 3xx - Yêu cầu thêm thông tin
    public static final int USERNAME_OK_NEED_PASSWORD = 331;              // User name okay, need password

    // 4xx - Lỗi tạm thời
    public static final int CANT_OPEN_DATA_CONNECTION = 425;              // Can't open data connection
    public static final int CONNECTION_CLOSED_TRANSFER_ABORTED = 426;     // Connection closed; transfer aborted

    // 5xx - Lỗi vĩnh viễn / Từ chối lệnh
    public static final int SYNTAX_ERROR_COMMAND = 500;                   // Syntax error, command unrecognized
    public static final int SYNTAX_ERROR_PARAMETERS = 501;                // Syntax error in parameters or arguments
    public static final int COMMAND_NOT_IMPLEMENTED = 502;                // Command not implemented
    public static final int BAD_SEQUENCE_OF_COMMANDS = 503;               // Bad sequence of commands
    public static final int NOT_LOGGED_IN = 530;                          // Not logged in
    public static final int FILE_UNAVAILABLE = 550;                       // File unavailable / not found / permission denied
}
