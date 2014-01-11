package com.trik.gamepad;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class SenderService {
    interface OnEventListener<ArgType> {
        void onEvent(ArgType arg);
    }

    private PrintWriter             mOut;

    private OnEventListener<String> mOnDisconnectedListener;
    private final Vibrator          mVibrator;

    private String                  mHostAddr;

    private int                     mHostPort;

    private final MainActivity      mMainActivity;

    final static long[]             SOS = new long[] { 0, 50, 50, 50, 50, 50,
            100, 200, 50, 200, 50, 200, 100, 50, 50, 50, 50, 50 };

    public SenderService(final MainActivity mainActivity) {
        mMainActivity = mainActivity;
        mVibrator = (Vibrator) mainActivity
                .getSystemService(Context.VIBRATOR_SERVICE);
    }

    public boolean connect() {

        mOut = null;
        try {
            mOut = new AsyncTask<Void, Void, PrintWriter>() {

                @Override
                protected PrintWriter doInBackground(final Void... params) {
                    return connectToCar();
                }
            }.execute().get();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        } catch (final ExecutionException e) {
            e.printStackTrace();
        }

        return mOut != null;
    }

    private PrintWriter connectToCar() {
        try {
            Log.e("TCP Client", "C: Connecting...");
            Socket socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(mHostAddr, mHostPort), 5000);
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

    public void send(final String command) {
        Log.d("TCP", "Sending '" + command + '\'');

        if (mOut == null) {
            final Boolean connected = connect();
            Toast.makeText(
                    mMainActivity,
                    "Connection to " + mHostAddr + ':' + mHostPort
                            + (connected ? " established." : " error."),
                    Toast.LENGTH_SHORT).show();
            if (!connected)
                return;
        }

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
                if (mOut.checkError()) {
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
