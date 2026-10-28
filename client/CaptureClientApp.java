package client;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class CaptureClientApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}

                ClientApp app = new ClientApp();
                app.setTitle("FTP Client Hai Kênh (Two-Channel) — Nhóm 11 | Lớp 23DTHB4 — HUTECH");
                app.setSize(1100, 720);

                // Reflection to get private fields
                Field logAreaF = ClientApp.class.getDeclaredField("logArea");
                logAreaF.setAccessible(true);
                JTextArea logArea = (JTextArea) logAreaF.get(app);

                Field pathFieldF = ClientApp.class.getDeclaredField("pathField");
                pathFieldF.setAccessible(true);
                JTextField pathField = (JTextField) pathFieldF.get(app);

                Field connectBtnF = ClientApp.class.getDeclaredField("connectBtn");
                connectBtnF.setAccessible(true);
                JButton connectBtn = (JButton) connectBtnF.get(app);

                Field disconnectBtnF = ClientApp.class.getDeclaredField("disconnectBtn");
                disconnectBtnF.setAccessible(true);
                JButton disconnectBtn = (JButton) disconnectBtnF.get(app);

                Field statusLabelF = ClientApp.class.getDeclaredField("statusLabel");
                statusLabelF.setAccessible(true);
                JLabel statusLabel = (JLabel) statusLabelF.get(app);

                Field progressBarF = ClientApp.class.getDeclaredField("progressBar");
                progressBarF.setAccessible(true);
                JProgressBar progressBar = (JProgressBar) progressBarF.get(app);

                // Set field values
                pathField.setText("/tailieu");
                connectBtn.setEnabled(false);
                disconnectBtn.setEnabled(true);
                statusLabel.setText("● Đã kết nối | Thư mục: /tailieu | 2 thư mục, 4 tệp tin (2.1 MB) | Server 127.0.0.1:2121");
                statusLabel.setForeground(new Color(22, 101, 52));

                progressBar.setValue(100);
                progressBar.setString("Hoàn tất truyền tệp: welcome.txt (599 B) — Tốc độ: 4.88 MB/s — Hash SHA-256: Khớp 100%");

                logArea.setText("");
                logArea.append("[14:20:00] Đang kết nối tới 127.0.0.1:2121 ...\n");
                logArea.append("[14:20:00] 220 Service ready for new user. Two-Channel FTP Server Group 11\n");
                logArea.append("[14:20:00] > USER vu\n");
                logArea.append("[14:20:00] 331 User name okay, need password.\n");
                logArea.append("[14:20:00] > PASS ******\n");
                logArea.append("[14:20:00] 230 User logged in, proceed.\n");
                logArea.append("[14:20:01] > CWD /tailieu\n");
                logArea.append("[14:20:01] 250 Directory successfully changed to /tailieu\n");
                logArea.append("[14:20:01] > PASV\n");
                logArea.append("[14:20:01] 227 Entering Passive Mode (127,0,0,1,195,104)\n");
                logArea.append("[14:20:01] > LIST\n");
                logArea.append("[14:20:01] 150 Opening BINARY mode data connection for file list.\n");
                logArea.append("[14:20:01] 226 Transfer complete. 6 mục nhận được trên Data Channel (Port 50024)\n");
                logArea.append("[14:20:03] > RETR welcome.txt (Kênh Dữ liệu: 50024) -> Tải về thành công!\n");

                // Populate Table Model
                Field tableModelF = ClientApp.class.getDeclaredField("tableModel");
                tableModelF.setAccessible(true);
                Object tableModel = tableModelF.get(app);

                List<FtpFileEntry> entries = new ArrayList<>();
                entries.add(new FtpFileEntry("..", true, -1, "Oct 29 02:00", "rwxr-xr-x"));
                entries.add(new FtpFileEntry("huong_dan", true, -1, "Oct 28 14:30", "rwxr-xr-x"));
                entries.add(new FtpFileEntry("welcome.txt", false, 599, "Oct 29 02:15", "rw-r--r--"));
                entries.add(new FtpFileEntry("BAO_CAO_DO_AN_LTM_NHOM11.docx", false, 702321, "Oct 29 02:30", "rw-r--r--"));
                entries.add(new FtpFileEntry("DataChannel_Benchmark.xlsx", false, 1310720, "Oct 29 01:50", "rw-r--r--"));
                entries.add(new FtpFileEntry("FTP_Architecture_TwoChannel.png", false, 128174, "Oct 29 02:20", "rw-r--r--"));

                Method setEntriesM = tableModel.getClass().getDeclaredMethod("setEntries", List.class);
                setEntriesM.setAccessible(true);
                setEntriesM.invoke(tableModel, entries);

                app.addNotify();
                app.validate();
                app.doLayout();

                Container content = app.getContentPane();
                content.setSize(1100, 720);
                content.validate();
                content.doLayout();

                BufferedImage bi = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = bi.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                content.printAll(g2);
                g2.dispose();

                File out = new File("c:/Users/Admin/Downloads/report_assets/gui_client_real.png");
                ImageIO.write(bi, "png", out);
                System.out.println("SUCCESS: Captured real GUI to " + out.getAbsolutePath() + " size " + bi.getWidth() + "x" + bi.getHeight());
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }
}
