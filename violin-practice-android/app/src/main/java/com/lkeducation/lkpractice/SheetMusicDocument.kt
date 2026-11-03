package com.lkeducation.lkpractice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.roundToInt

internal fun sheetMusicPageLabel(pageIndex: Int, pageCount: Int): String {
    val safeCount = pageCount.coerceAtLeast(1)
    val safeIndex = pageIndex.coerceIn(0, safeCount - 1)
    return "Page ${safeIndex + 1} of $safeCount"
}

internal fun sheetMusicRenderWidth(containerWidthPx: Int): Int =
    (containerWidthPx.coerceAtLeast(1) * 2).coerceIn(1_200, 2_000)

internal abstract class SheetMusicDocument : Closeable {
    abstract val displayName: String
    abstract val mimeType: String
    abstract val pageCount: Int
    abstract fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap
}

private class PdfSheetMusicDocument(
    override val displayName: String,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val fileToDeleteOnClose: File?,
) : SheetMusicDocument() {
    override val mimeType: String = "application/pdf"
    override val pageCount: Int = renderer.pageCount

    override fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap =
        synchronized(renderer) {
            renderer.openPage(pageIndex.coerceIn(0, pageCount - 1)).use { page ->
                val width = targetWidth.coerceAtLeast(1)
                val height = (width * page.height.toFloat() / page.width.coerceAtLeast(1))
                    .roundToInt()
                    .coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                }
            }
        }

    override fun close() {
        renderer.close()
        descriptor.close()
        fileToDeleteOnClose?.delete()
    }
}

private class ImageSheetMusicDocument(
    override val displayName: String,
    override val mimeType: String,
    private val sourceBitmap: Bitmap,
) : SheetMusicDocument() {
    override val pageCount: Int = 1

    override fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap {
        val width = sourceBitmap.width.coerceAtMost(targetWidth).coerceAtLeast(1)
        val height = (width * sourceBitmap.height.toFloat() / sourceBitmap.width.coerceAtLeast(1))
            .roundToInt()
            .coerceAtLeast(1)
        return if (width == sourceBitmap.width && height == sourceBitmap.height) {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            Bitmap.createScaledBitmap(sourceBitmap, width, height, true)
        }
    }

    override fun close() {
        if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
    }
}

internal fun openSheetMusicDocument(context: Context, uri: Uri): SheetMusicDocument {
    val resolver = context.contentResolver
    val managedFile = resolveManagedSheetMusicFile(context, uri)
    val displayName = managedFile?.name
        ?: querySheetMusicDisplayName(resolver, uri)
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "Sheet music"
    val mimeType = if (managedFile != null) {
        sheetMusicMimeTypeFromName(displayName)
    } else {
        resolver.getType(uri) ?: sheetMusicMimeTypeFromName(displayName)
    }
    return when (sheetMusicDocumentKind(mimeType, displayName)) {
        SheetMusicDocumentKind.PDF -> {
            if (managedFile != null) {
                openPdfFile(
                    file = managedFile,
                    displayName = displayName,
                    deleteOnClose = false,
                )
            } else {
                openPdfDocument(context, uri, displayName, mimeType)
            }
        }
        SheetMusicDocumentKind.IMAGE -> {
            val bitmap = if (managedFile != null) {
                require(managedFile.isFile) {
                    "The app-managed sheet-music copy is missing."
                }
                decodeSampledBitmap { options ->
                    managedFile.inputStream().use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }
            } else {
                decodeSampledBitmap { options ->
                    withSheetMusicInputStream(
                        resolver = resolver,
                        uri = uri,
                        mimeType = mimeType,
                    ) { source ->
                        BitmapFactory.decodeStream(source, null, options)
                    }
                }
            }
            ImageSheetMusicDocument(
                displayName = displayName,
                mimeType = mimeType ?: "image/*",
                sourceBitmap = bitmap,
            )
        }
        SheetMusicDocumentKind.UNSUPPORTED ->
            error("Choose a PDF, PNG, JPG, or WebP sheet-music file.")
    }
}

private fun openPdfDocument(
    context: Context,
    uri: Uri,
    displayName: String,
    mimeType: String?,
): SheetMusicDocument {
    val temporaryFile = copyPdfToTemporaryFile(context, uri, mimeType)
    return try {
        openPdfFile(
            file = temporaryFile,
            displayName = displayName,
            deleteOnClose = true,
        )
    } catch (error: Throwable) {
        temporaryFile.delete()
        throw error
    }
}

private fun openPdfFile(
    file: File,
    displayName: String,
    deleteOnClose: Boolean,
): SheetMusicDocument {
    if (!file.isFile) {
        throw FileNotFoundException("The app-managed sheet-music copy is missing.")
    }
    val descriptor = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_ONLY,
    )
    val renderer = try {
        PdfRenderer(descriptor)
    } catch (error: SecurityException) {
        descriptor.close()
        throw IllegalArgumentException("Password-protected PDFs are not supported.", error)
    } catch (error: Throwable) {
        descriptor.close()
        throw error
    }
    if (renderer.pageCount <= 0) {
        renderer.close()
        descriptor.close()
        error("This PDF does not contain any pages.")
    }
    return PdfSheetMusicDocument(
        displayName = displayName,
        descriptor = descriptor,
        renderer = renderer,
        fileToDeleteOnClose = file.takeIf { deleteOnClose },
    )
}

private fun copyPdfToTemporaryFile(
    context: Context,
    uri: Uri,
    mimeType: String?,
): File {
    val directory = File(context.cacheDir, "sheet-music-viewer").apply { mkdirs() }
    val destination = File.createTempFile("sheet-music-", ".pdf", directory)
    try {
        withSheetMusicInputStream(
            resolver = context.contentResolver,
            uri = uri,
            mimeType = mimeType,
        ) { source ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    require(totalBytes <= MAX_SHEET_MUSIC_BYTES) {
                        "This sheet-music file is larger than 50 MB."
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return destination
    } catch (error: Throwable) {
        destination.delete()
        throw error
    }
}

private fun decodeSampledBitmap(
    decode: (BitmapFactory.Options) -> Bitmap?,
): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) {
        "The selected image could not be decoded."
    }
    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MAX_SHEET_MUSIC_IMAGE_DIMENSION ||
        bounds.outHeight / sampleSize > MAX_SHEET_MUSIC_IMAGE_DIMENSION
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return decode(options)
        ?: throw FileNotFoundException("The selected image could not be read.")
}

private fun sheetMusicMimeTypeFromName(displayName: String): String? =
    when (displayName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> null
    }

internal fun sheetMusicErrorMessage(error: Throwable): String = when (error) {
    is FileNotFoundException, is SecurityException ->
        "LK Practice cannot read this attachment. Reattach it once so the app can keep a reliable private copy."
    is IllegalArgumentException -> error.message
        ?: "This PDF or image could not be opened."
    else -> "This PDF or image could not be opened in LK Practice."
}
