package com.trikset.gamepad;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.MotionEvents;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;


@RunWith(Enclosed.class)
public class MainWindowTests {
    @LargeTest
    @RunWith(Parameterized.class)
    public static class SquareButtonTest {
        @Rule
        public final ActivityTestRule<MainActivity> mActivityTestRule = new ActivityTestRule<>(MainActivity.class);

        @Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][]{{R.id.leftPad, "1"}, {R.id.rightPad, "2"}});
        }

        @Parameter
        public int currentPadId;
        @Parameter(1)
        public String currentPadName;

        @Before
        public void initNetworkSettings() {
            SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(mActivityTestRule.getActivity());
            SharedPreferences.Editor preferenceEditor = preferences.edit();

            preferenceEditor.putString(SettingsFragment.SK_HOST_ADDRESS, DummyServer.IP);
            preferenceEditor.putString(
                    SettingsFragment.SK_HOST_PORT,
                    Integer.toString(DummyServer.DEFAULT_PORT));
            // In order not to receive keep-alive messages
            preferenceEditor.putString(SettingsFragment.SK_KEEPALIVE, "100000000");

            preferenceEditor.commit();
        }

        @Test
        public void squareButtonsShouldHandleCircularTapsCorrectly() {
            DummyServer server = new DummyServer();

            onView(withId(currentPadId)).perform(movingTap());
            server.stopListening();

            Iterator<String> messages = server.getReceivedMessages().iterator();
            while (messages.hasNext()) {
                final String current = messages.next();

                if (messages.hasNext()) {
                    assertNotEquals(String.format("pad %s up", currentPadName), current);

                    String[] splitCommand = current.split(" ");
                    assertEquals(4, splitCommand.length);
                    assertEquals("pad", splitCommand[0]);
                    assertEquals(currentPadName, splitCommand[1]);

                    int x = Integer.parseInt(splitCommand[2]);
                    int y = Integer.parseInt(splitCommand[3]);
                    int radius = (int) Math.sqrt(x * x + y * y);
                    assertTrue((radius > 40) && (radius < 60));
                } else {
                    assertEquals(String.format("pad %s up", currentPadName), current);
                }
            }
        }

        @Test
        public void squareButtonsShouldHandleDiagonalTapsCorrectly() {
            DummyServer server = new DummyServer();

            onView(withId(currentPadId)).perform(diagonalTap());
            server.stopListening();

            Iterator<String> messages = server.getReceivedMessages().iterator();
            int currentIndex = 0;
            while (messages.hasNext()) {
                String current = messages.next();

                if (!messages.hasNext()) {
                    assertEquals(String.format("pad %s up", currentPadName), current);
                    break;
                }

                assertNotEquals(String.format("pad %s up", currentPadName), current);

                String[] splitCommand = current.split(" ");
                assertEquals(4, splitCommand.length);
                assertEquals("pad", splitCommand[0]);
                assertEquals(currentPadName, splitCommand[1]);

                int x = Integer.parseInt(splitCommand[2]);
                int y = Integer.parseInt(splitCommand[3]);

                assertTrue(Math.abs(-100 + currentIndex * 200 / 10 - x) <= 25);
                assertTrue(Math.abs(100 - currentIndex * 200 / 10 - y) <= 25);

                ++currentIndex;
            }
        }

        private ViewAction diagonalTap() {
            return new ViewAction() {
                final static int tapSegmentCount = 10;
                final float[] tapPrecision = new float[]{1f, 1f};

                @Override
                public Matcher<View> getConstraints() {
                    return isDisplayed();
                }

                @Override
                public String getDescription() {
                    return "Diagonal tap from top left corner to bottom right corner";
                }

                @Override
                public void perform(UiController uiController, View view) {
                    int[] topLeftCoords = new int[2];
                    view.getLocationOnScreen(topLeftCoords);

                    final float[] startCoords = {topLeftCoords[0] + tapPrecision[0], topLeftCoords[1] - tapPrecision[1]};
                    MotionEvent tap = MotionEvents.sendDown(uiController, startCoords, tapPrecision).down;
                    uiController.loopMainThreadUntilIdle();
                    try {
                        for (int i = 1; i < tapSegmentCount; ++i) {
                            final float[] currentCoords = {startCoords[0] + (float) i * (view.getWidth()) / tapSegmentCount, startCoords[1] - (float) i * (view.getHeight()) / tapSegmentCount,};
                            if (!MotionEvents.sendMovement(uiController, tap, currentCoords)) {
                                MotionEvents.sendCancel(uiController, tap);
                                break;
                            }
                        }
                        if (!MotionEvents.sendUp(uiController, tap)) {
                            MotionEvents.sendCancel(uiController, tap);
                        }
                    } finally {
                        tap.recycle();
                    }
                }
            };
        }

        private ViewAction movingTap() {
            return new ViewAction() {
                final static int tapSegmentCount = 10;
                final float[] tapPrecision = new float[]{1f, 1f};

                @Override
                public Matcher<View> getConstraints() {
                    return isDisplayed();
                }

                @Override
                public String getDescription() {
                    return "Circular press around the starting point";
                }

                @Override
                public void perform(UiController uiController, View view) {
                    final int tapRadius = view.getWidth() / 4;

                    int[] topLeftCoords = new int[2];
                    view.getLocationOnScreen(topLeftCoords);
                    int[] centerCoords = {topLeftCoords[0] + view.getWidth() / 2, topLeftCoords[1] + view.getWidth() / 2};

                    float[] startCoords = new float[]{centerCoords[0] + tapRadius, centerCoords[1]};
                    MotionEvent tap = MotionEvents.sendDown(uiController, startCoords, tapPrecision).down;
                    uiController.loopMainThreadForAtLeast(100);
                    for (int i = 1; i <= tapSegmentCount; ++i) {
                        double currentAngle = 2 * i * Math.PI / tapSegmentCount;
                        float[] currentCoords = {centerCoords[0] + tapRadius * (float) Math.cos(currentAngle), centerCoords[1] + tapRadius * (float) Math.sin(currentAngle)};

                        MotionEvents.sendMovement(uiController, tap, currentCoords);
                        uiController.loopMainThreadForAtLeast(50);
                    }
                    MotionEvents.sendUp(uiController, tap);
                }
            };
        }
    }

    @LargeTest
    @RunWith(JUnit4.class)
    public static class MagicButtonsTests {
        @Rule
        public final ActivityTestRule<MainActivity> mActivityTestRule = new ActivityTestRule<>(MainActivity.class);

        @Before
        public void initNetworkSettings() {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mActivityTestRule.getActivity());
            SharedPreferences.Editor preferenceEditor = preferences.edit();

            preferenceEditor.putString(SettingsFragment.SK_HOST_ADDRESS, DummyServer.IP);
            preferenceEditor.putString(SettingsFragment.SK_HOST_PORT, Integer.toString(DummyServer.DEFAULT_PORT));
            // In order not to receive keep-alive messages
            preferenceEditor.putString(SettingsFragment.SK_KEEPALIVE, "100000000");

            preferenceEditor.commit();
        }

        @Test
        public void magicButtonsShouldSendCorrectCommands() {
            DummyServer server = new DummyServer();

            onView(withId(R.id.buttons)).perform(pressButtons());
            server.stopListening();

            Iterator<String> messages = server.getReceivedMessages().iterator();
            for (int i = 1; i <= 5; ++i) {
                assertTrue(messages.hasNext());
                String currentMessage = messages.next();

                assertEquals(String.format("btn %d down", i), currentMessage);
            }
            assertFalse(messages.hasNext());
        }

        private ViewAction pressButtons() {
            return new ViewAction() {
                final float[] tapPrecision = {1f, 1f};

                @Override
                public Matcher<View> getConstraints() {
                    return isDisplayed();
                }

                @Override
                public String getDescription() {
                    return "Clicks on each of magic buttons";
                }

                @Override
                public void perform(UiController uiController, View view) {
                    final ViewGroup buttons = (ViewGroup) view;

                    for (int i = 0; i <= 4; ++i) {
                        final View currentButton = buttons.getChildAt(i);

                        int[] buttonTopLeftCoords = new int[2];
                        currentButton.getLocationOnScreen(buttonTopLeftCoords);
                        float[] clickCoords = {buttonTopLeftCoords[0] + (float) currentButton.getWidth() / 2, buttonTopLeftCoords[1] + (float) currentButton.getHeight() / 2};

                        MotionEvent tap = MotionEvents.sendDown(uiController, clickCoords, tapPrecision).down;
                        uiController.loopMainThreadForAtLeast(50);
                        MotionEvents.sendUp(uiController, tap);
                    }
                }
            };
        }
    }
}


