// http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask
package com.demo.mjpeg;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.apache.commons.io.input.BoundedInputStream;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Properties;

public class MjpegInputStream extends DataInputStream {
    private static final String TAG = "MjpegInputStream";
    private final static String CONTENT_LENGTH = "Content-Length";
    private final static int HEADER_MAX_LENGTH = 10000;
    private final static int FRAME_MAX_LENGTH = 300000 + HEADER_MAX_LENGTH;
    private final static byte[] SOI_MARKER = {(byte) 0xFF, (byte) 0xD8};
    //private final byte[] EOF_MARKER = {(byte) 0xFF, (byte) 0xD9};
    @Nullable
    private final static byte[] CONTENT_LENGTH_MARKER = getUTF8Bytes(CONTENT_LENGTH);
    private final Properties props = new Properties();
    @Nullable
    private static byte[] getUTF8Bytes(@NonNull String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public MjpegInputStream(@NonNull InputStream in) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }


    private int getEndOfSequence(@NonNull byte[] sequence) throws IOException {
        mark(FRAME_MAX_LENGTH);
        int seqIndex = 0;

        try {
            for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
                int c = readUnsignedByte();
                if (c < 0)
                    return -1;
                if ((byte) c == sequence[seqIndex]) {
                    seqIndex++;
                    if (seqIndex == sequence.length)
                        return i + 1;
                } else {
                    seqIndex = 0;
                }
            }
            return -1;
        } catch (IOException e) {
            return -1;
        } finally {
            reset();
        }
    }

    private int getStartOfSequence(@NonNull byte[] sequence) throws IOException {
        int end = getEndOfSequence(sequence);
        return end < 0 ? -1 : end - sequence.length;
    }

    @Nullable
    public BoundedInputStream readMjpegFrame() throws IOException {
        int contentLength = -1;
        int contentAttrPos = getStartOfSequence(CONTENT_LENGTH_MARKER);
        if (contentAttrPos < 0 || skipBytes(contentAttrPos) < contentAttrPos)
            throw new IOException("JPG stream is totally broken or this is extremely huge image");

        try {
            int headerLen = getStartOfSequence(SOI_MARKER);
            BoundedInputStream headerIn = new BoundedInputStream(this, headerLen);
            headerIn.setPropagateClose(false);
            props.clear();
            props.load(headerIn);
            contentLength = Integer.parseInt(props.getProperty(CONTENT_LENGTH));
            headerIn.close();

            if (contentLength >= 0 && available() < 2 * contentLength) {
                // we must be at the very beginning of data already, but ....
                int skip = getStartOfSequence(SOI_MARKER);
                if(skipBytes(skip) < skip)
                    return null;
                BoundedInputStream s = new BoundedInputStream(this, contentLength);
                s.setPropagateClose(false);
                return s;
            }
        } catch (@NonNull IOException | IllegalArgumentException e) {
            //e.getStackTrace();
            Log.d(TAG, "catch exn hit", e);
        }
        try {
            if (contentLength < 0) {
                Log.e(TAG, "Skipping to recover");
                contentLength = getStartOfSequence(CONTENT_LENGTH_MARKER);
            }
            else {
                Log.i(TAG, "Frame dropped.");
            }
            Log.v(TAG, contentLength + " bytes to skip until next frame header.");
            int skipped = skipBytes(contentLength);
            if (skipped != contentLength)
                Log.w(TAG, "Skipped only" + skipped + " bytes instead of " + contentLength);
        } catch (IOException e) {
            final StackTraceElement[] stackTrace = e.getStackTrace();
            Log.e(TAG, "Failed to skip bad data:" + e + "\n" + Arrays.toString(stackTrace));
        }
        return null;
    }

}