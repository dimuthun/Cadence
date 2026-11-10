package com.cadence.presentation.settings

import android.os.Build

/** Manufacturer-specific steps for keeping background apps alive. */
data class OemGuide(val brand: String, val steps: List<String>)

/**
 * Many manufacturers add their own background restrictions on top of Android's battery
 * optimization. Menu names vary by OS version, so the steps are phrased as what to look for.
 */
object OemBatteryGuide {

    private val xiaomi = OemGuide(
        "Xiaomi, Redmi & POCO",
        listOf(
            "Settings › Apps › Manage apps › Cadence › Autostart: turn on.",
            "Same screen › Battery saver: choose \"No restrictions\".",
            "Open Recent apps, long-press Cadence, and tap the lock icon so it isn't cleared.",
        ),
    )
    private val samsung = OemGuide(
        "Samsung",
        listOf(
            "Settings › Apps › Cadence › Battery: choose \"Unrestricted\".",
            "Settings › Battery › Background usage limits: make sure Cadence isn't in \"Sleeping apps\" or \"Deep sleeping apps\".",
            "Turn off \"Put unused apps to sleep\" there, or add Cadence to \"Never sleeping apps\".",
        ),
    )
    private val huawei = OemGuide(
        "Huawei & Honor",
        listOf(
            "Settings › Battery › App launch › Cadence: turn off \"Manage automatically\".",
            "In the dialog, turn on Auto-launch, Secondary launch, and Run in background.",
            "Lock Cadence in Recent apps (swipe down on its card).",
        ),
    )
    private val oneplus = OemGuide(
        "OnePlus, OPPO & realme",
        listOf(
            "Settings › Apps › Cadence › Battery usage: allow background activity, and turn off \"Optimize battery use\".",
            "Allow Auto launch for Cadence if your version shows it.",
            "Lock Cadence in Recent apps.",
        ),
    )
    private val motorola = OemGuide(
        "Motorola",
        listOf("Settings › Apps › Cadence › App battery usage: choose \"Unrestricted\"."),
    )
    private val generic = OemGuide(
        "Other Android phones",
        listOf(
            "Settings › Apps › Cadence › Battery: choose \"Unrestricted\" (or \"Not optimized\").",
            "If your phone has an \"Autostart\" or \"Background activity\" setting, allow it for Cadence.",
        ),
    )

    val all: List<OemGuide> = listOf(xiaomi, samsung, huawei, oneplus, motorola, generic)

    /** The guide for this device's manufacturer, or the generic one. */
    fun forThisDevice(manufacturer: String = Build.MANUFACTURER): OemGuide =
        when (manufacturer.lowercase()) {
            "xiaomi", "redmi", "poco" -> xiaomi
            "samsung" -> samsung
            "huawei", "honor" -> huawei
            "oneplus", "oppo", "realme" -> oneplus
            "motorola", "lenovo" -> motorola
            else -> generic
        }

    /** Community-maintained, regularly updated details per manufacturer. */
    const val MORE_INFO_URL = "https://dontkillmyapp.com"
}
