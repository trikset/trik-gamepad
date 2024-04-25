package com.trikset.gamepad;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.LEGACY)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class SenderServiceTest {
    @Test
    public void senderServiceShouldConnectToServerSuccessfullyAfterSendingCommand()
            throws InterruptedException {
        DummyServer server = new DummyServer(1, 4);
        SenderService client = new SenderService();
        client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT + 4);

        client.send("");
        Robolectric.flushBackgroundThreadScheduler();
        Assert.assertTrue(server.isConnected());
    }

    @Test
    public void senderServiceShouldSendSingleCommandCorrectly() throws InterruptedException {
        DummyServer server = new DummyServer(1, 0);
        SenderService client = new SenderService();
        client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT);
        client.setKeepaliveTimeout(10000000); // to disable keep-alive messages

        client.send("Test; check");
        Robolectric.flushBackgroundThreadScheduler();
        assertEquals("Test; check", server.getLastCommand());
    }

    @Test
    public void senderServiceShouldSendMultipleCommandsCorrectly() throws InterruptedException {
        DummyServer server = new DummyServer(5, 1);
        SenderService client = new SenderService();
        client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT + 1);
        client.setKeepaliveTimeout(10000000); // to disable keep-alive messages

        for (int i = 0; i < 5; ++i) {
            client.send(String.format("%d checking", i));
        }
        Robolectric.flushBackgroundThreadScheduler();
        assertEquals("4 checking", server.getLastCommand());
    }

    @Test
    public void setTargetShouldSetServerSuccessfully() {
        SenderService client = new SenderService();
        client.setTarget("someaddr-test", 12345);

        assertEquals("someaddr-test", client.getHostAddr());
    }

    @Test
    public void senderServiceShouldReturnCorrectKeepaliveTimeout() {
        SenderService client = new SenderService();

        client.setKeepaliveTimeout(3453);
        assertEquals(3453, client.getKeepaliveTimeout());

        client.setKeepaliveTimeout(1234);
        assertEquals(1234, client.getKeepaliveTimeout());
    }

    private static class DummyServer {
        static final String IP = "localhost";
        static final int DEFAULT_PORT = 12345;

        final int port;

        private boolean isConnected = false;
        public boolean isConnected() { return isConnected; }

        private String lastCommand;
        public String getLastCommand() { return lastCommand; }


        DummyServer(final int cmdNumber, final int portShift) {
            port = DEFAULT_PORT + portShift;

            Thread serverThread = new Thread(() -> {
                try (ServerSocket server = new ServerSocket(port)) {
                    Socket client = server.accept();
                    isConnected = true;

                    BufferedReader clientInput =
                            new BufferedReader(new InputStreamReader(client.getInputStream()));
                    for (int i = 0; i < cmdNumber; ++i) {
                        lastCommand = clientInput.readLine();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            serverThread.start();
        }
    }
}
