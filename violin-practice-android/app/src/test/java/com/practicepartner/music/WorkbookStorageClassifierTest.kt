package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbookStorageClassifierTest {
    @Test
    fun recognizesGoogleDriveProvider() {
        val byAuthority = WorkbookStorageClassifier.classify(
            authority = "com.google.android.apps.docs.storage",
            packageName = null,
            providerLabel = "Drive",
        )
        val byPackage = WorkbookStorageClassifier.classify(
            authority = "provider.changed",
            packageName = "com.google.android.apps.docs",
            providerLabel = "Drive",
            accountEmail = " KenKenLincoln@gmail.com ",
        )

        assertFalse(byAuthority.isGoogleDrive)
        assertTrue(byPackage.isGoogleDrive)
        assertEquals("Google Drive", byPackage.providerName)
        assertEquals("kenkenlincoln@gmail.com", byPackage.accountEmail)
        assertTrue(byPackage.matchesExpectedAccount("KENKENLINCOLN@gmail.com"))
    }

    @Test
    fun distinguishesDeviceAndOtherCloudStorage() {
        val device = WorkbookStorageClassifier.classify(
            authority = "com.android.externalstorage.documents",
            packageName = "com.android.providers.media",
            providerLabel = "Files",
        )
        val otherCloud = WorkbookStorageClassifier.classify(
            authority = "com.example.cloud.documents",
            packageName = "com.example.cloud",
            providerLabel = "Family Cloud",
        )

        assertEquals(WorkbookStorageKind.DEVICE, device.kind)
        assertFalse(device.isGoogleDrive)
        assertEquals("this device", device.providerName)
        assertEquals(WorkbookStorageKind.OTHER, otherCloud.kind)
        assertEquals("Family Cloud", otherCloud.providerName)
    }

    @Test
    fun selectsTheAccountBelongingToTheChosenDriveRoot() {
        val roots = listOf(
            "acc=1;root=drive" to "first@gmail.com",
            "acc=2;root=drive" to "lincoln@gmail.com",
        )

        assertEquals(
            "lincoln@gmail.com",
            DriveAccountMatcher.chooseAccount("acc=2;doc=folder/file", roots),
        )
        assertEquals(
            "only@gmail.com",
            DriveAccountMatcher.chooseAccount(
                selectedDocumentId = null,
                roots = listOf("root" to "only@gmail.com"),
            ),
        )
        assertEquals(
            null,
            DriveAccountMatcher.chooseAccount(
                selectedDocumentId = null,
                roots = roots,
            ),
        )
    }
}
