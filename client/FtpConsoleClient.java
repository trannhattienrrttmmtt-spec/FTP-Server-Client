package client;

import datachannel.DataTransferListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * FTP Client chế độ dòng lệnh (console) - dùng chung {@link FtpClientController} với bản GUI.
 * Thông báo in ra không dấu để hiển thị đúng trên mọi cửa sổ CMD.
 *
 * Phụ trách: Nguyễn Anh Vũ (MSSV: 2380602582)
 */
public class FtpConsoleClient {

    private final FtpClientController ftp = new FtpClientController();
    private boolean verbose = true;

    public static void main(String[] args) throws Exception {
        new FtpConsoleClient().run(args);
    }

    private void run(String[] args) throws IOException {
        ftp.setLogListener(line -> {
            if (verbose) System.out.println("   " + line);
        });

        System.out.println("=================================================");
        System.out.println(" FTP CLIENT CONSOLE - HE THONG HAI KENH (NHOM 11)");
        System.out.println(" Go 'help' de xem danh sach lenh.");
        System.out.println("=================================================");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        if (args.length >= 1) {
            String host = args[0];
            int port = args.length >= 2 ? Integer.parseInt(args[1]) : FtpClientController.DEFAULT_PORT;
            doOpen(in, host, port, args.length >= 3 ? args[2] : null, args.length >= 4 ? args[3] : null);
        }

        while (true) {
            System.out.print("ftp> ");
            System.out.flush();
            String line = in.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            int sp = line.indexOf(' ');
            String cmd = (sp < 0 ? line : line.substring(0, sp)).toLowerCase(Locale.ROOT);
            String arg = sp < 0 ? "" : line.substring(sp + 1).trim();

            try {
                if (!execute(in, cmd, arg)) break;
            } catch (IOException e) {
                System.out.println("[LOI] " + e.getMessage());
            }
        }
        if (ftp.isConnected()) ftp.disconnect();
        System.out.println("Tam biet!");
    }

