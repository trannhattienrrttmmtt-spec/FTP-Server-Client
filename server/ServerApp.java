package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Giao diện đồ họa Quản trị FTP Server (Java Swing GUI)
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh (Điều khiển và Dữ liệu)
 * Môn học: Lập trình mạng máy tính - Lớp: 23DTHB4 - Nhóm 11
 * Sinh viên thực hiện phần Server: Trần Nhật Tiến (MSSV: 2380602243)
 */
public class ServerApp extends JFrame {

    private final UserManager userManager = new UserManager();
    private FtpServer ftpServer;
    private Thread serverThread;
    private LocalDateTime serverStartTime;

    // --- Điều khiển cấu hình ---
    private final JTextField portField = new JTextField(String.valueOf(ServerConfig.DEFAULT_CONTROL_PORT), 6);
    private final JTextField rootField = new JTextField("server/ftp_root", 22);
    private final JButton browseBtn = new JButton("Chọn...");
    private final JButton openFolderBtn = new JButton("Mở thư mục");
    private final JButton startBtn = new JButton("▶ Khởi động Server");
    private final JButton stopBtn = new JButton("■ Dừng Server");

    // --- Thanh trạng thái Dashboard ---
    private final JLabel statusBadge = new JLabel("● ĐÃ DỪNG");
    private final JLabel lanIpLabel = new JLabel("IP LAN: 127.0.0.1");
    private final JLabel pasvRangeLabel = new JLabel(
            "PASV Ports: " + ServerConfig.PASSIVE_PORT_MIN + " – " + ServerConfig.PASSIVE_PORT_MAX);
    private final JLabel clientCountLabel = new JLabel("Lượt kết nối: 0");
    private final JLabel uptimeLabel = new JLabel("Uptime: 00:00:00");

    // --- Bảng quản lý tài khoản ---
    private final UserTableModel userTableModel = new UserTableModel();
    private final JTable userTable = new JTable(userTableModel);
    private final JTextField newUsernameField = new JTextField(9);
    private final JTextField newPasswordField = new JTextField(9);
    private final JButton addUserBtn = new JButton("+ Thêm / Sửa");
    private final JButton removeUserBtn = new JButton("- Xóa");

    // --- Bảng giám sát tệp tin trong Sandbox Root ---
    private final RootFileTableModel rootFileTableModel = new RootFileTableModel();
    private final JTable rootFileTable = new JTable(rootFileTableModel);
    private final JButton refreshFilesBtn = new JButton("↻ Làm mới kho tệp");
    private final JLabel rootSummaryLabel = new JLabel("0 mục");

    // --- Khung Nhật ký (Server Console Log) ---
    private final JTextArea logArea = new JTextArea();
    private final JCheckBox autoScrollCheck = new JCheckBox("Tự động cuộn", true);
    private final JButton clearLogBtn = new JButton("Xóa Log");
    private final JTextField adminCmdField = new JTextField();
    private final JButton sendAdminCmdBtn = new JButton("Thực thi");

    private final Timer dashboardTimer;

    public ServerApp(int initialPort, String initialRoot) {
        super("FTP Server Quản Trị Hai Kênh (Control & Data) — Nhóm 11 | Trần Nhật Tiến (2380602243)");
        portField.setText(String.valueOf(initialPort));
        if (initialRoot != null && !initialRoot.trim().isEmpty()) {
            rootField.setText(initialRoot.trim());
        }

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1120, 740);
        setLocationRelativeTo(null);

        buildUi();
        wireEvents();

        // Đăng ký nhận log thời gian thực từ ServerLogger
        ServerLogger.setUiLogListener(this::appendLogAsync);

        // Cập nhật bảng tài khoản và tệp tin ban đầu
        userTableModel.refresh(userManager.getAllUsers());
        refreshRootFiles();
        lanIpLabel.setText("IP LAN: " + detectLanIp());

        // Timer cập nhật Uptime & Số kết nối mỗi giây
        dashboardTimer = new Timer(1000, e -> updateDashboardMetrics());
        dashboardTimer.start();

