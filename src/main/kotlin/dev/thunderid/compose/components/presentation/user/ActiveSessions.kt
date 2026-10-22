// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.user

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.android.IAMException
import dev.thunderid.android.R
import dev.thunderid.android.SSOSession
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The browser, operating system and device model a User-Agent names, and whether it came from a
 * native app rather than a browser. A part the string doesn't reveal is null.
 */
data class UserAgentDescription(
    val browser: String? = null,
    val os: String? = null,
    val device: String? = null,
    val isApp: Boolean = false,
)

// The User-Agent the mobile SDKs send: `ThunderID-Android/1.4.0 (Android 14; Google Pixel 8)`, or the
// iOS equivalent. The Flutter SDK bridges to those, so its requests carry the same header. The
// first word of the comment is the operating system, the part after `;` the device.
private val NATIVE_SDK_PATTERN = Regex("""\bThunderID-(?:Android|iOS)/\S*\s*\((\w+)[^;)]*(?:;\s*([^)]+))?\)""")

// Checked in order, matching the JavaScript SDK's describeUserAgent: several browsers also carry the
// tokens of the ones they derive from, so Edge, Opera and Samsung Internet come before Chrome, and
// Chrome before Safari.
private val BROWSER_PATTERNS =
    listOf(
        Regex("""\bEdg(?:e|A|iOS)?/""") to "Edge",
        Regex("""\b(?:OPR|Opera)/""") to "Opera",
        Regex("""\bSamsungBrowser/""") to "Samsung Internet",
        Regex("""\b(?:Firefox|FxiOS)/""") to "Firefox",
        Regex("""\b(?:Chrome|CriOS)/""") to "Chrome",
        Regex("""\bVersion/[\d.]+.*\bSafari/""") to "Safari",
    )

private val OS_PATTERNS =
    listOf(
        Regex("""\bWindows\b""") to "Windows",
        Regex("""\b(?:iPhone|iPad|iPod)\b""") to "iOS",
        Regex("""\bAndroid\b""") to "Android",
        Regex("""\bCrOS\b""") to "ChromeOS",
        Regex("""\bMac OS X\b|\bMacintosh\b""") to "macOS",
        Regex("""\bLinux\b""") to "Linux",
    )

/**
 * Reads the browser and operating system from a User-Agent, or the device from a mobile SDK's, for
 * showing a session as, for example, "Chrome on macOS". It recognizes the common browsers only, and
 * is for display, never for decisions.
 */
fun describeUserAgent(userAgent: String?): UserAgentDescription {
    if (userAgent.isNullOrEmpty()) return UserAgentDescription()
    NATIVE_SDK_PATTERN.find(userAgent)?.let { match ->
        val device = match.groupValues[2].trim().ifEmpty { null }
        return UserAgentDescription(os = match.groupValues[1], device = device, isApp = true)
    }

    fun firstMatch(patterns: List<Pair<Regex, String>>): String? = patterns.firstOrNull { it.first.containsMatchIn(userAgent) }?.second
    return UserAgentDescription(browser = firstMatch(BROWSER_PATTERNS), os = firstMatch(OS_PATTERNS))
}

/** Sessions that share an operating system, listed together the way the Account Components design groups them. */
data class SessionGroup(
    val os: String?,
    val sessions: List<SSOSession>,
) {
    val hasCurrent: Boolean get() = sessions.any { it.isCurrent }
}

/**
 * Groups sessions by the operating system their User-Agent names. The group holding the current
 * session comes first; the rest keep the server's order, most recently active first.
 */
internal fun groupSessionsByOs(sessions: List<SSOSession>): List<SessionGroup> =
    sessions
        .groupBy { describeUserAgent(it.userAgent).os }
        .map { (os, grouped) -> SessionGroup(os, grouped) }
        .sortedByDescending { it.hasCurrent }

