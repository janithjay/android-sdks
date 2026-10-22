// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.http

import org.junit.Assert.assertEquals
import org.junit.Test

class UserAgentTest {
    @Test
    fun `build names the SDK, the Android version and the model`() {
        assertEquals("ThunderID-Android/1.4.0 (Android 14; Pixel 8)", UserAgent.build("1.4.0", "14", "Pixel 8"))
    }

    @Test
    fun `build leaves out what the platform does not report`() {
        assertEquals("ThunderID-Android/1.4.0 (Android)", UserAgent.build("1.4.0", null, " "))
    }

    @Test
    fun `deviceName reads as the device a person knows`() {
        assertEquals("Google Pixel 8", UserAgent.deviceName("Google", "Pixel 8", isEmulator = false))
        assertEquals("Samsung SM-S918B", UserAgent.deviceName("samsung", "SM-S918B", isEmulator = false))
        assertEquals("OnePlus CPH2449", UserAgent.deviceName("OnePlus", "OnePlus CPH2449", isEmulator = false))
        assertEquals("Android Emulator", UserAgent.deviceName("Google", "sdk_gphone16k_arm64", isEmulator = true))
        assertEquals("Google", UserAgent.deviceName("Google", null, isEmulator = false))
    }

    @Test
    fun `build strips characters that would break the header`() {
        assertEquals("ThunderID-Android/1.4.0 (Android 14; Pixel 8 Pro)", UserAgent.build("1.4.0", "14", "Pixel; 8 (Pro)\n"))
    }
}
