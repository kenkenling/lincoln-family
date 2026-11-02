package com.lkeducation.lkpractice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class AdultAccessManager(
    private val familyStore: FamilyDataStore,
    private val setMessage: (String) -> Unit,
) {
    var pinConfigured by mutableStateOf(familyStore.hasAdultPin())
        private set

    var unlocked by mutableStateOf(false)
        private set

    var externalActionInProgress: Boolean = false
        private set

    fun createPin(pin: String, confirmation: String): Boolean {
        val error = when {
            pinConfigured -> "A parent PIN is already configured."
            !isValidAdultPin(pin) -> "Create a 4-digit parent PIN."
            pin != confirmation -> "The two PIN entries do not match."
            else -> null
        }
        if (error != null) {
            setMessage(error)
            return false
        }
        if (!familyStore.setAdultPin(pin)) {
            setMessage("The parent PIN could not be saved.")
            return false
        }
        pinConfigured = true
        unlocked = true
        setMessage("Parent Space is protected on this device.")
        return true
    }

    fun unlock(pin: String): Boolean {
        if (!pinConfigured) {
            setMessage("Create the parent PIN first.")
            return false
        }
        if (!familyStore.verifyAdultPin(pin)) {
            setMessage("That parent PIN is not correct.")
            return false
        }
        unlocked = true
        return true
    }

    fun lock() {
        externalActionInProgress = false
        unlocked = false
    }

    fun beginExternalAction(): Boolean {
        if (!requireAccess()) return false
        externalActionInProgress = true
        return true
    }

    fun endExternalAction() {
        externalActionInProgress = false
    }

    fun requireAccess(): Boolean {
        if (unlocked) return true
        setMessage("Open Parent Space and enter the parent PIN first.")
        return false
    }
}
