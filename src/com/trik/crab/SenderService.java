package com.trik.crab;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

import android.os.AsyncTask;
import android.util.Log;

public class SenderService {// extends Service {
    private PrintWriter             mOut;
    private OnEventListener<String> mOnDisconnectedListener;

    public static final int         SERVERPORT = 4444;

    public boolean connect(final String hostAddr) {

        mOut = null;
        try {
            mOut = new AsyncTask<Void, Void, PrintWriter>() {

                @Override
                protected PrintWriter doInBackground(Void... params) {
                    return connectToCar(hostAddr);
                }
            }.execute().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return mOut != null;
    }

    private PrintWriter connectToCar(String hostAddr) {
        try {
            Log.e("TCP Client", "C: Connecting...");
            Socket socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(hostAddr, SERVERPORT), 5000);
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

    public void send(String command) {
        if (mOut == null)
            return;
        synchronized (mOut) {
            final String tempCommand = command;
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    mOut.println(tempCommand);
                    Log.d("TCP", "Sent: " + tempCommand);
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    if (mOut.checkError())
                    {
                        Log.d("TCP", "NotSent: " + tempCommand);
                        disconnect("Send failed.");
                    }

                };
            }.execute();
        }
    }

    public void disconnect(String reason) {
        if (mOut != null) {
            mOut.close();
            mOut = null;
            Log.d("TCP", "Disconnected.");
            mOnDisconnectedListener.onEvent(reason);
        }
    }

    void setOnDiconnectedListner(OnEventListener<String> oel) {
        mOnDisconnectedListener = oel;
    }

    interface OnEventListener<ArgType> {
        void onEvent(ArgType arg);
    }
}
