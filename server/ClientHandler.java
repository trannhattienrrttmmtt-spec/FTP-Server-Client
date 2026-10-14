package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Luồng xử lý từng Client kết nối vào Control Channel của FTP Server
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Thực hiện: Trần Nhật Tiến (MSSV: 2380602243)
 */
public class ClientHandler implements Runnable {
    private final Socket controlSocket;
    private final File rootDir;
    private final UserManager userManager;
    private final int clientId;
    private final String clientTag;

    private BufferedReader reader;
    private BufferedWriter writer;

    // Trạng thái phiên làm việc của Client
    private boolean isLoggedIn = false;
    private String userCandidate = null;
    private String currentUsername = null;
    private File currentDir;

    // Kênh dữ liệu (Data Channel) theo cơ chế Passive Mode
    private PassiveDataConnection passiveConnection;
    private volatile boolean isRunning = true;

    public ClientHandler(Socket controlSocket, File rootDir, UserManager userManager, int clientId) {
        this.controlSocket = controlSocket;
        this.rootDir = rootDir;
        this.userManager = userManager;
        this.clientId = clientId;
        this.clientTag = "Client-" + clientId + " [" + controlSocket.getInetAddress().getHostAddress() + "]";
        this.currentDir = rootDir;
    }

    @Override
    public void run() {
        ServerLogger.info(clientTag, "Bat dau xu ly phien ket noi.");
        try {
            controlSocket.setSoTimeout(ServerConfig.CONTROL_TIMEOUT_MS);
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), ServerConfig.CHARSET_NAME));
            writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), ServerConfig.CHARSET_NAME));

            // Gui banner chao mung 220
            sendResponse(FtpResponseCode.SERVICE_READY, "Chao mung den voi FTP Server Hai Kenh (Nhom 11 - HUTECH).");

            String line;
            while (isRunning && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                ServerLogger.logRecv(clientTag, line);
                handleCommand(line);
            }
        } catch (SocketTimeoutException e) {
            ServerLogger.warn(clientTag, "Het thoi gian cho (Timeout), dong ket noi.");
            sendResponse(FtpResponseCode.SERVICE_CLOSING_CONTROL, "Timeout, phien bi ngat.");
        } catch (SocketException e) {
            ServerLogger.info(clientTag, "Client da ngat ket noi dot ngot.");
        } catch (Exception e) {
            ServerLogger.error(clientTag, "Loi trong phien Client: " + e.getMessage(), e);
        } finally {
            closeSession();
        }
    }

    /**
     * Xu ly phan tich cu phap lenh tu Client
     */
    private void handleCommand(String fullCommand) throws IOException {
        String command;
        String argument = "";
        int spaceIdx = fullCommand.indexOf(' ');
        if (spaceIdx != -1) {
            command = fullCommand.substring(0, spaceIdx).toUpperCase(Locale.ENGLISH);
            argument = fullCommand.substring(spaceIdx + 1).trim();
        } else {
            command = fullCommand.toUpperCase(Locale.ENGLISH);
        }

        switch (command) {
            case "USER":
                handleUser(argument);
                break;
            case "PASS":
                handlePass(argument);
                break;
            case "PWD":
            case "XPWD":
                handlePwd();
                break;
            case "CWD":
            case "XCWD":
                handleCwd(argument);
                break;
            case "CDUP":
            case "XCUP":
                handleCdup();
                break;
            case "PASV":
                handlePasv();
                break;
            case "LIST":
            case "NLST":
                handleList(command.equals("NLST"), argument);
                break;
            case "RETR":
                handleRetr(argument);
                break;
            case "STOR":
                handleStor(argument);
                break;
            case "DELE":
                handleDele(argument);
                break;
            case "MKD":
            case "XMKD":
                handleMkd(argument);
                break;
            case "RMD":
            case "XRMD":
                handleRmd(argument);
                break;
            case "SIZE":
                handleSize(argument);
                break;
            case "TYPE":
                handleType(argument);
                break;
            case "SYST":
                sendResponse(FtpResponseCode.SYSTEM_TYPE, "UNIX Type: L8");
                break;
            case "FEAT":
                handleFeat();
                break;
            case "NOOP":
                sendResponse(FtpResponseCode.COMMAND_OK, "NOOP thanh cong.");
                break;
            case "HELP":
                handleHelp();
                break;
            case "QUIT":
                sendResponse(FtpResponseCode.SERVICE_CLOSING_CONTROL, "Tam biet!");
                isRunning = false;
                break;
            default:
                sendResponse(FtpResponseCode.SYNTAX_ERROR_COMMAND, "Lenh chua duoc ho tro: " + command);
                break;
        }
    }

    // ==========================================
    // CAC PHUONG THUC XU LY CHI TIET TUNG LENH
    // ==========================================

    private void handleUser(String username) {
        if (username.isEmpty()) {
            sendResponse(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "USER can co tham so ten dang nhap.");
            return;
        }
        userCandidate = username;
        isLoggedIn = false;
        sendResponse(FtpResponseCode.USERNAME_OK_NEED_PASSWORD, "Ten dang nhap hop le, vui long nhap mat khau bang lenh PASS.");
    }

    private void handlePass(String password) {
        if (userCandidate == null) {
            sendResponse(FtpResponseCode.BAD_SEQUENCE_OF_COMMANDS, "Vui long gui lenh USER truoc.");
            return;
        }
        if (userManager.authenticate(userCandidate, password)) {
            isLoggedIn = true;
            currentUsername = userCandidate;
            currentDir = rootDir;
            ServerLogger.info(clientTag, "Xac thuc thanh cong nguoi dung: " + currentUsername);
            sendResponse(FtpResponseCode.USER_LOGGED_IN, "Dang nhap thanh cong. Chao mung " + currentUsername + "!");
        } else {
            ServerLogger.warn(clientTag, "Xac thuc that bai cho nguoi dung: " + userCandidate);
            sendResponse(FtpResponseCode.NOT_LOGGED_IN, "Dang nhap that bai! Sai tai khoan hoac mat khau.");
        }
    }

    private boolean checkLogin() {
        if (!isLoggedIn) {
            sendResponse(FtpResponseCode.NOT_LOGGED_IN, "Vui long dang nhap bang USER va PASS truoc.");
            return false;
        }
        return true;
    }

    private void handlePwd() {
        if (!checkLogin()) return;
        String virtualPath = getVirtualPath(currentDir);
        sendResponse(FtpResponseCode.PATHNAME_CREATED, "\"" + virtualPath + "\" la thu muc hien tai.");
    }

    private void handleCwd(String dirName) {
        if (!checkLogin()) return;
        if (dirName.isEmpty()) {
            sendResponse(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "CWD can co tham so thu muc.");
            return;
        }

        File target;
        if (dirName.equals("/")) {
            target = rootDir;
        } else if (dirName.startsWith("/")) {
            target = resolvePath(dirName.substring(1));
        } else {
            target = resolvePath(dirName);
        }

        if (target != null && target.exists() && target.isDirectory() && isSafePath(target)) {
            currentDir = target;
            ServerLogger.info(clientTag, "Doi thu muc thanh: " + getVirtualPath(currentDir));
            sendResponse(FtpResponseCode.FILE_ACTION_OK, "Doi thu muc thanh cong sang: " + getVirtualPath(currentDir));
        } else {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Thu muc khong ton tai hoac khong co quyen truy cap.");
        }
    }

    private void handleCdup() {
        if (!checkLogin()) return;
        File parent = currentDir.getParentFile();
        if (parent != null && isSafePath(parent)) {
            currentDir = parent;
            sendResponse(FtpResponseCode.FILE_ACTION_OK, "Da len thu muc cha: " + getVirtualPath(currentDir));
        } else {
            // Dang o root roi thi giu nguyen tai root
            currentDir = rootDir;
            sendResponse(FtpResponseCode.FILE_ACTION_OK, "Dang o thu muc goc (Root): /");
        }
    }

    /**
     * Xu ly lenh PASV - Mo kenh du lieu Passive Mode
     */
    private void handlePasv() {
        if (!checkLogin()) return;
        try {
            // Dong ket noi passive cu neu con mo
            closePassiveConnection();

            // Tao ket noi Passive moi
            passiveConnection = new PassiveDataConnection(controlSocket.getLocalAddress(), clientTag);
            String responseStr = passiveConnection.getPassiveResponseString();
            sendRawResponse(responseStr);
        } catch (IOException e) {
            ServerLogger.error(clientTag, "Loi khi mo kenh Passive: " + e.getMessage(), e);
            sendResponse(FtpResponseCode.CANT_OPEN_DATA_CONNECTION, "Khong the mo kenh du lieu Passive.");
        }
    }

    /**
     * Xu ly lenh LIST / NLST - Gui danh sach tep tin qua Data Channel
     */
    private void handleList(boolean namesOnly, String argument) {
        if (!checkLogin()) return;
        if (passiveConnection == null || !passiveConnection.isOpen()) {
            sendResponse(FtpResponseCode.CANT_OPEN_DATA_CONNECTION, "Chua mo kenh du lieu. Vui long goi PASV truoc.");
            return;
        }

        File targetDir = currentDir;
        if (!argument.isEmpty()) {
            File specific = resolvePath(argument);
            if (specific != null && specific.exists() && specific.isDirectory()) {
                targetDir = specific;
            }
        }

        sendResponse(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION, 
                "Mo ket noi Data Channel de truyen danh sach tep tin.");

        try {
            File[] files = targetDir.listFiles();
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);

            if (files != null) {
                for (File file : files) {
                    if (namesOnly) {
                        sb.append(file.getName()).append("\r\n");
                    } else {
                        String perms = file.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                        long size = file.length();
                        String dateStr = sdf.format(new Date(file.lastModified()));
                        sb.append(String.format(Locale.ENGLISH, "%s 1 ftp ftp %10d %s %s\r\n", 
                                perms, size, dateStr, file.getName()));
                    }
                }
            }

            passiveConnection.sendTextData(sb.toString());
            sendResponse(FtpResponseCode.CLOSING_DATA_CONNECTION, "Da truyen xong danh sach tep tin.");
        } catch (IOException e) {
            ServerLogger.error(clientTag, "Loi khi truyen danh sach tep: " + e.getMessage(), e);
            sendResponse(FtpResponseCode.CONNECTION_CLOSED_TRANSFER_ABORTED, "Loi truyen du lieu qua Data Channel.");
        } finally {
            closePassiveConnection();
        }
    }

    /**
     * Xu ly lenh RETR (Download) - Gui file tu Server den Client
     */
    private void handleRetr(String fileName) {
        if (!checkLogin()) return;
        if (fileName.isEmpty()) {
            sendResponse(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "RETR can co tham so ten file.");
            return;
        }
        if (passiveConnection == null || !passiveConnection.isOpen()) {
            sendResponse(FtpResponseCode.CANT_OPEN_DATA_CONNECTION, "Chua mo kenh du lieu. Vui long goi PASV truoc.");
            return;
        }

        File file = resolvePath(fileName);
        if (file == null || !file.exists() || !file.isFile() || !isSafePath(file)) {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Tep khong ton tai hoac khong the doc: " + fileName);
            closePassiveConnection();
            return;
        }
        if (!file.canRead()) {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Khong co quyen doc tep tin (Permission Denied): " + fileName);
            closePassiveConnection();
            return;
        }

        sendResponse(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION, 
                String.format("Mo Data Channel che do BINARY cho tep '%s' (%d bytes).", file.getName(), file.length()));

        try {
            long transferred = passiveConnection.sendFile(file);
            sendResponse(FtpResponseCode.CLOSING_DATA_CONNECTION, 
                    String.format("Truyen tep thanh cong (%d bytes).", transferred));
        } catch (IOException e) {
            ServerLogger.error(clientTag, "Loi khi truyen file: " + e.getMessage(), e);
            sendResponse(FtpResponseCode.CONNECTION_CLOSED_TRANSFER_ABORTED, "Truyen tep bi huy hoac loi ket noi.");
        } finally {
            closePassiveConnection();
        }
    }

    /**
     * Xu ly lenh STOR (Upload) - Nhan file tu Client luu len Server
     */
    private void handleStor(String fileName) {
        if (!checkLogin()) return;
        if (fileName.isEmpty()) {
            sendResponse(FtpResponseCode.SYNTAX_ERROR_PARAMETERS, "STOR can co tham so ten file.");
            return;
        }
        if (passiveConnection == null || !passiveConnection.isOpen()) {
            sendResponse(FtpResponseCode.CANT_OPEN_DATA_CONNECTION, "Chua mo kenh du lieu. Vui long goi PASV truoc.");
            return;
        }

        File file = resolvePath(fileName);
        if (file == null || !isSafePath(file)) {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Duong dan khong hop le hoac bi tu choi quyen ghi: " + fileName);
            closePassiveConnection();
            return;
        }

        File parent = file.getParentFile();
        if (parent != null && (!parent.exists() || !parent.canWrite())) {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Khong co quyen ghi vao thu muc dich (Permission Denied).");
            closePassiveConnection();
            return;
        }

        if (file.exists() && !file.canWrite()) {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Tep tin da ton tai va chi co quyen doc (Read-only): " + fileName);
            closePassiveConnection();
            return;
        }

        sendResponse(FtpResponseCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION, 
                "San sang nhan du lieu cho tep: " + file.getName());

        try {
            long received = passiveConnection.receiveFile(file);
            sendResponse(FtpResponseCode.CLOSING_DATA_CONNECTION, 
                    String.format("Upload tep '%s' thanh cong (%d bytes).", file.getName(), received));
        } catch (IOException e) {
            ServerLogger.error(clientTag, "Loi khi nhan file upload: " + e.getMessage(), e);
            sendResponse(FtpResponseCode.CONNECTION_CLOSED_TRANSFER_ABORTED, "Upload tep bi loi giua chung.");
        } finally {
            closePassiveConnection();
        }
    }

    /**
     * Xu ly lenh DELE - Xoa file
     */
    private void handleDele(String fileName) {
        if (!checkLogin()) return;
        File file = resolvePath(fileName);
        if (file != null && file.exists() && file.isFile() && isSafePath(file)) {
            if (file.delete()) {
                sendResponse(FtpResponseCode.FILE_ACTION_OK, "Da xoa tep thanh cong: " + fileName);
            } else {
                sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Khong the xoa tep: " + fileName);
            }
        } else {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Tep khong ton tai hoac khong co quyen.");
        }
    }

    /**
     * Xu ly lenh MKD - Tao thu muc moi
     */
    private void handleMkd(String dirName) {
        if (!checkLogin()) return;
        File dir = resolvePath(dirName);
        if (dir != null && isSafePath(dir)) {
            if (dir.mkdirs()) {
                sendResponse(FtpResponseCode.PATHNAME_CREATED, "\"" + getVirtualPath(dir) + "\" da duoc tao.");
            } else {
                sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Khong the tao thu muc: " + dirName);
            }
        } else {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Duong dan khong hop le.");
        }
    }

    /**
     * Xu ly lenh RMD - Xoa thu muc rong
     */
    private void handleRmd(String dirName) {
        if (!checkLogin()) return;
        File dir = resolvePath(dirName);
        if (dir != null && dir.exists() && dir.isDirectory() && isSafePath(dir)) {
            if (dir.equals(rootDir)) {
                sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Khong the xoa thu muc goc Root.");
                return;
            }
            if (dir.delete()) {
                sendResponse(FtpResponseCode.FILE_ACTION_OK, "Da xoa thu muc: " + dirName);
            } else {
                sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Khong the xoa thu muc (thu muc phai rong).");
            }
        } else {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Thu muc khong ton tai.");
        }
    }

    /**
     * Xu ly lenh SIZE - Tra ve dung luong file
     */
    private void handleSize(String fileName) {
        if (!checkLogin()) return;
        File file = resolvePath(fileName);
        if (file != null && file.exists() && file.isFile() && isSafePath(file)) {
            sendResponse(FtpResponseCode.FILE_STATUS_SIZE, String.valueOf(file.length()));
        } else {
            sendResponse(FtpResponseCode.FILE_UNAVAILABLE, "Tep khong ton tai.");
        }
    }

    private void handleType(String type) {
        if (type.equalsIgnoreCase("I") || type.equalsIgnoreCase("L 8")) {
            sendResponse(FtpResponseCode.COMMAND_OK, "Kieu truyen dat thanh Binary (Type I).");
        } else if (type.equalsIgnoreCase("A")) {
            sendResponse(FtpResponseCode.COMMAND_OK, "Kieu truyen dat thanh ASCII (Type A).");
        } else {
            sendResponse(FtpResponseCode.COMMAND_OK, "Chap nhan kieu truyen: " + type);
        }
    }

    private void handleFeat() {
        sendRawResponse("211-Features:\r\n PASV\r\n SIZE\r\n UTF8\r\n211 End");
    }

    private void handleHelp() {
        sendRawResponse("214-Danh sach lenh ho tro boi FTP Server Nhom 11:\r\n" +
                " USER <username> - Dang nhap\r\n" +
                " PASS <password> - Mat khau\r\n" +
                " PWD             - Xem thu muc hien tai\r\n" +
                " CWD <dir>       - Chuyen thu muc\r\n" +
                " CDUP            - Ve thu muc cha\r\n" +
                " PASV            - Mo kenh du lieu Passive Mode\r\n" +
                " LIST            - Liet ke tep tin\r\n" +
                " RETR <file>     - Tai tep tin ve Client (Download)\r\n" +
                " STOR <file>     - Tai tep tin len Server (Upload)\r\n" +
                " DELE <file>     - Xoa tep tin\r\n" +
                " MKD <dir>       - Tao thu muc\r\n" +
                " RMD <dir>       - Xoa thu muc rong\r\n" +
                " SIZE <file>     - Xem kich thuoc tep\r\n" +
                " TYPE <A|I>      - Chon kieu truyen (ASCII hoac Binary)\r\n" +
                " NOOP            - Kiem tra ket noi\r\n" +
                " QUIT            - Thoat khoi server\r\n" +
                "214 Ket thuc tro giup.");
    }

    // ==========================================
    // CAC TIEN ICH KIEM TRA DUONG DAN VA MANG
    // ==========================================

    /**
     * Chuyen doi duong dan tuong doi/tuyet doi thanh File, chong Path Traversal
     */
    private File resolvePath(String relativeOrAbsolutePath) {
        if (relativeOrAbsolutePath == null || relativeOrAbsolutePath.isEmpty()) {
            return currentDir;
        }

        File target;
        if (relativeOrAbsolutePath.startsWith("/") || relativeOrAbsolutePath.startsWith("\\")) {
            target = new File(rootDir, relativeOrAbsolutePath.substring(1));
        } else {
            target = new File(currentDir, relativeOrAbsolutePath);
        }

        try {
            String canonicalRoot = rootDir.getCanonicalPath();
            String canonicalTarget = target.getCanonicalPath();
            if (canonicalTarget.startsWith(canonicalRoot)) {
                return target;
            }
        } catch (IOException ignored) {}
        return null;
    }

    /**
     * Kiem tra duong dan co nam an toan trong sandbox rootDir hay khong
     */
    private boolean isSafePath(File file) {
        try {
            String canonicalRoot = rootDir.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            return canonicalFile.startsWith(canonicalRoot);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Lay duong dan ao (Virtual Path) tuong doi voi rootDir de hien thi cho Client
     */
    private String getVirtualPath(File file) {
        try {
            String canonicalRoot = rootDir.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            if (canonicalFile.equals(canonicalRoot)) {
                return "/";
            }
            if (canonicalFile.startsWith(canonicalRoot)) {
                String sub = canonicalFile.substring(canonicalRoot.length()).replace('\\', '/');
                return sub.startsWith("/") ? sub : "/" + sub;
            }
        } catch (IOException ignored) {}
        return "/";
    }

    /**
     * Gui phan hoi chuan voi ma trang thai va thong diep
     */
    private void sendResponse(int code, String message) {
        sendRawResponse(code + " " + message);
    }

    /**
     * Gui chuoi phan hoi truc tiep qua Control Socket
     */
    private void sendRawResponse(String raw) {
        try {
            if (writer != null) {
                ServerLogger.logSend(clientTag, raw);
                writer.write(raw + "\r\n");
                writer.flush();
            }
        } catch (IOException e) {
            ServerLogger.warn(clientTag, "Khong the gui phan hoi: " + e.getMessage());
        }
    }

    private void closePassiveConnection() {
        if (passiveConnection != null) {
            passiveConnection.close();
            passiveConnection = null;
        }
    }

    private void closeSession() {
        isRunning = false;
        closePassiveConnection();
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {}
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {}
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (IOException ignored) {}
        ServerLogger.info(clientTag, "Da dong toan bo ket noi cua phien.");
    }
}
