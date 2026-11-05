package datachannel;

import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PassiveModeInfo {
    private static final Pattern PASV_PATTERN = Pattern.compile(
            "\\(?(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\)?"
    );

    private final String host;
    private final int port;
    private final String rawResponse;

    public PassiveModeInfo(String host, int port, String rawResponse) {
        this.host = host;
        this.port = port;
        this.rawResponse = rawResponse;
    }

    public static PassiveModeInfo parse(String pasvResponse) throws IllegalArgumentException {
        if (pasvResponse == null || pasvResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("Chuoi phan hoi PASV rong hoac null.");
        }

        Matcher matcher = PASV_PATTERN.matcher(pasvResponse);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Khong tim thay mau (h1,h2,h3,h4,p1,p2) trong phan hoi PASV: " + pasvResponse);
        }

        int h1 = Integer.parseInt(matcher.group(1));
        int h2 = Integer.parseInt(matcher.group(2));
        int h3 = Integer.parseInt(matcher.group(3));
        int h4 = Integer.parseInt(matcher.group(4));
        int p1 = Integer.parseInt(matcher.group(5));
        int p2 = Integer.parseInt(matcher.group(6));

        if (h1 > 255 || h2 > 255 || h3 > 255 || h4 > 255) {
            throw new IllegalArgumentException("Dia chi IP khong hop le trong phan hoi PASV: " + pasvResponse);
        }

        if (p1 > 255 || p2 > 255) {
            throw new IllegalArgumentException("Gia tri port octet khong hop le trong phan hoi PASV: " + pasvResponse);
        }

        String ip = String.format("%d.%d.%d.%d", h1, h2, h3, h4);
        int port = (p1 << 8) | p2; // p1 * 256 + p2

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Cong du lieu nam ngoai dai hop le (1-65535): " + port);
        }

        return new PassiveModeInfo(ip, port, pasvResponse);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    @Override
    public String toString() {
        return String.format("PassiveModeInfo{host='%s', port=%d}", host, port);
    }
}
