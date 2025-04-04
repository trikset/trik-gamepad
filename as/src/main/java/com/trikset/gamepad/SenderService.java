package com.trikset.gamepad;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class SenderService {

    private OnEventListener<String> getShowTextCallback() {
        return mShowTextCallback;
    }

    void setShowTextCallback(OnEventListener<String> showTextCallback) {
        mShowTextCallback = showTextCallback;
    }

    private OnEventListener<String> getOnDisconnectedListener() {
        return mOnDisconnectedListener;
    }

    void setOnDisconnectedListener(OnEventListener<String> onDisconnectedListener) {
        mOnDisconnectedListener = onDisconnectedListener;
    }

    public static final int DEFAULT_KEEPALIVE = 5000;
    public static final int MINIMAL_KEEPALIVE = 1000;
    private static int keepaliveTimeout = DEFAULT_KEEPALIVE;
    private final KeepAliveTimer mKeepAliveTimer = new KeepAliveTimer();

    private static final int TIMEOUT = 5000;
    private final Object mSyncFlag = new Object();
    private OnEventListener<String> mShowTextCallback;
    @Nullable
    private PrintWriter mOut;

    private OnEventListener<String> mOnDisconnectedListener;

    private String mHostAddr;
    private int mHostPort;

    public void setExecutor(@Nullable Executor executor) {
        if (executor != null) {
            SenderService.mExecutor = executor;
        }
    }

    @Nullable
    private static Executor mExecutor = Executors.newSingleThreadExecutor();

    @Nullable
    private static volatile AsyncTask<Void, Void, Void> mConnectTask;

    public SenderService() {
    }

    private AsyncTask<Void, Void, Void> connectAsync() {
        synchronized (mSyncFlag) {
            if (mConnectTask != null) {
                return null;
            } else {
                mConnectTask = new PrintWriterAsyncTask();
                return Objects.requireNonNull(mConnectTask).executeOnExecutor(mExecutor);
            }
        }
    }

    // socket is closed from PrintWriter.close()
    private Void connectToTRIK() {

        synchronized (mSyncFlag) {
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
                OutputStreamWriter osw =
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);

                mKeepAliveTimer.restartKeepAliveTimer();

                try {
                    mOut = new PrintWriter(osw, true);
                    return null;
                } catch (@NonNull final Exception e) {
                    Log.e("TCP", "GetStream: Error", e);
                    socket.close();
                    osw.close();
                }
            } catch (@NonNull final IOException e) {
                Log.e("TCP", "Connect: Error", e);
            }
            // mLastConnectionAttemptTimestamp = currentTime;
            return null;
        }
    }

    void disconnect(final String reason) {
        mKeepAliveTimer.stopKeepAliveTimer();

        if (mOut != null) {
            mOut.close();
            mOut = null;
            Log.d("TCP", "Disconnected.");
            OnEventListener<String> l = getOnDisconnectedListener();
            if (l != null) l.onEvent(reason);
        }
    }

    String getHostAddr() {
        return mHostAddr;
    }

    public void send(final String command) {
        if (mOut == null) {
            connectAsync(); // is synchronized on the same object as SendCommandAsyncTask
        }

        Log.d("TCP", "Sending '" + command + '\'');
        new SendCommandAsyncTask(command).executeOnExecutor(mExecutor);

        mKeepAliveTimer.restartKeepAliveTimer();
    }

    public void setTarget(@NonNull final String hostAddr, final int hostPort) {
        if (!hostAddr.equalsIgnoreCase(mHostAddr) || mHostPort != hostPort) {
            disconnect("Target changed.");
        }

        mHostAddr = hostAddr;
        mHostPort = hostPort;
    }

    public void setKeepaliveTimeout(final int timeout) {
        if (timeout != keepaliveTimeout) {
            mKeepAliveTimer.restartKeepAliveTimer();
            keepaliveTimeout = timeout;
        }
    }

    public int getKeepaliveTimeout() {
        return keepaliveTimeout;
    }

    interface OnEventListener<ArgType> {
        void onEvent(ArgType arg);
    }

    private class PrintWriterAsyncTask extends AsyncTask<Void, Void, Void> {
        @Nullable
        @Override
        protected Void doInBackground(final Void... params) {
            connectToTRIK();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            OnEventListener<String> cb = getShowTextCallback();
            if (cb != null) {
                cb.onEvent("Connection to " + mHostAddr + ':' + mHostPort + (mOut != null ? " established." : " error."));
            }
            mConnectTask = null;
        }

    }

    private class SendCommandAsyncTask extends AsyncTask<Void, Void, Void> {
        private final String command;

        SendCommandAsyncTask(String command) {
            this.command = command;
        }

        @Nullable
        @Override
        protected Void doInBackground(final Void... params) {
            synchronized (mSyncFlag) {
                // TODO: reimplement with Handle instead of multiple chaotic
                // AsyncTasks
                if (mOut != null) mOut.println(command);
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
    }

    private class KeepAliveTimer extends Timer {
        private KeepAliveTimerTask task = new KeepAliveTimerTask();

        private void restartKeepAliveTimer() {
            stopKeepAliveTimer();

            task = new KeepAliveTimerTask();
            // Using '300' in order to compensate ping
            final int realTimeout = keepaliveTimeout - 300;
            schedule(task, realTimeout, realTimeout);
        }

        private void stopKeepAliveTimer() {
            task.cancel();
            purge();
        }

        private class KeepAliveTimerTask extends TimerTask {
            @Override
            public void run() {
                if (mOut != null) {
                    final String command = "keepalive " + keepaliveTimeout;
                    Log.d("TCP", String.format("Sending %s message", command));
                    new SendCommandAsyncTask(command).executeOnExecutor(mExecutor);
                } else {
                    stopKeepAliveTimer();
                }
            }
        }
    }
}

