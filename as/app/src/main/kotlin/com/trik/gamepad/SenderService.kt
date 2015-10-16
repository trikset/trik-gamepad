package com.trik.gamepad

import android.os.AsyncTask
import android.util.Log
import android.widget.Toast

import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

internal class SenderService// private long mLastConnectionAttemptTimestamp;

(private val mMainActivity: MainActivity) {
    private val mSyncFlag = Object()
    private var mOut: PrintWriter? = null

    private var mOnDisconnectedListener: OnEventListener<String>? = null

    var hostAddr: String = ""
        private set

    private var mHostPort: Int = 0
    private var mConnectTask: AsyncTask<Void, Void, PrintWriter>? = null

    private fun connectAsync() {
        if (mConnectTask == null) {
            mConnectTask = object : AsyncTask<Void, Void, PrintWriter>() {
                override fun doInBackground(vararg params: Void): PrintWriter? {
                    return connectToTRIK()
                }

                override fun onPostExecute(result: PrintWriter) {
                    mOut = result
                    mConnectTask = null
                    Toast.makeText(
                            mMainActivity,
                            "Connection to " + hostAddr + ':' + mHostPort + (if (mOut != null) " established." else " error."), Toast.LENGTH_SHORT).show()
                }
            }
            mConnectTask!!.execute()
        }
    }

    // socket is closed from PrintWriter.close()
    @SuppressWarnings("resource")
    private fun connectToTRIK(): PrintWriter? {
        /*
         * final long currentTime = System.currentTimeMillis(); final long
         * elapsed = currentTime - mLastConnectionAttemptTimestamp; final int
         * DELAY = 5000;
         *
         * if (elapsed < TIMEOUT + DELAY) return null;
         */
        val TIMEOUT = 5000
        try {
            Log.e("TCP Client", "C: Connecting...")
            val socket = Socket()
            socket.connect(InetSocketAddress(hostAddr, mHostPort), TIMEOUT)

            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.setSoLinger(true, 0)
            socket.trafficClass = 15 // high priority, no-delay
            socket.oobInline = true
            socket.shutdownInput()

            // currently does nothing
            // socket.setPerformancePreferences(connectionTime, latency,
            // bandwidth);
            try {
                return PrintWriter(socket.outputStream, /* autoflush */true)
            } catch (e: Exception) {
                Log.e("TCP", "GetStream: Error", e)
                socket.close()
            }

        } catch (e: Exception) {
            Log.e("TCP", "Connect: Error", e)
        }

        // mLastConnectionAttemptTimestamp = currentTime;
        return null
    }

    fun disconnect(reason: String) {
        if (mOut != null) {
            mOut!!.close()
            mOut = null
            Log.d("TCP", "Disconnected.")
            mOnDisconnectedListener!!.onEvent(reason)
        }
    }

    fun send(command: String) {

        if (mOut == null) {
            connectAsync()
            // Data loss here! Nevermind ...
            return
        }

        Log.d("TCP", "Sending '$command'")

        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg params: Void): Void? {
                synchronized (mSyncFlag) {
                    // TODO: reimplement with Handle instead of multiple chaotic
                    // AyncTasks
                    mOut!!.println(command)
                }
                return null
            }

            override fun onPostExecute(result: Void) {
                if (mOut == null || mOut!!.checkError()) {
                    Log.e("TCP", "NotSent: " + command)
                    disconnect("Send failed.")
                }
            }

        }.execute()
    }

    fun setOnDiconnectedListner(oel: OnEventListener<String>) {
        mOnDisconnectedListener = oel
    }

    fun setTarget(hostAddr: String, hostPort: Int) {
        if (!hostAddr.equals(this.hostAddr, ignoreCase = true) || mHostPort != hostPort) {
            disconnect("Target changed.")
        }

        this.hostAddr = hostAddr
        mHostPort = hostPort
    }

    internal interface OnEventListener<ArgType> {
        fun onEvent(arg: ArgType)
    }
}
