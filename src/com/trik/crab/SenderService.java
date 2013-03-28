package com.trik.crab;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

import android.os.AsyncTask;
import android.util.Log;

public class SenderService {// extends Service {
    private PrintWriter     mOut;

    // public static final String SERVERIP = "192.168.1.150";
    // public static final String SERVERIP = "192.168.51.2";
    public static final int SERVERPORT = 4444;

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
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return mOut != null;
    }

    private PrintWriter connectToCar(String hostAddr) {
        try {
            InetAddress serverAddr = InetAddress.getByName(hostAddr);
            Log.e("TCP Client", "C: Connecting...");
            Socket socket = new Socket(serverAddr, SERVERPORT);
            socket.setTcpNoDelay(true);

            try {
                return new PrintWriter(socket.getOutputStream(), true);
            } catch (Exception e) {
                Log.e("TCP", "Send: Error", e);
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
        final String tempCommand = command;
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                mOut.println(tempCommand);
                Log.d("Sent", tempCommand);
                return null;
            }

        }.execute();
    }

    public void disconnect() {
        if (mOut != null) {
            mOut.close();
            mOut = null;
        }
    }
}
