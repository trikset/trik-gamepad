package com.trikset.gamepad;

import static android.os.Looper.getMainLooper;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.util.concurrent.PausedExecutorService;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

@RunWith(RobolectricTestRunner.class)
@LooperMode(PAUSED)
@Config(sdk = {Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK})
public class SenderServiceTest {
    private final PausedExecutorService mExecutor = new PausedExecutorService();

    @Test
    public void senderServiceShouldConnectToServerSuccessfullyAfterSendingCommand() throws InterruptedException {
        try (DummyServer server = new DummyServer(1, 4)) {
            SenderService client = new SenderService();
            client.setExecutor(mExecutor);
            client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT + 4);
            client.send("");
            mExecutor.runAll();
            shadowOf(getMainLooper()).idle();
            assertTrue(server.isConnected());
        }
    }

    @Test
    public void senderServiceShouldSendSingleCommandCorrectly() throws InterruptedException {
        try (DummyServer server = new DummyServer(1, 0)) {
            SenderService client = new SenderService();
            client.setExecutor(mExecutor);
            client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT);
            client.setKeepaliveTimeout(10000000); // to disable keep-alive messages
            client.send("Test; check");
            mExecutor.runAll();
            shadowOf(getMainLooper()).idle();
            assertEquals("Test; check", server.getLastCommand());
        }
    }

    @Test
    public void senderServiceShouldSendMultipleCommandsCorrectly() throws InterruptedException {
        try (DummyServer server = new DummyServer(5, 1)) {
            SenderService client = new SenderService();
            client.setExecutor(mExecutor);
            client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT + 1);
            client.setKeepaliveTimeout(10000000); // to disable keep-alive messages

            for (int i = 0; i < 5; ++i) {
                client.send(String.format("%d checking", i));
            }
            mExecutor.runAll();
            shadowOf(getMainLooper()).idle();
            assertEquals("4 checking", server.getLastCommand());
        }
    }

    @Test
    public void setTargetShouldSetServerSuccessfully() {
        SenderService client = new SenderService();
        client.setExecutor(mExecutor);
        client.setTarget("someaddr-test", DummyServer.DEFAULT_PORT);
        assertEquals("someaddr-test", client.getHostAddr());
    }

    @Test
    public void senderServiceShouldReturnCorrectKeepaliveTimeout() {
        SenderService client = new SenderService();
        client.setExecutor(mExecutor);
        client.setKeepaliveTimeout(3453);
        assertEquals(3453, client.getKeepaliveTimeout());
        client.setKeepaliveTimeout(1234);
        assertEquals(1234, client.getKeepaliveTimeout());
    }

    private static class DummyServer implements AutoCloseable {
        static final String IP = "localhost";
        static final int DEFAULT_PORT = 12345;

        final int port;
        private final Thread mThread;

        private boolean isConnected = false;

        public boolean isConnected() {
            return isConnected;
        }

        private String lastCommand;

        public String getLastCommand() {
            return lastCommand;
        }

        DummyServer(final int cmdNumber, final int portShift) {
            port = DEFAULT_PORT + portShift;
            mThread = new Thread(() -> {
                try (ServerSocket server = new ServerSocket(port)) {
                    Socket client = server.accept();
                    isConnected = true;

                    BufferedReader clientInput = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    for (int i = 0; i < cmdNumber; ++i) {
                        lastCommand = clientInput.readLine();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            mThread.start();
        }

        @Override
        public void close() {
            mThread.interrupt();
        }
    }
}
