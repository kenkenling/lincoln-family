package com.lkeducation.lkpractice

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SheetMusicManagedFileTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @After
    fun cleanUp() {
        File(context.filesDir, SHEET_MUSIC_DIRECTORY).deleteRecursively()
    }

    @Test
    fun managedUriResolvesToPrivateSheetMusicDirectory() {
        val expected = File(
            context.filesDir,
            "$SHEET_MUSIC_DIRECTORY/attachment-1/Boreas Vln2.png",
        )
        val uri = managedUri("attachment-1", "Boreas Vln2.png")

        assertEquals(expected.canonicalFile, resolveManagedSheetMusicFile(context, uri))
        assertNull(
            resolveManagedSheetMusicFile(
                context,
                uri.buildUpon().authority("com.example.other.files").build(),
            ),
        )
    }

    @Test
    fun managedImageOpensWithoutAProviderPermissionGrant() {
        val imageFile = File(
            context.filesDir,
            "$SHEET_MUSIC_DIRECTORY/attachment-2/page.png",
        )
        assertTrue(imageFile.parentFile?.mkdirs() == true)
        val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
        try {
            imageFile.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        val document = openSheetMusicDocument(
            context,
            managedUri("attachment-2", "page.png"),
        )
        try {
            assertEquals("page.png", document.displayName)
            assertEquals("image/png", document.mimeType)
            assertEquals(1, document.pageCount)
            val rendered = document.renderPage(pageIndex = 0, targetWidth = 12)
            try {
                assertEquals(12, rendered.width)
                assertEquals(16, rendered.height)
            } finally {
                rendered.recycle()
            }
        } finally {
            document.close()
        }
    }

    private fun managedUri(
        attachmentId: String,
        displayName: String,
    ): Uri = Uri.Builder()
        .scheme("content")
        .authority("${context.packageName}.files")
        .appendPath(SHEET_MUSIC_FILE_PROVIDER_ROOT)
        .appendPath(attachmentId)
        .appendPath(displayName)
        .build()
}