    /** @return false nếu người dùng muốn thoát chương trình */
    private boolean execute(BufferedReader in, String cmd, String arg) throws IOException {
        switch (cmd) {
            case "help":
            case "?":
                printHelp();
                return true;
            case "open": {
                String[] p = arg.split("\\s+");
                if (arg.isEmpty()) {
                    System.out.println("Cu phap: open <host> [port]");
                    return true;
                }
                int port = p.length >= 2 ? Integer.parseInt(p[1]) : FtpClientController.DEFAULT_PORT;
                doOpen(in, p[0], port, null, null);
                return true;
            }
            case "close":
            case "disconnect":
                ftp.disconnect();
                return true;
            case "quit":
            case "exit":
            case "bye":
                return false;
            case "verbose":
                verbose = !verbose;
                System.out.println("Hien thi log Control Channel: " + (verbose ? "BAT" : "TAT"));
                return true;
            default:
                break;
        }

        if (!ftp.isLoggedIn()) {
            System.out.println("Chua dang nhap. Dung: open <host> [port]");
            return true;
        }

        switch (cmd) {
            case "ls":
            case "dir":
            case "list":
                printListing(ftp.list());
                break;
            case "pwd":
                System.out.println("Thu muc hien tai: " + ftp.pwd());
                break;
            case "cd":
                if (arg.isEmpty()) arg = "/";
                if ("..".equals(arg)) ftp.cdup();
                else ftp.cwd(arg);
                System.out.println("Thu muc hien tai: " + ftp.pwd());
                break;
            case "cdup":
                ftp.cdup();
                System.out.println("Thu muc hien tai: " + ftp.pwd());
                break;
            case "get": {
                if (arg.isEmpty()) {
                    System.out.println("Cu phap: get <ten_file_tren_server> [duong_dan_luu]");
                    break;
                }
                String[] p = splitTwo(arg);
                File local = new File(p[1] != null ? p[1] : new File(p[0]).getName());
                if (local.isDirectory()) local = new File(local, new File(p[0]).getName());
                long n = ftp.download(p[0], local, new ConsoleProgress("Download " + p[0]));
                System.out.println("Da tai xuong " + n + " bytes -> " + local.getAbsolutePath());
                break;
            }
            case "put": {
                if (arg.isEmpty()) {
                    System.out.println("Cu phap: put <file_tren_may> [ten_tren_server]");
                    break;
                }
                String[] p = splitTwo(arg);
                File local = new File(p[0]);
                if (!local.isFile()) {
                    System.out.println("[LOI] Khong tim thay tep: " + local.getAbsolutePath());
                    break;
                }
                String remote = p[1] != null ? p[1] : local.getName();
                long n = ftp.upload(local, remote, new ConsoleProgress("Upload " + local.getName()));
                System.out.println("Da tai len " + n + " bytes -> " + remote);
                break;
            }
            case "del":
            case "delete":
            case "rm":
                ftp.dele(arg);
                System.out.println("Da xoa tep: " + arg);
                break;
            case "mkdir":
            case "md":
                ftp.mkd(arg);
                System.out.println("Da tao thu muc: " + arg);
                break;
            case "rmdir":
            case "rd":
                ftp.rmd(arg);
                System.out.println("Da xoa thu muc: " + arg);
                break;
            case "size": {
                long s = ftp.size(arg);
                System.out.println(s >= 0 ? arg + ": " + s + " bytes (" + FtpFileEntry.formatSize(s) + ")"
                        : "Khong lay duoc kich thuoc.");
                break;
            }
            case "quote":
            case "raw": {
                if (arg.isEmpty()) {
                    System.out.println("Cu phap: quote <LENH FTP>   vd: quote SYST");
                    break;
                }
                String verb = arg.split("\\s+")[0].toUpperCase(Locale.ROOT);
                if (verb.equals("LIST") || verb.equals("NLST")) {
                    System.out.print(ftp.listRaw(arg));
                } else {
                    FtpResponse r = ftp.sendCommand(arg);
                    if (!verbose) System.out.println(r.getFullText());
                }
                break;
            }
            default:
                System.out.println("Lenh khong hop le: '" + cmd + "'. Go 'help'.");
        }
        return true;
    }

    private void doOpen(BufferedReader in, String host, int port, String user, String pass) throws IOException {
        try {
            FtpResponse banner = ftp.connect(host, port);
            if (!verbose) System.out.println(banner.getFullText());
            if (user == null) {
                System.out.print("User: ");
                System.out.flush();
                user = in.readLine();
            }
            if (pass == null) {
                pass = readPassword(in);
            }
            ftp.login(user == null ? "" : user.trim(), pass == null ? "" : pass);
            System.out.println("Dang nhap thanh cong. Thu muc hien tai: " + ftp.pwd());
        } catch (IOException e) {
            System.out.println("[LOI] " + e.getMessage());
            if (ftp.isConnected()) ftp.disconnect();
        }
    }

    private String readPassword(BufferedReader in) throws IOException {
        if (System.console() != null) {
            char[] p = System.console().readPassword("Pass: ");
            return p == null ? "" : new String(p);
        }
        System.out.print("Pass: ");
        System.out.flush();
        return in.readLine();
    }

    private static String[] splitTwo(String arg) {
        // Hỗ trợ tên có khoảng trắng bằng dấu ngoặc kép: get "bao cao.docx" D:\bc.docx
        String first, rest;
        if (arg.startsWith("\"")) {
            int end = arg.indexOf('"', 1);
            if (end < 0) return new String[]{arg.substring(1), null};
            first = arg.substring(1, end);
            rest = arg.substring(end + 1).trim();
        } else {
            int sp = arg.indexOf(' ');
            first = sp < 0 ? arg : arg.substring(0, sp);
            rest = sp < 0 ? "" : arg.substring(sp + 1).trim();
        }
        if (rest.startsWith("\"") && rest.endsWith("\"") && rest.length() >= 2) {
            rest = rest.substring(1, rest.length() - 1);
        }
        return new String[]{first, rest.isEmpty() ? null : rest};
    }

