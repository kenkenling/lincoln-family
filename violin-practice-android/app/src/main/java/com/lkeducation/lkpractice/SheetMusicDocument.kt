package com.lkeducation.lkpractice

import android.content.ContentResolver
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
    private val temporaryFile: File,
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
        temporaryFile.delete()
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
    val displayName = querySheetMusicDisplayName(resolver, uri)
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "Sheet music"
    val mimeType = resolver.getType(uri)
    return when (sheetMusicDocumentKind(mimeType, displayName)) {
        SheetMusicDocumentKind.PDF -> openPdfDocument(context, uri, displayName)
        SheetMusicDocumentKind.IMAGE -> {
            val bitmap = decodeSampledBitmap(resolver, uri)
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
): SheetMusicDocument {
    val temporaryFile = copyPdfToTemporaryFile(context, uri)
    val descriptor = ParcelFileDescriptor.open(
        temporaryFile,
        ParcelFileDescriptor.MODE_READ_ONLY,
    )
    val renderer = try {
        PdfRenderer(descriptor)
    } catch (error: SecurityException) {
        descriptor.close()
        temporaryFile.delete()
        throw IllegalArgumentException("Password-protected PDFs are not supported.", error)
    } catch (error: Throwable) {
        descriptor.close()
        temporaryFile.delete()
        throw error
    }
    if (renderer.pageCount <= 0) {
        renderer.close()
        descriptor.close()
        temporaryFile.delete()
        error("This PDF does not contain any pages.")
    }
    return PdfSheetMusicDocument(displayName, descriptor, renderer, temporaryFile)
}

private fun copyPdfToTemporaryFile(context: Context, uri: Uri): File {
    val directory = File(context.cacheDir, "sheet-music-viewer").apply { mkdirs() }
    val destination = File.createTempFile("sheet-music-", ".pdf", directory)
    try {
        val input = context.contentResolver.openInputStream(uri)
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

private fun decodeSampledBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw FileNotFoundException("The selected image could not be read.")
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
    return resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: throw FileNotFoundException("The selected image could not be read.")
}

internal fun sheetMusicErrorMessage(error: Throwable): String = when (error) {
    is FileNotFoundException, is SecurityException ->
        "LK Practice no longer has access to this file. Ask a grown-up to reattach it."
    is IllegalArgumentException -> error.message
        ?: "This PDF or image could not be opened."
    else -> "This PDF or image could not be opened in LK Practice."
}
