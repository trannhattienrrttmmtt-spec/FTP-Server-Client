package client;

import datachannel.DataTransferListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Giao diện đồ họa Java Swing NÂNG CẤP TOÀN DIỆN cho FTP Client Hai Kênh.
 *
 * ĐẶC ĐIỂM THIẾT KẾ:
 * 1. Khắc phục triệt để lỗi mất chữ, chữ biến mất hoặc lỗi ô vuông []:
 *    - Tuyệt đối không dùng Unicode Emoji surrogate pairs làm lỗi font Windows Swing.
 *    - Toàn bộ Icon thư mục và tệp tin được vẽ trực tiếp bằng Vector Graphics2D sắc nét.
 *    - Các nút có màu tùy chỉnh (Kết nối, Ngắt kết nối, Tải lên, Tải xuống, Hủy) được
 *      tự vẽ qua ModernButton, đảm bảo chữ luôn nổi bật, tương phản cao, căn giữa hoàn hảo.
 * 2. Bố cục 2 Bảng Dual-Pane Explorer (Máy Cục Bộ <--> Máy Chủ Server):
 *    - Local Explorer: Xem và duyệt tệp cục bộ, lọc nhanh theo tên.
 *    - Remote Explorer: Xem và duyệt tệp máy chủ, lọc nhanh theo tên.
 *    - Nhấp đúp vào tệp Local: Tự động Upload sang Server.
 *    - Nhấp đúp vào tệp Server: Tự động Download về thư mục Local hiện tại.
 *    - Nút truyền trung tâm: [ Tải lên >> ] và [ << Tải xuống ].
 * 3. Quick Connect Bar với đèn LED trạng thái tự co giãn, không bị che khuất địa chỉ IP.
 * 4. Terminal Console Dark Theme với cú pháp màu tương phản cao, dễ nhìn.
 *
 * Phụ trách phát triển: Nguyễn Anh Vũ (MSSV: 2380602582) - Nhóm 11 (HUTECH)
 */
public class ClientApp extends JFrame {

    private static final int KEEP_ALIVE_MS = 60_000;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // Bảng màu giao diện phẳng (Flat UI Colors)
    private static final Color CLR_BG_MAIN = new Color(245, 247, 250);
    private static final Color CLR_CARD_BG = Color.WHITE;
    private static final Color CLR_BORDER = new Color(220, 226, 235);
    private static final Color CLR_PRIMARY = new Color(24, 119, 242);        // Royal Blue
    private static final Color CLR_DANGER = new Color(220, 38, 38);          // Crimson Red
    private static final Color CLR_SUCCESS = new Color(16, 185, 129);        // Emerald Green
    private static final Color CLR_WARNING = new Color(245, 158, 11);        // Amber
    private static final Color CLR_HEADER_BG = new Color(238, 242, 246);
    private static final Color CLR_ZEBRA_ROW = new Color(250, 252, 255);
    private static final Color CLR_SELECTION = new Color(219, 234, 254);
    private static final Color CLR_CONSOLE_BG = new Color(26, 27, 30);       // Dark Slate

    // Icon Vector vẽ bằng Graphics2D (hoàn toàn không bị lỗi font hay ô vuông)
    private static final Icon FOLDER_ICON = new FolderVectorIcon(16, 16);
    private static final Icon FILE_ICON = new FileVectorIcon(16, 16);

