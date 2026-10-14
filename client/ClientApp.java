package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.File;
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
 * Giao diện đồ họa Java Swing cho FTP Client hai kênh.
 * - Khung đăng nhập (Host, Port, User, Pass)
 * - Thanh đường dẫn + nút Lên thư mục cha
 * - Bảng danh sách tệp tin (kết quả LIST)
 * - Các nút: Làm mới, Tải lên, Tải xuống, Xóa, Tạo thư mục
 * - Thanh tiến trình upload/download + nút Hủy
 * - Khung Log hiển thị lệnh gửi đi / phản hồi Server + ô nhập lệnh FTP thủ công
 *
 * Phụ trách: Nguyễn Anh Vũ (MSSV: 2380602582)
 */
public class ClientApp extends JFrame {

    private static final int KEEP_ALIVE_MS = 60_000; // Server ngắt phiên sau 120s không có lệnh

    private final FtpClientController controller = new FtpClientController();
    private final ExecutorService background = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ftp-client-bg");
        t.setDaemon(true);
        return t;
    });

    // --- Khung đăng nhập ---
    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField(String.valueOf(FtpClientController.DEFAULT_PORT), 5);
    private final JTextField userField = new JTextField("vu", 8);
    private final JPasswordField passField = new JPasswordField("123456", 8);
    private final JButton connectBtn = new JButton("Kết nối");
    private final JButton disconnectBtn = new JButton("Ngắt kết nối");

    // --- Thanh điều hướng & tác vụ ---
    private final JButton upBtn = new JButton("↑ Lên");
    private final JTextField pathField = new JTextField("/");
    private final JButton goBtn = new JButton("Đi tới");
    private final JButton refreshBtn = new JButton("Làm mới");
    private final JButton uploadBtn = new JButton("Tải lên");
    private final JButton downloadBtn = new JButton("Tải xuống");
    private final JButton deleteBtn = new JButton("Xóa");
    private final JButton mkdirBtn = new JButton("Tạo thư mục");

    // --- Bảng tệp ---
    private final FileTableModel tableModel = new FileTableModel();
    private final JTable fileTable = new JTable(tableModel);

    // --- Log & lệnh thủ công ---
    private final JTextArea logArea = new JTextArea();
    private final JTextField cmdField = new JTextField();
    private final JButton sendCmdBtn = new JButton("Gửi lệnh");

    // --- Tiến trình ---
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Chưa kết nối.");
    private final JButton cancelBtn = new JButton("Hủy");

    private String currentPath = "/";
    private FileTransferTask currentTransfer;
    private File lastLocalDir = new File(System.getProperty("user.home"));
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    public ClientApp() {
        super("FTP Client Hai Kênh - Nhóm 11");
        controller.setLogListener(this::appendLog);
        buildUi();
        wireEvents();
        updateUiState();

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
        setSize(980, 700);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
    }

    // =====================================================================
    // BỐ CỤC GIAO DIỆN
    // =====================================================================

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        // ---- Dòng 1: đăng nhập ----
        JPanel loginPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        loginPanel.setBorder(new TitledBorder("Kết nối Server (Control Channel)"));
        loginPanel.add(new JLabel("Host:"));
        loginPanel.add(hostField);
        loginPanel.add(new JLabel("Port:"));
        loginPanel.add(portField);
        loginPanel.add(new JLabel("User:"));
        loginPanel.add(userField);
        loginPanel.add(new JLabel("Pass:"));
        loginPanel.add(passField);
        loginPanel.add(connectBtn);
        loginPanel.add(disconnectBtn);

        // ---- Dòng 2: đường dẫn + nút tác vụ ----
        JPanel pathPanel = new JPanel(new BorderLayout(4, 0));
        pathPanel.add(upBtn, BorderLayout.WEST);
        JPanel pathCenter = new JPanel(new BorderLayout(4, 0));
        pathCenter.add(new JLabel(" Thư mục hiện tại: "), BorderLayout.WEST);
        pathCenter.add(pathField, BorderLayout.CENTER);
        pathPanel.add(pathCenter, BorderLayout.CENTER);
        pathPanel.add(goBtn, BorderLayout.EAST);

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(refreshBtn);
        toolBar.addSeparator();
        toolBar.add(uploadBtn);
        toolBar.add(downloadBtn);
        toolBar.addSeparator();
        toolBar.add(mkdirBtn);
        toolBar.add(deleteBtn);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(loginPanel, BorderLayout.NORTH);
        north.add(pathPanel, BorderLayout.CENTER);
        north.add(toolBar, BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);

        // ---- Bảng tệp ----
        fileTable.setAutoCreateRowSorter(false);
        TableRowSorter<FileTableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(0, Comparator.comparing((String s) -> s.toLowerCase(Locale.ROOT)));
        fileTable.setRowSorter(sorter);
        fileTable.setFillsViewportHeight(true);
        fileTable.setRowHeight(22);
        fileTable.setShowGrid(false);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(320);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(130);
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(0).setCellRenderer(new NameRenderer());
        fileTable.getColumnModel().getColumn(2).setCellRenderer(new SizeRenderer());
        fileTable.setTransferHandler(new UploadDropHandler());
        fileTable.setComponentPopupMenu(buildPopupMenu());

        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.setBorder(new TitledBorder("Danh sách tệp trên Server (LIST) - kéo thả tệp vào đây để tải lên"));

        // ---- Log + lệnh thủ công ----
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setLineWrap(false);
        JScrollPane logScroll = new JScrollPane(logArea);

        JPanel cmdPanel = new JPanel(new BorderLayout(4, 0));
        cmdPanel.add(new JLabel(" Lệnh FTP: "), BorderLayout.WEST);
        cmdPanel.add(cmdField, BorderLayout.CENTER);
        JPanel cmdButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton clearLogBtn = new JButton("Xóa log");
        clearLogBtn.addActionListener(e -> logArea.setText(""));
        cmdButtons.add(sendCmdBtn);
        cmdButtons.add(clearLogBtn);
        cmdPanel.add(cmdButtons, BorderLayout.EAST);
        cmdField.setToolTipText("Ví dụ: PWD, NOOP, SIZE welcome.txt, HELP, FEAT, SYST, LIST, MKD abc ...");

        JPanel logPanel = new JPanel(new BorderLayout(4, 4));
        logPanel.setBorder(new TitledBorder("Nhật ký Control Channel (>> gửi đi, << Server phản hồi)"));
        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.add(cmdPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logPanel);
        split.setResizeWeight(0.6);
        split.setDividerLocation(330);
        root.add(split, BorderLayout.CENTER);

        // ---- Thanh trạng thái / tiến trình ----
        progressBar.setStringPainted(true);
        progressBar.setString("Sẵn sàng");
        progressBar.setPreferredSize(new Dimension(420, 22));
        JPanel south = new JPanel(new BorderLayout(6, 0));
        south.add(statusLabel, BorderLayout.CENTER);
        JPanel progressPanel = new JPanel(new BorderLayout(4, 0));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(cancelBtn, BorderLayout.EAST);
        south.add(progressPanel, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
    }

    private JPopupMenu buildPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("Mở thư mục");
        open.addActionListener(e -> openSelected());
        JMenuItem dl = new JMenuItem("Tải xuống");
        dl.addActionListener(e -> startDownload());
        JMenuItem size = new JMenuItem("Xem kích thước (SIZE)");
        size.addActionListener(e -> showSize());
        JMenuItem del = new JMenuItem("Xóa");
        del.addActionListener(e -> deleteSelected());
        JMenuItem refresh = new JMenuItem("Làm mới");
        refresh.addActionListener(e -> refreshListing());
        menu.add(open);
        menu.add(dl);
        menu.add(size);
        menu.addSeparator();
        menu.add(del);
        menu.addSeparator();
        menu.add(refresh);
        return menu;
    }

    private void wireEvents() {
        connectBtn.addActionListener(e -> connect());
        passField.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());
        upBtn.addActionListener(e -> navigate(null));
        goBtn.addActionListener(e -> navigate(pathField.getText().trim()));
        pathField.addActionListener(e -> navigate(pathField.getText().trim()));
        refreshBtn.addActionListener(e -> refreshListing());
        uploadBtn.addActionListener(e -> chooseAndUpload());
        downloadBtn.addActionListener(e -> startDownload());
        deleteBtn.addActionListener(e -> deleteSelected());
        mkdirBtn.addActionListener(e -> makeDirectory());
        sendCmdBtn.addActionListener(e -> sendManualCommand());
        cmdField.addActionListener(e -> sendManualCommand());
        cancelBtn.addActionListener(e -> {
            if (currentTransfer != null) {
                cancelBtn.setEnabled(false);
                currentTransfer.requestCancel();
            }
        });

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelected();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Chuột phải chọn luôn dòng dưới con trỏ trước khi hiện menu
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = fileTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && !fileTable.isRowSelected(row)) {
                        fileTable.setRowSelectionInterval(row, row);
                    }
                }
            }
        });
        fileTable.getSelectionModel().addListSelectionListener(e -> updateUiState());
        fileTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "ftpDelete");
        fileTable.getActionMap().put("ftpDelete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelected();
            }
        });
        fileTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ftpOpen");
        fileTable.getActionMap().put("ftpOpen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelected();
            }
        });
        fileTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "ftpUp");
        fileTable.getActionMap().put("ftpUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (upBtn.isEnabled()) navigate(null);
            }
        });
        getRootPane().registerKeyboardAction(e -> {
            if (refreshBtn.isEnabled()) refreshListing();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Bật/tắt các điều khiển theo trạng thái kết nối và truyền tải. */
    private void updateUiState() {
        boolean connected = controller.isConnected();
        boolean loggedIn = controller.isLoggedIn();
        boolean transferring = currentTransfer != null;
        boolean ready = loggedIn && !transferring;
        boolean hasSelection = fileTable.getSelectedRowCount() > 0;

        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        userField.setEnabled(!connected);
        passField.setEnabled(!connected);
        connectBtn.setEnabled(!connected);
        disconnectBtn.setEnabled(connected);

        upBtn.setEnabled(ready && !"/".equals(currentPath));
        pathField.setEnabled(ready);
        goBtn.setEnabled(ready);
        refreshBtn.setEnabled(ready);
        uploadBtn.setEnabled(ready);
        mkdirBtn.setEnabled(ready);
        downloadBtn.setEnabled(ready && hasSelection);
        deleteBtn.setEnabled(ready && hasSelection);
        cmdField.setEnabled(connected && !transferring);
        sendCmdBtn.setEnabled(connected && !transferring);
        cancelBtn.setEnabled(transferring);
        fileTable.setEnabled(loggedIn);

        setTitle("FTP Client Hai Kênh - Nhóm 11" + (loggedIn
                ? "  [" + userField.getText().trim() + "@" + controller.getHost() + ":" + portField.getText().trim()
                + "  " + currentPath + "]" : ""));
    }

    // =====================================================================
    // TIỆN ÍCH CHẠY NỀN
    // =====================================================================

    /**
     * Chạy một tác vụ mạng trên luồng nền, sau đó trả kết quả về EDT.
     * Lỗi được ghi log và hiển thị hộp thoại.
     */
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
        appendLog("!!! Lỗi: " + msg);
        setStatus("Lỗi: " + msg);
        if (!controller.isConnected()) {
            onDisconnected();
        }
        updateUiState();
        JOptionPane.showMessageDialog(this, msg, "Lỗi FTP", JOptionPane.ERROR_MESSAGE);
    }

    private void appendLog(String line) {
        String stamped = "[" + timeFmt.format(new Date()) + "] " + line + "\n";
        if (SwingUtilities.isEventDispatchThread()) {
            logArea.append(stamped);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        } else {
            SwingUtilities.invokeLater(() -> {
                logArea.append(stamped);
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    // =====================================================================
    // XỬ LÝ CÁC THAO TÁC NGƯỜI DÙNG
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
            JOptionPane.showMessageDialog(this, "Port không hợp lệ.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (host.isEmpty() || user.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Host và User.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        connectBtn.setEnabled(false);
        runAsync("Đang kết nối tới " + host + ":" + port + " ...", () -> {
            controller.connect(host, port);
            try {
                controller.login(user, pass);
            } catch (Exception e) {
                controller.disconnect();
                throw e;
            }
            return loadListing();
        }, listing -> {
            applyListing(listing);
            setStatus("Đã đăng nhập với tài khoản '" + user + "'.");
        });
    }

    private void disconnect() {
        if (currentTransfer != null) {
            int c = JOptionPane.showConfirmDialog(this, "Đang truyền tệp. Hủy và ngắt kết nối?",
                    "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (c != JOptionPane.YES_OPTION) return;
            currentTransfer.requestCancel();
        }
        runAsync("Đang ngắt kết nối...", () -> {
            controller.disconnect();
            return null;
        }, v -> onDisconnected());
    }

    private void onDisconnected() {
        tableModel.setEntries(new ArrayList<>());
        currentPath = "/";
        pathField.setText("/");
        progressBar.setValue(0);
        progressBar.setString("Sẵn sàng");
        setStatus("Đã ngắt kết nối.");
        updateUiState();
    }

    /** Kết quả của PWD + LIST. */
    private static class Listing {
        final String path;
        final List<FtpFileEntry> entries;

        Listing(String path, List<FtpFileEntry> entries) {
            this.path = path;
            this.entries = entries;
        }
    }

    /** Chạy trên luồng nền: lấy thư mục hiện tại và danh sách tệp. */
    private Listing loadListing() throws Exception {
        String path = controller.pwd();
        List<FtpFileEntry> entries = controller.list();
        entries.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return new Listing(path, entries);
    }

    private void applyListing(Listing listing) {
        currentPath = listing.path;
        pathField.setText(listing.path);
        tableModel.setEntries(listing.entries);
        int dirs = 0;
        long bytes = 0;
        for (FtpFileEntry e : listing.entries) {
            if (e.isDirectory()) dirs++;
            else if (e.getSize() > 0) bytes += e.getSize();
        }
        setStatus(String.format("%s  —  %d thư mục, %d tệp (%s)", listing.path, dirs,
                listing.entries.size() - dirs, FtpFileEntry.formatSize(bytes)));
        updateUiState();
    }

    private void refreshListing() {
        if (!controller.isLoggedIn()) return;
        runAsync("Đang tải danh sách tệp (PASV + LIST)...", this::loadListing, this::applyListing);
    }

    /** @param target null = lên thư mục cha (CDUP), ngược lại CWD tới target. */
    private void navigate(final String target) {
        if (!controller.isLoggedIn()) return;
        if (target != null && target.isEmpty()) return;
        runAsync(target == null ? "Đang lên thư mục cha..." : "Đang chuyển tới " + target + " ...", () -> {
            if (target == null) controller.cdup();
            else controller.cwd(target);
            return loadListing();
        }, this::applyListing);
    }

    private List<FtpFileEntry> getSelectedEntries() {
        List<FtpFileEntry> list = new ArrayList<>();
        for (int viewRow : fileTable.getSelectedRows()) {
            list.add(tableModel.getEntry(fileTable.convertRowIndexToModel(viewRow)));
        }
        return list;
    }

    private void openSelected() {
        List<FtpFileEntry> sel = getSelectedEntries();
        if (sel.size() != 1 || currentTransfer != null) return;
        FtpFileEntry entry = sel.get(0);
        if (entry.isDirectory()) navigate(entry.getName());
        else startDownload();
    }

    private void showSize() {
        List<FtpFileEntry> sel = getSelectedEntries();
        if (sel.size() != 1 || sel.get(0).isDirectory()) return;
        final String name = sel.get(0).getName();
        runAsync("Đang hỏi kích thước " + name + " ...", () -> controller.size(name), size ->
                setStatus("SIZE " + name + " = " + size + " bytes (" + FtpFileEntry.formatSize(size) + ")"));
    }

    private void makeDirectory() {
        final String name = JOptionPane.showInputDialog(this, "Tên thư mục mới:", "Tạo thư mục (MKD)",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        runAsync("Đang tạo thư mục " + name.trim() + " ...", () -> {
            controller.mkd(name.trim());
            return loadListing();
        }, this::applyListing);
    }

    private void deleteSelected() {
        final List<FtpFileEntry> sel = getSelectedEntries();
        if (sel.isEmpty() || !deleteBtn.isEnabled()) return;
        String what = sel.size() == 1 ? "'" + sel.get(0).getName() + "'" : sel.size() + " mục đã chọn";
        int c = JOptionPane.showConfirmDialog(this, "Xóa " + what + " trên Server?\n(Thư mục phải rỗng mới xóa được)",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c != JOptionPane.YES_OPTION) return;
        runAsync("Đang xóa...", () -> {
            List<String> failed = new ArrayList<>();
            for (FtpFileEntry e : sel) {
                try {
                    if (e.isDirectory()) controller.rmd(e.getName());
                    else controller.dele(e.getName());
                } catch (FtpClientController.FtpException ex) {
                    failed.add(e.getName() + ": " + ex.getMessage());
                }
            }
            Listing l = loadListing();
            if (!failed.isEmpty()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        String.join("\n", failed), "Một số mục không xóa được", JOptionPane.WARNING_MESSAGE));
            }
            return l;
        }, this::applyListing);
    }

    // ---------------------------------------------------------------------
    // Upload / Download
    // ---------------------------------------------------------------------

    private void chooseAndUpload() {
        JFileChooser chooser = new JFileChooser(lastLocalDir);
        chooser.setDialogTitle("Chọn tệp để tải lên Server");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File[] files = chooser.getSelectedFiles();
        if (files.length == 0 && chooser.getSelectedFile() != null) files = new File[]{chooser.getSelectedFile()};
        lastLocalDir = chooser.getCurrentDirectory();
        startUpload(java.util.Arrays.asList(files));
    }

    private void startUpload(List<File> files) {
        if (!controller.isLoggedIn() || currentTransfer != null) return;
        List<FileTransferTask.Job> jobs = new ArrayList<>();
        List<String> skippedDirs = new ArrayList<>();
        for (File f : files) {
            if (f.isDirectory()) {
                skippedDirs.add(f.getName());
                continue;
            }
            if (!f.isFile()) continue;
            if (tableModel.containsName(f.getName())) {
                int c = JOptionPane.showConfirmDialog(this, "Tệp '" + f.getName()
                        + "' đã tồn tại trên Server. Ghi đè?", "Ghi đè?", JOptionPane.YES_NO_CANCEL_OPTION);
                if (c == JOptionPane.CANCEL_OPTION || c == JOptionPane.CLOSED_OPTION) return;
                if (c == JOptionPane.NO_OPTION) continue;
            }
            jobs.add(new FileTransferTask.Job(f, f.getName()));
        }
        if (!skippedDirs.isEmpty()) {
            appendLog("*** Bỏ qua thư mục (chỉ hỗ trợ tải lên tệp): " + String.join(", ", skippedDirs));
        }
        if (!jobs.isEmpty()) startTransfer(FileTransferTask.Direction.UPLOAD, jobs);
    }

    private void startDownload() {
        if (!controller.isLoggedIn() || currentTransfer != null) return;
        List<FtpFileEntry> files = new ArrayList<>();
        for (FtpFileEntry e : getSelectedEntries()) {
            if (!e.isDirectory()) files.add(e);
        }
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Hãy chọn ít nhất một tệp (không phải thư mục) để tải xuống.",
                    "Tải xuống", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<FileTransferTask.Job> jobs = new ArrayList<>();
        if (files.size() == 1) {
            JFileChooser chooser = new JFileChooser(lastLocalDir);
            chooser.setDialogTitle("Lưu tệp tải xuống");
            chooser.setSelectedFile(new File(lastLocalDir, files.get(0).getName()));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File target = chooser.getSelectedFile();
            lastLocalDir = target.getParentFile();
            if (target.exists() && JOptionPane.showConfirmDialog(this, "Tệp '" + target.getName()
                    + "' đã tồn tại. Ghi đè?", "Ghi đè?", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            jobs.add(new FileTransferTask.Job(target, files.get(0).getName()));
        } else {
            JFileChooser chooser = new JFileChooser(lastLocalDir);
            chooser.setDialogTitle("Chọn thư mục lưu " + files.size() + " tệp");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File dir = chooser.getSelectedFile();
            lastLocalDir = dir;
            for (FtpFileEntry e : files) {
                jobs.add(new FileTransferTask.Job(new File(dir, e.getName()), e.getName()));
            }
        }
        startTransfer(FileTransferTask.Direction.DOWNLOAD, jobs);
    }

    private void startTransfer(FileTransferTask.Direction direction, List<FileTransferTask.Job> jobs) {
        final String verb = direction == FileTransferTask.Direction.UPLOAD ? "Tải lên" : "Tải xuống";
        progressBar.setValue(0);
        progressBar.setString(verb + "...");
        setStatus(verb + " " + jobs.size() + " tệp...");

        currentTransfer = new FileTransferTask(controller, direction, jobs, new FileTransferTask.Callback() {
            @Override
            public void onProgress(FileTransferTask.Direction d, FileTransferTask.Progress p) {
                progressBar.setIndeterminate(p.bytesTotal <= 0 && p.percent == 0 && p.bytesDone > 0);
                progressBar.setValue(p.percent);
                String sizeText = p.bytesTotal > 0
                        ? FtpFileEntry.formatSize(p.bytesDone) + " / " + FtpFileEntry.formatSize(p.bytesTotal)
                        : FtpFileEntry.formatSize(p.bytesDone);
                progressBar.setString(p.percent + "%  " + sizeText
                        + (p.bytesPerSecond > 0 ? "  (" + FtpFileEntry.formatSize((long) p.bytesPerSecond) + "/s)" : ""));
                setStatus(verb + " '" + p.fileName + "'"
                        + (p.fileCount > 1 ? "  [" + p.fileIndex + "/" + p.fileCount + "]" : ""));
            }

            @Override
            public void onFinished(FileTransferTask.Direction d, int succeeded, int total,
                                   List<String> errors, boolean cancelled) {
                currentTransfer = null;
                progressBar.setIndeterminate(false);
                String summary;
                if (cancelled) {
                    summary = verb + ": đã hủy (" + succeeded + "/" + total + " tệp hoàn tất).";
                    progressBar.setString("Đã hủy");
                } else if (errors.isEmpty()) {
                    summary = verb + " thành công " + succeeded + "/" + total + " tệp.";
                    progressBar.setValue(100);
                    progressBar.setString("Hoàn tất 100%");
                } else {
                    summary = verb + ": " + succeeded + "/" + total + " tệp thành công, " + errors.size() + " lỗi.";
                    progressBar.setString("Có lỗi");
                }
                appendLog("*** " + summary);
                for (String err : errors) appendLog("!!! " + err);
                setStatus(summary);
                updateUiState();

                if (!controller.isConnected()) {
                    onDisconnected();
                    JOptionPane.showMessageDialog(ClientApp.this, "Mất kết nối tới Server.\n"
                            + String.join("\n", errors), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!errors.isEmpty() && !cancelled) {
                    JOptionPane.showMessageDialog(ClientApp.this, String.join("\n", errors),
                            verb + " - lỗi", JOptionPane.WARNING_MESSAGE);
                }
                if (d == FileTransferTask.Direction.UPLOAD) refreshListing();
            }
        });
        updateUiState();
        currentTransfer.execute();
    }

    // ---------------------------------------------------------------------
    // Lệnh FTP gõ tay
    // ---------------------------------------------------------------------

    private void sendManualCommand() {
        final String cmd = cmdField.getText().trim();
        if (cmd.isEmpty() || !controller.isConnected()) return;
        cmdField.setText("");
        int sp = cmd.indexOf(' ');
        final String verb = (sp < 0 ? cmd : cmd.substring(0, sp)).toUpperCase(Locale.ROOT);
        final String arg = sp < 0 ? "" : cmd.substring(sp + 1).trim();

        switch (verb) {
            case "USER":
            case "PASS":
                appendLog("*** Hãy dùng khung đăng nhập phía trên để xác thực.");
                return;
            case "PASV":
            case "PORT":
                appendLog("*** Client tự động gửi PASV và mở Data Channel khi cần (LIST/RETR/STOR).");
                return;
            case "QUIT":
                disconnect();
                return;
            case "LIST":
            case "NLST":
                runAsync("Đang thực hiện " + verb + " ...", () -> controller.listRaw(cmd), text -> {
                    appendLog("----- Dữ liệu nhận qua Data Channel -----");
                    for (String line : text.split("\r?\n")) {
                        if (!line.isEmpty()) appendLog("   " + line);
                    }
                    appendLog("-----------------------------------------");
                    setStatus(verb + " hoàn tất.");
                });
                return;
            case "RETR":
                if (!arg.isEmpty()) selectByName(arg);
                startDownload();
                return;
            case "STOR":
                chooseAndUpload();
                return;
            default:
                break;
        }

        final boolean changesListing = verb.equals("CWD") || verb.equals("CDUP") || verb.equals("MKD")
                || verb.equals("RMD") || verb.equals("DELE") || verb.equals("XCUP") || verb.equals("XCWD")
                || verb.equals("XMKD") || verb.equals("XRMD");
        runAsync("Đang gửi lệnh " + verb + " ...", () -> {
            FtpResponse r = controller.sendCommand(cmd);
            Listing l = (changesListing && r.isSuccess() && controller.isLoggedIn()) ? loadListing() : null;
            return new Object[]{r, l};
        }, result -> {
            FtpResponse r = (FtpResponse) result[0];
            setStatus("Phản hồi: " + r.getCode() + " " + r.getMessage());
            if (result[1] != null) applyListing((Listing) result[1]);
            if (!controller.isConnected()) onDisconnected();
        });
    }

    private void selectByName(String name) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getEntry(i).getName().equals(name)) {
                int view = fileTable.convertRowIndexToView(i);
                fileTable.setRowSelectionInterval(view, view);
                return;
            }
        }
    }

    private void exitApp() {
        if (currentTransfer != null) {
            int c = JOptionPane.showConfirmDialog(this, "Đang truyền tệp. Hủy và thoát?", "Thoát",
                    JOptionPane.YES_NO_OPTION);
            if (c != JOptionPane.YES_OPTION) return;
            currentTransfer.requestCancel();
        }
        Thread t = new Thread(controller::disconnect);
        t.setDaemon(true);
        t.start();
        try {
            t.join(2000);
        } catch (InterruptedException ignored) {
        }
        dispose();
        System.exit(0);
    }

    // =====================================================================
    // MODEL & RENDERER CHO BẢNG
    // =====================================================================

    private static class FileTableModel extends AbstractTableModel {
        private final String[] columns = {"Tên", "Loại", "Kích thước", "Ngày sửa đổi", "Quyền"};
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

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Long.class : String.class;
        }

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

    private class NameRenderer extends DefaultTableCellRenderer {
        private final Icon dirIcon = UIManager.getIcon("FileView.directoryIcon");
        private final Icon fileIcon = UIManager.getIcon("FileView.fileIcon");

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            FtpFileEntry e = tableModel.getEntry(table.convertRowIndexToModel(row));
            setIcon(e.isDirectory() ? dirIcon : fileIcon);
            return this;
        }
    }

    private static class SizeRenderer extends DefaultTableCellRenderer {
        SizeRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            long v = value instanceof Long ? (Long) value : -1;
            setText(v < 0 ? "" : FtpFileEntry.formatSize(v));
            setToolTipText(v < 0 ? null : v + " bytes");
        }
    }

    /** Cho phép kéo thả tệp từ Windows Explorer vào bảng để tải lên. */
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

    // =====================================================================
    // MAIN
    // =====================================================================

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            ClientApp app = new ClientApp();
            if (args.length >= 1) app.hostField.setText(args[0]);
            if (args.length >= 2) app.portField.setText(args[1]);
            app.setVisible(true);
        });
    }
}
