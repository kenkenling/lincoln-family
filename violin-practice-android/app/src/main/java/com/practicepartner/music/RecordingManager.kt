package com.practicepartner.music

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.net.toUri
import java.io.File

data class RecordingDeleteResult(
    val isManaged: Boolean,
    val existed: Boolean,
    val deleted: Boolean,
)

class RecordingManager(
    private val managedDirectoriesProvider: () -> List<File>,
    private val releaseUriPermission: (String) -> Unit = {},
    private val contentUriReadable: (String?) -> Boolean = { true },
) {
    fun createRecordingFile(requestedFileName: String): File {
        val directory = managedDirectories()
            .firstOrNull { it.isDirectory || it.mkdirs() }
            ?: error("LK Practice could not create its private recording folder.")
        return nextAvailableRecordingFile(directory, requestedFileName)
    }

    fun managedRecordingFile(path: String): File? {
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val withinManagedDirectory = managedDirectories().any { directory ->
            val root = runCatching { directory.canonicalFile }.getOrNull() ?: return@any false
            candidate.path == root.path ||
                candidate.path.startsWith(root.path + File.separator)
        }
        return candidate.takeIf {
            withinManagedDirectory && isManagedPracticeRecordingFileName(it.name)
        }
    }

    fun deleteRecording(path: String): RecordingDeleteResult {
        val recordingFile = managedRecordingFile(path)
            ?: return RecordingDeleteResult(
                isManaged = false,
                existed = false,
                deleted = false,
            )
        val existed = recordingFile.exists()
        val deleted = runCatching {
            !existed || recordingFile.delete() || !recordingFile.exists()
        }.getOrDefault(false)
        return RecordingDeleteResult(
            isManaged = true,
            existed = existed,
            deleted = deleted,
        )
    }

    fun deleteManagedRecordingFileOnly(path: String): Boolean {
        val file = managedRecordingFile(path) ?: return false
        return runCatching {
            !file.exists() || file.delete() || !file.exists()
        }.getOrDefault(false)
    }

    fun deleteUnsharedRecordings(
        recordingPaths: Collection<String>,
        pathsReferencedElsewhere: Set<String>,
    ): Int = recordingPaths
        .distinct()
        .filterNot(pathsReferencedElsewhere::contains)
        .count { !deleteManagedRecordingFileOnly(it) }

    fun cleanupOrphanedRecordings(referencedPaths: Set<String>): Int {
        var deletedCount = 0
        managedDirectories().forEach { directory ->
            directory.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
                .filterNot { it.absolutePath in referencedPaths }
                .forEach {
                    if (deleteManagedRecordingFileOnly(it.absolutePath)) {
                        deletedCount += 1
                    }
                }
        }
        return deletedCount
    }

    fun releaseWorkbookPermissionIfUnused(
        deletedProfile: ChildProfile,
        remainingProfiles: List<ChildProfile>,
    ) {
        val uriText = deletedProfile.excelWorkbookUri?.takeIf(String::isNotBlank) ?: return
        if (remainingProfiles.any { it.excelWorkbookUri == uriText }) return
        runCatching { releaseUriPermission(uriText) }
    }

    fun canReadContentUri(value: String?): Boolean = contentUriReadable(value)

    private fun managedDirectories(): List<File> =
        managedDirectoriesProvider().distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }

    companion object {
        fun from(context: Context): RecordingManager {
            val appContext = context.applicationContext
            return RecordingManager(
                managedDirectoriesProvider = {
                    buildList {
                        appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let {
                            add(File(it, RECORDING_DIRECTORY))
                        }
                        add(File(appContext.filesDir, RECORDING_DIRECTORY))
                    }
                },
                releaseUriPermission = { uriText ->
                    appContext.contentResolver.releasePersistableUriPermission(
                        uriText.toUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                },
                contentUriReadable = { value ->
                    if (value.isNullOrBlank() || !value.startsWith("content://")) {
                        true
                    } else {
                        runCatching {
                            appContext.contentResolver
                                .openFileDescriptor(value.toUri(), "r")
                                ?.use { true }
                                ?: false
                        }.getOrDefault(false)
                    }
                },
            )
        }
    }
}

private const val RECORDING_DIRECTORY = "Practice Partner"
