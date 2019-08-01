package com.trikset.gamepad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class DummyServer {
    static final String IP = "localhost";
    static final int DEFAULT_PORT = 12345;

    private boolean canStopListening = false;
    public void stopListening() { canStopListening = true; }

    private final ArrayList<String> receivedMessages = new ArrayList<String>();
    public List<String> getReceivedMessages() { return receivedMessages; }

    DummyServer() {
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (ServerSocket server = new ServerSocket(DEFAULT_PORT)) {
                    Socket client = server.accept();

                    BufferedReader clientInput =
                            new BufferedReader(new InputStreamReader(client.getInputStream()));
                    do {
                        receivedMessages.add(clientInput.readLine());
                    } while (!canStopListening);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        serverThread.start();
    }
}