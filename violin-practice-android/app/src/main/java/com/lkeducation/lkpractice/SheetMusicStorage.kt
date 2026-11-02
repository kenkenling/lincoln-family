package com.lkeducation.lkpractice

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

internal data class ImportedSheetMusic(
    val uri: String,
    val displayName: String,
)

internal enum class SheetMusicDocumentKind {
    PDF,
    IMAGE,
    UNSUPPORTED,
}

internal fun sheetMusicDocumentKind(
    mimeType: String?,
    displayName: String?,
): SheetMusicDocumentKind {
    val normalizedMime = mimeType?.lowercase().orEmpty()
    val normalizedName = displayName?.lowercase().orEmpty()
    return when {
        normalizedMime == "application/pdf" || normalizedName.endsWith(".pdf") ->
            SheetMusicDocumentKind.PDF
        normalizedMime == "image/png" ||
            normalizedMime == "image/jpeg" ||
            normalizedMime == "image/webp" ||
            normalizedName.endsWith(".png") ||
            normalizedName.endsWith(".jpg") ||
            normalizedName.endsWith(".jpeg") ||
            normalizedName.endsWith(".webp") ->
            SheetMusicDocumentKind.IMAGE
        else -> SheetMusicDocumentKind.UNSUPPORTED
    }
}

internal fun sheetMusicPickerMimeTypes(): Array<String> = arrayOf(
    "application/pdf",
    "image/png",
    "image/jpeg",
    "image/webp",
)

internal class SheetMusicStorage private constructor(
    private val appContext: Context,
) {
    suspend fun importFrom(sourceUri: Uri): ImportedSheetMusic = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = querySheetMusicDisplayName(resolver, sourceUri)
            ?: sourceUri.lastPathSegment?.substringAfterLast('/')
            ?: "Sheet music"
        val mimeType = resolver.getType(sourceUri)
        val kind = sheetMusicDocumentKind(mimeType, displayName)
        require(kind != SheetMusicDocumentKind.UNSUPPORTED) {
            "Choose a PDF, PNG, JPG, or WebP sheet-music file."
        }

        val safeName = safeSheetMusicFileName(displayName, mimeType, kind)
        val attachmentDirectory = File(
            appContext.filesDir,
            "$SHEET_MUSIC_DIRECTORY/${UUID.randomUUID()}",
        )
        require(attachmentDirectory.mkdirs() || attachmentDirectory.isDirectory) {
            "LK Practice could not create its private sheet-music folder."
        }
        val destination = File(attachmentDirectory, safeName)

        try {
            copySheetMusicFile(resolver, sourceUri, destination)
            validateSheetMusicFile(destination, kind)
            val managedUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.files",
                destination,
            )
            ImportedSheetMusic(
                uri = managedUri.toString(),
                displayName = safeName,
            )
        } catch (error: Throwable) {
            attachmentDirectory.deleteRecursively()
            throw error
        }
    }

    companion object {
        fun from(context: Context): SheetMusicStorage =
            SheetMusicStorage(context.applicationContext)
    }
}

internal fun querySheetMusicDisplayName(
    resolver: ContentResolver,
    uri: Uri,
): String? {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
    return runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}

private fun safeSheetMusicFileName(
    displayName: String,
    mimeType: String?,
    kind: SheetMusicDocumentKind,
): String {
    val extension = when (kind) {
        SheetMusicDocumentKind.PDF -> "pdf"
        SheetMusicDocumentKind.IMAGE -> when (mimeType?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/png" -> "png"
            else -> displayName.substringAfterLast('.', "png").lowercase()
        }
        SheetMusicDocumentKind.UNSUPPORTED -> error("Unsupported sheet music kind")
    }
    val normalizedBase = displayName
        .substringAfterLast('/')
        .substringBeforeLast('.', displayName.substringAfterLast('/'))
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .trim()
        .take(100)
        .ifBlank { "sheet-music" }
    return "$normalizedBase.$extension"
}

private fun copySheetMusicFile(
    resolver: ContentResolver,
    sourceUri: Uri,
    destination: File,
) {
    val input = resolver.openInputStream(sourceUri)
        ?: throw FileNotFoundException("The selected sheet music could not be read.")
    input.use { source ->
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                totalBytes += read
                require(totalBytes <= MAX_SHEET_MUSIC_BYTES) {
                    "Sheet music must be 50 MB or smaller."
                }
                output.write(buffer, 0, read)
            }
        }
    }
}

private fun validateSheetMusicFile(
    file: File,
    kind: SheetMusicDocumentKind,
) {
    require(file.length() > 0L) { "The selected sheet-music file is empty." }
    when (kind) {
        SheetMusicDocumentKind.PDF -> validatePdf(file)
        SheetMusicDocumentKind.IMAGE -> validateImage(file)
        SheetMusicDocumentKind.UNSUPPORTED -> error("Unsupported sheet music kind")
    }
}

private fun validatePdf(file: File) {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = try {
        PdfRenderer(descriptor)
    } catch (error: SecurityException) {
        descriptor.close()
        throw IllegalArgumentException("Password-protected PDFs are not supported.", error)
    } catch (error: Throwable) {
        descriptor.close()
        throw IllegalArgumentException("This PDF could not be read.", error)
    }
    try {
        require(renderer.pageCount > 0) { "This PDF does not contain any pages." }
    } finally {
        renderer.close()
        descriptor.close()
    }
}

private fun validateImage(file: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) {
        "This image could not be read."
    }
}

internal const val MAX_SHEET_MUSIC_BYTES = 50L * 1024L * 1024L
internal const val MAX_SHEET_MUSIC_IMAGE_DIMENSION = 4_096
internal const val SHEET_MUSIC_DIRECTORY = "Sheet Music"
