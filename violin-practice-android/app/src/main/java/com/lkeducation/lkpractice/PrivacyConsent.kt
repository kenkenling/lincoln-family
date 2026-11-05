package com.lkeducation.lkpractice

import android.content.Context
import androidx.core.content.edit

enum class ChildAgeRange(val label: String) {
    UNDER_13("Under 13"),
    AGE_13_TO_17("13–17"),
    AGE_18_OR_OLDER("18 or older"),
}

data class PrivacyPolicySection(
    val title: String,
    val paragraphs: List<String>,
)

internal const val CURRENT_PRIVACY_POLICY_VERSION = 3
internal const val PRIVACY_POLICY_LAST_UPDATED = "November 5, 2026"
internal const val PRIVACY_CONSENT_PREFERENCES = "lk_practice_privacy_consent"

internal val PRIVACY_POLICY_SECTIONS = listOf(
    PrivacyPolicySection(
        title = "Who should use LK Practice",
        paragraphs = listOf(
            "LK Practice is a local-first music practice app published by LK Education for families. A parent or legal guardian should set up and supervise the app for a child.",
            "The consent screen records only an age range, not a birth date. A parent or guardian should not continue unless they are authorized to make privacy choices for the child.",
        ),
    ),
    PrivacyPolicySection(
        title = "Information kept by the app",
        paragraphs = listOf(
            "The app may keep a student profile name and instrument, assignments and coaching notes, practice time and repetitions, reflections, recording filenames and videos, optional sheet-music files and references, and the location of an Excel workbook selected with the Android file picker.",
            "The app also keeps a salted hash of the local Parent Space PIN, the confirmed age range, and the accepted privacy-policy version. It does not need an exact birth date.",
        ),
    ),
    PrivacyPolicySection(
        title = "Where information is stored",
        paragraphs = listOf(
            "Practice data and consent choices stay in this app's private storage on this device. Practice recordings and attached sheet music stay in app-managed storage until a parent deletes the related data or shares a copy.",
            "LK Practice has no developer-operated account, cloud-sync service, advertising SDK, or analytics SDK. Android cloud backup and device-transfer backup are disabled for app data.",
        ),
    ),
    PrivacyPolicySection(
        title = "Permissions and network use",
        paragraphs = listOf(
            "Camera and microphone access are requested only for recording a practice take. The Android file picker grants access only to the video, sheet music, workbook, or export destination a parent chooses. Selected sheet music is copied into LK Practice's private storage for reliable in-app viewing.",
            "Internet access is used for an assigned YouTube reference video. The embedded YouTube player may send network and device information, such as an IP address and playback activity, to YouTube or Google when the reference player is opened or used.",
            "The app does not request location, contacts, phone, SMS, or broad photo-library access.",
        ),
    ),
    PrivacyPolicySection(
        title = "Sharing and external files",
        paragraphs = listOf(
            "A practice video leaves app storage only when a parent deliberately uses Android Share. The original sheet-music source remains controlled by its file provider; LK Practice keeps a private app copy for the assignment.",
            "External Excel files, exported profile archives, and copies shared to another app are outside LK Practice's private storage. LK Practice cannot automatically delete those external copies.",
        ),
    ),
    PrivacyPolicySection(
        title = "Content rights and publishing",
        paragraphs = listOf(
            "Only attach, record, export, or share sheet music, videos, performances, and teaching materials that the family, teacher, or student has permission to use.",
            "LK Practice does not automatically publish child recordings and does not claim ownership of family content. A parent should approve any publication or sharing that identifies a child.",
        ),
    ),
    PrivacyPolicySection(
        title = "Data choices and deletion",
        paragraphs = listOf(
            "A parent can delete an individual managed recording from Practice Summary.",
            "Delete all data in Parent Space asks Android to erase all LK Practice application data, including consent, profiles, assignments, practice history, local databases, app-managed recordings, and attached sheet-music copies. The app closes after the wipe and shows this consent screen again the next time it opens.",
            "Files previously exported, shared, or saved outside app-managed storage must be deleted separately from the file provider or receiving app.",
        ),
    ),
    PrivacyPolicySection(
        title = "Policy changes and questions",
        paragraphs = listOf(
            "If this policy changes in a way that requires a new acknowledgement, LK Practice will ask a parent to accept the new policy version before loading practice data.",
            "For privacy questions, use the developer contact published with LK Practice in its app-store listing.",
        ),
    ),
)

internal fun canAcceptPrivacyConsent(
    ageRange: ChildAgeRange?,
    parentConfirmed: Boolean,
    privacyPolicyAccepted: Boolean,
): Boolean = ageRange != null && parentConfirmed && privacyPolicyAccepted

class PrivacyConsentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PRIVACY_CONSENT_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun hasAcceptedCurrentPolicy(): Boolean =
        prefs.getInt(KEY_POLICY_VERSION, 0) == CURRENT_PRIVACY_POLICY_VERSION &&
            prefs.getBoolean(KEY_PARENT_CONFIRMED, false) &&
            prefs.getBoolean(KEY_POLICY_ACCEPTED, false) &&
            acceptedAgeRange() != null

    fun acceptedAgeRange(): ChildAgeRange? =
        prefs.getString(KEY_AGE_RANGE, null)
            ?.let { stored -> ChildAgeRange.entries.firstOrNull { it.name == stored } }

    fun accept(
        ageRange: ChildAgeRange?,
        parentConfirmed: Boolean,
        privacyPolicyAccepted: Boolean,
        acceptedAtMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!canAcceptPrivacyConsent(ageRange, parentConfirmed, privacyPolicyAccepted)) {
            return false
        }
        prefs.edit(commit = true) {
            putInt(KEY_POLICY_VERSION, CURRENT_PRIVACY_POLICY_VERSION)
            putString(KEY_AGE_RANGE, ageRange?.name)
            putBoolean(KEY_PARENT_CONFIRMED, true)
            putBoolean(KEY_POLICY_ACCEPTED, true)
            putLong(KEY_ACCEPTED_AT, acceptedAtMillis.coerceAtLeast(0L))
        }
        return hasAcceptedCurrentPolicy()
    }
}

private const val KEY_POLICY_VERSION = "privacy_policy_version"
private const val KEY_AGE_RANGE = "child_age_range"
private const val KEY_PARENT_CONFIRMED = "parent_or_adult_confirmed"
private const val KEY_POLICY_ACCEPTED = "privacy_policy_accepted"
private const val KEY_ACCEPTED_AT = "privacy_policy_accepted_at"
