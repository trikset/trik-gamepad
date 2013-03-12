package com.trik.car;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;

import android.os.Binder;
import android.util.Log;

public class SenderService {// extends Service {
	// private final IBinder mBinder = new LocalBinder();
	private OutputStreamWriter mOut;

	public static final String SERVERIP = "192.168.1.150";
	public static final int SERVERPORT = 4444;

	public class LocalBinder extends Binder {
		SenderService getService() {
			// Return this instance of LocalService so clients can call public
			// methods
			return SenderService.this;
		}
	}

	// @Override
	// public IBinder onBind(Intent intent) {
	// return mBinder;
	// }

	boolean connect() {
		mOut = connectToCar();
		return mOut != null;
	}

	OutputStreamWriter connectToCar() {
		try {
			// here you must put your computer's IP address.
			InetAddress serverAddr = InetAddress.getByName(SERVERIP);

			Log.e("TCP Client", "C: Connecting...");

			// create a socket to make the connection with the server
			Socket socket = new Socket(serverAddr, SERVERPORT);

			try {
				return new OutputStreamWriter(socket.getOutputStream());

			} catch (Exception e) {

				Log.e("TCP", "S: Error", e);

			} finally {
				// the socket must be closed. It is not possible to
				// reconnect to
				// this socket
				// after it is closed, which means a new socket instance has
				// to
				// be created.
				// socket.close();
			}

		} catch (Exception e) {

			Log.e("TCP", "C: Error", e);

		}
		return null;
	}

	public void send(String command) {
		try {
			mOut.write(command + "\n");
			mOut.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
