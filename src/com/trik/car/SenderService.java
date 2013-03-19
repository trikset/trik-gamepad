package com.trik.car;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

import android.os.AsyncTask;
import android.util.Log;

public class SenderService {// extends Service {
	// private final IBinder mBinder = new LocalBinder();
	private PrintWriter mOut;

	// public static final String SERVERIP = "192.168.1.150";
	public static final String SERVERIP = "192.168.51.2";
	public static final int SERVERPORT = 4444;

	public boolean connect() {

		mOut = null;
		try {
			mOut = new AsyncTask<Void, Void, PrintWriter>() {

				@Override
				protected PrintWriter doInBackground(Void... params) {
					return connectToCar();
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

	private PrintWriter connectToCar() {
		try {
			InetAddress serverAddr = InetAddress.getByName(SERVERIP);
			Log.e("TCP Client", "C: Connecting...");
			Socket socket = new Socket(serverAddr, SERVERPORT);

			try {
				return new PrintWriter(socket.getOutputStream(), true);
			} catch (Exception e) {
				Log.e("TCP", "S: Error", e);
				socket.close();
				socket = null;
			}
		} catch (Exception e) {
			Log.e("TCP", "C: Error", e);
		}
		return null;
	}

	public void send(String command) {
		if (mOut == null)
			return;
		final String tempCommand = command;
		synchronized (mOut) {
			new AsyncTask<Void, Void, Void>() {

				@Override
				protected Void doInBackground(Void... params) {
					mOut.println(tempCommand);
					return null;
				}

			}.execute();

		}
	}

	// public class SendingServiceThread implements Runnable {
	//
	// public SendingServiceThread() {
	// // Nothing to do...
	// }
	//
	// @Override
	// public void run() {
	// try {
	// while (true) {
	// String newLine = inBuff.readLine();
	// System.out.println(newLine); // Replace this with any
	// // processing you want !
	// }
	// } catch (IOException e) {
	// System.err.println("Connection problem");
	// }
	// }
	// }
}
