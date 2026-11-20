package com.gsm.savetopdf.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedPdfResult(
    val uri: Uri,
    val displayName: String,
    val bytesWritten: Long,
)

/**
 * Minimal, memory-optimized PDF stream writer using a fixed 16 KB buffer.
 * Supports Storage Access Framework (File Manager) and direct [MediaStore.Downloads]
 * without storage permissions or recent file tracking overhead.
 */
class PdfDownloadsRepository(
    private val contentResolver: ContentResolver,
) {

    @Throws(IOException::class)
    fun spoolToCacheFile(
        inputFd: ParcelFileDescriptor,
        cacheDir: File,
        requestId: String,
    ): File {
        val spoolDir = File(cacheDir, SPOOL_DIR_NAME).apply { mkdirs() }
        val spoolFile = File(spoolDir, "$requestId.pdf")
        try {
            ParcelFileDescriptor.AutoCloseInputStream(inputFd).use { input ->
                FileOutputStream(spoolFile).use { output ->
                    copyStreamLowMemory(input, output)
                    output.flush()
                }
            }
            return spoolFile
        } catch (t: Throwable) {
            runCatching { spoolFile.delete() }
            if (t is IOException) throw t
            throw IOException("Failed to stage PDF stream", t)
        }
    }

    @Throws(IOException::class)
    fun copyFileToTargetUri(
        sourceFile: File,
        targetUri: Uri,
        fallbackName: String,
    ): SavedPdfResult {
        val bytesWritten = FileInputStream(sourceFile).use { input ->
            contentResolver.openOutputStream(targetUri, "wt")?.use { output ->
                val copied = copyStreamLowMemory(input, output)
                output.flush()
                copied
            } ?: throw IOException("Unable to open selected File Manager destination")
        }

        val resolvedName = resolveOpenableDisplayName(targetUri) ?: fallbackName
        return SavedPdfResult(
            uri = targetUri,
            displayName = resolvedName,
            bytesWritten = bytesWritten,
        )
    }

    @Throws(IOException::class)
    fun saveRawPdfStream(
        inputFd: ParcelFileDescriptor,
        rawJobName: String?,
        timestampMillis: Long = System.currentTimeMillis(),
    ): SavedPdfResult {
        val displayName = buildPdfFileName(rawJobName, timestampMillis)
        val collectionUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val pendingValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, PDF_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        var insertedUri: Uri? = null
        try {
            ParcelFileDescriptor.AutoCloseInputStream(inputFd).use { inputStream ->
                val uri = contentResolver.insert(collectionUri, pendingValues)
                    ?: throw IOException("Unable to create MediaStore.Downloads entry for $displayName")
                insertedUri = uri

                val bytesCopied = contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val copied = copyStreamLowMemory(inputStream, outputStream)
                    outputStream.flush()
                    copied
                } ?: throw IOException("Unable to open output stream for $uri")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                contentResolver.update(uri, publishValues, null, null)

                val finalName = resolveOpenableDisplayName(uri) ?: displayName
                return SavedPdfResult(
                    uri = uri,
                    displayName = finalName,
                    bytesWritten = bytesCopied,
                )
            }
        } catch (t: Throwable) {
            insertedUri?.let { orphanUri ->
                runCatching { contentResolver.delete(orphanUri, null, null) }
            }
            if (t is IOException) throw t
            throw IOException("Failed to write PDF stream to MediaStore.Downloads", t)
        }
    }

    fun cleanupStaleSpoolFiles(cacheDir: File, maxAgeMillis: Long = STALE_SPOOL_AGE_MS) {
        val spoolDir = File(cacheDir, SPOOL_DIR_NAME)
        if (!spoolDir.exists()) return
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        spoolDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private fun resolveOpenableDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun copyStreamLowMemory(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            totalBytes += read
        }
        return totalBytes
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        private const val SPOOL_DIR_NAME = "pdf_spool"
        private const val STREAM_BUFFER_BYTES = 16 * 1024
        private const val STALE_SPOOL_AGE_MS = 60 * 60 * 1000L
        private val INVALID_FILENAME_CHARS = Regex("[^A-Za-z0-9._\\- ]+")
        private val WHITESPACE_AND_UNDERSCORE_COLLAPSE = Regex("[\\s_]+")

        fun buildPdfFileName(rawJobName: String?, timestampMillis: Long): String {
            val cleanedBase = (rawJobName ?: "")
                .trim()
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
                .replace(INVALID_FILENAME_CHARS, "_")
                .replace(WHITESPACE_AND_UNDERSCORE_COLLAPSE, "_")
                .trim('_', '.', ' ')
                .take(64)
                .ifBlank { "PrintDocument" }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
            return "${cleanedBase}_${stamp}.pdf"
        }
    }
}