/** Formats a server timestamp as a medium date and short time in [locale], or returns it unchanged when it can't be read. */
internal fun formatSessionTime(
    value: String,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    runCatching {
        OffsetDateTime
            .parse(value)
            .atZoneSameInstant(zone)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    }.getOrDefault(value)

/** The address the session was last seen from, falling back to the one it was created from. */
internal fun sessionIp(session: SSOSession): String? = session.lastActiveIp ?: session.creationIp

/** Whether a revoke failed only because the session is already gone, revoked elsewhere or expired. */
internal fun isAlreadyRevoked(error: Throwable): Boolean = error is IAMException && error.code == ThunderIDErrorCode.NOT_FOUND

@DrawableRes
internal fun osIcon(os: String?): Int =
    when (os) {
        "Android" -> R.drawable.ic_os_android
        "iOS", "macOS" -> R.drawable.ic_os_apple
        "Windows" -> R.drawable.ic_os_windows
        "Linux" -> R.drawable.ic_os_linux
        "ChromeOS" -> R.drawable.ic_browser_chrome
        else -> R.drawable.ic_monitor
    }

@DrawableRes
internal fun browserIcon(browser: String?): Int =
    when (browser) {
        "Chrome" -> R.drawable.ic_browser_chrome
        "Firefox" -> R.drawable.ic_browser_firefox
        "Opera" -> R.drawable.ic_browser_opera
        "Safari" -> R.drawable.ic_browser_safari
        else -> R.drawable.ic_globe
    }

@DrawableRes
internal fun clientIcon(description: UserAgentDescription): Int =
    if (description.browser == null && description.isApp) R.drawable.ic_smartphone else browserIcon(description.browser)

/**
 * What the session is listed as: the browser, the device a native app runs on, or, when a native app
 * names no device, the application that started the session. That name comes from the server, not
 * from the client.
 */
internal fun clientName(
    session: SSOSession,
    i18n: ThunderIDI18n,
): String {
    val description = describeUserAgent(session.userAgent)
    return description.browser ?: description.device ?: startingAppName(session) ?: i18n.sessions("unknownBrowser")
}

// The server lists participants first joined first, so the first started the session.
private fun startingAppName(session: SSOSession): String? = session.participants.firstOrNull()?.appName

/** Resolves an active-sessions i18n key and substitutes `{name}` placeholders in one call. */
internal fun ThunderIDI18n.sessions(
    key: String,
    vararg values: Pair<String, Any>,
): String = values.fold(resolve("activeSessions.$key")) { text, (name, value) -> text.replace("{$name}", value.toString()) }

/** "Chrome on macOS", a native app's device ("Google Pixel 8"), or the part that is known, or "Unknown device". */
internal fun deviceLabel(
    session: SSOSession,
    i18n: ThunderIDI18n,
): String {
    val description = describeUserAgent(session.userAgent)
    // A native app's session is named by its device alone; its applications are listed separately.
    description.device?.let { return it }
    val client = description.browser ?: startingAppName(session)
    val place = description.os
    return when {
        client != null && place != null -> i18n.sessions("device", "client" to client, "place" to place)
        else -> client ?: place ?: i18n.sessions("unknownDevice")
    }
}

/** State passed to the [BaseActiveSessions] builder slot. */
@Stable
class ActiveSessionsState {
    var sessions by mutableStateOf<List<SSOSession>>(emptyList())
        internal set
    var totalCount by mutableStateOf(0)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var revokingSessionId by mutableStateOf<String?>(null)
        internal set
    var revokingOthers by mutableStateOf(false)
        internal set

    internal var onRefresh: () -> Unit = {}
    internal var onRevoke: (String) -> Unit = {}
    internal var onRevokeOthers: () -> Unit = {}

    val groups: List<SessionGroup> get() = groupSessionsByOs(sessions)

    /** Sessions other than the current one, counted across every page, not just the loaded one. */
    val otherCount: Int get() = (totalCount - sessions.count { it.isCurrent }).coerceAtLeast(0)

    fun refresh() = onRefresh()

    fun revoke(sessionId: String) = onRevoke(sessionId)

    fun revokeOthers() = onRevokeOthers()
}

/**
 * Headless active-sessions list. Loads the signed-in user's live SSO sessions from
 * `GET /users/me/sessions`, revokes one or every other session, and hands its [ActiveSessionsState] to
 * a caller-supplied builder, mirroring the [BaseUserProfile] split.
 *
 * Only the first page is loaded, [limit] sessions at most (the server's page size by default);
 * sessions beyond it can still be revoked with [ActiveSessionsState.revokeOthers].
 */
@Composable
fun BaseActiveSessions(
    modifier: Modifier = Modifier,
    limit: Int? = null,
    onRevoked: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    content: @Composable (ActiveSessionsState) -> Unit,
) {
    val thunderState = LocalThunderID.current
    val i18n = thunderState.i18n
    val scope = rememberCoroutineScope()
    val state = remember { ActiveSessionsState() }

    suspend fun load() {
        state.isLoading = true
        try {
            val page = thunderState.client.getUserSessions(limit = limit)
            state.sessions = page.sessions
            state.totalCount = page.totalResults
            state.error = null
        } catch (e: Exception) {
            state.error = i18n.sessions("error.load")
            onError?.invoke()
        } finally {
            state.isLoading = false
        }
    }

    suspend fun runRevoke(call: suspend () -> Unit) {
        state.error = null
        try {
            call()
            load()
            onRevoked?.invoke()
        } catch (e: Exception) {
            if (isAlreadyRevoked(e)) {
                load()
            } else {
                state.error = i18n.sessions("error.revoke")
                onError?.invoke()
            }
        }
    }

    fun revoke(sessionId: String) {
        if (state.revokingSessionId != null || state.revokingOthers) return
        scope.launch {
            state.revokingSessionId = sessionId
            try {
                runRevoke { thunderState.client.revokeUserSession(sessionId) }
            } finally {
                state.revokingSessionId = null
            }
        }
    }

    fun revokeOthers() {
        if (state.revokingSessionId != null || state.revokingOthers) return
        scope.launch {
            state.revokingOthers = true
            try {
                runRevoke { thunderState.client.revokeUserSessions(exceptCurrent = true) }
            } finally {
                state.revokingOthers = false
            }
        }
    }

    state.onRefresh = { scope.launch { load() } }
    state.onRevoke = ::revoke
    state.onRevokeOthers = ::revokeOthers

    LaunchedEffect(limit) { load() }

    Box(modifier = modifier) { content(state) }
}

private enum class SessionConfirm { ONE, OTHERS }

/**
 * Styled default active-sessions list (Account Components design). Renders a summary card with
 * one row per operating system, and a Manage sessions row, that open a full-screen list of every
 * session on tap. A session opens a full-screen detail page, where it can be signed out after a
 * confirmation dialog, matching [ChangeCredential]'s summary-row-then-editor structure.
 *
 * The current session has no Revoke action, since ending it would also end the session the
 * app is using. Use the regular sign-out for that.
 *
 * [title] overrides the "Active sessions" heading; pass an empty string to omit it.
 */
@Composable
fun ActiveSessions(
    modifier: Modifier = Modifier,
    limit: Int? = null,
    title: String? = null,
    onRevoked: (() -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    BaseActiveSessions(
        modifier = modifier,
        limit = limit,
        onRevoked = onRevoked,
    ) { state ->
        var listOpen by remember { mutableStateOf(false) }
        var focusGroup by remember { mutableStateOf<Int?>(null) }
        var openSessionId by remember { mutableStateOf<String?>(null) }
        var confirm by remember { mutableStateOf<SessionConfirm?>(null) }
        val groups = state.groups

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).exposeTestTagsAsResourceIds(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val heading = title ?: i18n.sessions("section")
            if (heading.isNotEmpty()) {
                Text(
                    heading.uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            when {
                state.isLoading && state.sessions.isEmpty() -> {
                    Text(
                        i18n.sessions("loading"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.sessions.isEmpty() -> {
                    Text(
                        state.error ?: i18n.sessions("empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            groups.forEachIndexed { index, group ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                SessionGroupRow(group = group, i18n = i18n) {
                                    focusGroup = index
                                    listOpen = true
                                }
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable {
                                        focusGroup = null
                                        listOpen = true
                                    }.padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("thunderid-action-manageSessions"),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tune),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                i18n.sessions("manage"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        i18n.sessions("footnote"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                    if (!listOpen) {
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (listOpen) {
            SessionListDialog(
                state = state,
                groups = groups,
                focusGroup = focusGroup,
                i18n = i18n,
                onOpenSession = { openSessionId = it },
                onRevokeOthers = { confirm = SessionConfirm.OTHERS },
                onDismiss = { listOpen = false },
            )
        }

        // A session that has been signed out drops out of the reloaded list, which closes its page.
        val openSession = state.sessions.firstOrNull { it.id == openSessionId }
        if (openSession != null) {
            SessionDetailDialog(
                session = openSession,
                state = state,
                i18n = i18n,
                onRevokeSession = { confirm = SessionConfirm.ONE },
                onDismiss = { openSessionId = null },
            )
        }

        when (confirm) {
            SessionConfirm.ONE -> {
                if (openSession != null) {
                    RevokeConfirmDialog(
                        title = i18n.sessions("confirm.one.title"),
                        body = i18n.sessions("confirm.one.body", "device" to deviceLabel(openSession, i18n)),
                        i18n = i18n,
                        onConfirm = {
                            confirm = null
                            state.revoke(openSession.id)
                        },
                        onDismiss = { confirm = null },
                    )
                }
            }

            SessionConfirm.OTHERS -> {
                val count = state.otherCount
                RevokeConfirmDialog(
                    title = i18n.sessions("confirm.others.title"),
                    body =
                        if (count == 1) {
                            i18n.sessions("confirm.others.body.one")
                        } else {
                            i18n.sessions("confirm.others.body.other", "count" to count)
                        },
                    i18n = i18n,
                    onConfirm = {
                        confirm = null
                        state.revokeOthers()
                    },
                    onDismiss = { confirm = null },
                )
            }

            null -> {}
        }
    }
}
