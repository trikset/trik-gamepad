package com.trik.gamepad;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

class SenderService {
    interface OnEventListener<ArgType> {
        void onEvent(ArgType arg);
    }

    private PrintWriter                        mOut;

    private OnEventListener<String>            mOnDisconnectedListener;

    private String                             mHostAddr;

    private int                                mHostPort;

    private final MainActivity                 mMainActivity;

    // private long mLastConnectionAttemptTimestamp;

    private AsyncTask<Void, Void, PrintWriter> mConnectTask;

    public SenderService(final MainActivity mainActivity) {
        mMainActivity = mainActivity;
    }

    private void connectAsync() {
        if (mConnectTask == null) {
            mConnectTask = new AsyncTask<Void, Void, PrintWriter>() {
                @Override
                protected PrintWriter doInBackground(final Void... params) {
                    return connectToTRIK();
                }

                @Override
                protected void onPostExecute(PrintWriter result) {
                    mOut = result;
                    mConnectTask = null;
                    Toast.makeText(
                            mMainActivity,
                            "Connection to " + mHostAddr + ':' + mHostPort
                                    + (mOut != null ? " established." : " error."), Toast.LENGTH_SHORT).show();
                }
            };
            mConnectTask.execute();
        }
    }

    // socket is closed from PrintWriter.close()
    @SuppressWarnings("resource")
    private PrintWriter connectToTRIK() {
        /*
         * final long currentTime = System.currentTimeMillis(); final long
         * elapsed = currentTime - mLastConnectionAttemptTimestamp; final int
         * DELAY = 5000;
         * 
         * if (elapsed < TIMEOUT + DELAY) return null;
         */
        final int TIMEOUT = 5000;
        try {
            Log.e("TCP Client", "C: Connecting...");
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(mHostAddr, mHostPort), TIMEOUT);

            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoLinger(true, 0);
            socket.setTrafficClass(0x0F); // high priority, no-delay
            socket.setOOBInline(true);
            socket.shutdownInput();

            // currently does nothing
            // socket.setPerformancePreferences(connectionTime, latency,
            // bandwidth);
            try {
                return new PrintWriter(socket.getOutputStream(), /* autoflush */true);
            } catch (final Exception e) {
                Log.e("TCP", "GetStream: Error", e);
                socket.close();
                socket = null;
            }
        } catch (final Exception e) {
            Log.e("TCP", "Connect: Error", e);
        }
        // mLastConnectionAttemptTimestamp = currentTime;
        return null;
    }

    public void disconnect(final String reason) {
        if (mOut != null) {
            mOut.close();
            mOut = null;
            Log.d("TCP", "Disconnected.");
            mOnDisconnectedListener.onEvent(reason);
        }
    }

    public String getHostAddr() {
        return mHostAddr;
    }

    public void send(final String command) {

        if (mOut == null) {
            connectAsync();
            // Data loss here! Nevermind ...
            return;
        }

        Log.d("TCP", "Sending '" + command + '\'');

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                synchronized (mOut) {
                    // TODO: reimplement with Handle instead of multiple chaotic
                    // AyncTasks
                    mOut.println(command);
                }
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                if (mOut == null || mOut.checkError()) {
                    Log.e("TCP", "NotSent: " + command);
                    disconnect("Send failed.");
                }
            }

        }.execute();
    }

    void setOnDiconnectedListner(final OnEventListener<String> oel) {
        mOnDisconnectedListener = oel;
    }

    public void setTarget(final String hostAddr, final int hostPort) {
        if (!hostAddr.equalsIgnoreCase(mHostAddr) || mHostPort != hostPort) {
            disconnect("Target changed.");
        }

        mHostAddr = hostAddr;
        mHostPort = hostPort;
    }
}
