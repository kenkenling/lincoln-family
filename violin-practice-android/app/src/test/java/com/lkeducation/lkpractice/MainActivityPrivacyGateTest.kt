package com.lkeducation.lkpractice

import android.content.Context
import androidx.core.content.edit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityPrivacyGateTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    @After
    fun clearPreferences() {
        listOf(
            PRIVACY_CONSENT_PREFERENCES,
            PRACTICE_PREFERENCES,
            LEGACY_PRACTICE_PREFERENCES,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit(commit = true) { clear() }
        }
    }

    @Test
    fun freshLaunchDoesNotInitializePracticePreferencesBeforeConsent() {
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()

        shadowOf(context.mainLooper).idle()

        assertTrue(
            context.getSharedPreferences(PRACTICE_PREFERENCES, Context.MODE_PRIVATE)
                .all
                .isEmpty(),
        )
        assertTrue(
            context.getSharedPreferences(LEGACY_PRACTICE_PREFERENCES, Context.MODE_PRIVATE)
                .all
                .isEmpty(),
        )

        activity.pause().stop().destroy()
    }

    @Test
    fun acceptedPolicyAllowsPracticeInitialization() {
        assertTrue(
            PrivacyConsentStore(context).accept(
                ageRange = ChildAgeRange.UNDER_13,
                parentConfirmed = true,
                privacyPolicyAccepted = true,
            ),
        )
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()

        shadowOf(context.mainLooper).idle()

        assertFalse(
            context.getSharedPreferences(PRACTICE_PREFERENCES, Context.MODE_PRIVATE)
                .all
                .isEmpty(),
        )

        activity.pause().stop().destroy()
    }
}

private const val PRACTICE_PREFERENCES = "practice_partner_v2"
private const val LEGACY_PRACTICE_PREFERENCES = "practice_partner_v1"
