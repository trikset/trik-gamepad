package com.trikset.gamepad;

import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Iterator;
import java.util.ListIterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class KeepAliveTests {
    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule =
            new ActivityTestRule<>(MainActivity.class);

    @Test
    public void keepAliveShouldBeReceivedAfterGivenTimePeriod() throws InterruptedException {
        final int timeout = 4000;

        DummyServer server = new DummyServer();
        SenderService client = mActivityTestRule.getActivity().getSenderService();

        client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT);
        client.setKeepaliveTimeout(timeout);
        // In order to set connection up
        client.send("test");

        Thread.sleep(timeout + 300);
        server.stopListening();

        ListIterator<String> messages = server.getReceivedMessages().listIterator();
        while (messages.hasNext()) {
            messages.next();
        }
        assertEquals(String.format("keepalive %d", timeout), messages.previous());
    }

    @Test
    public void keepaliveMessagesShouldNotBeSentAfterDisconnect() throws InterruptedException {
        DummyServer server = new DummyServer();
        SenderService client = mActivityTestRule.getActivity().getSenderService();
        client.setTarget(DummyServer.IP, DummyServer.DEFAULT_PORT);
        client.setKeepaliveTimeout(1000);

        // In order to set connection up
        client.send("testtest");
        Thread.sleep(500);
        client.disconnect("testtest");

        Thread.sleep(600);
        server.stopListening();

        Iterator<String> messages = server.getReceivedMessages().iterator();
        assertTrue(messages.hasNext());
        assertEquals("testtest", messages.next());
        assertFalse(messages.hasNext());
    }

}
