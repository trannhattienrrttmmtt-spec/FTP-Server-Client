package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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

public class ServerApp extends JFrame {

    private final UserManager userManager = new UserManager();
    private FtpServer ftpServer;
    private Thread serverThread;
    private LocalDateTime serverStartTime;

    private static final Color CLR_START = new Color(22, 163, 74);      // Green 600
    private static final Color CLR_STOP = new Color(220, 38, 38);       // Red 600
    private static final Color CLR_PRIMARY = new Color(37, 99, 235);     // Blue 600
    private static final Color CLR_BORDER = new Color(226, 232, 240);    // Slate 200

    private static final Icon FOLDER_ICON = new FolderVectorIcon(16, 16);
    private static final Icon FILE_ICON = new FileVectorIcon(16, 16);

    private final JTextField portField = new JTextField(String.valueOf(ServerConfig.DEFAULT_CONTROL_PORT), 6);
    private final JTextField rootField = new JTextField("server/ftp_root", 22);
    private final JButton browseBtn = new JButton("Chọn...");
    private final JButton openFolderBtn = new JButton("Mở thư mục");
    private final ServerCustomButton startBtn = new ServerCustomButton("Khởi động Server", CLR_START, Color.WHITE);
    private final ServerCustomButton stopBtn = new ServerCustomButton("Dừng Server", CLR_STOP, Color.WHITE);

    private final StatusBadgeLabel statusBadge = new StatusBadgeLabel();
    private final JLabel lanIpLabel = new JLabel("IP LAN: 127.0.0.1");
    private final JLabel pasvRangeLabel = new JLabel(
            "PASV Ports: " + ServerConfig.PASSIVE_PORT_MIN + " – " + ServerConfig.PASSIVE_PORT_MAX);
    private final JLabel clientCountLabel = new JLabel("Lượt kết nối: 0");
    private final JLabel uptimeLabel = new JLabel("Uptime: 00:00:00");

    private final UserTableModel userTableModel = new UserTableModel();
    private final JTable userTable = new JTable(userTableModel);
    private final JTextField newUsernameField = new JTextField(9);
    private final JTextField newPasswordField = new JTextField(9);
    private final JButton addUserBtn = new JButton("+ Thêm / Sửa");
    private final JButton removeUserBtn = new JButton("- Xóa");

    private final RootFileTableModel rootFileTableModel = new RootFileTableModel();
    private final JTable rootFileTable = new JTable(rootFileTableModel);
    private final JButton refreshFilesBtn = new JButton("Làm mới kho tệp");
    private final JLabel rootSummaryLabel = new JLabel("0 mục");

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
        setSize(1140, 750);
        setLocationRelativeTo(null);

        buildUi();
        wireEvents();

        ServerLogger.setUiLogListener(this::appendLogAsync);

        userTableModel.refresh(userManager.getAllUsers());
        refreshRootFiles();
        lanIpLabel.setText("IP LAN: " + detectLanIp());

        dashboardTimer = new Timer(1000, e -> updateDashboardMetrics());
        dashboardTimer.start();

        SwingUtilities.invokeLater(this::startServer);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        JPanel topContainer = new JPanel(new BorderLayout(0, 6));

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

        startBtn.setPreferredSize(new Dimension(145, 28));
        stopBtn.setPreferredSize(new Dimension(115, 28));
        stopBtn.setEnabled(false);

        c.gridx = col++;
        configBar.add(startBtn, c);
        c.gridx = col;
        configBar.add(stopBtn, c);

        JPanel statsBar = new JPanel(new GridLayout(1, 5, 10, 0));
        statsBar.setBorder(new EmptyBorder(2, 2, 2, 2));

        statusBadge.setStatus(false, "ĐÃ DỪNG");
        statsBar.add(createMetricCard("Trạng thái Máy chủ", statusBadge));
        statsBar.add(createMetricCard("Địa chỉ Mạng (Client nhập)", lanIpLabel));
        statsBar.add(createMetricCard("Kênh Dữ liệu (Passive Mode)", pasvRangeLabel));
        statsBar.add(createMetricCard("Tổng phiên Client", clientCountLabel));
        statsBar.add(createMetricCard("Thời gian hoạt động", uptimeLabel));

        topContainer.add(configBar, BorderLayout.NORTH);
        topContainer.add(statsBar, BorderLayout.CENTER);
        root.add(topContainer, BorderLayout.NORTH);

