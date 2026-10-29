package server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Lớp ghi log hoạt động của Server ra Console, tệp server.log và Giao diện UI
 * Đề tài: Hệ thống FTP Server/Client cơ bản với Hai kênh
 * Thực hiện: Trần Nhật Tiến (MSSV: 2380602243)
 */
public class ServerLogger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();
    private static PrintWriter fileWriter = null;
    private static volatile Consumer<String> uiLogListener = null;

    public static void setUiLogListener(Consumer<String> listener) {
        uiLogListener = listener;
    }

    static {
        try {
            File logFile = new File("server.log");
            fileWriter = new PrintWriter(new FileWriter(logFile, true), true);
        } catch (IOException e) {
            System.err.println("[WARN] Khong the khoi tao file ghi log: " + e.getMessage());
        }
    }

    public enum Level {
        INFO, WARN, ERROR, DEBUG, CLIENT_IN, CLIENT_OUT
    }

    public static void log(Level level, String clientTag, String message) {
        String timeStr = LocalDateTime.now().format(FORMATTER);
        String tag = (clientTag != null && !clientTag.isEmpty()) ? "[" + clientTag + "] " : "";
        String prefix;
        switch (level) {
            case CLIENT_IN:
                prefix = "<-- [RECV] ";
                break;
            case CLIENT_OUT:
                prefix = "--> [SEND] ";
                break;
            case WARN:
                prefix = "[WARN] ";
                break;
            case ERROR:
                prefix = "[ERROR] ";
                break;
            case DEBUG:
                prefix = "[DEBUG] ";
                break;
            case INFO:
            default:
                prefix = "[INFO] ";
                break;
        }

        String fullMessage = String.format("[%s] %s%s%s", timeStr, prefix, tag, message);

        synchronized (LOCK) {
            System.out.println(fullMessage);
            if (fileWriter != null) {
                fileWriter.println(fullMessage);
            }
        }
        Consumer<String> listener = uiLogListener;
        if (listener != null) {
            try {
                listener.accept(fullMessage);
            } catch (Exception ignored) {}
        }
    }

    public static void info(String message) {
        log(Level.INFO, null, message);
    }

    public static void info(String clientTag, String message) {
        log(Level.INFO, clientTag, message);
    }

    public static void warn(String message) {
        log(Level.WARN, null, message);
    }

    public static void warn(String clientTag, String message) {
        log(Level.WARN, clientTag, message);
    }

    public static void error(String message) {
        log(Level.ERROR, null, message);
    }

    public static void error(String clientTag, String message, Throwable t) {
        log(Level.ERROR, clientTag, message + (t != null ? " (" + t.getMessage() + ")" : ""));
        if (t != null) {
            synchronized (LOCK) {
                t.printStackTrace(System.err);
                if (fileWriter != null) {
                    t.printStackTrace(fileWriter);
                }
            }
        }
    }

    public static void logRecv(String clientTag, String command) {
        log(Level.CLIENT_IN, clientTag, command);
    }

    public static void logSend(String clientTag, String response) {
        log(Level.CLIENT_OUT, clientTag, response);
    }
}
