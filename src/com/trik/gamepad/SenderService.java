package com.trik.gamepad;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.Log;

public class SenderService {
    interface OnEventListener<ArgType> {
        void onEvent(ArgType arg);
    }

    private PrintWriter             mOut;

    private OnEventListener<String> mOnDisconnectedListener;
    private final Vibrator          mVibrator;

    final static long[]             SOS = new long[] { 0, 50, 50, 50, 50, 50, 100, 200, 50, 200, 50, 200, 100, 50, 50,
                                        50, 50, 50 };

    public SenderService(MainActivity mainActivity) {
        mVibrator = (Vibrator) mainActivity.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public boolean connect(final String hostAddr, final int hostPort) {

        mOut = null;
        try {
            mOut = new AsyncTask<Void, Void, PrintWriter>() {

                @Override
                protected PrintWriter doInBackground(Void... params) {
                    return connectToCar(hostAddr, hostPort);
                }
            }.execute().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return mOut != null;
    }

    private PrintWriter connectToCar(String hostAddr, int hostPort) {
        try {
            Log.e("TCP Client", "C: Connecting...");
            Socket socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(hostAddr, hostPort), 5000);
            try {
                return new PrintWriter(socket.getOutputStream(), true);
            } catch (Exception e) {
                Log.e("TCP", "GetStream: Error", e);
                socket.close();
                socket = null;
            }
        } catch (Exception e) {
            Log.e("TCP", "Connect: Error", e);
        }
        return null;
    }

    public void disconnect(String reason) {
        if (mOut != null) {
            mOut.close();
            mOut = null;
            Log.d("TCP", "Disconnected.");
            mOnDisconnectedListener.onEvent(reason);
        }
    }

    public void send(final String command) {
        Log.d("TCP", "Sending '" + command + "'");

        if (mOut == null)
            return;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                synchronized (mOut) {
                    mOut.println(command);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (mOut.checkError())
                {
                    Log.e("TCP", "NotSent: " + command);
                    disconnect("Send failed.");
                    mVibrator.vibrate(SOS, -1);
                } else {
                    mVibrator.vibrate(20);
                }
            };

        }.execute();
    }

    void setOnDiconnectedListner(OnEventListener<String> oel) {
        mOnDisconnectedListener = oel;
    }
}