        // ==================== CENTER: SPLIT PANE ====================
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 0, 8));

        JPanel userPanel = new JPanel(new BorderLayout(4, 6));
        userPanel.setBorder(new TitledBorder("Quản lý Tài khoản Xác thực (UserManager)"));

        userTable.setRowHeight(24);
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

        JPanel filePanel = new JPanel(new BorderLayout(4, 6));
        filePanel.setBorder(new TitledBorder("Giám sát Kho Lưu trữ Gốc (Sandbox Root Directory)"));

        rootFileTable.setRowHeight(24);
        rootFileTable.setFillsViewportHeight(true);
        rootFileTable.getColumnModel().getColumn(0).setCellRenderer(new RootFileNameRenderer());
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
        cmdInputPanel.add(new JLabel("Lệnh Admin: "), BorderLayout.WEST);
        adminCmdField.setToolTipText("Lệnh quản trị: status, users, adduser <user> <pass>, start, stop, clear");
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
        splitPane.setDividerLocation(450);
        root.add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createMetricCard(String title, JComponent valueComp) {
        JPanel card = new JPanel(new BorderLayout(2, 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        card.setBackground(new Color(248, 250, 252));
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.PLAIN, 11f));
        titleLbl.setForeground(new Color(100, 116, 139));
        valueComp.setFont(valueComp.getFont().deriveFont(Font.BOLD, 12.5f));
        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueComp, BorderLayout.CENTER);
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

        statusBadge.setStatus(true, "ĐANG CHẠY (Port " + port + ")");
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
        statusBadge.setStatus(false, "ĐÃ DỪNG");
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

    // =====================================================================

    // =====================================================================

    private static class ServerCustomButton extends JButton {
        private final Color bg;
        private final Color fg;

        public ServerCustomButton(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            this.fg = fg;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (!isEnabled()) {
                g2.setColor(new Color(226, 232, 240));
                g2.fillRoundRect(0, 0, w, h, 6, 6);
                g2.setColor(new Color(148, 163, 184));
            } else {
                if (getModel().isPressed()) {
                    g2.setColor(bg.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(bg.brighter());
                } else {
                    g2.setColor(bg);
                }
                g2.fillRoundRect(0, 0, w, h, 6, 6);
                g2.setColor(fg);
            }

            FontMetrics fm = g2.getFontMetrics(getFont());
            String text = getText();
            int tw = fm.stringWidth(text);
            int th = fm.getAscent();
            int x = (w - tw) / 2;
            int y = (h + th - fm.getDescent()) / 2;

            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    // =====================================================================

    // =====================================================================

    private static class StatusBadgeLabel extends JPanel {
        private boolean isRunning = false;
        private String text = "ĐÃ DỪNG";

        public StatusBadgeLabel() {
            setOpaque(false);
        }

        public void setStatus(boolean running, String txt) {
            this.isRunning = running;
            this.text = txt;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color dotColor = isRunning ? new Color(22, 163, 74) : new Color(220, 38, 38);
            g2.setColor(dotColor);
            g2.fillOval(2, 6, 10, 10);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(dotColor);
            g2.drawString(text, 18, 15);
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(170, 22);
        }
    }

    // =====================================================================

    // =====================================================================

    private static class FolderVectorIcon implements Icon {
        private final int width, height;
        FolderVectorIcon(int w, int h) { width = w; height = h; }
        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(217, 119, 6));
            g2.fillRoundRect(x + 1, y + 2, 7, 5, 2, 2);
            g2.setColor(new Color(245, 158, 11));
            g2.fillRoundRect(x + 1, y + 4, width - 2, height - 5, 3, 3);
            g2.dispose();
        }
    }

    private static class FileVectorIcon implements Icon {
        private final int width, height;
        FileVectorIcon(int w, int h) { width = w; height = h; }
        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int px = x + 2, py = y + 1, pw = width - 4, ph = height - 2;
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(px, py, pw, ph, 2, 2);
            g2.setColor(new Color(148, 163, 184));
            g2.drawRoundRect(px, py, pw, ph, 2, 2);
            g2.setColor(new Color(203, 213, 225));
            g2.drawLine(px + 3, py + 4, px + pw - 3, py + 4);
            g2.drawLine(px + 3, py + 7, px + pw - 3, py + 7);
            g2.dispose();
        }
    }

    private static class RootFileNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String val = value != null ? value.toString() : "";
            boolean isDir = val.startsWith("[DIR]");
            setIcon(isDir ? FOLDER_ICON : FILE_ICON);
            setText(val.replace("[DIR] ", "").replace("[FILE] ", ""));
            setFont(new Font("Segoe UI", isDir ? Font.BOLD : Font.PLAIN, 12));
            return this;
        }
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

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int col) { return cols[col]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
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

        @Override public int getRowCount() { return files.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int col) { return cols[col]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
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
