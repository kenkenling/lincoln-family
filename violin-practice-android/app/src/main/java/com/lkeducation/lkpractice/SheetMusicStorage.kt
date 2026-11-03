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
import java.io.InputStream
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
    fun isManaged(uri: Uri): Boolean =
        resolveManagedSheetMusicFile(appContext, uri) != null

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

internal fun resolveManagedSheetMusicFile(
    context: Context,
    uri: Uri,
): File? {
    if (
        uri.scheme != ContentResolver.SCHEME_CONTENT ||
        uri.authority != "${context.packageName}.files"
    ) {
        return null
    }
    val segments = uri.pathSegments
    if (
        segments.firstOrNull() != SHEET_MUSIC_FILE_PROVIDER_ROOT ||
        segments.size < 3
    ) {
        return null
    }
    return runCatching {
        val root = File(context.filesDir, SHEET_MUSIC_DIRECTORY).canonicalFile
        val candidate = segments.drop(1)
            .fold(root) { parent, segment -> File(parent, segment) }
            .canonicalFile
        val rootPrefix = "${root.path}${File.separator}"
        candidate.takeIf { it.path.startsWith(rootPrefix) }
    }.getOrNull()
}

internal fun <T> withSheetMusicInputStream(
    resolver: ContentResolver,
    uri: Uri,
    mimeType: String?,
    block: (InputStream) -> T,
): T {
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        val path = uri.path ?: throw FileNotFoundException(
            "The selected sheet music does not have a readable file path.",
        )
        return File(path).inputStream().use(block)
    }

    var firstFailure: Throwable? = null
    try {
        resolver.openInputStream(uri)?.use { return block(it) }
    } catch (error: Throwable) {
        firstFailure = error
    }

    try {
        resolver.openTypedAssetFileDescriptor(
            uri,
            mimeType ?: "*/*",
            null,
        )?.use { descriptor ->
            descriptor.createInputStream().use { return block(it) }
        }
    } catch (error: Throwable) {
        if (firstFailure == null) firstFailure = error
    }

    when (val failure = firstFailure) {
        is SecurityException -> throw failure
        is FileNotFoundException -> throw failure
        else -> throw FileNotFoundException(
            "The selected sheet music could not be read.",
        ).also { notFound ->
            if (failure != null) notFound.initCause(failure)
        }
    }
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
    withSheetMusicInputStream(
        resolver = resolver,
        uri = sourceUri,
        mimeType = resolver.getType(sourceUri),
    ) { source ->
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
internal const val SHEET_MUSIC_FILE_PROVIDER_ROOT = "sheet_music"
