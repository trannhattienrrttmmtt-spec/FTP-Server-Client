package client;

public class FtpResponse {
    private final int code;
    private final String message;
    private final String fullText;

    public FtpResponse(int code, String message, String fullText) {
        this.code = code;
        this.message = message;
        this.fullText = fullText;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public String getFullText() { return fullText; }

    public boolean isPreliminary() { return code >= 100 && code < 200; }

    public boolean isSuccess() { return code >= 200 && code < 300; }

    public boolean isIntermediate() { return code >= 300 && code < 400; }

    public boolean isError() { return code >= 400; }

    @Override
    public String toString() { return fullText; }
}
