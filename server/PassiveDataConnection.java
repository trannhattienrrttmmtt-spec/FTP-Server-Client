package server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Quản lý kênh dữ liệu Passive Mode (Data Channel) cho từng phiên làm việc của Client
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Thực hiện: Trần Nhật Tiến (MSSV: 2380602243)
 */
public class PassiveDataConnection implements Closeable {
    private ServerSocket serverSocket;
    private Socket dataSocket;
    private final InetAddress serverAddress;
    private final String clientTag;

    public PassiveDataConnection(InetAddress serverAddress, String clientTag) throws IOException {
        this.serverAddress = serverAddress;
        this.clientTag = clientTag;
        openPassiveServerSocket();
    }

    /**
     * Mở ServerSocket trên một cổng khả dụng trong khoảng cấu hình hoặc cổng ngẫu nhiên của OS
     */
    private void openPassiveServerSocket() throws IOException {
        int minPort = ServerConfig.PASSIVE_PORT_MIN;
        int maxPort = ServerConfig.PASSIVE_PORT_MAX;

        // Thử mở trong dải port cấu hình
        for (int port = minPort; port <= maxPort; port++) {
            try {
                serverSocket = new ServerSocket(port, 1, serverAddress);
                serverSocket.setSoTimeout(ServerConfig.DATA_TIMEOUT_MS);
                ServerLogger.info(clientTag, "Da mo Passive ServerSocket tai cong: " + port);
                return;
            } catch (IOException ignored) {
                // Thử cổng tiếp theo
            }
        }

        // Nếu tất cả cổng trong dải bận, để hệ điều hành tự chọn cổng tự do
        serverSocket = new ServerSocket(0, 1, serverAddress);
        serverSocket.setSoTimeout(ServerConfig.DATA_TIMEOUT_MS);
        ServerLogger.info(clientTag, "Da mo Passive ServerSocket tai cong ngau nhien: " + serverSocket.getLocalPort());
    }

    /**
     * Tạo chuỗi phản hồi 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
     */
    public String getPassiveResponseString() {
        if (serverSocket == null) {
            return "425 Khong the mo kenh du lieu.";
        }

        // Lấy địa chỉ IP
        InetAddress address = serverAddress;
        byte[] ip = address.getAddress();

        // Nếu là wildcard 0.0.0.0 hoặc loopback, lấy 127.0.0.1 cho an toàn khi test local
        int h1, h2, h3, h4;
        if (address.isAnyLocalAddress()) {
            h1 = 127; h2 = 0; h3 = 0; h4 = 1;
        } else {
            h1 = ip[0] & 0xFF;
            h2 = ip[1] & 0xFF;
            h3 = ip[2] & 0xFF;
            h4 = ip[3] & 0xFF;
        }

        int port = serverSocket.getLocalPort();
        int p1 = (port >> 8) & 0xFF;
        int p2 = port & 0xFF;

        return String.format("%d Entering Passive Mode (%d,%d,%d,%d,%d,%d).",
                FtpResponseCode.ENTERING_PASSIVE_MODE, h1, h2, h3, h4, p1, p2);
    }

    /**
     * Chấp nhận kết nối Data Socket từ Client
     */
    public Socket acceptConnection() throws IOException {
        if (serverSocket == null || serverSocket.isClosed()) {
            throw new IOException("Data ServerSocket chua duoc khoi tao hoac da dong.");
        }
        ServerLogger.info(clientTag, "Dang cho Client ket noi den Data Channel (Port " + serverSocket.getLocalPort() + ")...");
        try {
            dataSocket = serverSocket.accept();
            dataSocket.setSoTimeout(ServerConfig.DATA_TIMEOUT_MS);
            ServerLogger.info(clientTag, "Client da ket noi Data Channel thanh cong tu " 
                    + dataSocket.getRemoteSocketAddress());
            return dataSocket;
        } catch (SocketTimeoutException e) {
            ServerLogger.warn(clientTag, "Timeout cho ket noi Data Channel.");
            throw e;
        }
    }

    /**
     * Gửi chuỗi danh sách tệp tin qua Data Socket (phục vụ lệnh LIST / NLST)
     */
    public void sendTextData(String textData) throws IOException {
        try (Socket socket = acceptConnection();
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), ServerConfig.CHARSET_NAME))) {
            writer.write(textData);
            writer.flush();
            ServerLogger.info(clientTag, "Da gui xong du lieu text qua Data Channel.");
        } finally {
            close();
        }
    }

    /**
     * Gửi nội dung tệp tin đến Client qua Data Socket (phục vụ lệnh RETR / Download)
     */
    public long sendFile(File file) throws IOException {
        long totalBytes = 0;
        try (Socket socket = acceptConnection();
             BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));
             BufferedOutputStream socketOut = new BufferedOutputStream(socket.getOutputStream())) {

            byte[] buffer = new byte[ServerConfig.BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                socketOut.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            socketOut.flush();
            ServerLogger.info(clientTag, String.format("Da truyen thanh cong tep '%s' (%d bytes).", 
                    file.getName(), totalBytes));
            return totalBytes;
        } finally {
            close();
        }
    }

    /**
     * Nhận nội dung tệp tin từ Client qua Data Socket và ghi vào ổ đĩa (phục vụ lệnh STOR / Upload)
     */
    public long receiveFile(File targetFile) throws IOException {
        long totalBytes = 0;
        try (Socket socket = acceptConnection();
             BufferedInputStream socketIn = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(targetFile))) {

            byte[] buffer = new byte[ServerConfig.BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = socketIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            fileOut.flush();
            ServerLogger.info(clientTag, String.format("Da nhan thanh cong tep '%s' (%d bytes) tu Client.", 
                    targetFile.getName(), totalBytes));
            return totalBytes;
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (dataSocket != null && !dataSocket.isClosed()) {
            try {
                dataSocket.close();
            } catch (IOException ignored) {}
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public boolean isOpen() {
        return serverSocket != null && !serverSocket.isClosed();
    }
}