    private final FtpClientController controller = new FtpClientController();
    private final ExecutorService background = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ftp-client-bg");
        t.setDaemon(true);
        return t;
    });

    // --- Khung Đăng Nhập Nhanh (Quick Connect) ---
    private final JTextField hostField = createStyledTextField("127.0.0.1", 10);
    private final JTextField portField = createStyledTextField(String.valueOf(FtpClientController.DEFAULT_PORT), 4);
    private final JTextField userField = createStyledTextField("vu", 8);
    private final JPasswordField passField = new JPasswordField("123456", 8);
    private final ModernButton connectBtn = new ModernButton("Kết nối", CLR_PRIMARY, Color.WHITE);
    private final ModernButton disconnectBtn = new ModernButton("Ngắt kết nối", CLR_DANGER, Color.WHITE);
    private final JComboBox<String> sampleAccountsBox = new JComboBox<>(new String[]{
            "Tài khoản mẫu...", "vu (123456)", "tien (123456)", "trong (123456)", "admin (admin123)", "anonymous"
    });
    private final ConnectionStatusBadge statusBadge = new ConnectionStatusBadge();

    // --- Thanh Công Cụ Chính (Top Toolbar) ---
    private final ModernButton toolbarRefreshBtn = new ModernButton("Làm mới tất cả", null, new Color(30, 41, 59));
    private final ModernButton toolbarUploadBtn = new ModernButton("Tải lên (-> Server)", new Color(241, 245, 249), new Color(15, 23, 42));
    private final ModernButton toolbarDownloadBtn = new ModernButton("Tải xuống (<- Local)", new Color(241, 245, 249), new Color(15, 23, 42));
    private final ModernButton toolbarMkdirBtn = new ModernButton("Tạo thư mục", null, new Color(30, 41, 59));
    private final ModernButton toolbarDeleteBtn = new ModernButton("Xóa", null, new Color(185, 28, 28));
    private final ModernButton toolbarToggleLogBtn = new ModernButton("Nhật ký (Bật/Tắt)", null, new Color(71, 85, 105));

    // --- Bảng Cục Bộ (Local Explorer - Left Pane) ---
    private File currentLocalDir = new File(System.getProperty("user.home"));
    private final JTextField localPathField = createStyledTextField(currentLocalDir.getAbsolutePath(), 15);
    private final JTextField localSearchField = createStyledTextField("", 10);
    private final ModernButton localUpBtn = new ModernButton("Lên (..)", null, new Color(30, 41, 59));
    private final ModernButton localHomeBtn = new ModernButton("Home", null, new Color(30, 41, 59));
    private final ModernButton localRefreshBtn = new ModernButton("Tải lại", null, new Color(30, 41, 59));
    private final LocalFileTableModel localTableModel = new LocalFileTableModel();
    private final JTable localTable = new JTable(localTableModel);
    private final JLabel localSummaryLabel = new JLabel("0 mục");

    // --- Nút Chuyển Giao Trung Tâm (Center Action Buttons) ---
    private final ModernButton centerUploadBtn = new ModernButton("Tải lên >>", CLR_PRIMARY, Color.WHITE);
    private final ModernButton centerDownloadBtn = new ModernButton("<< Tải xuống", new Color(13, 148, 136), Color.WHITE);

    // --- Bảng Máy Chủ (Remote Server Explorer - Right Pane) ---
    private String currentRemotePath = "/";
    private final JTextField remotePathField = createStyledTextField("/", 15);
    private final JTextField remoteSearchField = createStyledTextField("", 10);
    private final ModernButton remoteUpBtn = new ModernButton("Lên (..)", null, new Color(30, 41, 59));
    private final ModernButton remoteRootBtn = new ModernButton("Gốc (/)", null, new Color(30, 41, 59));
    private final ModernButton remoteRefreshBtn = new ModernButton("Tải lại", null, new Color(30, 41, 59));
    private final RemoteFileTableModel remoteTableModel = new RemoteFileTableModel();
    private final JTable remoteTable = new JTable(remoteTableModel);
    private final JLabel remoteSummaryLabel = new JLabel("Chưa kết nối");

    // --- Khung Log Terminal (Dark Console) ---
    private final JTextPane consolePane = new JTextPane();
    private final JTextField consoleCmdField = createStyledTextField("", 25);
    private final ModernButton consoleSendBtn = new ModernButton("Gửi lệnh", new Color(51, 65, 85), Color.WHITE);
    private final ModernButton consoleClearBtn = new ModernButton("Xóa log", null, new Color(71, 85, 105));
    private final List<String> commandHistory = new ArrayList<>();
    private int commandHistoryIndex = -1;
    private JSplitPane mainVerticalSplit;
    private boolean consoleVisible = true;
    private int lastConsoleDividerLocation = 480;

    // --- Thanh Tiến Trình & Trạng Thái Đáy (Bottom Status Bar) ---
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusTextLabel = new JLabel("Sẵn sàng.");
    private final JLabel transferDetailLabel = new JLabel("");
    private final ModernButton cancelTransferBtn = new ModernButton("Hủy", CLR_DANGER, Color.WHITE);

    private FileTransferTask currentTransfer;

    public ClientApp() {
        super("FTP Client Hai Kênh Hiện Đại - Nhóm 11 (HUTECH)");
        initGlobalLookAndFeel();
        controller.setLogListener(this::appendLog);

        buildUi();
        wireEvents();
        loadLocalDirectory(currentLocalDir);
        updateUiState();

        // Định kỳ gửi NOOP giữ phiên kết nối
        new javax.swing.Timer(KEEP_ALIVE_MS, e -> {
            if (controller.isLoggedIn() && currentTransfer == null) {
                background.submit(controller::keepAliveIfIdle);
            }
        }).start();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApp();
            }
        });

        setSize(1180, 780);
        setMinimumSize(new Dimension(880, 580));
        setLocationRelativeTo(null);
    }

    private void initGlobalLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Font baseFont = new Font("Segoe UI", Font.PLAIN, 12);
        Font boldFont = new Font("Segoe UI", Font.BOLD, 12);
        UIManager.put("Label.font", baseFont);
        UIManager.put("TextField.font", baseFont);
        UIManager.put("Table.font", baseFont);
        UIManager.put("TableHeader.font", boldFont);
    }

    // =====================================================================
    // XÂY DỰNG BỐ CỤC GIAO DIỆN (UI LAYOUT)
    // =====================================================================

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(CLR_BG_MAIN);
        setContentPane(root);

        // 1. VÙNG PHÍA BẮC (Header: Quick Connect + Main Toolbar)
        JPanel northPanel = new JPanel(new BorderLayout(0, 0));
        northPanel.setBackground(CLR_BG_MAIN);
        northPanel.add(buildQuickConnectBar(), BorderLayout.NORTH);
        northPanel.add(buildMainToolBar(), BorderLayout.SOUTH);
        root.add(northPanel, BorderLayout.NORTH);

        // 2. VÙNG TRUNG TÂM (Dual Pane Explorer: Local vs Remote)
        JPanel dualPaneContainer = buildDualExplorerPanel();

        // 3. KHUNG TERMINAL LOG (Dark Console)
        JPanel consolePanel = buildConsolePanel();

        // Split ngang: Trên là Dual Pane, dưới là Console Log
        mainVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, dualPaneContainer, consolePanel);
        mainVerticalSplit.setResizeWeight(0.72);
        mainVerticalSplit.setDividerSize(6);
        mainVerticalSplit.setContinuousLayout(true);
        mainVerticalSplit.setBorder(null);
        root.add(mainVerticalSplit, BorderLayout.CENTER);

        // 4. VÙNG PHÍA NAM (Status Bar & Progress Panel)
        root.add(buildBottomStatusBar(), BorderLayout.SOUTH);
    }

    /** Thanh đăng nhập nhanh (Quick Connect Bar) */
    private JPanel buildQuickConnectBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(CLR_CARD_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, CLR_BORDER),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        fields.setOpaque(false);

        fields.add(createHeaderLabel("Máy chủ:"));
        fields.add(hostField);
        fields.add(createHeaderLabel("Cổng:"));
        fields.add(portField);
        fields.add(createHeaderLabel("Tài khoản:"));
        fields.add(userField);
        fields.add(createHeaderLabel("Mật khẩu:"));
        passField.setPreferredSize(new Dimension(85, 26));
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        passField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CLR_BORDER, 1, true),
                new EmptyBorder(2, 6, 2, 6)
        ));
        fields.add(passField);

        connectBtn.setPreferredSize(new Dimension(95, 28));
        disconnectBtn.setPreferredSize(new Dimension(115, 28));
        fields.add(connectBtn);
        fields.add(disconnectBtn);

        sampleAccountsBox.setPreferredSize(new Dimension(145, 27));
        sampleAccountsBox.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sampleAccountsBox.setToolTipText("Chọn nhanh tài khoản thử nghiệm của nhóm");
        sampleAccountsBox.addActionListener(e -> applySampleAccount());
        fields.add(sampleAccountsBox);

        bar.add(fields, BorderLayout.CENTER);

        JPanel statusWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        statusWrapper.setOpaque(false);
        statusWrapper.add(statusBadge);
        bar.add(statusWrapper, BorderLayout.EAST);

        return bar;
    }

    /** Thanh công cụ chính (Main Toolbar) */
    private JPanel buildMainToolBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.setBackground(new Color(241, 245, 249));
        bar.setBorder(new MatteBorder(0, 0, 1, 0, CLR_BORDER));

        toolbarRefreshBtn.setPreferredSize(new Dimension(115, 27));
        toolbarUploadBtn.setPreferredSize(new Dimension(145, 27));
        toolbarDownloadBtn.setPreferredSize(new Dimension(145, 27));
        toolbarMkdirBtn.setPreferredSize(new Dimension(105, 27));
        toolbarDeleteBtn.setPreferredSize(new Dimension(75, 27));
        toolbarToggleLogBtn.setPreferredSize(new Dimension(125, 27));

        bar.add(toolbarRefreshBtn);
        bar.add(createSeparator());
        bar.add(toolbarUploadBtn);
        bar.add(toolbarDownloadBtn);
        bar.add(createSeparator());
        bar.add(toolbarMkdirBtn);
        bar.add(toolbarDeleteBtn);
        bar.add(createSeparator());
        bar.add(toolbarToggleLogBtn);

        return bar;
    }

    /** Bố cục 2 Bảng: Máy Cục Bộ (Trái) <--> Máy Chủ (Phải) */
    private JPanel buildDualExplorerPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 0));
        container.setBackground(CLR_BG_MAIN);
        container.setBorder(new EmptyBorder(6, 8, 4, 8));

        // Panel Cục Bộ (Local)
        JPanel leftPanel = buildLocalExplorerPanel();

        // Panel Nút Trung Tâm (Transfer Action Buttons)
        JPanel centerTransferPanel = new JPanel(new GridBagLayout());
        centerTransferPanel.setOpaque(false);
        centerTransferPanel.setBorder(new EmptyBorder(0, 4, 0, 4));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(6, 2, 6, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        centerUploadBtn.setPreferredSize(new Dimension(110, 30));
        centerDownloadBtn.setPreferredSize(new Dimension(110, 30));
        centerUploadBtn.setToolTipText("Tải lên các tệp đang chọn từ Máy cục bộ sang Máy chủ");
        centerDownloadBtn.setToolTipText("Tải xuống các tệp đang chọn từ Máy chủ về Máy cục bộ");
        centerTransferPanel.add(centerUploadBtn, gbc);
        centerTransferPanel.add(centerDownloadBtn, gbc);

        // Panel Máy Chủ (Remote)
        JPanel rightPanel = buildRemoteExplorerPanel();

        // Ghép SplitPane 2 bên
        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        horizontalSplit.setResizeWeight(0.5);
        horizontalSplit.setDividerSize(8);
        horizontalSplit.setContinuousLayout(true);
        horizontalSplit.setBorder(null);

        container.add(horizontalSplit, BorderLayout.CENTER);
        return container;
    }

    /** Trình duyệt tệp tin Cục bộ (Local Explorer) */
    private JPanel buildLocalExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(CLR_CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CLR_BORDER, 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));

        // Tiêu đề & Điều hướng Local
        JPanel headerPanel = new JPanel(new BorderLayout(4, 4));
        headerPanel.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel titleLabel = new JLabel("MÁY CỤC BỘ (LOCAL DISK)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(new Color(30, 41, 59));
        titleRow.add(titleLabel, BorderLayout.WEST);

        JPanel searchWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        searchWrapper.setOpaque(false);
        searchWrapper.add(new JLabel("Tìm kiếm:"));
        localSearchField.setToolTipText("Gõ để lọc nhanh tệp tin cục bộ");
        searchWrapper.add(localSearchField);
        titleRow.add(searchWrapper, BorderLayout.EAST);
        headerPanel.add(titleRow, BorderLayout.NORTH);

        JPanel pathRow = new JPanel(new BorderLayout(4, 0));
        pathRow.setOpaque(false);
        JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navBtns.setOpaque(false);
        localUpBtn.setPreferredSize(new Dimension(70, 24));
        localHomeBtn.setPreferredSize(new Dimension(65, 24));
        localRefreshBtn.setPreferredSize(new Dimension(65, 24));
        navBtns.add(localUpBtn);
        navBtns.add(localHomeBtn);
        navBtns.add(localRefreshBtn);
        pathRow.add(navBtns, BorderLayout.WEST);
        pathRow.add(localPathField, BorderLayout.CENTER);
        headerPanel.add(pathRow, BorderLayout.SOUTH);

        panel.add(headerPanel, BorderLayout.NORTH);

        // Bảng tệp Local
        setupTableAppearance(localTable);
        localTable.setModel(localTableModel);
        localTable.getColumnModel().getColumn(0).setPreferredWidth(230);
        localTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        localTable.getColumnModel().getColumn(2).setPreferredWidth(85);
        localTable.getColumnModel().getColumn(3).setPreferredWidth(125);
        localTable.getColumnModel().getColumn(0).setCellRenderer(new LocalNameRenderer());
        localTable.getColumnModel().getColumn(2).setCellRenderer(new SizeRenderer());
        localTable.setComponentPopupMenu(buildLocalPopupMenu());

        JScrollPane scroll = new JScrollPane(localTable);
        scroll.setBorder(new LineBorder(CLR_BORDER, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        panel.add(scroll, BorderLayout.CENTER);

        // Tóm tắt Local
        localSummaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        localSummaryLabel.setForeground(new Color(100, 116, 139));
        panel.add(localSummaryLabel, BorderLayout.SOUTH);

        return panel;
    }

    /** Trình duyệt tệp tin Máy chủ (Remote Explorer) */
    private JPanel buildRemoteExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(CLR_CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CLR_BORDER, 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));

        // Tiêu đề & Điều hướng Remote
        JPanel headerPanel = new JPanel(new BorderLayout(4, 4));
        headerPanel.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel titleLabel = new JLabel("MÁY CHỦ FTP (REMOTE SERVER)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(CLR_PRIMARY);
        titleRow.add(titleLabel, BorderLayout.WEST);

        JPanel searchWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        searchWrapper.setOpaque(false);
        searchWrapper.add(new JLabel("Tìm kiếm:"));
        remoteSearchField.setToolTipText("Gõ để lọc nhanh tệp tin trên máy chủ");
        searchWrapper.add(remoteSearchField);
        titleRow.add(searchWrapper, BorderLayout.EAST);
        headerPanel.add(titleRow, BorderLayout.NORTH);

        JPanel pathRow = new JPanel(new BorderLayout(4, 0));
        pathRow.setOpaque(false);
        JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navBtns.setOpaque(false);
        remoteUpBtn.setPreferredSize(new Dimension(70, 24));
        remoteRootBtn.setPreferredSize(new Dimension(65, 24));
        remoteRefreshBtn.setPreferredSize(new Dimension(65, 24));
        navBtns.add(remoteUpBtn);
        navBtns.add(remoteRootBtn);
        navBtns.add(remoteRefreshBtn);
        pathRow.add(navBtns, BorderLayout.WEST);
        pathRow.add(remotePathField, BorderLayout.CENTER);
        headerPanel.add(pathRow, BorderLayout.SOUTH);

        panel.add(headerPanel, BorderLayout.NORTH);

        // Bảng tệp Remote
        setupTableAppearance(remoteTable);
        remoteTable.setModel(remoteTableModel);
        remoteTable.getColumnModel().getColumn(0).setPreferredWidth(230);
        remoteTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        remoteTable.getColumnModel().getColumn(2).setPreferredWidth(85);
        remoteTable.getColumnModel().getColumn(3).setPreferredWidth(125);
        remoteTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        remoteTable.getColumnModel().getColumn(0).setCellRenderer(new RemoteNameRenderer());
        remoteTable.getColumnModel().getColumn(2).setCellRenderer(new SizeRenderer());
        remoteTable.setTransferHandler(new UploadDropHandler());
        remoteTable.setComponentPopupMenu(buildRemotePopupMenu());

        JScrollPane scroll = new JScrollPane(remoteTable);
        scroll.setBorder(new LineBorder(CLR_BORDER, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        panel.add(scroll, BorderLayout.CENTER);

        // Tóm tắt Remote
        remoteSummaryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        remoteSummaryLabel.setForeground(new Color(100, 116, 139));
        panel.add(remoteSummaryLabel, BorderLayout.SOUTH);

        return panel;
    }

    /** Khung Log Terminal (Dark Console Style) */
    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(CLR_CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, CLR_BORDER),
                new EmptyBorder(4, 8, 4, 8)
        ));

        // Thanh tiêu đề Console
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JLabel title = new JLabel("NHẬT KÝ LỆNH CONTROL CHANNEL & TRẠNG THÁI GIAO THỨC");
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));
        title.setForeground(new Color(71, 85, 105));
        bar.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        consoleClearBtn.setPreferredSize(new Dimension(75, 23));
        actions.add(consoleClearBtn);
        bar.add(actions, BorderLayout.EAST);
        panel.add(bar, BorderLayout.NORTH);

        // Vùng chữ Terminal
        consolePane.setEditable(false);
        consolePane.setBackground(CLR_CONSOLE_BG);
        consolePane.setFont(new Font("Consolas", Font.PLAIN, 12));
        consolePane.setCaretColor(Color.WHITE);
        consolePane.setBorder(new EmptyBorder(4, 6, 4, 6));

        JScrollPane scroll = new JScrollPane(consolePane);
        scroll.setBorder(new LineBorder(new Color(51, 65, 85), 1));
        scroll.setPreferredSize(new Dimension(800, 130));
        panel.add(scroll, BorderLayout.CENTER);

        // Ô gõ lệnh thủ công
        JPanel inputBar = new JPanel(new BorderLayout(4, 0));
        inputBar.setOpaque(false);
        JLabel prompt = new JLabel(" FTP> ");
        prompt.setFont(new Font("Consolas", Font.BOLD, 12));
        prompt.setForeground(CLR_PRIMARY);
        inputBar.add(prompt, BorderLayout.WEST);
        consoleCmdField.setToolTipText("Nhập lệnh FTP thủ công (ví dụ: PWD, LIST, SIZE welcome.txt, NOOP, SYST, HELP)");
        inputBar.add(consoleCmdField, BorderLayout.CENTER);
        consoleSendBtn.setPreferredSize(new Dimension(85, 26));
        inputBar.add(consoleSendBtn, BorderLayout.EAST);
        panel.add(inputBar, BorderLayout.SOUTH);

        return panel;
    }

    /** Thanh Tiến trình & Trạng thái Đáy */
    private JPanel buildBottomStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(new Color(241, 245, 249));
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, CLR_BORDER),
                new EmptyBorder(4, 12, 4, 12)
        ));

        // Nhãn trạng thái bên trái
        statusTextLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bar.add(statusTextLabel, BorderLayout.WEST);

        // Tiến trình bên phải
        JPanel progressPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        progressPanel.setOpaque(false);

        transferDetailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        transferDetailLabel.setForeground(new Color(71, 85, 105));
        progressPanel.add(transferDetailLabel);

        progressBar.setPreferredSize(new Dimension(280, 20));
        progressBar.setStringPainted(true);
        progressBar.setString("Sẵn sàng");
        progressPanel.add(progressBar);

        cancelTransferBtn.setPreferredSize(new Dimension(75, 22));
        cancelTransferBtn.setEnabled(false);
        progressPanel.add(cancelTransferBtn);

        bar.add(progressPanel, BorderLayout.EAST);
        return bar;
    }

    // =====================================================================
    // CÁC HÀM XỬ LÝ SỰ KIỆN (EVENT WIRING)
    // =====================================================================

    private void wireEvents() {
        // Đăng nhập / Ngắt kết nối
        connectBtn.addActionListener(e -> connect());
        passField.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());

        // Toolbar
        toolbarRefreshBtn.addActionListener(e -> {
            loadLocalDirectory(currentLocalDir);
            refreshRemoteListing();
        });
        toolbarUploadBtn.addActionListener(e -> uploadSelectedLocalFiles());
        toolbarDownloadBtn.addActionListener(e -> downloadSelectedRemoteFiles());
        toolbarMkdirBtn.addActionListener(e -> makeRemoteDirectory());
        toolbarDeleteBtn.addActionListener(e -> deleteSelectedRemote());
        toolbarToggleLogBtn.addActionListener(e -> toggleConsoleLog());

        // Center Buttons
        centerUploadBtn.addActionListener(e -> uploadSelectedLocalFiles());
        centerDownloadBtn.addActionListener(e -> downloadSelectedRemoteFiles());

        // Local Explorer Events
        localUpBtn.addActionListener(e -> navigateLocalUp());
        localHomeBtn.addActionListener(e -> loadLocalDirectory(new File(System.getProperty("user.home"))));
        localRefreshBtn.addActionListener(e -> loadLocalDirectory(currentLocalDir));
        localPathField.addActionListener(e -> {
            File f = new File(localPathField.getText().trim());
            if (f.exists() && f.isDirectory()) loadLocalDirectory(f);
            else JOptionPane.showMessageDialog(this, "Đường dẫn thư mục cục bộ không tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        });
        localSearchField.getDocument().addDocumentListener(new SimpleDocumentListener(this::filterLocalTable));
        localTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelectedLocal();
                }
            }
        });

        // Remote Explorer Events
        remoteUpBtn.addActionListener(e -> navigateRemote(null));
        remoteRootBtn.addActionListener(e -> navigateRemote("/"));
        remoteRefreshBtn.addActionListener(e -> refreshRemoteListing());
        remotePathField.addActionListener(e -> navigateRemote(remotePathField.getText().trim()));
        remoteSearchField.getDocument().addDocumentListener(new SimpleDocumentListener(this::filterRemoteTable));
        remoteTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelectedRemote();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = remoteTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && !remoteTable.isRowSelected(row)) {
                        remoteTable.setRowSelectionInterval(row, row);
                    }
                }
            }
        });

        // Table Selection Updates
        localTable.getSelectionModel().addListSelectionListener(e -> updateUiState());
        remoteTable.getSelectionModel().addListSelectionListener(e -> updateUiState());

        // Console Events
        consoleClearBtn.addActionListener(e -> consolePane.setText(""));
        consoleSendBtn.addActionListener(e -> sendManualCommand());
        consoleCmdField.addActionListener(e -> sendManualCommand());
        consoleCmdField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP && !commandHistory.isEmpty()) {
                    if (commandHistoryIndex > 0) commandHistoryIndex--;
                    else commandHistoryIndex = commandHistory.size() - 1;
                    consoleCmdField.setText(commandHistory.get(commandHistoryIndex));
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN && !commandHistory.isEmpty()) {
                    if (commandHistoryIndex < commandHistory.size() - 1) {
                        commandHistoryIndex++;
                        consoleCmdField.setText(commandHistory.get(commandHistoryIndex));
                    } else {
                        commandHistoryIndex = commandHistory.size();
                        consoleCmdField.setText("");
                    }
                }
            }
        });

        // Cancel Transfer
        cancelTransferBtn.addActionListener(e -> {
            if (currentTransfer != null) {
                cancelTransferBtn.setEnabled(false);
                currentTransfer.requestCancel();
            }
        });

        // Phím tắt bàn phím F5, Delete
        getRootPane().registerKeyboardAction(e -> {
            loadLocalDirectory(currentLocalDir);
            if (controller.isLoggedIn()) refreshRemoteListing();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        remoteTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remoteDelete");
        remoteTable.getActionMap().put("remoteDelete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedRemote();
            }
        });
    }

    /** Cập nhật trạng thái enabled/disabled của các nút theo ngữ cảnh */
    private void updateUiState() {
        boolean connected = controller.isConnected();
        boolean loggedIn = controller.isLoggedIn();
        boolean transferring = currentTransfer != null;
        boolean ready = loggedIn && !transferring;

        boolean hasLocalSelection = localTable.getSelectedRowCount() > 0;
        boolean hasRemoteSelection = remoteTable.getSelectedRowCount() > 0;

        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        userField.setEnabled(!connected);
        passField.setEnabled(!connected);
        sampleAccountsBox.setEnabled(!connected);
        connectBtn.setEnabled(!connected);
        disconnectBtn.setEnabled(connected);

        // Remote Navigation
        remoteUpBtn.setEnabled(ready && !"/".equals(currentRemotePath));
        remoteRootBtn.setEnabled(ready && !"/".equals(currentRemotePath));
        remotePathField.setEnabled(ready);
        remoteRefreshBtn.setEnabled(ready);
        remoteSearchField.setEnabled(ready);

        // Toolbar & Center Action Buttons
        toolbarRefreshBtn.setEnabled(!transferring);
        toolbarUploadBtn.setEnabled(ready && hasLocalSelection);
        centerUploadBtn.setEnabled(ready && hasLocalSelection);
        toolbarDownloadBtn.setEnabled(ready && hasRemoteSelection);
        centerDownloadBtn.setEnabled(ready && hasRemoteSelection);
        toolbarMkdirBtn.setEnabled(ready);
        toolbarDeleteBtn.setEnabled(ready && hasRemoteSelection);

        // Console
        consoleCmdField.setEnabled(connected && !transferring);
        consoleSendBtn.setEnabled(connected && !transferring);
        cancelTransferBtn.setEnabled(transferring);

        // Status badge
        if (connected && loggedIn) {
            statusBadge.setStatus(ConnectionStatusBadge.State.CONNECTED, userField.getText().trim() + "@" + controller.getHost() + ":" + portField.getText().trim());
        } else if (connected) {
            statusBadge.setStatus(ConnectionStatusBadge.State.CONNECTING, "Đang xác thực...");
        } else {
            statusBadge.setStatus(ConnectionStatusBadge.State.DISCONNECTED, "Chưa kết nối");
        }

        setTitle("FTP Client Hai Kênh Hiện Đại - Nhóm 11" + (loggedIn
                ? "  [" + userField.getText().trim() + "@" + controller.getHost() + ":" + portField.getText().trim() + "  " + currentRemotePath + "]"
                : ""));
    }

    private void applySampleAccount() {
        int idx = sampleAccountsBox.getSelectedIndex();
        switch (idx) {
            case 1: userField.setText("vu"); passField.setText("123456"); break;
            case 2: userField.setText("tien"); passField.setText("123456"); break;
            case 3: userField.setText("trong"); passField.setText("123456"); break;
            case 4: userField.setText("admin"); passField.setText("admin123"); break;
            case 5: userField.setText("anonymous"); passField.setText(""); break;
        }
    }

    private void toggleConsoleLog() {
        consoleVisible = !consoleVisible;
        if (consoleVisible) {
            mainVerticalSplit.setDividerLocation(lastConsoleDividerLocation);
            mainVerticalSplit.getBottomComponent().setVisible(true);
        } else {
            lastConsoleDividerLocation = mainVerticalSplit.getDividerLocation();
            mainVerticalSplit.getBottomComponent().setVisible(false);
        }
    }

    // =====================================================================
    // QUẢN LÝ TỆP TIN CỤC BỘ (LOCAL FILE MANAGEMENT)
    // =====================================================================

    private void loadLocalDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        currentLocalDir = dir;
        localPathField.setText(dir.getAbsolutePath());

        File[] files = dir.listFiles();
        List<LocalFileEntry> list = new ArrayList<>();
        int dirsCount = 0;
        long totalBytes = 0;

        if (files != null) {
            for (File f : files) {
                if (f.isHidden()) continue;
                boolean isDir = f.isDirectory();
                long sz = isDir ? -1 : f.length();
                if (isDir) dirsCount++;
                else totalBytes += sz;
                list.add(new LocalFileEntry(f.getName(), isDir, sz, f.lastModified(), f));
            }
        }

        list.sort((a, b) -> {
            if (a.isDirectory != b.isDirectory) return a.isDirectory ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        localTableModel.setEntries(list);
        localSummaryLabel.setText(String.format("%d thư mục, %d tệp (%s)",
                dirsCount, list.size() - dirsCount, FtpFileEntry.formatSize(totalBytes)));
        localUpBtn.setEnabled(dir.getParentFile() != null);
    }

    private void navigateLocalUp() {
        File parent = currentLocalDir.getParentFile();
        if (parent != null) loadLocalDirectory(parent);
    }

    private void openSelectedLocal() {
        int row = localTable.getSelectedRow();
        if (row < 0) return;
        LocalFileEntry entry = localTableModel.getEntry(localTable.convertRowIndexToModel(row));
        if (entry.isDirectory) {
            loadLocalDirectory(entry.file);
        } else {
            if (controller.isLoggedIn()) {
                startUpload(java.util.Collections.singletonList(entry.file));
            } else {
                openFileWithSystemDefault(entry.file);
            }
        }
    }

    private void filterLocalTable() {
        String query = localSearchField.getText().trim().toLowerCase(Locale.ROOT);
        TableRowSorter<?> sorter = (TableRowSorter<?>) localTable.getRowSorter();
        if (query.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + PatternQuote(query), 0));
        }
    }

    private JPopupMenu buildLocalPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem up = new JMenuItem("Tải lên Server (Upload)");
        up.addActionListener(e -> uploadSelectedLocalFiles());
        JMenuItem open = new JMenuItem("Mở bằng ứng dụng hệ thống");
        open.addActionListener(e -> {
            int row = localTable.getSelectedRow();
            if (row >= 0) {
                LocalFileEntry entry = localTableModel.getEntry(localTable.convertRowIndexToModel(row));
                openFileWithSystemDefault(entry.file);
            }
        });
        JMenuItem refresh = new JMenuItem("Làm mới");
        refresh.addActionListener(e -> loadLocalDirectory(currentLocalDir));

        menu.add(up);
        menu.add(open);
        menu.addSeparator();
        menu.add(refresh);
        return menu;
    }

    private void openFileWithSystemDefault(File file) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở tệp: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =====================================================================
    // QUẢN LÝ MÁY CHỦ (REMOTE FILE MANAGEMENT)
    // =====================================================================

    private void connect() {
        if (controller.isConnected()) return;
        final String host = hostField.getText().trim();
        final String user = userField.getText().trim();
        final String pass = new String(passField.getPassword());
        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng Port không hợp lệ.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (host.isEmpty() || user.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Máy chủ và Tên tài khoản.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        connectBtn.setEnabled(false);
        statusBadge.setStatus(ConnectionStatusBadge.State.CONNECTING, "Đang kết nối...");
        setStatus("Đang kết nối tới " + host + ":" + port + " ...");

        runAsync("Đang kết nối tới Server...", () -> {
            controller.connect(host, port);
            try {
                controller.login(user, pass);
            } catch (Exception e) {
                controller.disconnect();
                throw e;
            }
            return loadRemoteListing();
        }, listing -> {
            applyRemoteListing(listing);
            setStatus("Đã đăng nhập thành công với tài khoản '" + user + "'.");
        });
    }

    private void disconnect() {
        if (currentTransfer != null) {
            int c = JOptionPane.showConfirmDialog(this, "Đang có tệp truyền dở dang. Bạn có chắc muốn hủy và ngắt kết nối?",
                    "Xác nhận ngắt kết nối", JOptionPane.YES_NO_OPTION);
            if (c != JOptionPane.YES_OPTION) return;
            currentTransfer.requestCancel();
        }
        runAsync("Đang ngắt kết nối...", () -> {
            controller.disconnect();
            return null;
        }, v -> onDisconnected());
    }

    private void onDisconnected() {
        remoteTableModel.setEntries(new ArrayList<>());
        currentRemotePath = "/";
        remotePathField.setText("/");
        remoteSummaryLabel.setText("Chưa kết nối.");
        progressBar.setValue(0);
        progressBar.setString("Sẵn sàng");
        transferDetailLabel.setText("");
        setStatus("Đã ngắt kết nối máy chủ.");
        updateUiState();
    }

    private static class Listing {
        final String path;
        final List<FtpFileEntry> entries;

        Listing(String path, List<FtpFileEntry> entries) {
            this.path = path;
            this.entries = entries;
        }
    }

    private Listing loadRemoteListing() throws Exception {
        String path = controller.pwd();
        List<FtpFileEntry> entries = controller.list();
        entries.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return new Listing(path, entries);
    }

    private void applyRemoteListing(Listing listing) {
        currentRemotePath = listing.path;
        remotePathField.setText(listing.path);
        remoteTableModel.setEntries(listing.entries);

        int dirs = 0;
        long bytes = 0;
        for (FtpFileEntry e : listing.entries) {
            if (e.isDirectory()) dirs++;
            else if (e.getSize() > 0) bytes += e.getSize();
        }
        remoteSummaryLabel.setText(String.format("%d thư mục, %d tệp (%s)",
                dirs, listing.entries.size() - dirs, FtpFileEntry.formatSize(bytes)));
        setStatus(String.format("Thư mục: %s (Tổng: %d mục)", listing.path, listing.entries.size()));
        updateUiState();
    }

    private void refreshRemoteListing() {
        if (!controller.isLoggedIn()) return;
        runAsync("Đang tải danh sách tệp Server (PASV + LIST)...", this::loadRemoteListing, this::applyRemoteListing);
    }

    private void navigateRemote(final String target) {
        if (!controller.isLoggedIn()) return;
        if (target != null && target.isEmpty()) return;
        runAsync(target == null ? "Đang lên thư mục cha..." : "Đang chuyển tới " + target + " ...", () -> {
            if (target == null) controller.cdup();
            else controller.cwd(target);
            return loadRemoteListing();
        }, this::applyRemoteListing);
    }

    private void openSelectedRemote() {
        List<FtpFileEntry> sel = getSelectedRemoteEntries();
        if (sel.size() != 1 || currentTransfer != null) return;
        FtpFileEntry entry = sel.get(0);
        if (entry.isDirectory()) {
            navigateRemote(entry.getName());
        } else {
            downloadSelectedRemoteFiles();
        }
    }

    private void filterRemoteTable() {
        String query = remoteSearchField.getText().trim().toLowerCase(Locale.ROOT);
        TableRowSorter<?> sorter = (TableRowSorter<?>) remoteTable.getRowSorter();
        if (query.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + PatternQuote(query), 0));
        }
    }

    private List<FtpFileEntry> getSelectedRemoteEntries() {
        List<FtpFileEntry> list = new ArrayList<>();
        for (int viewRow : remoteTable.getSelectedRows()) {
            list.add(remoteTableModel.getEntry(remoteTable.convertRowIndexToModel(viewRow)));
        }
        return list;
    }

    private void makeRemoteDirectory() {
        if (!controller.isLoggedIn()) return;
        String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới cần tạo trên Server:", "Tạo thư mục mới (MKD)",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        runAsync("Đang tạo thư mục " + name.trim() + " ...", () -> {
            controller.mkd(name.trim());
            return loadRemoteListing();
        }, this::applyRemoteListing);
    }

    private void deleteSelectedRemote() {
        final List<FtpFileEntry> sel = getSelectedRemoteEntries();
        if (sel.isEmpty() || !controller.isLoggedIn()) return;

        String what = sel.size() == 1 ? "'" + sel.get(0).getName() + "'" : sel.size() + " mục đã chọn";
        int c = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn xóa " + what + " trên Máy chủ FTP?\n(Lưu ý: Thư mục phải rỗng mới xóa được)",
                "Xác nhận xóa tệp tin Server", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c != JOptionPane.YES_OPTION) return;

        runAsync("Đang xóa tệp tin trên Server...", () -> {
            List<String> failed = new ArrayList<>();
            for (FtpFileEntry e : sel) {
                try {
                    if (e.isDirectory()) controller.rmd(e.getName());
                    else controller.dele(e.getName());
                } catch (FtpClientController.FtpException ex) {
                    failed.add(e.getName() + ": " + ex.getMessage());
                }
            }
            Listing l = loadRemoteListing();
            if (!failed.isEmpty()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        String.join("\n", failed), "Một số mục không thể xóa", JOptionPane.WARNING_MESSAGE));
            }
            return l;
        }, this::applyRemoteListing);
    }

    private JPopupMenu buildRemotePopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem dl = new JMenuItem("Tải xuống máy cục bộ (Download)");
        dl.addActionListener(e -> downloadSelectedRemoteFiles());
        JMenuItem open = new JMenuItem("Mở thư mục");
        open.addActionListener(e -> openSelectedRemote());
        JMenuItem size = new JMenuItem("Xem kích thước chính xác (SIZE)");
        size.addActionListener(e -> showRemoteSize());
        JMenuItem mkdir = new JMenuItem("Tạo thư mục mới tại đây");
        mkdir.addActionListener(e -> makeRemoteDirectory());
        JMenuItem del = new JMenuItem("Xóa mục đã chọn");
        del.addActionListener(e -> deleteSelectedRemote());
        JMenuItem refresh = new JMenuItem("Làm mới");
        refresh.addActionListener(e -> refreshRemoteListing());

        menu.add(dl);
        menu.add(open);
        menu.add(size);
        menu.addSeparator();
        menu.add(mkdir);
        menu.add(del);
        menu.addSeparator();
        menu.add(refresh);
        return menu;
    }

    private void showRemoteSize() {
        List<FtpFileEntry> sel = getSelectedRemoteEntries();
        if (sel.size() != 1 || sel.get(0).isDirectory()) return;
        final String name = sel.get(0).getName();
        runAsync("Đang truy vấn kích thước " + name + " ...", () -> controller.size(name), size ->
                setStatus("SIZE " + name + " = " + size + " bytes (" + FtpFileEntry.formatSize(size) + ")"));
    }

    // =====================================================================
    // TRUYỀN TẢI TỆP TIN (UPLOAD / DOWNLOAD LOGIC)
    // =====================================================================

    private void uploadSelectedLocalFiles() {
        if (!controller.isLoggedIn() || currentTransfer != null) return;
        int[] rows = localTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn ít nhất một tệp tin bên Máy cục bộ để tải lên!",
                    "Chưa chọn tệp", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<File> files = new ArrayList<>();
        for (int r : rows) {
            LocalFileEntry e = localTableModel.getEntry(localTable.convertRowIndexToModel(r));
            if (!e.isDirectory) files.add(e.file);
        }
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Hiện tại chỉ hỗ trợ tải lên từng tệp tin (không bao gồm cả thư mục).",
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        startUpload(files);
    }

    private void startUpload(List<File> files) {
        if (!controller.isLoggedIn() || currentTransfer != null) return;
        List<FileTransferTask.Job> jobs = new ArrayList<>();
        for (File f : files) {
            if (remoteTableModel.containsName(f.getName())) {
                int c = JOptionPane.showConfirmDialog(this,
                        "Tệp '" + f.getName() + "' đã tồn tại trên Server. Bạn có muốn ghi đè không?",
                        "Xác nhận ghi đè", JOptionPane.YES_NO_CANCEL_OPTION);
                if (c == JOptionPane.CANCEL_OPTION || c == JOptionPane.CLOSED_OPTION) return;
                if (c == JOptionPane.NO_OPTION) continue;
            }
            jobs.add(new FileTransferTask.Job(f, f.getName()));
        }
        if (!jobs.isEmpty()) {
            startTransfer(FileTransferTask.Direction.UPLOAD, jobs);
        }
    }

    private void downloadSelectedRemoteFiles() {
        if (!controller.isLoggedIn() || currentTransfer != null) return;
        List<FtpFileEntry> sel = getSelectedRemoteEntries();
        List<FtpFileEntry> files = new ArrayList<>();
        for (FtpFileEntry e : sel) {
            if (!e.isDirectory()) files.add(e);
        }
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn ít nhất một tệp tin (không phải thư mục) trên Server để tải xuống!",
                    "Chưa chọn tệp", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<FileTransferTask.Job> jobs = new ArrayList<>();
        for (FtpFileEntry e : files) {
            File target = new File(currentLocalDir, e.getName());
            if (target.exists()) {
                int c = JOptionPane.showConfirmDialog(this,
                        "Tệp '" + target.getName() + "' đã tồn tại tại máy cục bộ. Bạn có muốn ghi đè?",
                        "Xác nhận ghi đè", JOptionPane.YES_NO_CANCEL_OPTION);
                if (c == JOptionPane.CANCEL_OPTION || c == JOptionPane.CLOSED_OPTION) return;
                if (c == JOptionPane.NO_OPTION) continue;
            }
            jobs.add(new FileTransferTask.Job(target, e.getName()));
        }
        if (!jobs.isEmpty()) {
            startTransfer(FileTransferTask.Direction.DOWNLOAD, jobs);
        }
    }

    private void startTransfer(FileTransferTask.Direction direction, List<FileTransferTask.Job> jobs) {
        final String verb = direction == FileTransferTask.Direction.UPLOAD ? "Tải lên" : "Tải xuống";
        progressBar.setValue(0);
        progressBar.setString(verb + "...");
        cancelTransferBtn.setEnabled(true);
        setStatus(verb + " " + jobs.size() + " tệp tin...");

        currentTransfer = new FileTransferTask(controller, direction, jobs, new FileTransferTask.Callback() {
            @Override
            public void onProgress(FileTransferTask.Direction d, FileTransferTask.Progress p) {
                progressBar.setIndeterminate(p.bytesTotal <= 0 && p.percent == 0 && p.bytesDone > 0);
                progressBar.setValue(p.percent);

                String sizeText = p.bytesTotal > 0
                        ? FtpFileEntry.formatSize(p.bytesDone) + " / " + FtpFileEntry.formatSize(p.bytesTotal)
                        : FtpFileEntry.formatSize(p.bytesDone);

                String speed = p.bytesPerSecond > 0 ? " (" + FtpFileEntry.formatSize((long) p.bytesPerSecond) + "/s)" : "";
                progressBar.setString(p.percent + "%  " + sizeText + speed);

                transferDetailLabel.setText(String.format("[%d/%d] %s", p.fileIndex, p.fileCount, p.fileName));
                setStatus(verb + " '" + p.fileName + "'  " + p.percent + "%");
            }

            @Override
            public void onFinished(FileTransferTask.Direction d, int succeeded, int total,
                                   List<String> errors, boolean cancelled) {
                currentTransfer = null;
                progressBar.setIndeterminate(false);
                progressBar.setValue(cancelled ? 0 : 100);
                progressBar.setString(cancelled ? "Đã hủy" : "Hoàn tất (" + succeeded + "/" + total + ")");
                transferDetailLabel.setText("");
                cancelTransferBtn.setEnabled(false);

                loadLocalDirectory(currentLocalDir);
                if (controller.isLoggedIn()) refreshRemoteListing();

                if (cancelled) {
                    setStatus("Đã hủy truyền tải.");
                    appendLog("*** Quá trình truyền tải đã bị hủy bởi người dùng.");
                } else if (!errors.isEmpty()) {
                    setStatus("Truyền xong " + succeeded + "/" + total + " tệp (có lỗi).");
                    JOptionPane.showMessageDialog(ClientApp.this,
                            String.join("\n", errors), "Một số tệp bị lỗi khi truyền", JOptionPane.WARNING_MESSAGE);
                } else {
                    setStatus(verb + " hoàn tất thành công " + succeeded + " tệp.");
                }
                updateUiState();
            }
        });

        updateUiState();
        currentTransfer.execute();
    }

    // =====================================================================
    // GỬI LỆNH DÒNG LỆNH THỦ CÔNG (COMMAND CONSOLE)
    // =====================================================================

    private void sendManualCommand() {
        final String cmd = consoleCmdField.getText().trim();
        if (cmd.isEmpty() || !controller.isConnected()) return;

        commandHistory.add(cmd);
        commandHistoryIndex = commandHistory.size();
        consoleCmdField.setText("");

        String upper = cmd.toUpperCase(Locale.ROOT);
        String verb = upper.contains(" ") ? upper.substring(0, upper.indexOf(' ')) : upper;

        final boolean changesListing = verb.equals("CWD") || verb.equals("CDUP") || verb.equals("MKD")
                || verb.equals("RMD") || verb.equals("DELE") || verb.equals("XCUP") || verb.equals("XCWD")
                || verb.equals("XMKD") || verb.equals("XRMD");

        runAsync("Đang gửi lệnh: " + verb + " ...", () -> {
            FtpResponse r = controller.sendCommand(cmd);
            Listing l = (changesListing && r.isSuccess() && controller.isLoggedIn()) ? loadRemoteListing() : null;
            return new Object[]{r, l};
        }, result -> {
            FtpResponse r = (FtpResponse) result[0];
            setStatus("Phản hồi: " + r.getCode() + " " + r.getMessage());
            if (result[1] != null) applyRemoteListing((Listing) result[1]);
            if (!controller.isConnected()) onDisconnected();
        });
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = consolePane.getStyledDocument();
                String stamped = "[" + TIME_FMT.format(new Date()) + "] " + line + "\n";

                Style style = consolePane.addStyle("LogStyle", null);
                if (line.startsWith(">>")) {
                    StyleConstants.setForeground(style, new Color(56, 189, 248));   // Sky Blue lệnh gửi
                    StyleConstants.setBold(style, true);
                } else if (line.startsWith("<<")) {
                    if (line.contains(" 5") || line.contains(" 4")) {
                        StyleConstants.setForeground(style, new Color(248, 113, 113)); // Red lỗi
                        StyleConstants.setBold(style, true);
                    } else {
                        StyleConstants.setForeground(style, new Color(74, 222, 128)); // Bright Green thành công
                    }
                } else if (line.startsWith("***")) {
                    StyleConstants.setForeground(style, new Color(251, 191, 36));   // Yellow info
                } else if (line.startsWith("!!!")) {
                    StyleConstants.setForeground(style, new Color(244, 63, 94));    // Rose error
                    StyleConstants.setBold(style, true);
                } else {
                    StyleConstants.setForeground(style, new Color(226, 232, 240));  // White text
                }

                doc.insertString(doc.getLength(), stamped, style);
                consolePane.setCaretPosition(doc.getLength());
            } catch (Exception ignored) {
            }
        });
    }

    private void setStatus(String text) {
        statusTextLabel.setText(text);
    }

    private <T> void runAsync(String status, Callable<T> work, Consumer<T> onSuccess) {
        setStatus(status);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        background.submit(() -> {
            try {
                T result = work.call();
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    if (onSuccess != null) onSuccess.accept(result);
                    updateUiState();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    handleError(ex);
                });
            }
        });
    }

    private void handleError(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
        appendLog("!!! Lỗi ngoại lệ: " + msg);
        setStatus("Lỗi: " + msg);
        if (!controller.isConnected()) onDisconnected();
        updateUiState();
        JOptionPane.showMessageDialog(this, msg, "Lỗi FTP", JOptionPane.ERROR_MESSAGE);
    }

    private void exitApp() {
        if (currentTransfer != null) {
            int c = JOptionPane.showConfirmDialog(this, "Đang truyền tải tệp tin. Bạn có chắc muốn hủy và thoát?",
                    "Xác nhận thoát", JOptionPane.YES_NO_OPTION);
            if (c != JOptionPane.YES_OPTION) return;
            currentTransfer.requestCancel();
        }
        Thread t = new Thread(controller::disconnect);
        t.setDaemon(true);
        t.start();
        try {
            t.join(1500);
        } catch (InterruptedException ignored) {
        }
        dispose();
        System.exit(0);
    }

    // =====================================================================
    // UI COMPONENTS FACTORIES & CUSTOM BUTTON
    // =====================================================================

    /**
     * Nút bấm hiện đại tự vẽ:
     * - Hoàn toàn không phụ thuộc Native Windows Button UI bị lỗi mất chữ / đen / trắng.
     * - Chữ luôn căn giữa, tương phản rõ ràng, đổi màu khi hover / click / disabled.
     */
    public static class ModernButton extends JButton {
        private final Color normalBg;
        private final Color normalFg;

        public ModernButton(String text, Color bg, Color fg) {
            super(text);
            this.normalBg = bg;
            this.normalFg = fg != null ? fg : new Color(30, 41, 59);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(2, 6, 2, 6));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (normalBg != null) {
                if (!isEnabled()) {
                    g2.setColor(new Color(226, 232, 240));
                    g2.fillRoundRect(0, 0, w, h, 6, 6);
                } else if (getModel().isPressed()) {
                    g2.setColor(normalBg.darker());
                    g2.fillRoundRect(0, 0, w, h, 6, 6);
                } else if (getModel().isRollover()) {
                    g2.setColor(blendColor(normalBg, Color.WHITE, 0.15f));
                    g2.fillRoundRect(0, 0, w, h, 6, 6);
                } else {
                    g2.setColor(normalBg);
                    g2.fillRoundRect(0, 0, w, h, 6, 6);
                }
            } else {
                // Nút outline chuẩn
                if (getModel().isRollover() && isEnabled()) {
                    g2.setColor(new Color(241, 245, 249));
                    g2.fillRoundRect(0, 0, w, h, 6, 6);
                } else {
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(0, 0, w, h, 6, 6);
                }
                g2.setColor(isEnabled() ? new Color(203, 213, 225) : new Color(226, 232, 240));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
            }

            // Vẽ chữ căn giữa hoàn hảo
            FontMetrics fm = g2.getFontMetrics(getFont());
            String text = getText();
            int tw = fm.stringWidth(text);
            int th = fm.getAscent();
            int x = (w - tw) / 2;
            int y = (h + th - fm.getDescent()) / 2;

            if (!isEnabled()) {
                g2.setColor(new Color(148, 163, 184));
            } else {
                g2.setColor(normalFg);
            }
            g2.drawString(text, x, y);
            g2.dispose();
        }

        private static Color blendColor(Color c1, Color c2, float ratio) {
            float r = 1 - ratio;
            return new Color(
                    Math.min(255, (int) (c1.getRed() * r + c2.getRed() * ratio)),
                    Math.min(255, (int) (c1.getGreen() * r + c2.getGreen() * ratio)),
                    Math.min(255, (int) (c1.getBlue() * r + c2.getBlue() * ratio))
            );
        }
    }

    private static JTextField createStyledTextField(String text, int cols) {
        JTextField f = new JTextField(text, cols);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CLR_BORDER, 1, true),
                new EmptyBorder(3, 6, 3, 6)
        ));
        return f;
    }

    private static JLabel createHeaderLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(51, 65, 85));
        return l;
    }

    private static JSeparator createSeparator() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 20));
        s.setForeground(CLR_BORDER);
        return s;
    }

    private void setupTableAppearance(JTable table) {
        table.setRowHeight(25);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(CLR_SELECTION);
        table.setSelectionForeground(new Color(15, 23, 42));
        table.setFillsViewportHeight(true);

        JTableHeader th = table.getTableHeader();
        th.setFont(new Font("Segoe UI", Font.BOLD, 11));
        th.setBackground(CLR_HEADER_BG);
        th.setForeground(new Color(71, 85, 105));
        th.setBorder(new MatteBorder(0, 0, 1, 0, CLR_BORDER));

        TableRowSorter<?> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);
    }

    private static String PatternQuote(String s) {
        return java.util.regex.Pattern.quote(s);
    }

    // =====================================================================
    // VECTOR ICONS (VẼ THƯ MỤC VÀ TỆP TIN BẰNG VECTOR GRAPHICS)
    // =====================================================================

    /** Icon Thư mục màu vàng cam - không bao giờ bị ô vuông [] */
    private static class FolderVectorIcon implements Icon {
        private final int width;
        private final int height;

        FolderVectorIcon(int w, int h) {
            this.width = w;
            this.height = h;
        }

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Nắp folder tab
            g2.setColor(new Color(217, 119, 6)); // Amber dark
            g2.fillRoundRect(x + 1, y + 2, 7, 5, 2, 2);

            // Thân folder
            g2.setColor(new Color(245, 158, 11)); // Amber vibrant
            g2.fillRoundRect(x + 1, y + 4, width - 2, height - 5, 3, 3);

            // Viền nhẹ
            g2.setColor(new Color(180, 83, 9));
            g2.drawRoundRect(x + 1, y + 4, width - 3, height - 6, 3, 3);
            g2.dispose();
        }
    }

    /** Icon Tệp tin màu xanh xám - không bao giờ bị ô vuông [] */
    private static class FileVectorIcon implements Icon {
        private final int width;
        private final int height;

        FileVectorIcon(int w, int h) {
            this.width = w;
            this.height = h;
        }

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int px = x + 2;
            int py = y + 1;
            int pw = width - 4;
            int ph = height - 2;

            // Thân giấy
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(px, py, pw, ph, 2, 2);

            // Viền tệp
            g2.setColor(new Color(148, 163, 184));
            g2.drawRoundRect(px, py, pw, ph, 2, 2);

            // Các dòng chữ mô phỏng
            g2.setColor(new Color(203, 213, 225));
            g2.drawLine(px + 3, py + 4, px + pw - 3, py + 4);
            g2.drawLine(px + 3, py + 7, px + pw - 3, py + 7);
            g2.drawLine(px + 3, py + 10, px + pw - 5, py + 10);
            g2.dispose();
        }
    }

    // =====================================================================
    // TABLE MODELS & RENDERERS
    // =====================================================================

    public static class LocalFileEntry {
        public final String name;
        public final boolean isDirectory;
        public final long size;
        public final long modified;
        public final File file;

        public LocalFileEntry(String name, boolean isDirectory, long size, long modified, File file) {
            this.name = name;
            this.isDirectory = isDirectory;
            this.size = size;
            this.modified = modified;
            this.file = file;
        }
    }

    private static class LocalFileTableModel extends AbstractTableModel {
        private final String[] cols = {"Tên tệp", "Loại", "Kích thước", "Ngày sửa đổi"};
        private List<LocalFileEntry> entries = new ArrayList<>();

        void setEntries(List<LocalFileEntry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        LocalFileEntry getEntry(int row) {
            return entries.get(row);
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 2 ? Long.class : String.class; }

        @Override
        public Object getValueAt(int row, int col) {
            LocalFileEntry e = entries.get(row);
            switch (col) {
                case 0: return e.name;
                case 1: return e.isDirectory ? "Thư mục" : "Tệp";
                case 2: return e.isDirectory ? -1L : e.size;
                case 3: return DATE_FMT.format(new Date(e.modified));
                default: return "";
            }
        }
    }

    private static class RemoteFileTableModel extends AbstractTableModel {
        private final String[] cols = {"Tên tệp", "Loại", "Kích thước", "Ngày sửa đổi", "Quyền"};
        private List<FtpFileEntry> entries = new ArrayList<>();

        void setEntries(List<FtpFileEntry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        FtpFileEntry getEntry(int row) {
            return entries.get(row);
        }

        boolean containsName(String name) {
            for (FtpFileEntry e : entries) {
                if (e.getName().equals(name)) return true;
            }
            return false;
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 2 ? Long.class : String.class; }

        @Override
        public Object getValueAt(int row, int col) {
            FtpFileEntry e = entries.get(row);
            switch (col) {
                case 0: return e.getName();
                case 1: return e.isDirectory() ? "Thư mục" : "Tệp";
                case 2: return e.isDirectory() ? -1L : e.getSize();
                case 3: return e.getModified();
                case 4: return e.getPermissions();
                default: return "";
            }
        }
    }

    private class LocalNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int mRow = table.convertRowIndexToModel(row);
            LocalFileEntry e = localTableModel.getEntry(mRow);
            setIcon(e.isDirectory ? FOLDER_ICON : FILE_ICON);
            setText(e.name);
            setFont(new Font("Segoe UI", e.isDirectory ? Font.BOLD : Font.PLAIN, 12));
            if (!isSelected) {
                setBackground(row % 2 == 0 ? Color.WHITE : CLR_ZEBRA_ROW);
            }
            return this;
        }
    }

    private class RemoteNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int mRow = table.convertRowIndexToModel(row);
            FtpFileEntry e = remoteTableModel.getEntry(mRow);
            setIcon(e.isDirectory() ? FOLDER_ICON : FILE_ICON);
            setText(e.getName());
            setFont(new Font("Segoe UI", e.isDirectory() ? Font.BOLD : Font.PLAIN, 12));
            if (!isSelected) {
                setBackground(row % 2 == 0 ? Color.WHITE : CLR_ZEBRA_ROW);
            }
            return this;
        }
    }

    private static class SizeRenderer extends DefaultTableCellRenderer {
        SizeRenderer() {
            setHorizontalAlignment(RIGHT);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            long v = value instanceof Long ? (Long) value : -1;
            setText(v < 0 ? "" : FtpFileEntry.formatSize(v));
            setToolTipText(v < 0 ? null : v + " bytes");
            if (!isSelected) {
                setBackground(row % 2 == 0 ? Color.WHITE : CLR_ZEBRA_ROW);
            }
            return this;
        }
    }

    private class UploadDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return controller.isLoggedIn() && currentTransfer == null
                    && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                SwingUtilities.invokeLater(() -> startUpload(files));
                return true;
            } catch (Exception e) {
                appendLog("!!! Kéo thả thất bại: " + e.getMessage());
                return false;
            }
        }
    }

    /** Đèn LED trạng thái kết nối - tự động tính chiều rộng tránh mất chữ */
    private static class ConnectionStatusBadge extends JPanel {
        enum State { DISCONNECTED, CONNECTING, CONNECTED }
        private State state = State.DISCONNECTED;
        private String text = "Chưa kết nối";

        ConnectionStatusBadge() {
            setOpaque(false);
        }

        void setStatus(State s, String msg) {
            this.state = s;
            this.text = msg;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(new Font("Segoe UI", Font.BOLD, 11));
            int w = 24 + (text != null ? fm.stringWidth(text) : 0) + 12;
            return new Dimension(Math.max(w, 130), 26);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color dotColor = state == State.CONNECTED ? CLR_SUCCESS
                    : (state == State.CONNECTING ? CLR_WARNING : CLR_DANGER);

            // Chấm LED
            g2.setColor(dotColor);
            g2.fillOval(4, 8, 10, 10);

            // Viền mờ LED
            g2.setColor(new Color(dotColor.getRed(), dotColor.getGreen(), dotColor.getBlue(), 60));
            g2.drawOval(2, 6, 13, 13);

            // Nhãn chữ
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(new Color(51, 65, 85));
            g2.drawString(text, 22, 17);
            g2.dispose();
        }
    }

    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable onChange;
        SimpleDocumentListener(Runnable onChange) { this.onChange = onChange; }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
    }

    // =====================================================================
    // MAIN ENTRY POINT
    // =====================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientApp app = new ClientApp();
            if (args.length >= 1) app.hostField.setText(args[0]);
            if (args.length >= 2) app.portField.setText(args[1]);
            app.setVisible(true);
        });
    }
}
