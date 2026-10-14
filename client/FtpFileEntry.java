package client;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Một dòng trong kết quả lệnh LIST (định dạng kiểu UNIX "ls -l") do Server trả về:
 * <pre>
 * drwxr-xr-x 1 ftp ftp          0 Oct 15 02:05 tailieu
 * -rw-r--r-- 1 ftp ftp        599 Oct 15 02:05 welcome.txt
 * </pre>
 * Phụ trách: Nguyễn Anh Vũ (MSSV: 2380602582)
 */
public class FtpFileEntry {
    // perms  links  owner  group  size  month day time/year  name (có thể chứa khoảng trắng)
    private static final Pattern UNIX_LIST = Pattern.compile(
            "^([\\-dlrwxsStT]{10})\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+(\\S+\\s+\\d{1,2}\\s+[\\d:]+)\\s(.+)$");

    private final String name;
    private final boolean directory;
    private final long size;
    private final String modified;
    private final String permissions;

    public FtpFileEntry(String name, boolean directory, long size, String modified, String permissions) {
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.modified = modified;
        this.permissions = permissions;
    }

    /**
     * Phân tích một dòng LIST. Nếu không khớp định dạng UNIX, coi cả dòng là tên tệp (vd. kết quả NLST).
     * @return null nếu dòng rỗng
     */
    public static FtpFileEntry parse(String line) {
        if (line == null) return null;
        line = line.replace("\r", "").replace("\n", "");
        if (line.trim().isEmpty()) return null;

        Matcher m = UNIX_LIST.matcher(line);
        if (m.matches()) {
            String perms = m.group(1);
            long size;
            try {
                size = Long.parseLong(m.group(2));
            } catch (NumberFormatException e) {
                size = -1;
            }
            String date = m.group(3).replaceAll("\\s+", " ");
            String name = m.group(4);
            return new FtpFileEntry(name, perms.charAt(0) == 'd', size, date, perms);
        }
        return new FtpFileEntry(line.trim(), false, -1, "", "");
    }

    /** Định dạng dung lượng dễ đọc: 1.5 KB, 2.3 MB ... */
    public static String formatSize(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    public String getName() { return name; }
    public boolean isDirectory() { return directory; }
    public long getSize() { return size; }
    public String getModified() { return modified; }
    public String getPermissions() { return permissions; }

    @Override
    public String toString() {
        return (directory ? "[DIR] " : "      ") + name;
    }
}
