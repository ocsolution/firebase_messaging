package io.flutter.plugins.firebase.messaging;

import java.io.IOException;
import java.io.InputStream;

public class MyLimitedInputStream extends InputStream {
    private final InputStream inputStream;
    private final long limit;
    private long bytesRead;

    public MyLimitedInputStream(InputStream inputStream, long limit) {
        this.inputStream = inputStream;
        this.limit = limit;
        this.bytesRead = 0;
    }

    @Override
    public int read() throws IOException {
        if (bytesRead >= limit) {
            // Reached the download limit, return -1 to indicate end of stream.
            return -1;
        }

        int data = inputStream.read();
        if (data != -1) {
            bytesRead++;
        }
        return data;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (bytesRead >= limit) {
            // Reached the download limit, return -1 to indicate end of stream.
            return -1;
        }

        int bytesToRead = (int) Math.min(len, limit - bytesRead);
        int bytesReadThisTime = inputStream.read(b, off, bytesToRead);

        if (bytesReadThisTime > 0) {
            bytesRead += bytesReadThisTime;
        }

        return bytesReadThisTime;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
