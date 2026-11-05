package client;

import datachannel.DataTransferListener;

import javax.swing.SwingWorker;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileTransferTask extends SwingWorker<Integer, FileTransferTask.Progress> {

    public enum Direction { UPLOAD, DOWNLOAD }

    public static class Job {
        final File localFile;
        final String remoteName;

        public Job(File localFile, String remoteName) {
            this.localFile = localFile;
            this.remoteName = remoteName;
        }
    }

    public static class Progress {
        public final String fileName;
        public final int fileIndex;
        public final int fileCount;
        public final long bytesDone;
        public final long bytesTotal;
        public final int percent;
        public final double bytesPerSecond;

        Progress(String fileName, int fileIndex, int fileCount, long bytesDone, long bytesTotal,
                 int percent, double bytesPerSecond) {
            this.fileName = fileName;
            this.fileIndex = fileIndex;
            this.fileCount = fileCount;
            this.bytesDone = bytesDone;
            this.bytesTotal = bytesTotal;
            this.percent = percent;
            this.bytesPerSecond = bytesPerSecond;
        }
    }

    public interface Callback {
        void onProgress(Direction direction, Progress progress);

        void onFinished(Direction direction, int succeeded, int total, List<String> errors, boolean cancelled);
    }

    private final FtpClientController controller;
    private final Direction direction;
    private final List<Job> jobs;
    private final Callback callback;
    private final List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile int succeededCount = 0;

    public FileTransferTask(FtpClientController controller, Direction direction, List<Job> jobs, Callback callback) {
        this.controller = controller;
        this.direction = direction;
        this.jobs = new ArrayList<>(jobs);
        this.callback = callback;
    }

    public void requestCancel() {
        cancel(false);
        controller.abortTransfer();
    }

    @Override
    protected Integer doInBackground() {
        int succeeded = 0;
        for (int i = 0; i < jobs.size() && !isCancelled(); i++) {
            final Job job = jobs.get(i);
            final int index = i + 1;
            final String displayName = direction == Direction.UPLOAD ? job.localFile.getName() : job.remoteName;
            final long knownSize = direction == Direction.UPLOAD ? job.localFile.length() : -1;
            publish(new Progress(displayName, index, jobs.size(), 0, knownSize, 0, 0));

            DataTransferListener listener = new DataTransferListener() {
                private long startNanos;
                private long lastPublishNanos;

                @Override
                public void onTransferStart(String fileName, long totalBytes) {
                    startNanos = System.nanoTime();
                }

                @Override
                public void onTransferProgress(long bytesTransferred, long totalBytes, int percent) {
                    long now = System.nanoTime();

                    if (now - lastPublishNanos < 50_000_000L && bytesTransferred < totalBytes) return;
                    lastPublishNanos = now;
                    publish(new Progress(displayName, index, jobs.size(), bytesTransferred, totalBytes,
                            Math.min(100, percent), speed(bytesTransferred, now)));
                }

                @Override
                public void onTransferComplete(long totalBytes) {
                    publish(new Progress(displayName, index, jobs.size(), totalBytes, totalBytes, 100,
                            speed(totalBytes, System.nanoTime())));
                }

                @Override
                public void onTransferError(Exception ex) {

                }

                private double speed(long bytes, long now) {
                    double seconds = (now - startNanos) / 1e9;
                    return seconds > 0 ? bytes / seconds : 0;
                }
            };

            try {
                if (direction == Direction.UPLOAD) {
                    controller.upload(job.localFile, job.remoteName, listener);
                } else {
                    controller.download(job.remoteName, job.localFile, listener);
                }
                succeeded++;
                succeededCount = succeeded;
            } catch (IOException e) {
                errors.add(displayName + ": " + e.getMessage());
                if (!controller.isConnected()) break;
            }
        }
        return succeeded;
    }

    @Override
    protected void process(List<Progress> chunks) {
        if (!chunks.isEmpty()) {
            callback.onProgress(direction, chunks.get(chunks.size() - 1));
        }
    }

    @Override
    protected void done() {
        callback.onFinished(direction, succeededCount, jobs.size(), errors, isCancelled());
    }
}
