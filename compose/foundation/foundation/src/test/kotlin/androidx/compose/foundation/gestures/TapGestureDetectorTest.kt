/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalPointerInput::class)

package androidx.compose.foundation.gestures

import androidx.compose.ui.gesture.DoubleTapTimeout
import androidx.compose.ui.gesture.ExperimentalPointerInput
import androidx.compose.ui.gesture.LongPressTimeout
import androidx.compose.ui.input.pointer.consumeDownChange
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.unit.inMilliseconds
import androidx.compose.ui.unit.milliseconds
import kotlinx.coroutines.delay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@OptIn(ExperimentalPointerInput::class)
class TapGestureDetectorTest {
    private var pressed = false
    private var released = false
    private var canceled = false
    private var tapped = false
    private var doubleTapped = false
    private var longPressed = false

    private val util = SuspendingGestureTestUtil {
        detectTapGestures(
            onPress = {
                pressed = true
                if (tryAwaitRelease()) {
                    released = true
                } else {
                    canceled = true
                }
            },
            onTap = {
                tapped = true
            }
        )
    }

    private val allGestures = SuspendingGestureTestUtil {
        detectTapGestures(
            onPress = {
                pressed = true
                try {
                    awaitRelease()
                    released = true
                } catch (_: GestureCancellationException) {
                    canceled = true
                }
            },
            onTap = { tapped = true },
            onLongPress = { longPressed = true },
            onDoubleTap = { doubleTapped = true }
        )
    }

    @Before
    fun setup() {
        pressed = false
        released = false
        canceled = false
        tapped = false
        doubleTapped = false
        longPressed = false
    }

    /**
     * Clicking in the region should result in the callback being invoked.
     */
    @Test
    fun normalTap() = util.executeInComposition {
        val down = down(5f, 5f)
        assertTrue(down.consumed.downChange)

        assertTrue(pressed)
        assertFalse(tapped)
        assertFalse(released)

        val up = down.up(50.milliseconds)
        assertTrue(up.consumed.downChange)

        assertTrue(tapped)
        assertTrue(released)
        assertFalse(canceled)
    }

    /**
     * Clicking in the region should result in the callback being invoked.
     */
    @Test
    fun normalTapWithAllGestures() = allGestures.executeInComposition {
        val down = down(5f, 5f)
        assertTrue(down.consumed.downChange)

        assertTrue(pressed)

        val up = down.up(50.milliseconds)
        assertTrue(up.consumed.downChange)

        assertTrue(released)

        // we have to wait for the double-tap timeout before we receive an event

        assertFalse(tapped)
        assertFalse(doubleTapped)

        delay(DoubleTapTimeout.inMilliseconds() + 10)

        assertTrue(tapped)
        assertFalse(doubleTapped)
    }

    /**
     * Clicking in the region should result in the callback being invoked.
     */
    @Test
    fun normalDoubleTap() = allGestures.executeInComposition {
        val up = down(5f, 5f)
            .up()
        assertTrue(up.consumed.downChange)

        assertTrue(pressed)
        assertTrue(released)
        assertFalse(tapped)
        assertFalse(doubleTapped)

        pressed = false
        released = false

        val up2 = down(5f, 5f, 50.milliseconds)
            .up()
        assertTrue(up2.consumed.downChange)

        assertFalse(tapped)
        assertTrue(doubleTapped)
        assertTrue(pressed)
        assertTrue(released)
    }

    /**
     * Long press in the region should result in the callback being invoked.
     */
    @Test
    fun normalLongPress() = allGestures.executeInComposition {
        val down = down(5f, 5f)
        assertTrue(down.consumed.downChange)

        assertTrue(pressed)
        delay(LongPressTimeout.inMilliseconds() + 10)

        assertTrue(longPressed)

        val up = down.up(500.milliseconds)
        assertTrue(up.consumed.downChange)

        assertFalse(tapped)
        assertFalse(doubleTapped)
        assertTrue(released)
        assertFalse(canceled)
    }

    /**
     * Pressing in the region, sliding out and then lifting should result in
     * the callback not being invoked
     */
    @Test
    fun tapMiss() = util.executeInComposition {
        val up = down(5f, 5f)
            .moveTo(15f, 15f)
            .up()

        assertTrue(pressed)
        assertTrue(canceled)
        assertFalse(released)
        assertFalse(tapped)
        assertFalse(up.consumed.downChange)
    }

    /**
     * Pressing in the region, sliding out and then lifting should result in
     * the callback not being invoked
     */
    @Test
    fun longPressMiss() = allGestures.executeInComposition {
        val pointer = down(5f, 5f)
            .moveTo(15f, 15f)

        delay(DoubleTapTimeout.inMilliseconds() + 10)
        val up = pointer.up()
        assertFalse(up.consumed.downChange)

        assertTrue(pressed)
        assertFalse(released)
        assertTrue(canceled)
        assertFalse(tapped)
        assertFalse(longPressed)
        assertFalse(doubleTapped)
    }

