package client;

import datachannel.DataChannelHelper;
import datachannel.DataTransferListener;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtpClientController {

    public interface LogListener {
        void onLog(String line);
    }

    public static final int DEFAULT_PORT = 2121;
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final Pattern PASV_PATTERN =
            Pattern.compile("\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)");
    private static final Pattern BYTES_PATTERN = Pattern.compile("\\((\\d+) bytes\\)");
    private static final Pattern QUOTED_PATH = Pattern.compile("\"([^\"]*)\"");

    private final ReentrantLock lock = new ReentrantLock();
    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String host;
    private boolean loggedIn = false;
    private volatile Socket activeDataSocket;
    private volatile boolean abortRequested = false;
    private LogListener logListener;

    public void setLogListener(LogListener listener) {
        this.logListener = listener;
    }

    private void log(String line) {
        if (logListener != null) logListener.onLog(line);
    }

    // =====================================================================

    // =====================================================================

    public FtpResponse connect(String host, int port) throws IOException {
        lock.lock();
        try {
            if (isConnected()) closeQuietly();
            this.host = host;
            log("*** Đang kết nối tới " + host + ":" + port + " ...");
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(READ_TIMEOUT_MS);
            controlSocket = s;
            reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            log("*** Đã kết nối Control Channel.");

            FtpResponse banner = readResponse();
            if (banner.getCode() != 220) {
                closeQuietly();
                throw new IOException("Server từ chối kết nối: " + banner.getFullText());
            }
            return banner;
        } finally {
            lock.unlock();
        }
    }

    public void login(String username, String password) throws IOException {
        lock.lock();
        try {
            FtpResponse r = sendCommand("USER " + username);
            if (r.getCode() == 331) {
                r = sendCommand("PASS " + password);
            }
            if (r.getCode() != 230) {
                throw new FtpException(r);
            }
            loggedIn = true;
            sendCommand("TYPE I");
        } finally {
            lock.unlock();
        }
    }

    public void disconnect() {
        lock.lock();
        try {
            if (isConnected()) {
                try {
                    sendCommand("QUIT");
                } catch (IOException ignored) {
                }
            }
            closeQuietly();
            log("*** Đã ngắt kết nối.");
        } finally {
            lock.unlock();
        }
    }

    public boolean isConnected() {
        return controlSocket != null && controlSocket.isConnected() && !controlSocket.isClosed();
    }

    public boolean isLoggedIn() {
        return isConnected() && loggedIn;
    }

    public String getHost() {
        return host;
    }

    // =====================================================================

    // =====================================================================

    public FtpResponse sendCommand(String command) throws IOException {
        lock.lock();
        try {
            ensureConnected();
            String shown = command.regionMatches(true, 0, "PASS ", 0, 5) ? "PASS ******" : command;
            log(">> " + shown);
            writer.write(command + "\r\n");
            writer.flush();
            return readResponse();
        } finally {
            lock.unlock();
        }
    }

    private FtpResponse readResponse() throws IOException {
        String line = readLineOrFail();
        log("<< " + line);
        StringBuilder full = new StringBuilder(line);

        if (line.length() >= 4 && line.charAt(3) == '-') {
            String endPrefix = line.substring(0, 3) + " ";
            while (true) {
                String next = readLineOrFail();
                log("<< " + next);
                full.append("\n").append(next);
                if (next.startsWith(endPrefix)) {
                    line = next;
                    break;
                }
            }
        }

        int code;
        try {
            code = Integer.parseInt(line.substring(0, 3));
        } catch (Exception e) {
            throw new IOException("Phản hồi không hợp lệ từ Server: " + line);
        }
        String msg = line.length() > 4 ? line.substring(4) : "";
        if (code == 221 || code == 421) {

            loggedIn = false;
        }
        return new FtpResponse(code, msg, full.toString());
    }

    private String readLineOrFail() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            closeQuietly();
            throw new IOException("Server đã đóng kết nối Control Channel.");
        }
        return line;
    }

    // =====================================================================

    // =====================================================================

    public String pwd() throws IOException {
        FtpResponse r = sendCommand("PWD");
        expect(r, 257);
        Matcher m = QUOTED_PATH.matcher(r.getMessage());
        return m.find() ? m.group(1) : r.getMessage();
    }

    public void cwd(String path) throws IOException {
        expect(sendCommand("CWD " + path), 250);
    }

    public void cdup() throws IOException {
        expect(sendCommand("CDUP"), 250);
    }

    public void mkd(String name) throws IOException {
        expect(sendCommand("MKD " + name), 257);
    }

    public void rmd(String name) throws IOException {
        expect(sendCommand("RMD " + name), 250);
    }

    public void dele(String name) throws IOException {
        expect(sendCommand("DELE " + name), 250);
    }

    public void noop() throws IOException {
        sendCommand("NOOP");
    }

    public long size(String name) throws IOException {
        FtpResponse r = sendCommand("SIZE " + name);
        if (r.getCode() == 213) {
            try {
                return Long.parseLong(r.getMessage().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    public void keepAliveIfIdle() {
        if (!isLoggedIn() || !lock.tryLock()) return;
        try {
            noop();
        } catch (IOException e) {
            log("*** Giữ kết nối thất bại: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    // =====================================================================
    // DATA CHANNEL (PASSIVE MODE)
    // =====================================================================

    private Socket openPassiveDataSocket() throws IOException {
        FtpResponse r = sendCommand("PASV");
        expect(r, 227);
        Matcher m = PASV_PATTERN.matcher(r.getMessage());
        if (!m.find()) {
            throw new IOException("Không phân tích được phản hồi PASV: " + r.getMessage());
        }
        String ip = m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4);
        int port = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));

        if ("0.0.0.0".equals(ip) || ("127.0.0.1".equals(ip) && !"127.0.0.1".equals(host) && !"localhost".equalsIgnoreCase(host))) {
            ip = host;
        }

        log("*** Mở Data Channel tới " + ip + ":" + port + " (port = "
                + m.group(5) + " * 256 + " + m.group(6) + ")");
        Socket data = new Socket();
        data.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
        data.setSoTimeout(READ_TIMEOUT_MS);
        return data;
    }

    public List<FtpFileEntry> list() throws IOException {
        String raw = listRaw("LIST");
        List<FtpFileEntry> entries = new ArrayList<>();
        for (String line : raw.split("\r?\n")) {
            FtpFileEntry e = FtpFileEntry.parse(line);
            if (e != null && !".".equals(e.getName()) && !"..".equals(e.getName())) {
                entries.add(e);
            }
        }
        return entries;
    }

    public String listRaw(String command) throws IOException {
        lock.lock();
        Socket data = null;
        try {
            ensureLoggedIn();
            data = openPassiveDataSocket();
            FtpResponse r = sendCommand(command);
            if (!r.isPreliminary()) throw new FtpException(r);

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataChannelHelper.transfer(data.getInputStream(), buf, -1, null);
            data.close();
            data = null;

            expect(readResponse(), 226);
            String text = buf.toString("UTF-8");
            log("*** Nhận " + buf.size() + " bytes danh sách qua Data Channel.");
            return text;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        } finally {
            closeSocket(data);
            lock.unlock();
        }
    }

    public long download(String remoteName, File localFile, DataTransferListener listener) throws IOException {
        lock.lock();
        Socket data = null;
        boolean ok = false;
        boolean fileCreated = false;
        abortRequested = false;
        try {
            ensureLoggedIn();
            long total = size(remoteName);
            data = openPassiveDataSocket();
            activeDataSocket = data;

            FtpResponse r = sendCommand("RETR " + remoteName);
            if (!r.isPreliminary()) throw new FtpException(r);
            if (total < 0) {
                Matcher m = BYTES_PATTERN.matcher(r.getMessage());
                if (m.find()) total = Long.parseLong(m.group(1));
            }

            long transferred = -1;
            IOException transferError = null;
            try (InputStream in = data.getInputStream();
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(localFile))) {
                fileCreated = true;
                transferred = DataChannelHelper.transfer(in, out, total, listener);
            } catch (Exception e) {
                transferError = wrapTransferError(e);
            } finally {
                closeSocket(data);
                activeDataSocket = null;
                data = null;
            }

            FtpResponse done = readResponse();
            if (transferError != null) throw transferError;
            expect(done, 226);
            ok = true;
            return transferred;
        } finally {
            closeSocket(data);
            activeDataSocket = null;
            if (!ok && fileCreated && localFile.exists()) {

                if (localFile.delete()) log("*** Đã xóa tệp tải dở: " + localFile.getName());
            }
            lock.unlock();
        }
    }

    public long upload(File localFile, String remoteName, DataTransferListener listener) throws IOException {
        lock.lock();
        Socket data = null;
        abortRequested = false;
        try {
            ensureLoggedIn();
            data = openPassiveDataSocket();
            activeDataSocket = data;

            FtpResponse r = sendCommand("STOR " + remoteName);
            if (!r.isPreliminary()) throw new FtpException(r);

            long transferred;
            IOException transferError = null;
            try (InputStream in = new BufferedInputStream(new FileInputStream(localFile))) {
                OutputStream out = data.getOutputStream();
                transferred = DataChannelHelper.transfer(in, out, localFile.length(), listener);
            } catch (Exception e) {
                transferError = wrapTransferError(e);
                transferred = -1;
            } finally {

                closeSocket(data);
                activeDataSocket = null;
                data = null;
            }

            FtpResponse done = readResponse();
            if (transferError != null) {

                try {
                    sendCommand("DELE " + remoteName);
                } catch (IOException ignored) {
                }
                throw transferError;
            }
            expect(done, 226);
            return transferred;
        } finally {
            closeSocket(data);
            activeDataSocket = null;
            lock.unlock();
        }
    }

    public void abortTransfer() {
        abortRequested = true;
        Socket s = activeDataSocket;
        if (s != null) {
            log("*** Người dùng yêu cầu hủy truyền tải...");
            closeSocket(s);
        }
    }

    private IOException wrapTransferError(Exception e) {
        if (abortRequested) return new IOException("Đã hủy truyền tải theo yêu cầu.", e);
        if (e instanceof IOException) return (IOException) e;
        return new IOException(e.getMessage(), e);
    }

    // =====================================================================

    // =====================================================================

    private void expect(FtpResponse r, int code) throws FtpException {
        if (r.getCode() != code) throw new FtpException(r);
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) throw new IOException("Chưa kết nối tới Server.");
    }

    private void ensureLoggedIn() throws IOException {
        ensureConnected();
        if (!loggedIn) throw new IOException("Chưa đăng nhập.");
    }

    private static void closeSocket(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void closeQuietly() {
        loggedIn = false;
        closeSocket(activeDataSocket);
        closeSocket(controlSocket);
        controlSocket = null;
        reader = null;
        writer = null;
    }

    public static class FtpException extends IOException {
        private final FtpResponse response;

        public FtpException(FtpResponse response) {
            super(response.getCode() + " " + response.getMessage());
            this.response = response;
        }

        public FtpResponse getResponse() {
            return response;
        }
    }
}
