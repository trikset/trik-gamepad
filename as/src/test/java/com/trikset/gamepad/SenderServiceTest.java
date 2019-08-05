package com.trikset.gamepad;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SenderServiceTest {
    @Test
    public void senderServiceShouldConnectToServerSuccessfullyAfterSendingCommand()
            throws InterruptedException {
        SimpleDummyServer server = new SimpleDummyServer(1, 4);
        SenderService client = new SenderService();
        client.setTarget(KeepaliveDummyServer.IP, KeepaliveDummyServer.DEFAULT_PORT + 4);

        client.send("");
        Thread.sleep(200);
        assertTrue(server.isConnected());
    }

    @Test
    public void senderServiceShouldSendSingleCommandCorrectly() throws InterruptedException {
        SimpleDummyServer server = new SimpleDummyServer(1, 0);
        SenderService client = new SenderService();
        client.setTarget(KeepaliveDummyServer.IP, KeepaliveDummyServer.DEFAULT_PORT);
        client.setKeepaliveTimeout(10000000); // in order not to receive keepalives

        client.send("Test; check");
        Thread.sleep(100);
        assertEquals("Test; check", server.getLastCommand());
    }

    @Test
    public void senderServiceShouldSendMultipleCommandsCorrectly() throws InterruptedException {
        SimpleDummyServer server = new SimpleDummyServer(5, 1);
        SenderService client = new SenderService();
        client.setTarget(KeepaliveDummyServer.IP, KeepaliveDummyServer.DEFAULT_PORT + 1);
        client.setKeepaliveTimeout(10000000); // in order not to receive keepalives

        for (int i = 0; i < 5; ++i) {
            client.send(String.format("%d checking", i));
            Thread.sleep(100);
        }

        assertEquals("4 checking", server.getLastCommand());
    }

    @Test
    public void keepAliveShouldBeReceivedAfterGivenTimePeriod() throws InterruptedException {
        final int timeout = 4000;

        KeepaliveDummyServer server = new KeepaliveDummyServer(timeout, 2);
        SenderService client = new SenderService();
        client.setKeepaliveTimeout(timeout);
        client.setTarget(KeepaliveDummyServer.IP, KeepaliveDummyServer.DEFAULT_PORT + 2);
        // In order to set connection up
        client.send("test");

        Thread.sleep(timeout + 100);

        assertTrue(server.isKeepAliveReceived());
    }

    @Test
    public void setTargetShouldSetServerSuccessfully() {
        SenderService client = new SenderService();
        client.setTarget("someaddr-test", 12345);

        assertEquals("someaddr-test", client.getHostAddr());
    }

    @Test
    public void keepaliveMessagesShouldNotBeSentAfterDisconnect() throws InterruptedException {
        KeepaliveDummyServer server = new KeepaliveDummyServer(500, KeepaliveDummyServer.DEFAULT_PORT + 6);
        SenderService client = new SenderService();
        client.setTarget(KeepaliveDummyServer.IP, KeepaliveDummyServer.DEFAULT_PORT + 6);

        // In order to set connection up
        client.send("testtest");
        Thread.sleep(300);
        client.disconnect("testtest");

        Thread.sleep(500);

        assertFalse(server.isKeepAliveReceived());
    }

    @Test
    public void senderServiceShouldReturnCorrectKeepaliveTimeout() {
        SenderService client = new SenderService();

        client.setKeepaliveTimeout(3453);
        assertEquals(3453, client.getKeepaliveTimeout());

        client.setKeepaliveTimeout(1234);
        assertEquals(1234, client.getKeepaliveTimeout());
    }

    private abstract class DummyServerBase {
        static final String IP = "localhost";
        static final int DEFAULT_PORT = 12345;

        final int port;

        DummyServerBase(final int portShift) {
            port = DEFAULT_PORT + portShift;
        }
    }

    private class SimpleDummyServer extends DummyServerBase {
        private boolean isConnected = false;
        public boolean isConnected() { return isConnected; }

        private String lastCommand;
        public String getLastCommand() { return lastCommand; }


        SimpleDummyServer(final int cmdNumber, final int portShift) {
            super(portShift);

            Thread serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
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
                }
            });
            serverThread.start();
        }
    }

    private class KeepaliveDummyServer extends DummyServerBase {
        private boolean keepAliveReceived = false;
        boolean isKeepAliveReceived() { return keepAliveReceived; }

        KeepaliveDummyServer(final int timeout, final int portShit) {
            super(portShit);

            Thread serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (ServerSocket server = new ServerSocket(port)) {
                        server.setSoTimeout(timeout);

                        Socket client = server.accept();
                        BufferedReader clientInput =
                                new BufferedReader(new InputStreamReader(client.getInputStream()));

                        String command;
                        do {
                            command = clientInput.readLine();
                        } while (!command.equals(String.format("keepalive %d", timeout)));

                        keepAliveReceived = true;
                    } catch (SocketTimeoutException e) {
                        keepAliveReceived = false;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            serverThread.start();
        }

    }
}