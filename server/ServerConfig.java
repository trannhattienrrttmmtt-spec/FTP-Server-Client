package server;

import java.io.File;

public class ServerConfig {

    public static final int DEFAULT_CONTROL_PORT = 2121;

    public static final String SERVER_NAME = "HUTECH FTP Two-Channel Server (Group 11)";
    public static final String SERVER_VERSION = "1.0.0";

    public static final String DEFAULT_ROOT_DIR = "ftp_root";

    public static final int BUFFER_SIZE = 8192;

    public static final int CONTROL_TIMEOUT_MS = 120000;

    public static final int DATA_TIMEOUT_MS = 60000;

    public static final String CHARSET_NAME = "UTF-8";

    public static final int PASSIVE_PORT_MIN = 50000;
    public static final int PASSIVE_PORT_MAX = 50100;

    public static File getResolvedRootDir(String customPath) {
        String path = (customPath != null && !customPath.trim().isEmpty()) ? customPath : DEFAULT_ROOT_DIR;
        File dir = new File(path);
        if (!dir.isAbsolute()) {

            dir = new File(System.getProperty("user.dir"), path);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