    /**
     * Pressing in the region, sliding out and then lifting should result in
     * the callback not being invoked for double-tap
     */
    @Test
    fun doubleTapMiss() = allGestures.executeInComposition {
        val up1 = down(5f, 5f).up()
        assertTrue(up1.consumed.downChange)

        assertTrue(pressed)
        assertTrue(released)
        assertFalse(canceled)

        pressed = false
        released = false

        val up2 = down(5f, 5f, 50.milliseconds)
            .moveTo(15f, 15f)
            .up()

        assertFalse(up2.consumed.downChange)

        assertTrue(pressed)
        assertFalse(released)
        assertTrue(canceled)
        assertTrue(tapped)
        assertFalse(longPressed)
        assertFalse(doubleTapped)
    }

    /**
     * Pressing in the region, sliding out, then back in, then lifting
     * should result the gesture being canceled.
     */
    @Test
    fun tapOutAndIn() = util.executeInComposition {
        val up = down(5f, 5f)
            .moveTo(15f, 15f)
            .moveTo(6f, 6f)
            .up()

        assertFalse(tapped)
        assertFalse(up.consumed.downChange)
        assertTrue(pressed)
        assertFalse(released)
        assertTrue(canceled)
    }

    /**
     * After a first tap, a second tap should also be detected.
     */
    @Test
    fun secondTap() = util.executeInComposition {
        down(5f, 5f)
            .up()

        assertTrue(pressed)
        assertTrue(released)
        assertFalse(canceled)

        tapped = false
        pressed = false
        released = false

        val up2 = down(4f, 4f)
            .up()
        assertTrue(tapped)
        assertTrue(up2.consumed.downChange)
        assertTrue(pressed)
        assertTrue(released)
        assertFalse(canceled)
    }

    /**
     * Clicking in the region with the up already consumed should result in the callback not
     * being invoked.
     */
    @Test
    fun consumedUpTap() = util.executeInComposition {
        val down = down(5f, 5f)

        assertFalse(tapped)
        assertTrue(pressed)

        down.up {
            consumeDownChange()
        }

        assertFalse(tapped)
        assertFalse(released)
        assertTrue(canceled)
    }

    /**
     * Clicking in the region with the motion consumed should result in the callback not
     * being invoked.
     */
    @Test
    fun consumedMotionTap() = util.executeInComposition {
        down(5f, 5f)
            .moveTo(6f, 2f) {
                consumePositionChange(1f, -3f)
            }
            .up(50.milliseconds)

        assertFalse(tapped)
        assertTrue(pressed)
        assertFalse(released)
        assertTrue(canceled)
    }

    /**
     * Ensure that two-finger taps work.
     */
    @Test
    fun twoFingerTap() = util.executeInComposition {
        val down = down(1f, 1f)
        assertTrue(down.consumed.downChange)

        assertTrue(pressed)
        pressed = false

        val down2 = down(9f, 5f)
        assertFalse(down2.consumed.downChange)

        assertFalse(pressed)

        val up = down.up()
        assertFalse(up.consumed.downChange)
        assertFalse(tapped)
        assertFalse(released)

        val up2 = down2.up()
        assertTrue(up2.consumed.downChange)

        assertTrue(tapped)
        assertTrue(released)
        assertFalse(canceled)
    }

    /**
     * A position change consumption on any finger should cause tap to cancel.
     */
    @Test
    fun twoFingerTapCancel() = util.executeInComposition {
        val down = down(1f, 1f)

        assertTrue(pressed)

        val down2 = down(9f, 5f)

        val up = down.moveTo(5f, 5f) {
            consumePositionChange(4f, 4f)
        }.up()
        assertFalse(up.consumed.downChange)

        assertFalse(tapped)
        assertTrue(canceled)

        val up2 = down2.up(50.milliseconds)
        assertFalse(up2.consumed.downChange)

        assertFalse(tapped)
        assertFalse(released)
    }

    /**
     * Detect the second tap as long press.
     */
    @Test
    fun secondTapLongPress() = allGestures.executeInComposition {
        down(5f, 5f).up()

        assertTrue(pressed)
        assertTrue(released)
        assertFalse(canceled)
        assertFalse(tapped)
        assertFalse(doubleTapped)
        assertFalse(longPressed)

        pressed = false
        released = false

        val secondDown = down(5f, 5f, 50.milliseconds)

        assertTrue(pressed)

        delay(LongPressTimeout.inMilliseconds() + 10)

        assertTrue(tapped)
        assertTrue(longPressed)
        assertFalse(released)
        assertFalse(canceled)

        secondDown.up(500.milliseconds)
        assertTrue(released)
    }
}