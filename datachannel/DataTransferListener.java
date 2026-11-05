package datachannel;

public interface DataTransferListener {

    void onTransferStart(String fileName, long totalBytes);

    void onTransferProgress(long bytesTransferred, long totalBytes, int percent);

    default void onTransferProgress(long bytesTransferred, long totalBytes, int percent, double speedKBps) {
        onTransferProgress(bytesTransferred, totalBytes, percent);
    }

    void onTransferComplete(long totalBytes);

    default void onTransferComplete(long totalBytes, long elapsedTimeMs, double averageSpeedKBps) {
        onTransferComplete(totalBytes);
    }

    void onTransferError(Exception ex);
}
