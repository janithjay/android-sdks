// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.http

import android.os.Build
import dev.thunderid.android.BuildConfig

/**
 * The User-Agent the SDK sends on every request, so the server can show a session as the device
 * that holds it: `ThunderID-Android/1.4.0 (Android 14; Google Pixel 8)`. It is for display only; the client
 * sets it, so nothing may rely on it for a decision.
 */
internal object UserAgent {
    val value: String by lazy {
        val device = deviceName(Build.MANUFACTURER, Build.MODEL, Build.HARDWARE in EMULATOR_HARDWARE)
        build(BuildConfig.SDK_VERSION, Build.VERSION.RELEASE, device)
    }

    fun build(
        sdkVersion: String,
        osVersion: String?,
        device: String?,
    ): String {
        val os = listOfNotNull("Android", clean(osVersion)).joinToString(" ")
        val comment = listOfNotNull(os, clean(device)).joinToString("; ")
        return "ThunderID-Android/$sdkVersion ($comment)"
    }

    /**
     * The name a person recognizes their device by: "Google Pixel 8", "Samsung SM-S918B", or
     * "Android Emulator" rather than the emulator's build name.
     */
    fun deviceName(
        manufacturer: String?,
        model: String?,
        isEmulator: Boolean,
    ): String? {
        if (isEmulator) return "Android Emulator"
        val make = clean(manufacturer)?.replaceFirstChar { it.uppercaseChar() }
        val name = clean(model) ?: return make
        return if (make == null || name.startsWith(make, ignoreCase = true)) name else "$make $name"
    }

    // The virtual hardware the Android Emulator reports, older and current.
    private val EMULATOR_HARDWARE = setOf("goldfish", "ranchu")

    // A comment's delimiters, or a control character, would break the header's structure.
    private fun clean(value: String?): String? = value?.replace(Regex("""[\p{Cntrl}();]"""), "")?.trim()?.ifEmpty { null }
}
