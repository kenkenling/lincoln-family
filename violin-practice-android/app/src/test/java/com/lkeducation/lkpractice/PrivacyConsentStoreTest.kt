package com.lkeducation.lkpractice

import android.content.Context
import androidx.core.content.edit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PrivacyConsentStoreTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences(PRIVACY_CONSENT_PREFERENCES, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }
        context.getSharedPreferences(PRACTICE_PREFERENCES, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }
    }

    @Test
    fun checkingFirstLaunchDoesNotCreatePracticeDataOrConsentValues() {
        val consentPreferences = context.getSharedPreferences(
            PRIVACY_CONSENT_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val practicePreferences = context.getSharedPreferences(
            PRACTICE_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val store = PrivacyConsentStore(context)

        assertFalse(store.hasAcceptedCurrentPolicy())
        assertTrue(consentPreferences.all.isEmpty())
        assertTrue(practicePreferences.all.isEmpty())
    }

    @Test
    fun acceptanceRequiresAgeParentAndPolicyConfirmations() {
        val store = PrivacyConsentStore(context)

        assertFalse(store.accept(null, parentConfirmed = true, privacyPolicyAccepted = true))
        assertFalse(
            store.accept(
                ChildAgeRange.UNDER_13,
                parentConfirmed = false,
                privacyPolicyAccepted = true,
            ),
        )
        assertFalse(
            store.accept(
                ChildAgeRange.UNDER_13,
                parentConfirmed = true,
                privacyPolicyAccepted = false,
            ),
        )
        assertFalse(store.hasAcceptedCurrentPolicy())
    }

    @Test
    fun acceptedCurrentPolicyRestoresAgeRange() {
        val store = PrivacyConsentStore(context)

        assertTrue(
            store.accept(
                ageRange = ChildAgeRange.AGE_13_TO_17,
                parentConfirmed = true,
                privacyPolicyAccepted = true,
                acceptedAtMillis = 1234L,
            ),
        )

        assertTrue(store.hasAcceptedCurrentPolicy())
        assertEquals(ChildAgeRange.AGE_13_TO_17, store.acceptedAgeRange())
    }

    @Test
    fun olderPolicyVersionRequiresConsentAgain() {
        val store = PrivacyConsentStore(context)
        assertTrue(
            store.accept(
                ageRange = ChildAgeRange.AGE_18_OR_OLDER,
                parentConfirmed = true,
                privacyPolicyAccepted = true,
            ),
        )
        context.getSharedPreferences(PRIVACY_CONSENT_PREFERENCES, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putInt("privacy_policy_version", CURRENT_PRIVACY_POLICY_VERSION - 1)
            }

        assertFalse(store.hasAcceptedCurrentPolicy())
    }

    @Test
    fun policyDisclosesNetworkStorageAndDeletionBoundaries() {
        val policy = PRIVACY_POLICY_SECTIONS
            .flatMap { section -> listOf(section.title) + section.paragraphs }
            .joinToString(" ")

        assertTrue(policy.contains("YouTube"))
        assertTrue(policy.contains("advertising SDK"))
        assertTrue(policy.contains("analytics SDK"))
        assertTrue(policy.contains("Delete all data"))
        assertTrue(policy.contains("External Excel files"))
        assertTrue(policy.contains("does not automatically publish child recordings"))
        assertFalse(policy.contains("Lincoln"))
    }
}

private const val PRACTICE_PREFERENCES = "practice_partner_v2"