        // Tự động khởi chạy Server ngay khi mở giao diện để tiện demo
        SwingUtilities.invokeLater(this::startServer);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        // ==================== TOP: CẤU HÌNH & DASHBOARD ====================
        JPanel topContainer = new JPanel(new BorderLayout(0, 6));

        // Dòng 1: Cấu hình cổng, thư mục gốc và nút Bật/Tắt
        JPanel configBar = new JPanel(new GridBagLayout());
        configBar.setBorder(new TitledBorder("Cấu hình & Điều khiển Máy chủ FTP (Control Channel & Passive Data Pool)"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        int col = 0;
        c.gridx = col++;
        configBar.add(new JLabel("Cổng Control:"), c);
        c.gridx = col++;
        portField.setFont(portField.getFont().deriveFont(Font.BOLD));
        configBar.add(portField, c);

        c.gridx = col++;
        configBar.add(new JLabel("Thư mục Sandbox Root:"), c);
        c.gridx = col++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        configBar.add(rootField, c);

        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = col++;
        configBar.add(browseBtn, c);
        c.gridx = col++;
        configBar.add(openFolderBtn, c);

        startBtn.setBackground(new Color(22, 163, 74));
        startBtn.setForeground(Color.WHITE);
        startBtn.setFocusPainted(false);
        startBtn.setFont(startBtn.getFont().deriveFont(Font.BOLD));

        stopBtn.setBackground(new Color(220, 38, 38));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setFocusPainted(false);
        stopBtn.setFont(stopBtn.getFont().deriveFont(Font.BOLD));
        stopBtn.setEnabled(false);

        c.gridx = col++;
        configBar.add(startBtn, c);
        c.gridx = col;
        configBar.add(stopBtn, c);

        // Dòng 2: Thanh chỉ số trạng thái (Dashboard Pills)
        JPanel statsBar = new JPanel(new GridLayout(1, 5, 10, 0));
        statsBar.setBorder(new EmptyBorder(2, 2, 2, 2));

        statusBadge.setFont(statusBadge.getFont().deriveFont(Font.BOLD, 13f));
        statusBadge.setForeground(new Color(185, 28, 28));
        statsBar.add(createMetricCard("Trạng thái Máy chủ", statusBadge));
        statsBar.add(createMetricCard("Địa chỉ Mạng (Client nhập)", lanIpLabel));
        statsBar.add(createMetricCard("Kênh Dữ liệu (Passive Mode)", pasvRangeLabel));
        statsBar.add(createMetricCard("Tổng phiên Client", clientCountLabel));
        statsBar.add(createMetricCard("Thời gian hoạt động", uptimeLabel));

        topContainer.add(configBar, BorderLayout.NORTH);
        topContainer.add(statsBar, BorderLayout.CENTER);
        root.add(topContainer, BorderLayout.NORTH);

        // ==================== CENTER: SPLIT PANE ====================
        // Bên trái: Quản lý User + Giám sát Kho tệp Sandbox
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 0, 8));

        // 1. Panel Quản lý Tài khoản
        JPanel userPanel = new JPanel(new BorderLayout(4, 6));
        userPanel.setBorder(new TitledBorder("Quản lý Tài khoản Xác thực (UserManager)"));

        userTable.setRowHeight(23);
        userTable.setFillsViewportHeight(true);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userPanel.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel userForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        userForm.add(new JLabel("User:"));
        userForm.add(newUsernameField);
        userForm.add(new JLabel("Pass:"));
        userForm.add(newPasswordField);
        userForm.add(addUserBtn);
        userForm.add(removeUserBtn);
        userPanel.add(userForm, BorderLayout.SOUTH);

        // 2. Panel Giám sát Tệp trong Sandbox Root
        JPanel filePanel = new JPanel(new BorderLayout(4, 6));
        filePanel.setBorder(new TitledBorder("Giám sát Kho Lưu trữ Gốc (Sandbox Root Directory)"));

