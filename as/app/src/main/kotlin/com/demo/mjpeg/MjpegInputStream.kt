// http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask
package com.demo.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties

class MjpegInputStream(`in`: InputStream) : DataInputStream(BufferedInputStream(`in`, MjpegInputStream.FRAME_MAX_LENGTH)) {
    private val SOI_MARKER = byteArrayOf(255.toByte(), 216.toByte())
    private val EOF_MARKER = byteArrayOf(255.toByte(), 217.toByte())

    @Throws(IOException::class)
    private fun getEndOfSeqeunce(`in`: DataInputStream, sequence: ByteArray): Int {
        var seqIndex = 0
        var c: Byte
        for (i in 0..FRAME_MAX_LENGTH - 1) {
            c = `in`.readUnsignedByte().toByte()
            if (c == sequence[seqIndex]) {
                seqIndex++
                if (seqIndex == sequence.size())
                    return i + 1
            } else {
                seqIndex = 0
            }
        }
        return -1
    }

    @Throws(IOException::class)
    private fun getStartOfSequence(`in`: DataInputStream, sequence: ByteArray): Int {
        val end = getEndOfSeqeunce(`in`, sequence)
        return if (end < 0) -1 else end - sequence.size()
    }

    @Throws(IOException::class)
    private fun parseContentLength(headerBytes: ByteArray): Int {
        val headerIn = ByteArrayInputStream(headerBytes)
        val props = Properties()
        props.load(headerIn)
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH))
    }

    @Throws(IOException::class)
    fun readMjpegFrame(): Bitmap {
        mark(FRAME_MAX_LENGTH)
        val headerLen = getStartOfSequence(this, SOI_MARKER)
        reset()
        val header = ByteArray(headerLen)
        readFully(header)
        val length: Int
        try {
            length = parseContentLength(header)
        } catch (nfe: NumberFormatException) {
            nfe.getStackTrace()
            Log.d(TAG, "catch NumberFormatException hit", nfe)
            length = getEndOfSeqeunce(this, EOF_MARKER)
        }

        reset()
        val frameData = ByteArray(length)
        skipBytes(headerLen)
        readFully(frameData)
        return BitmapFactory.decodeStream(ByteArrayInputStream(frameData))
    }

    companion object {
        private val TAG = "MjpegInputStream"
        private val CONTENT_LENGTH = "Content-Length"
        private val HEADER_MAX_LENGTH = 100
        private val FRAME_MAX_LENGTH = 200000 + HEADER_MAX_LENGTH
    }
}