    private static void printListing(List<FtpFileEntry> entries) {
        System.out.println(String.format("%-6s %12s  %-13s %s", "LOAI", "KICH THUOC", "NGAY SUA", "TEN"));
        System.out.println("------------------------------------------------------------");
        int dirs = 0;
        for (FtpFileEntry e : entries) {
            if (e.isDirectory()) dirs++;
            System.out.println(String.format("%-6s %12s  %-13s %s",
                    e.isDirectory() ? "<DIR>" : "", e.isDirectory() ? "" : FtpFileEntry.formatSize(e.getSize()),
                    e.getModified(), e.getName()));
        }
        System.out.println("------------------------------------------------------------");
        System.out.println(dirs + " thu muc, " + (entries.size() - dirs) + " tep.");
    }

    private static void printHelp() {
        System.out.println("Danh sach lenh:");
        System.out.println("  open <host> [port]        Ket noi va dang nhap (mac dinh port 2121)");
        System.out.println("  ls | dir                  Liet ke tep (PASV + LIST)");
        System.out.println("  pwd                       Xem thu muc hien tai");
        System.out.println("  cd <dir> | cd .. | cdup   Doi thu muc");
        System.out.println("  get <remote> [local]      Tai tep xuong (RETR) co thanh tien trinh");
        System.out.println("  put <local> [remote]      Tai tep len (STOR) co thanh tien trinh");
        System.out.println("  del <file>                Xoa tep (DELE)");
        System.out.println("  mkdir <dir> | rmdir <dir> Tao / xoa thu muc rong (MKD / RMD)");
        System.out.println("  size <file>               Xem kich thuoc tep (SIZE)");
        System.out.println("  quote <LENH>              Gui lenh FTP tho, vd: quote SYST, quote HELP");
        System.out.println("  verbose                   Bat/tat hien thi log Control Channel");
        System.out.println("  close                     Ngat ket noi (QUIT)");
        System.out.println("  quit | exit               Thoat chuong trinh");
        System.out.println("  * Ten co khoang trang: dat trong dau ngoac kep, vd: get \"bao cao.txt\"");
    }

    /** Thanh tiến trình dạng văn bản: [##########----------]  50%  1.2 MB / 2.4 MB  800 KB/s */
    private static class ConsoleProgress implements DataTransferListener {
        private final String label;
        private long start;
        private int lastPercent = -1;
        private long lastPrint;

        ConsoleProgress(String label) {
            this.label = label;
        }

        @Override
        public void onTransferStart(String fileName, long totalBytes) {
            start = System.currentTimeMillis();
            System.out.println(label + " (" + (totalBytes >= 0 ? FtpFileEntry.formatSize(totalBytes) : "?") + ")");
        }

        @Override
        public void onTransferProgress(long done, long total, int percent) {
            long now = System.currentTimeMillis();
            if (percent == lastPercent && now - lastPrint < 500) return;
            lastPercent = percent;
            lastPrint = now;
            int width = 30;
            int filled = Math.max(0, Math.min(width, percent * width / 100));
            StringBuilder bar = new StringBuilder("\r[");
            for (int i = 0; i < width; i++) bar.append(i < filled ? '#' : '-');
            double secs = Math.max(0.001, (now - start) / 1000.0);
            bar.append(String.format("] %3d%%  %s / %s  %s/s   ", percent, FtpFileEntry.formatSize(done),
                    FtpFileEntry.formatSize(total), FtpFileEntry.formatSize((long) (done / secs))));
            System.out.print(bar);
            System.out.flush();
        }

        @Override
        public void onTransferComplete(long totalBytes) {
            if (lastPercent < 100 && totalBytes > 0) onTransferProgress(totalBytes, totalBytes, 100);
            System.out.println();
        }

        @Override
        public void onTransferError(Exception ex) {
            System.out.println();
        }
    }
}
