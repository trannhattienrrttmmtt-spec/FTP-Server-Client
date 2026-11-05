package datachannel;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class DataChannelHelper {
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    public static final int LARGE_BUFFER_SIZE   = 65536;
    public static final int DEFAULT_TIMEOUT_MS  = 30000;

    public static PassiveModeInfo parsePassiveAddress(String pasvResponse) {
        return PassiveModeInfo.parse(pasvResponse);
    }

    public static Socket openDataSocket(String host, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();

        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        socket.setReceiveBufferSize(LARGE_BUFFER_SIZE);
        socket.setSendBufferSize(LARGE_BUFFER_SIZE);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        return socket;
    }

    //

    public static void validateFileForUpload(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Doi tuong File khong duoc null.");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("Tep tin khong ton tai tren dia: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IOException("Duong dan khong phai la tep tin (co the la thu muc): " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new SecurityException("Khong co quyen doc tep tin (Permission Denied): " + file.getAbsolutePath());
        }
    }

    public static void validateDirectoryForDownload(File destFile) throws IOException {
        if (destFile == null) {
            throw new IllegalArgumentException("Doi tuong DestFile khong duoc null.");
        }
        File parentDir = destFile.getParentFile();
        if (parentDir != null) {
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Khong the tao thu muc cha cho file: " + parentDir.getAbsolutePath());
            }
            if (!parentDir.canWrite()) {
                throw new SecurityException("Khong co quyen ghi vao thu muc (Permission Denied): " + parentDir.getAbsolutePath());
            }
        }
        if (destFile.exists() && !destFile.canWrite()) {
            throw new SecurityException("Tep dich da ton tai va chi co quyen doc (Read-only): " + destFile.getAbsolutePath());
        }
    }

    public static DataTransferStats uploadFile(Socket dataSocket, File localFile, int bufferSize,
                                              DataTransferListener listener) throws Exception {
        validateFileForUpload(localFile);
        long fileSize = localFile.length();
        String fileName = localFile.getName();
        DataTransferStats stats = new DataTransferStats(fileName, "UPLOAD");

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("He thong khong ho tro thuat toan SHA-256", e);
        }

        if (listener != null) {
            listener.onTransferStart(fileName, fileSize);
        }

        long startTime = System.currentTimeMillis();
        long totalBytesSent = 0;
        int bufSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        byte[] buffer = new byte[bufSize];

        try (FileInputStream fis = new FileInputStream(localFile);
             BufferedInputStream bis = new BufferedInputStream(fis, bufSize);
             OutputStream rawOut = dataSocket.getOutputStream();
             DigestOutputStream dos = new DigestOutputStream(rawOut, md);
             BufferedOutputStream bos = new BufferedOutputStream(dos, bufSize)) {

            int bytesRead;
            long lastProgressUpdate = startTime;

            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;

                long now = System.currentTimeMillis();

                if (listener != null && (now - lastProgressUpdate >= 50 || totalBytesSent == fileSize)) {
                    int percent = (fileSize > 0) ? (int) ((totalBytesSent * 100) / fileSize) : -1;
                    double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
                    double currentSpeedKBps = (totalBytesSent / elapsedSec) / 1024.0;
                    listener.onTransferProgress(totalBytesSent, fileSize, percent, currentSpeedKBps);
                    lastProgressUpdate = now;
                }
            }

            bos.flush();
            long elapsedTimeMs = System.currentTimeMillis() - startTime;
            String sha256 = bytesToHex(dos.getMessageDigest().digest());

            stats.recordSuccess(totalBytesSent, elapsedTimeMs, sha256);

            if (listener != null) {
                listener.onTransferComplete(totalBytesSent, elapsedTimeMs, stats.getSpeedKBps());
            }

            return stats;
        } catch (SocketException | SocketTimeoutException se) {
            stats.recordError("Loi socket mat ket noi giua chung: " + se.getMessage());
            if (listener != null) listener.onTransferError(se);
            throw se;
        } catch (Exception e) {
            stats.recordError("Loi khi upload: " + e.getMessage());
            if (listener != null) listener.onTransferError(e);
            throw e;
        } finally {
            closeQuietly(dataSocket);
        }
    }

    public static DataTransferStats downloadFile(Socket dataSocket, File destFile, long expectedSize,
                                                int bufferSize, DataTransferListener listener) throws Exception {
        validateDirectoryForDownload(destFile);
        String fileName = destFile.getName();
        DataTransferStats stats = new DataTransferStats(fileName, "DOWNLOAD");

        File tempFile = new File(destFile.getParentFile(), destFile.getName() + ".part");

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("He thong khong ho tro thuat toan SHA-256", e);
        }

        if (listener != null) {
            listener.onTransferStart(fileName, expectedSize);
        }

        long startTime = System.currentTimeMillis();
        long totalBytesReceived = 0;
        int bufSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        byte[] buffer = new byte[bufSize];

        try (InputStream rawIn = dataSocket.getInputStream();
             DigestInputStream dis = new DigestInputStream(rawIn, md);
             BufferedInputStream bis = new BufferedInputStream(dis, bufSize);
             FileOutputStream fos = new FileOutputStream(tempFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, bufSize)) {

            int bytesRead;
            long lastProgressUpdate = startTime;

            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesReceived += bytesRead;

                long now = System.currentTimeMillis();
                if (listener != null && (now - lastProgressUpdate >= 50 || (expectedSize > 0 && totalBytesReceived == expectedSize))) {
                    int percent = (expectedSize > 0) ? (int) ((totalBytesReceived * 100) / expectedSize) : -1;
                    double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
                    double currentSpeedKBps = (totalBytesReceived / elapsedSec) / 1024.0;
                    listener.onTransferProgress(totalBytesReceived, expectedSize, percent, currentSpeedKBps);
                    lastProgressUpdate = now;
                }
            }

            bos.flush();
            long elapsedTimeMs = System.currentTimeMillis() - startTime;
            String sha256 = bytesToHex(dis.getMessageDigest().digest());

            bos.close();
            fos.close();

            Files.move(tempFile.toPath(), destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            stats.recordSuccess(totalBytesReceived, elapsedTimeMs, sha256);

            if (listener != null) {
                listener.onTransferComplete(totalBytesReceived, elapsedTimeMs, stats.getSpeedKBps());
            }

            return stats;
        } catch (Exception e) {

            if (tempFile.exists()) {
                tempFile.delete();
            }
            stats.recordError("Loi truyen file giua chung: " + e.getMessage());
            if (listener != null) listener.onTransferError(e);
            throw e;
        } finally {
            closeQuietly(dataSocket);
        }
    }

    public static long transfer(InputStream in, OutputStream out, long totalSize, DataTransferListener listener) throws Exception {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long totalBytesRead = 0;
        int bytesRead;
        long startTime = System.currentTimeMillis();

        if (listener != null) {
            listener.onTransferStart("", totalSize);
        }

        try {
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (listener != null && totalSize > 0) {
                    int percent = (int) ((totalBytesRead * 100) / totalSize);
                    double elapsedSec = Math.max(0.001, (System.currentTimeMillis() - startTime) / 1000.0);
                    double speedKBps = (totalBytesRead / elapsedSec) / 1024.0;
                    listener.onTransferProgress(totalBytesRead, totalSize, percent, speedKBps);
                }
            }
            out.flush();
            if (listener != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                double avgSpeed = (totalBytesRead / Math.max(0.001, elapsed / 1000.0)) / 1024.0;
                listener.onTransferComplete(totalBytesRead, elapsed, avgSpeed);
            }
            return totalBytesRead;
        } catch (Exception e) {
            if (listener != null) {
                listener.onTransferError(e);
            }
            throw e;
        }
    }

    // =========================================================================

    // =========================================================================

    public static String calculateFileSHA256(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis, LARGE_BUFFER_SIZE)) {
                byte[] buffer = new byte[LARGE_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("He thong khong ho tro thuat toan SHA-256", e);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // =========================================================================

    // =========================================================================

    public static void createDummyFile(File file, long sizeBytes) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        byte[] pattern = new byte[DEFAULT_BUFFER_SIZE];
        Random rnd = new Random(42);
        rnd.nextBytes(pattern);

        long written = 0;
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos, LARGE_BUFFER_SIZE)) {
            while (written < sizeBytes) {
                int toWrite = (int) Math.min(pattern.length, sizeBytes - written);
                bos.write(pattern, 0, toWrite);
                written += toWrite;
            }
            bos.flush();
        }
    }

    // =========================================================================

    // =========================================================================

    public static void closeQuietly(AutoCloseable... closeables) {
        if (closeables == null) return;
        for (AutoCloseable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
