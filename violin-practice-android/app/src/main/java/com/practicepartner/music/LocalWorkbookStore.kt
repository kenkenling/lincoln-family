package com.practicepartner.music

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

data class LocalWorkbookFile(
    val uri: String,
    val name: String,
)

data class LocalWorkbookSave(
    val uri: String,
    val name: String,
    val savedAtMillis: Long,
)

class LocalWorkbookException(message: String) : IOException(message)

class LocalWorkbookStore(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver

    fun describe(uriText: String?): LocalWorkbookFile? {
        val uri = uriText?.toUri() ?: return null
        if (uri.scheme != "content") return null
        val accessible = runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (!accessible) return null
        return LocalWorkbookFile(
            uri = uri.toString(),
            name = displayName(uri) ?: "Practice Partner.xlsx",
        )
    }

    suspend fun write(
        uriText: String,
        profile: StudentProfile,
        snapshot: PracticeSnapshot,
        updatedAtMillis: Long,
        requireExistingTemplate: Boolean = false,
    ): LocalWorkbookSave = withContext(Dispatchers.IO) {
        val uri = requireContentUri(uriText)
        val existingBytes = readExistingBytes(uri)
        if (requireExistingTemplate) {
            validateExistingTemplate(existingBytes)
        } else if (
            existingBytes != null &&
            existingBytes.isNotEmpty() &&
            !PracticeWorkbookDetector.matches(ByteArrayInputStream(existingBytes))
        ) {
            throw LocalWorkbookException(
                "This file already contains another workbook. Choose a new name or a Practice Partner workbook.",
            )
        }

        val workbookBytes = ByteArrayOutputStream().use { buffer ->
            XlsxWorkbookWriter.write(
                output = buffer,
                sheets = PracticeWorkbookTemplate.sheets(
                    profile = profile,
                    snapshot = snapshot,
                    updatedAtMillis = updatedAtMillis,
                ),
            )
            buffer.toByteArray()
        }
        if (!isValidGeneratedWorkbook(workbookBytes)) {
            throw LocalWorkbookException(
                "The new Excel file is missing a required sheet or column heading.",
            )
        }

        try {
            writeBytes(uri, workbookBytes)
            if (!verifySavedWorkbook(uri)) {
                throw IOException("The saved Excel file could not be verified.")
            }
        } catch (error: LocalWorkbookException) {
            throw error
        } catch (_: Exception) {
            if (existingBytes != null && existingBytes.isNotEmpty()) {
                runCatching { writeBytes(uri, existingBytes) }
            }
            throw LocalWorkbookException(
                "The local Excel file could not be saved and verified. The previous file was restored when possible.",
            )
        }

        LocalWorkbookSave(
            uri = uri.toString(),
            name = displayName(uri) ?: PracticeWorkbookTemplate.fileName(profile),
            savedAtMillis = updatedAtMillis,
        )
    }

    suspend fun readAssignments(
        uriText: String,
    ): WorkbookAssignmentImportResult = withContext(Dispatchers.IO) {
        val uri = requireContentUri(uriText)
        val bytes = readExistingBytes(uri)
            ?: throw LocalWorkbookException(
                "The app can no longer read this local Excel file. Choose it again in Profile.",
            )
        validateExistingTemplate(bytes)
        XlsxWorkbookReader.readAssignments(ByteArrayInputStream(bytes))
    }

    private fun requireContentUri(uriText: String): Uri {
        val uri = uriText.toUri()
        if (uri.scheme != "content") {
            throw LocalWorkbookException(
                "Choose an Excel file with the Android file picker.",
            )
        }
        return uri
    }

    private fun validateExistingTemplate(existingBytes: ByteArray?) {
        val bytes = existingBytes ?: throw LocalWorkbookException(
            "The app cannot read this Excel file. Choose it again with the Android file picker.",
        )
        if (!PracticeWorkbookDetector.matches(ByteArrayInputStream(bytes))) {
            throw LocalWorkbookException(
                "Choose a workbook previously created by Practice Partner. Other Excel files are not overwritten.",
            )
        }
    }

    private fun readExistingBytes(uri: Uri): ByteArray? {
        val input = try {
            contentResolver.openInputStream(uri)
        } catch (_: SecurityException) {
            null
        } ?: return null
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_WORKBOOK_BYTES) {
                    throw LocalWorkbookException(
                        "This workbook is unexpectedly large. Choose the Practice Partner file again.",
                    )
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        val output = sequenceOf("rwt", "wt")
            .mapNotNull { mode ->
                runCatching { contentResolver.openOutputStream(uri, mode) }.getOrNull()
            }
            .firstOrNull()
            ?: throw LocalWorkbookException(
                "The app no longer has permission to update this local file. Choose it again.",
            )
        output.use {
            it.write(bytes)
            it.flush()
        }
    }

    private fun verifySavedWorkbook(uri: Uri): Boolean {
        repeat(3) { attempt ->
            val verified = runCatching {
                readExistingBytes(uri)?.let(::isValidGeneratedWorkbook)
            }.getOrNull() == true
            if (verified) return true
            if (attempt < 2) Thread.sleep(150)
        }
        return false
    }

    private fun isValidGeneratedWorkbook(bytes: ByteArray): Boolean =
        PracticeWorkbookDetector.matches(ByteArrayInputStream(bytes)) &&
            XlsxWorkbookReader.hasCurrentTemplateHeaders(ByteArrayInputStream(bytes))

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else cursor.getString(index)
        }
    }.getOrNull()
}

private const val MAX_WORKBOOK_BYTES = 20 * 1024 * 1024
