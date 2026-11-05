package client;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtpFileEntry {

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
