// http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask
package com.demo.mjpeg;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MjpegInputStream extends DataInputStream {
    private static final String TAG               = "MjpegInputStream";
    private final static String CONTENT_LENGTH = "Content-Length";
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 200000 + HEADER_MAX_LENGTH;
    private final byte[]        SOI_MARKER        = { (byte) 0xFF, (byte) 0xD8 };
    private final byte[]        EOF_MARKER        = { (byte) 0xFF, (byte) 0xD9 };

    public MjpegInputStream(InputStream in) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }

    private int getEndOfSeqeunce(DataInputStream in, byte[] sequence)
            throws IOException {
        int seqIndex = 0;
        byte c;
        for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
            c = (byte) in.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                seqIndex++;
                if (seqIndex == sequence.length)
                    return i + 1;
            } else {
                seqIndex = 0;
            }
        }
        return -1;
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence)
            throws IOException {
        int end = getEndOfSeqeunce(in, sequence);
        return end < 0 ? -1 : end - sequence.length;
    }

    private int parseContentLength(byte[] headerBytes) throws IOException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
    }

    public Bitmap readMjpegFrame() throws IOException {
        mark(FRAME_MAX_LENGTH);
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();
        byte[] header = new byte[headerLen];
        readFully(header);
        int length;
        try {
            length = parseContentLength(header);
        } catch (NumberFormatException nfe) {
            nfe.getStackTrace();
            Log.d(TAG, "catch NumberFormatException hit", nfe);
            length = getEndOfSeqeunce(this, EOF_MARKER);
        }
        reset();
        byte[] frameData = new byte[length];
        skipBytes(headerLen);
        readFully(frameData);
        return BitmapFactory.decodeStream(new ByteArrayInputStream(frameData));
    }
}