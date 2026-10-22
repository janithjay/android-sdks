// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.user

import dev.thunderid.android.IAMException
import dev.thunderid.android.SSOSession
import dev.thunderid.android.SSOSessionParticipant
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class ActiveSessionsTest {
    private val chromeOnMac =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36"
    private val edgeOnWindows =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36 Edg/124.0"
    private val safariOnIphone =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
    private val chromeOnAndroid =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"

    private fun session(
        id: String,
        userAgent: String?,
        isCurrent: Boolean = false,
    ) = SSOSession(
        id = id,
        userId = "u1",
        authenticatedAt = "2026-10-09T19:30:00Z",
        createdAt = "2026-10-09T19:30:00Z",
        lastActiveAt = "2026-10-12T09:14:00Z",
        userAgent = userAgent,
        isCurrent = isCurrent,
    )

    // ── describeUserAgent ───────────────────────────────────────────────────

    @Test
    fun `describeUserAgent reads the browser and operating system`() {
        assertEquals(UserAgentDescription("Chrome", "macOS"), describeUserAgent(chromeOnMac))
        assertEquals(UserAgentDescription("Safari", "iOS"), describeUserAgent(safariOnIphone))
    }

    @Test
    fun `describeUserAgent prefers a derived browser over the one it is built on`() {
        assertEquals(UserAgentDescription("Edge", "Windows"), describeUserAgent(edgeOnWindows))
    }

    @Test
    fun `describeUserAgent prefers Android over the Linux token it carries`() {
        assertEquals("Android", describeUserAgent(chromeOnAndroid).os)
    }

    @Test
    fun `describeUserAgent leaves out what it cannot recognize`() {
        assertEquals(UserAgentDescription(), describeUserAgent(null))
        assertEquals(UserAgentDescription(), describeUserAgent(""))
        assertEquals(UserAgentDescription(), describeUserAgent("curl/8.4.0"))
    }

    @Test
    fun `describeUserAgent reads the device from the mobile SDK User-Agent`() {
        assertEquals(
            UserAgentDescription(os = "Android", device = "Google Pixel 8", isApp = true),
            describeUserAgent("ThunderID-Android/1.4.0 (Android 14; Google Pixel 8)"),
        )
        assertEquals(
            UserAgentDescription(os = "iOS", device = "iPhone15,2", isApp = true),
            describeUserAgent("ThunderID-iOS/1.3.0 (iOS 17.4; iPhone15,2)"),
        )
        assertEquals(
            UserAgentDescription(os = "Android", isApp = true),
            describeUserAgent("ThunderID-Android/1.4.0 (Android 14)"),
        )
    }

    // ── labels ──────────────────────────────────────────────────────────────

    @Test
    fun `a native app session is named after its device, or the application that started it`() {
        val i18n = ThunderIDI18n()
        val native =
            session("s1", "ThunderID-Android/1.4.0 (Android 14; Google Pixel 8)").copy(
                participants =
                    listOf(
                        SSOSessionParticipant("a1", "Quickstart", "2026-10-09T19:30:00Z", "2026-10-09T19:30:00Z"),
                        SSOSessionParticipant("a2", "Console", "2026-10-09T19:40:00Z", "2026-10-09T19:40:00Z"),
                    ),
            )

        assertEquals("Google Pixel 8", clientName(native, i18n))
        assertEquals("Google Pixel 8", deviceLabel(native, i18n))
        val noDevice = native.copy(userAgent = "ThunderID-Android/1.4.0 (Android 14)")
        assertEquals("Quickstart", clientName(noDevice, i18n))
        assertEquals("Quickstart on Android", deviceLabel(noDevice, i18n))
        assertEquals("Chrome on macOS", deviceLabel(session("s2", chromeOnMac), i18n))
        assertEquals("Unknown browser", clientName(session("s3", "curl/8.4.0"), i18n))
    }

    @Test
    fun `a native app session shows the app icon`() {
        assertEquals(dev.thunderid.android.R.drawable.ic_smartphone, clientIcon(UserAgentDescription(os = "Android", isApp = true)))
        assertEquals(dev.thunderid.android.R.drawable.ic_browser_chrome, clientIcon(describeUserAgent(chromeOnAndroid)))
    }

    // ── groupSessionsByOs ───────────────────────────────────────────────────

    @Test
    fun `groupSessionsByOs puts the current session's group first and keeps the server order otherwise`() {
        val groups =
            groupSessionsByOs(
                listOf(
                    session("s1", chromeOnMac),
                    session("s2", edgeOnWindows),
                    session("s3", chromeOnAndroid, isCurrent = true),
                    session("s4", chromeOnMac),
                ),
            )

        assertEquals(listOf("Android", "macOS", "Windows"), groups.map { it.os })
        assertEquals(listOf("s1", "s4"), groups[1].sessions.map { it.id })
        assertTrue(groups[0].hasCurrent)
        assertFalse(groups[1].hasCurrent)
    }

    @Test
    fun `groupSessionsByOs groups unrecognized user agents together`() {
        val groups = groupSessionsByOs(listOf(session("s1", null), session("s2", "curl/8.4.0")))

        assertEquals(1, groups.size)
        assertNull(groups[0].os)
        assertEquals(2, groups[0].sessions.size)
    }

    // ── formatting ──────────────────────────────────────────────────────────

    @Test
    fun `formatSessionTime formats a timestamp in the given zone`() {
        val formatted = formatSessionTime("2026-10-09T19:30:00Z", Locale.US, ZoneOffset.ofHoursMinutes(5, 30))

        assertTrue(formatted, formatted.startsWith("Oct 10, 2026"))
    }

    @Test
    fun `formatSessionTime accepts an offset timestamp`() {
        val formatted = formatSessionTime("2026-10-09T19:30:00+05:30", Locale.US, ZoneOffset.UTC)

        assertTrue(formatted, formatted.startsWith("Oct 9, 2026"))
    }

    @Test
    fun `formatSessionTime returns an unreadable value unchanged`() {
        assertEquals("not a date", formatSessionTime("not a date", Locale.US))
    }

    @Test
    fun `sessionIp prefers the last active address`() {
        val base = session("s1", null)

        assertEquals("10.0.0.2", sessionIp(base.copy(creationIp = "10.0.0.1", lastActiveIp = "10.0.0.2")))
        assertEquals("10.0.0.1", sessionIp(base.copy(creationIp = "10.0.0.1")))
        assertNull(sessionIp(base))
    }

    // ── revoke errors ───────────────────────────────────────────────────────

    @Test
    fun `isAlreadyRevoked is true only for a not-found failure`() {
        assertTrue(isAlreadyRevoked(IAMException(ThunderIDErrorCode.NOT_FOUND, "Not found")))
        assertFalse(isAlreadyRevoked(IAMException(ThunderIDErrorCode.FORBIDDEN, "Forbidden")))
        assertFalse(isAlreadyRevoked(RuntimeException("network error")))
    }

    // ── icons ───────────────────────────────────────────────────────────────

    @Test
    fun `icons fall back to generic glyphs for unknown platforms`() {
        assertEquals(dev.thunderid.android.R.drawable.ic_os_apple, osIcon("iOS"))
        assertEquals(dev.thunderid.android.R.drawable.ic_monitor, osIcon(null))
        assertEquals(dev.thunderid.android.R.drawable.ic_globe, browserIcon("Samsung Internet"))
    }
}
