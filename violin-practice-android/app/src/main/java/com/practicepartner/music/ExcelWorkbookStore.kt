package com.practicepartner.music

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

data class ExcelWorkbookConnection(
    val uri: String,
    val name: String,
    val destination: WorkbookDestinationInfo,
    val syncedAtMillis: Long,
)

class ExcelWorkbookException(message: String) : IOException(message)

class ExcelWorkbookStore(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val packageManager = appContext.packageManager

    fun describe(uriText: String?): WorkbookDestinationInfo? {
        val uri = uriText?.toUri() ?: return null
        if (uri.scheme != "content") return null
        val authority = uri.authority.orEmpty()
        @Suppress("DEPRECATION")
        val provider = packageManager.resolveContentProvider(authority, 0)
        val packageName = provider?.applicationInfo?.packageName
        val providerLabel = runCatching {
            provider?.applicationInfo?.loadLabel(packageManager)?.toString()
        }.getOrNull()
        val accountEmail = resolveDriveAccountEmail(
            uri = uri,
            authority = authority,
            packageName = packageName,
        )
        return WorkbookStorageClassifier.classify(
            authority = authority,
            packageName = packageName,
            providerLabel = providerLabel,
            accountEmail = accountEmail,
        )
    }

    suspend fun write(
        uriText: String,
        profile: StudentProfile,
        snapshot: PracticeSnapshot,
        updatedAtMillis: Long,
        requireExistingTemplate: Boolean = false,
        requireGoogleDrive: Boolean = true,
    ): ExcelWorkbookConnection = withContext(Dispatchers.IO) {
        val uri = uriText.toUri()
        if (uri.scheme != "content") {
            throw ExcelWorkbookException(
                "Choose the workbook again with the Android file picker.",
            )
        }
        val destination = describe(uriText)
            ?: throw ExcelWorkbookException("The selected storage provider could not be verified.")
        if (requireGoogleDrive && !destination.isGoogleDrive) {
            throw ExcelWorkbookException(
                "This file was saved to ${destination.providerName}, not Google Drive. " +
                    "No practice data was written. Delete the empty local file if it appears, " +
                    "then open the picker menu, choose Drive and the intended Google account, and save again.",
            )
        }
        val expectedAccount = profile.driveAccountEmail
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?: throw ExcelWorkbookException(
                "Enter the Google Drive account email before connecting a workbook.",
            )
        val selectedAccount = destination.accountEmail
            ?: throw ExcelWorkbookException(
                "Android did not expose the selected Drive account. Reopen Drive, confirm the account, and choose the file again.",
            )
        if (!destination.matchesExpectedAccount(expectedAccount)) {
            throw ExcelWorkbookException(
                "This workbook belongs to $selectedAccount, not $expectedAccount. No practice data was written.",
            )
        }

        val existingBytes = readExistingBytes(uri)
        if (requireExistingTemplate) {
            validateExistingTemplate(existingBytes)
        } else if (
            existingBytes != null &&
            existingBytes.isNotEmpty() &&
            !PracticeWorkbookDetector.matches(ByteArrayInputStream(existingBytes))
        ) {
            throw ExcelWorkbookException(
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
        if (!PracticeWorkbookDetector.matches(ByteArrayInputStream(workbookBytes))) {
            throw ExcelWorkbookException("The new Excel workbook failed its integrity check.")
        }

        try {
            writeBytes(uri, workbookBytes)
            if (!verifySavedWorkbook(uri)) {
                throw IOException("The saved workbook could not be verified.")
            }
        } catch (error: ExcelWorkbookException) {
            throw error
        } catch (_: Exception) {
            if (existingBytes != null && existingBytes.isNotEmpty()) {
                runCatching { writeBytes(uri, existingBytes) }
            }
            throw ExcelWorkbookException(
                "The Excel workbook could not be saved and verified. The previous workbook was restored when possible.",
            )
        }

        ExcelWorkbookConnection(
            uri = uri.toString(),
            name = displayName(uri) ?: PracticeWorkbookTemplate.fileName(profile),
            destination = destination,
            syncedAtMillis = updatedAtMillis,
        )
    }

    suspend fun readAssignments(
        uriText: String,
        profile: StudentProfile,
    ): WorkbookAssignmentImportResult = withContext(Dispatchers.IO) {
        val uri = uriText.toUri()
        if (uri.scheme != "content") {
            throw ExcelWorkbookException(
                "Choose the workbook again with the Android file picker.",
            )
        }
        val destination = describe(uriText)
            ?: throw ExcelWorkbookException("The selected storage provider could not be verified.")
        if (!destination.isGoogleDrive) {
            throw ExcelWorkbookException("The connected workbook is not in Google Drive.")
        }
        val expectedAccount = profile.driveAccountEmail
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?: throw ExcelWorkbookException(
                "Enter the Google Drive account email before loading assignments.",
            )
        val selectedAccount = destination.accountEmail
            ?: throw ExcelWorkbookException(
                "Android did not expose the selected Drive account. Choose the workbook again.",
            )
        if (!destination.matchesExpectedAccount(expectedAccount)) {
            throw ExcelWorkbookException(
                "This workbook belongs to $selectedAccount, not $expectedAccount.",
            )
        }
        val bytes = readExistingBytes(uri)
            ?: throw ExcelWorkbookException("The connected workbook could not be read.")
        validateExistingTemplate(bytes)
        XlsxWorkbookReader.readAssignments(ByteArrayInputStream(bytes))
    }

    private fun validateExistingTemplate(existingBytes: ByteArray?) {
        val bytes = existingBytes ?: throw ExcelWorkbookException(
            "The app cannot read this workbook. Choose it again with the Android file picker.",
        )
        if (!PracticeWorkbookDetector.matches(ByteArrayInputStream(bytes))) {
            throw ExcelWorkbookException(
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
                    throw ExcelWorkbookException(
                        "This workbook is unexpectedly large. Choose the Practice Partner workbook again.",
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
            ?: throw ExcelWorkbookException(
            "The app no longer has permission to update this workbook. Choose it again.",
        )
        output.use {
            it.write(bytes)
            it.flush()
        }
    }

    private fun verifySavedWorkbook(uri: Uri): Boolean {
        repeat(3) { attempt ->
            val verified = runCatching {
                contentResolver.openInputStream(uri)?.use(PracticeWorkbookDetector::matches)
            }.getOrNull() == true
            if (verified) return true
            if (attempt < 2) Thread.sleep(150)
        }
        return false
    }

    private fun resolveDriveAccountEmail(
        uri: Uri,
        authority: String,
        packageName: String?,
    ): String? {
        if (packageName != WorkbookStorageClassifier.GOOGLE_DRIVE_PACKAGE) return null
        val selectedDocumentId = runCatching {
            if (DocumentsContract.isTreeUri(uri)) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                DocumentsContract.getDocumentId(uri)
            }
        }.getOrNull()
        val roots = runCatching {
            contentResolver.query(
                DocumentsContract.buildRootsUri(authority),
                arrayOf(
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Root.COLUMN_SUMMARY,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val documentIndex = cursor.getColumnIndex(
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                )
                val summaryIndex = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_SUMMARY)
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            (if (documentIndex >= 0) cursor.getString(documentIndex) else null) to
                                (if (summaryIndex >= 0) cursor.getString(summaryIndex) else null),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
        return DriveAccountMatcher.chooseAccount(selectedDocumentId, roots)
    }

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

object WorkbookStorageClassifier {
    const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"

    private val deviceAuthorities = setOf(
        "com.android.externalstorage.documents",
        "com.android.providers.downloads.documents",
        "com.android.providers.media.documents",
    )

    fun classify(
        authority: String?,
        packageName: String?,
        providerLabel: String?,
        accountEmail: String? = null,
    ): WorkbookDestinationInfo {
        val normalizedAuthority = authority.orEmpty()
        val normalizedPackage = packageName.orEmpty()
        return when {
            normalizedPackage == GOOGLE_DRIVE_PACKAGE ->
                WorkbookDestinationInfo(
                    kind = WorkbookStorageKind.GOOGLE_DRIVE,
                    providerName = "Google Drive",
                    accountEmail = normalizedAccountEmail(accountEmail.orEmpty())
                        ?.takeIf(String::isNotBlank),
                )

            normalizedAuthority in deviceAuthorities ||
                normalizedPackage.startsWith("com.android.providers.") ->
                WorkbookDestinationInfo(
                    kind = WorkbookStorageKind.DEVICE,
                    providerName = "this device",
                )

            else ->
                WorkbookDestinationInfo(
                    kind = WorkbookStorageKind.OTHER,
                    providerName = providerLabel?.trim()?.takeIf { it.isNotBlank() }
                        ?: "another storage provider",
                )
        }
    }
}

object DriveAccountMatcher {
    fun accountToken(documentId: String?): String? =
        documentId
            ?.substringBefore(';')
            ?.takeIf { it.startsWith("acc=") }

    fun chooseAccount(
        selectedDocumentId: String?,
        roots: List<Pair<String?, String?>>,
    ): String? {
        val selectedToken = accountToken(selectedDocumentId)
        val candidates = roots.mapNotNull { (documentId, summary) ->
            val email = normalizedAccountEmail(summary.orEmpty())
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            accountToken(documentId) to email
        }
        selectedToken?.let { token ->
            candidates.firstOrNull { it.first == token }?.second?.let { return it }
        }
        return candidates.map { it.second }.distinct().singleOrNull()
    }
}

private const val MAX_WORKBOOK_BYTES = 20 * 1024 * 1024
