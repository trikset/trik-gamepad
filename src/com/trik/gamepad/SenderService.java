package com.trik.gamepad;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

import android.os.AsyncTask;
import android.util.Log;

public class SenderService {// extends Service {
    interface OnEventListener<ArgType> {
        void onEvent(ArgType arg);
    }

    private PrintWriter             mOut;

    private OnEventListener<String> mOnDisconnectedListener;

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
                }
            };

        }.execute();
    }

    void setOnDiconnectedListner(OnEventListener<String> oel) {
        mOnDisconnectedListener = oel;
    }
}
