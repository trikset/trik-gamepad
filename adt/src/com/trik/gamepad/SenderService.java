package com.trik.gamepad;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class SenderService {
    interface OnEventListener<ArgType> {
        void onEvent(ArgType arg);
    }

    private PrintWriter                        mOut;

    private OnEventListener<String>            mOnDisconnectedListener;
    private final Vibrator                     mVibrator;

    private String                             mHostAddr;

    private int                                mHostPort;

    private final MainActivity                 mMainActivity;

    private long                               mLastConnectionAttemptTimestamp;

    private AsyncTask<Void, Void, PrintWriter> mConnectTask;
    final static long[]                        SOS = new long[] { 0, 50, 50, 50, 50, 50, 100, 200, 50, 200, 50, 200,
            100, 50, 50, 50, 50, 50               };

    public SenderService(final MainActivity mainActivity) {
        mMainActivity = mainActivity;
        mVibrator = (Vibrator) mainActivity.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void connectAsync() {
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
                };
            };
            mConnectTask.execute();
        }
    }

    private PrintWriter connectToTRIK() {
        final long currentTime = System.currentTimeMillis();
        final long elapsed = currentTime - mLastConnectionAttemptTimestamp;
        final int TIMEOUT = 5000;
        final int DELAY = 5000;

        if (elapsed < TIMEOUT + DELAY)
            return null;
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
                return new PrintWriter(socket.getOutputStream(), true);
            } catch (final Exception e) {
                Log.e("TCP", "GetStream: Error", e);
                socket.close();
                socket = null;
            }
        } catch (final Exception e) {
            Log.e("TCP", "Connect: Error", e);
        }
        mLastConnectionAttemptTimestamp = currentTime;
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
                    mOut.println(command);
                }
                return null;
            }

            @Override
            protected void onPostExecute(final Void result) {
                if (mOut == null || mOut.checkError()) {
                    Log.e("TCP", "NotSent: " + command);
                    disconnect("Send failed.");
                    mVibrator.vibrate(SOS, -1);
                } else {
                    mVibrator.vibrate(20);
                }
            };

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
