// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.user

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thunderid.android.R
import dev.thunderid.android.SSOSession
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import dev.thunderid.compose.i18n.ThunderIDI18n
import java.util.Locale

// The pages and rows the styled [ActiveSessions] renders, kept apart from its state and logic.

/** One operating system's row in the summary card: "2 sessions on Android" over the browsers or apps it holds. */
@Composable
internal fun SessionGroupRow(
    group: SessionGroup,
    i18n: ThunderIDI18n,
    onClick: () -> Unit,
) {
    val clients =
        group.sessions
            .map { clientName(it, i18n) }
            .distinct()
            .joinToString(", ")
    val subtitle = if (group.hasCurrent) "${i18n.sessions("current")} · $clients" else clients

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformBadge(icon = osIcon(group.os), size = 40.dp, iconSize = 20.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(groupTitle(group, i18n), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun groupTitle(
    group: SessionGroup,
    i18n: ThunderIDI18n,
): String {
    val os = group.os ?: i18n.sessions("unknownDevice")
    return if (group.sessions.size == 1) {
        i18n.sessions("group.one", "os" to os)
    } else {
        i18n.sessions("group.other", "count" to group.sessions.size, "os" to os)
    }
}

/** A platform logo centred in a tonal circle. */
@Composable
private fun PlatformBadge(
    @DrawableRes icon: Int,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Box(
        modifier = modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColorFor(color),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Back arrow and title shared by the full-screen pages, matching [ChangeCredential]'s editor header. */
@Composable
private fun PageHeader(
    title: String?,
    i18n: ThunderIDI18n,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = i18n.sessions("cancel"),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Full-screen list of every session, one card per operating system, that [ActiveSessions] opens
 * from its summary card. Opened from a group row, it scrolls to that group's card.
 */
@Composable
internal fun SessionListDialog(
    state: ActiveSessionsState,
    groups: List<SessionGroup>,
    focusGroup: Int?,
    i18n: ThunderIDI18n,
    onOpenSession: (String) -> Unit,
    onRevokeOthers: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusGroup) {
        focusGroup?.takeIf { it < groups.size }?.let { listState.scrollToItem(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().exposeTestTagsAsResourceIds()) {
                PageHeader(title = i18n.sessions("title"), i18n = i18n, onBack = onDismiss)
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(groups) { group ->
                        SessionGroupCard(group = group, i18n = i18n, onOpenSession = onOpenSession)
                    }
                    // Without the current session in the list, "you'll stay signed in" can't be promised.
                    if (state.otherCount > 0 && state.sessions.any { it.isCurrent }) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedCard(
                                    onClick = onRevokeOthers,
                                    enabled = !state.revokingOthers,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("thunderid-action-revokeOtherSessions"),
                                ) {
                                    Text(
                                        i18n.sessions("revokeOthers"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(16.dp),
                                    )
                                }
                                Text(
                                    i18n.sessions("revokeOthers.note"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                    state.error?.let { error ->
                        item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

/** One operating system's card on the list page, with a row per session. */
@Composable
private fun SessionGroupCard(
    group: SessionGroup,
    i18n: ThunderIDI18n,
    onOpenSession: (String) -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)) {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlatformBadge(icon = osIcon(group.os), size = 64.dp, iconSize = 30.dp)
                Text(groupTitle(group, i18n), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            group.sessions.forEach { session ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SessionRow(session = session, i18n = i18n, onClick = { onOpenSession(session.id) })
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SSOSession,
    i18n: ThunderIDI18n,
    onClick: () -> Unit,
) {
    val locale = Locale.forLanguageTag(i18n.activeLocale)
    val description = describeUserAgent(session.userAgent)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val apps = session.participants.mapNotNull { it.appName }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(clientIcon(description)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    clientName(session, i18n),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (session.isCurrent) CurrentChip(i18n)
            }
            Text(
                listOfNotNull(description.os, sessionIp(session)).joinToString(" · ").ifEmpty { i18n.sessions("unknown") },
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
            )
            Text(
                i18n.sessions("lastActive.since", "date" to formatSessionTime(session.lastActiveAt, locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
            )
            if (apps.isNotEmpty()) {
                Text(apps.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = secondary)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = secondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CurrentChip(i18n: ThunderIDI18n) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            i18n.sessions("current"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Full-screen page for one session, matching [ChangeCredential]'s editor structure: back arrow,
 * large title, rounded card underneath with the session's details and its Revoke action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionDetailDialog(
    session: SSOSession,
    state: ActiveSessionsState,
    i18n: ThunderIDI18n,
    onRevokeSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = Locale.forLanguageTag(i18n.activeLocale)
    val description = describeUserAgent(session.userAgent)
    val unknown = i18n.sessions("unknown")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().exposeTestTagsAsResourceIds()) {
                PageHeader(title = null, i18n = i18n, onBack = onDismiss)
                Row(
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        PlatformBadge(icon = osIcon(description.os), size = 56.dp, iconSize = 28.dp)
                        PlatformBadge(
                            icon = clientIcon(description),
                            size = 24.dp,
                            iconSize = 14.dp,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            deviceLabel(session, i18n),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (session.isCurrent) {
                            Text(
                                i18n.sessions("current"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                        DetailRow(R.drawable.ic_globe, i18n.sessions("ip")) {
                            DetailValue(sessionIp(session) ?: unknown, fontFamily = FontFamily.Monospace)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(R.drawable.ic_sign_in, i18n.sessions("signedIn")) {
                            DetailValue(formatSessionTime(session.authenticatedAt, locale))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(R.drawable.ic_clock, i18n.sessions("lastActive")) {
                            DetailValue(formatSessionTime(session.lastActiveAt, locale))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(R.drawable.ic_apps, i18n.sessions("applications")) {
                            if (session.participants.isEmpty()) {
                                DetailValue(unknown)
                            } else {
                                FlowRow(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    session.participants.forEach { participant ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        ) {
                                            Text(
                                                participant.appName ?: unknown,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (session.isCurrent) {
                            Text(
                                i18n.sessions("current.note"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 20.dp),
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = onRevokeSession,
                                    enabled = state.revokingSessionId == null,
                                    shape = RoundedCornerShape(20.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError,
                                        ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
                                    modifier = Modifier.testTag("thunderid-action-revokeSession"),
                                ) { Text(i18n.sessions("revoke")) }
                            }
                        }

                        state.error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    @DrawableRes icon: Int,
    label: String,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            value()
        }
    }
}

@Composable
private fun DetailValue(
    text: String,
    fontFamily: FontFamily? = null,
) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontFamily = fontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Material 3 confirmation dialog shown before any session is signed out. */
@Composable
internal fun RevokeConfirmDialog(
    title: String,
    body: String,
    i18n: ThunderIDI18n,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.exposeTestTagsAsResourceIds(),
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_sign_out),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("thunderid-action-confirmRevoke")) {
                Text(i18n.sessions("revoke"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(i18n.sessions("cancel")) }
        },
    )
}