        rootFileTable.setRowHeight(23);
        rootFileTable.setFillsViewportHeight(true);
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        rootFileTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);
        filePanel.add(new JScrollPane(rootFileTable), BorderLayout.CENTER);

        JPanel fileFooter = new JPanel(new BorderLayout());
        fileFooter.add(rootSummaryLabel, BorderLayout.WEST);
        fileFooter.add(refreshFilesBtn, BorderLayout.EAST);
        filePanel.add(fileFooter, BorderLayout.SOUTH);

        leftPanel.add(userPanel);
        leftPanel.add(filePanel);

        // Bên phải: Nhật ký Server Console Log thời gian thực
        JPanel logPanel = new JPanel(new BorderLayout(4, 6));
        logPanel.setBorder(new TitledBorder("Nhật ký Hoạt động Thời gian thực (Control Channel & Data Channel Logs)"));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(15, 23, 42));
        logArea.setForeground(new Color(226, 232, 240));
        logArea.setCaretColor(Color.WHITE);
        logArea.setMargin(new Insets(8, 8, 8, 8));
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel logBottom = new JPanel(new BorderLayout(6, 0));
        JPanel cmdInputPanel = new JPanel(new BorderLayout(4, 0));
        cmdInputPanel.add(new JLabel("Lệnh Admin (status / users / adduser u p / help): "), BorderLayout.WEST);
        cmdInputPanel.add(adminCmdField, BorderLayout.CENTER);
        cmdInputPanel.add(sendAdminCmdBtn, BorderLayout.EAST);

        JPanel logControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        logControls.add(autoScrollCheck);
        logControls.add(clearLogBtn);

        logBottom.add(cmdInputPanel, BorderLayout.CENTER);
        logBottom.add(logControls, BorderLayout.EAST);
        logPanel.add(logBottom, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, logPanel);
        splitPane.setResizeWeight(0.42);
        splitPane.setDividerLocation(440);
        root.add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createMetricCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        card.setBackground(new Color(248, 250, 252));
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.PLAIN, 11f));
        titleLbl.setForeground(new Color(100, 116, 139));
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void wireEvents() {
        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());

        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(rootField.getText().trim());
            chooser.setDialogTitle("Chọn thư mục gốc Sandbox Root cho FTP Server");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                rootField.setText(selected.getAbsolutePath());
                refreshRootFiles();
            }
        });

        openFolderBtn.addActionListener(e -> {
            try {
                File dir = ServerConfig.getResolvedRootDir(rootField.getText().trim());
                Desktop.getDesktop().open(dir);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        userTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = userTable.getSelectedRow();
                if (row >= 0) {
                    newUsernameField.setText((String) userTableModel.getValueAt(row, 0));
                    newPasswordField.setText((String) userTableModel.getValueAt(row, 1));
                }
            }
        });

        addUserBtn.addActionListener(e -> {
            String u = newUsernameField.getText().trim();
            String p = newPasswordField.getText().trim();
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Username và Password.",
                        "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }
            userManager.addUser(u, p);
            userTableModel.refresh(userManager.getAllUsers());
            ServerLogger.info("Admin UI: Đã thêm/cập nhật tài khoản '" + u + "'.");
            newUsernameField.setText("");
            newPasswordField.setText("");
        });

        removeUserBtn.addActionListener(e -> {
            int row = userTable.getSelectedRow();
            String u = (row >= 0) ? (String) userTableModel.getValueAt(row, 0) : newUsernameField.getText().trim();
            if (u.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Hãy chọn một tài khoản trong bảng để xóa.",
                        "Chưa chọn tài khoản", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if ("anonymous".equalsIgnoreCase(u)) {
                JOptionPane.showMessageDialog(this, "Tài khoản 'anonymous' là mặc định của chuẩn FTP.",
                        "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (userManager.removeUser(u)) {
                userTableModel.refresh(userManager.getAllUsers());
                ServerLogger.info("Admin UI: Đã xóa tài khoản '" + u + "'.");
                newUsernameField.setText("");
                newPasswordField.setText("");
            }
        });

        refreshFilesBtn.addActionListener(e -> refreshRootFiles());

        clearLogBtn.addActionListener(e -> logArea.setText(""));

        sendAdminCmdBtn.addActionListener(e -> executeAdminCommand());
        adminCmdField.addActionListener(e -> executeAdminCommand());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }
        });
    }

    private void startServer() {
        if (ftpServer != null && ftpServer.isRunning()) {
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng (Port) phải là số nguyên từ 1 đến 65535.",
                    "Lỗi cấu hình", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String rootPath = rootField.getText().trim();
        ftpServer = new FtpServer(port, rootPath, userManager, false);
        serverStartTime = LocalDateTime.now();

        portField.setEnabled(false);
        rootField.setEnabled(false);
        browseBtn.setEnabled(false);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);

        statusBadge.setText("● ĐANG CHẠY (Port " + port + ")");
        statusBadge.setForeground(new Color(21, 128, 61));

        refreshRootFiles();

        serverThread = new Thread(() -> {
            ftpServer.start();
            SwingUtilities.invokeLater(this::updateStoppedUiState);
        }, "FTP-Server-Main-Thread");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void stopServer() {
        if (ftpServer != null) {
            new Thread(() -> {
                ftpServer.stop();
                SwingUtilities.invokeLater(this::updateStoppedUiState);
            }, "FTP-Server-Stop-Thread").start();
        }
    }

    private void updateStoppedUiState() {
        portField.setEnabled(true);
        rootField.setEnabled(true);
        browseBtn.setEnabled(true);
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        statusBadge.setText("● ĐÃ DỪNG");
        statusBadge.setForeground(new Color(185, 28, 28));
    }

    private void updateDashboardMetrics() {
        if (ftpServer != null && ftpServer.isRunning()) {
            clientCountLabel.setText("Lượt kết nối: " + ftpServer.getClientCount());
            if (serverStartTime != null) {
                Duration d = Duration.between(serverStartTime, LocalDateTime.now());
                long hrs = d.toHours();
                long mins = d.toMinutesPart();
                long secs = d.toSecondsPart();
                uptimeLabel.setText(String.format("Uptime: %02d:%02d:%02d", hrs, mins, secs));
            }
        }
    }

    private void executeAdminCommand() {
        String line = adminCmdField.getText().trim();
        if (line.isEmpty()) return;
        adminCmdField.setText("");

        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "status":
                boolean running = ftpServer != null && ftpServer.isRunning();
                appendLogAsync(String.format(
                        "=== [STATUS] Trạng thái: %s | Port: %s | Root: %s | Tổng kết nối: %d ===",
                        running ? "RUNNING" : "STOPPED",
                        portField.getText(),
                        ServerConfig.getResolvedRootDir(rootField.getText()).getAbsolutePath(),
                        ftpServer != null ? ftpServer.getClientCount() : 0));
                break;
            case "users":
                appendLogAsync("=== [USERS] Danh sách tài khoản: " + userManager.getAllUsers().keySet() + " ===");
                break;
            case "adduser":
                if (parts.length >= 3) {
                    userManager.addUser(parts[1], parts[2]);
                    userTableModel.refresh(userManager.getAllUsers());
                    appendLogAsync("=== [SUCCESS] Đã thêm tài khoản '" + parts[1] + "' ===");
                } else {
                    appendLogAsync("=== [USAGE] adduser <username> <password> ===");
                }
                break;
            case "clear":
                logArea.setText("");
                break;
            case "stop":
                stopServer();
                break;
            case "start":
                startServer();
                break;
            case "help":
                appendLogAsync("=== [HELP] Các lệnh hỗ trợ: status, users, adduser <u > <p>, start, stop, clear ===");
                break;
            default:
                appendLogAsync("=== [WARN] Lệnh không hợp lệ. Gõ 'help' để xem hướng dẫn. ===");
                break;
        }
    }

    private void appendLogAsync(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            if (autoScrollCheck.isSelected()) {
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
            // Tự động làm mới bảng tệp nếu có thao tác tải lên / xóa / tạo thư mục hoàn tất
            if (msg.contains("226 ") || msg.contains("250 ")) {
                refreshRootFiles();
            }
        });
    }

    public void refreshRootFiles() {
        File dir = ServerConfig.getResolvedRootDir(rootField.getText().trim());
        rootFileTableModel.loadDirectory(dir);
        int dirs = 0, files = 0;
        long totalBytes = 0;
        for (File f : rootFileTableModel.files) {
            if (f.isDirectory()) dirs++;
            else {
                files++;
                totalBytes += f.length();
            }
        }
        rootSummaryLabel.setText(String.format("Thư mục gốc: %d thư mục, %d tệp tin (%s)",
                dirs, files, formatSize(totalBytes)));
    }

    private void onExit() {
        dashboardTimer.stop();
        if (ftpServer != null && ftpServer.isRunning()) {
            ftpServer.stop();
        }
        dispose();
        System.exit(0);
    }

    private static String detectLanIp() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface ni = nets.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private static String formatSize(long bytes) {
        if (bytes < 0) return "--";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format("%.2f MB", mb);
    }

    // ==================== TABLE MODELS ====================
    private static class UserTableModel extends AbstractTableModel {
        private final String[] cols = {"Tên đăng nhập", "Mật khẩu", "Ghi chú / Vai trò"};
        private final List<String[]> rows = new ArrayList<>();

        void refresh(Map<String, String> map) {
            rows.clear();
            Map<String, String> sorted = new TreeMap<>(map);
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                String u = e.getKey();
                String role = "Người dùng chuẩn";
                if ("tien".equals(u)) role = "Nhóm trưởng (Server)";
                else if ("trong".equals(u)) role = "Thành viên (Data Channel)";
                else if ("vu".equals(u)) role = "Thành viên (Client UI)";
                else if ("admin".equals(u)) role = "Quản trị viên";
                else if ("anonymous".equals(u)) role = "Khách vô danh (RFC 959)";
                rows.add(new String[]{u, e.getValue(), role});
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return rows.size(); }

        @Override
        public int getColumnCount() { return cols.length; }

        @Override
        public String getColumnName(int col) { return cols[col]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return rows.get(rowIndex)[columnIndex];
        }
    }

    private static class RootFileTableModel extends AbstractTableModel {
        private final String[] cols = {"Tên tệp / thư mục", "Loại", "Kích thước", "Ngày sửa đổi"};
        private final List<File> files = new ArrayList<>();
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        void loadDirectory(File rootDir) {
            files.clear();
            if (rootDir != null && rootDir.exists() && rootDir.isDirectory()) {
                File[] list = rootDir.listFiles();
                if (list != null) {
                    Collections.addAll(files, list);
                    files.sort((a, b) -> {
                        if (a.isDirectory() != b.isDirectory()) {
                            return a.isDirectory() ? -1 : 1;
                        }
                        return a.getName().compareToIgnoreCase(b.getName());
                    });
                }
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return files.size(); }

        @Override
        public int getColumnCount() { return cols.length; }

        @Override
        public String getColumnName(int col) { return cols[col]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            File f = files.get(rowIndex);
            switch (columnIndex) {
                case 0: return (f.isDirectory() ? "[DIR] " : "[FILE] ") + f.getName();
                case 1: return f.isDirectory() ? "Thư mục" : "Tệp tin";
                case 2: return f.isDirectory() ? "--" : formatSize(f.length());
                case 3: return sdf.format(new Date(f.lastModified()));
                default: return "";
            }
        }
    }

    public static void main(String[] args) {
        int port = ServerConfig.DEFAULT_CONTROL_PORT;
        String rootDir = "server/ftp_root";
        if (!new File(rootDir).exists() && new File("ftp_root").exists()) {
            rootDir = "ftp_root";
        }
        if (args.length >= 1) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        if (args.length >= 2) {
            rootDir = args[1];
        }

        final int finalPort = port;
        final String finalRoot = rootDir;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new ServerApp(finalPort, finalRoot).setVisible(true);
        });
    }
}
