package server;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lớp khởi chạy chính của FTP Server (Multi-threaded TCP Two-Channel)
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh (Điều khiển và Dữ liệu)
 * Môn học: Lập trình mạng máy tính - Lớp: 23DTHB4 - Nhóm 11
 * Sinh viên thực hiện phần Server: Trần Nhật Tiến (MSSV: 2380602243)
 */
public class FtpServer {
    private final int port;
    private final File rootDir;
    private final UserManager userManager;
    private final ExecutorService threadPool;
    private final AtomicInteger clientCounter = new AtomicInteger(0);

    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;
    private final LocalDateTime startTime = LocalDateTime.now();
    private final boolean enableConsoleInput;

    public FtpServer(int port, String rootDirPath) {
        this(port, rootDirPath, new UserManager(), true);
    }

    public FtpServer(int port, String rootDirPath, UserManager userManager, boolean enableConsoleInput) {
        this.port = port;
        this.rootDir = ServerConfig.getResolvedRootDir(rootDirPath);
        this.userManager = (userManager != null) ? userManager : new UserManager();
        this.threadPool = Executors.newCachedThreadPool();
        this.enableConsoleInput = enableConsoleInput;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    public File getRootDir() {
        return rootDir;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public int getClientCount() {
        return clientCounter.get();
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * Bắt đầu lắng nghe kết nối từ Client
     */
    public void start() {
        printBanner();
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;

            ServerLogger.info(String.format("Server dang lang nghe tren cong Control Channel: %d", port));
            ServerLogger.info("Thu muc luu tru goc (Root): " + rootDir.getAbsolutePath());
            ServerLogger.info("San sang tiep nhan cac ket noi Client...\n");

            // Đăng ký Shutdown Hook giải phóng tài nguyên khi tắt ứng dụng
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            // Khởi chạy luồng đọc lệnh console của quản trị viên (nếu chạy ở chế độ CLI)
            if (enableConsoleInput) {
                Thread consoleThread = new Thread(this::runAdminConsole, "Admin-Console-Thread");
                consoleThread.setDaemon(true);
                consoleThread.start();
            }

            // Vòng lặp chính tiếp nhận kết nối đa luồng
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    int clientId = clientCounter.incrementAndGet();
                    ServerLogger.info(String.format("Tiep nhan Client moi #%d tu dia chi: %s", 
                            clientId, clientSocket.getRemoteSocketAddress()));

                    // Chuyển giao xử lý cho luồng riêng biệt trong ThreadPool
                    ClientHandler handler = new ClientHandler(clientSocket, rootDir, userManager, clientId);
                    threadPool.execute(handler);
                } catch (IOException e) {
                    if (!isRunning) {
                        break; // ServerSocket đã chủ động đóng
                    }
                    ServerLogger.error("Loi khi chap nhan ket noi Client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            ServerLogger.error("Khong the khoi dong ServerSocket tren port " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    /**
     * Dừng Server và dọn dẹp các tài nguyên mạng
     */
    public synchronized void stop() {
        if (!isRunning && (serverSocket == null || serverSocket.isClosed())) {
            return;
        }
        isRunning = false;
        ServerLogger.info("Dang tat FTP Server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        threadPool.shutdownNow();
        ServerLogger.info("FTP Server da dung hoan toan.");
    }

    /**
     * Giao diện dòng lệnh dành cho Quản trị viên điều khiển Server trực tiếp
     */
    private void runAdminConsole() {
        Scanner scanner = new Scanner(System.in);
        while (isRunning) {
            try {
                if (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\s+");
                    String cmd = parts[0].toLowerCase();

                    switch (cmd) {
                        case "status":
                            printStatus();
                            break;
                        case "users":
                            printUsers();
                            break;
                        case "adduser":
                            if (parts.length >= 3) {
                                userManager.addUser(parts[1], parts[2]);
                                System.out.println(">> [SUCCESS] Da them tai khoan: " + parts[1]);
                            } else {
                                System.out.println(">> [USAGE] adduser <username> <password>");
                            }
                            break;
                        case "help":
                            printAdminHelp();
                            break;
                        case "exit":
                        case "stop":
                            System.out.println(">> [INFO] Yeu cau tat server tu Console...");
                            stop();
                            System.exit(0);
                            break;
                        default:
                            System.out.println(">> Lenh console khong hop le. Go 'help' de xem danh sach lenh.");
                            break;
                    }
                }
            } catch (Exception e) {
                // Tránh crash console thread
                break;
            }
        }
    }

    private void printBanner() {
        System.out.println("========================================================================");
        System.out.println("   TRUONG DAI HOC CONG NGHE TP.HCM - KHOA CONG NGHE THONG TIN          ");
        System.out.println("   DO AN CUOI KY: LAP TRINH MANG MAY TINH - LOP 23DTHB4                ");
        System.out.println("   DE TAI: HE THONG FTP SERVER/CLIENT VOI HAI KENH (NHOM 11)           ");
        System.out.println("------------------------------------------------------------------------");
        System.out.println("   Thanh vien nhom:");
        System.out.println("   01. Tran Nhat Tien   - MSSV: 2380602243 [Phu trach Server]           ");
        System.out.println("   02. Nguyen Anh Vu    - MSSV: 2380602582 [Phu trach Client]           ");
        System.out.println("   03. Phan Thanh Trong - MSSV: 2380602351 [Phu trach Data Channel]     ");
        System.out.println("========================================================================");
        System.out.println("   Phien ban: " + ServerConfig.SERVER_VERSION + " | San sang hoat dong.");
        System.out.println("   Go 'help' de xem cac lenh quan tri console.");
        System.out.println("========================================================================");
    }

    private void printStatus() {
        System.out.println("------------- [SERVER STATUS] -------------");
        System.out.println(" Trang thai: " + (isRunning ? "DANG CHAY (RUNNING)" : "DA DUNG"));
        System.out.println(" Cong Control: " + port);
        System.out.println(" Root Sandbox: " + rootDir.getAbsolutePath());
        System.out.println(" Tong so Client da ket noi: " + clientCounter.get());
        System.out.println(" Thoi gian khoi dong: " + startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("-------------------------------------------");
    }

    private void printUsers() {
        System.out.println("------------- [DANH SACH TAI KHOAN] -------------");
        Map<String, String> users = userManager.getAllUsers();
        users.forEach((u, p) -> System.out.println("  - Username: " + u + " | Password: " + p));
        System.out.println("-------------------------------------------------");
    }

    private void printAdminHelp() {
        System.out.println("------------- [LENH QUAN TRI CONSOLE] -------------");
        System.out.println(" status                 : Xem thong tin hoat dong server");
        System.out.println(" users                  : Xem danh sach tai khoan he thong");
        System.out.println(" adduser <user> <pass>  : Them tai khoan moi ngay lap tuc");
        System.out.println(" stop hoac exit         : Dung server an toan");
        System.out.println(" help                   : Hien thi tro giup nay");
        System.out.println("----------------------------------------------------");
    }

    public static void main(String[] args) {
        int port = ServerConfig.DEFAULT_CONTROL_PORT;
        String rootDir = ServerConfig.DEFAULT_ROOT_DIR;

        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("[WARN] Cong khong hop le, su dung cong mac dinh: " + port);
            }
        }
        if (args.length >= 2) {
            rootDir = args[1];
        }

        FtpServer server = new FtpServer(port, rootDir);
        server.start();
    }
}
