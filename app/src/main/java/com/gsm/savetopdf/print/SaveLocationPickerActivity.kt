package com.gsm.savetopdf.print

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import com.gsm.savetopdf.R
import com.gsm.savetopdf.data.PdfDownloadsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Translucent, zero-Compose activity that invokes Android's system File Manager
 * ([Intent.ACTION_CREATE_DOCUMENT]) on every print job and streams the lossless PDF
 * to the chosen location using a 16 KB buffer.
 */
class SaveLocationPickerActivity : ComponentActivity() {

    private var activeRequestId: String? = null
    private var hasLaunchedPicker = false

    private val createPdfLauncher = registerForActivityResult(CreatePdfDocumentContract()) { targetUri ->
        handlePickerResult(targetUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activeRequestId = savedInstanceState?.getString(KEY_REQUEST_ID)
            ?: intent.getStringExtra(EXTRA_REQUEST_ID)
        hasLaunchedPicker = savedInstanceState?.getBoolean(KEY_LAUNCHED, false) ?: false

        val requestId = activeRequestId
        val request = requestId?.let { PendingPdfSaveRegistry.peek(it) }
        if (request == null || !request.spoolFile.exists()) {
            finish()
            return
        }

        if (!hasLaunchedPicker) {
            hasLaunchedPicker = true
            createPdfLauncher.launch(request.suggestedFileName)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val nextRequestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        if (nextRequestId != activeRequestId) {
            activeRequestId = nextRequestId
            val request = PendingPdfSaveRegistry.peek(nextRequestId)
            if (request != null && request.spoolFile.exists()) {
                hasLaunchedPicker = true
                createPdfLauncher.launch(request.suggestedFileName)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_REQUEST_ID, activeRequestId)
        outState.putBoolean(KEY_LAUNCHED, hasLaunchedPicker)
    }

    private fun handlePickerResult(targetUri: Uri?) {
        val requestId = activeRequestId
        val request = requestId?.let { PendingPdfSaveRegistry.consume(it) }
        if (request == null) {
            finish()
            return
        }

        if (targetUri == null) {
            deleteSpoolQuietly(request.spoolFile)
            request.onCancelled()
            finish()
            return
        }

        val repository = PdfDownloadsRepository(contentResolver)

        lifecycleScope.launch {
            try {
                val savedResult = withContext(Dispatchers.IO) {
                    repository.copyFileToTargetUri(
                        sourceFile = request.spoolFile,
                        targetUri = targetUri,
                        fallbackName = request.suggestedFileName,
                    )
                }

                request.onSaved(savedResult.uri, savedResult.displayName, savedResult.bytesWritten)
                Toast.makeText(
                    applicationContext,
                    getString(R.string.toast_saved_pdf, savedResult.displayName),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                val detail = e.localizedMessage ?: e.javaClass.simpleName
                val failureMsg = getString(R.string.error_save_failed, detail)
                request.onFailed(failureMsg)
                Toast.makeText(applicationContext, failureMsg, Toast.LENGTH_LONG).show()
            } finally {
                deleteSpoolQuietly(request.spoolFile)
                finish()
            }
        }
    }

    private fun deleteSpoolQuietly(file: File) {
        runCatching { file.delete() }
    }

    private class CreatePdfDocumentContract : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent {
            return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = PdfDownloadsRepository.PDF_MIME_TYPE
                putExtra(Intent.EXTRA_TITLE, input)
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                )
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            if (resultCode != RESULT_OK) return null
            return intent?.data
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "com.gsm.savetopdf.EXTRA_REQUEST_ID"
        private const val KEY_REQUEST_ID = "state_request_id"
        private const val KEY_LAUNCHED = "state_picker_launched"

        fun createLaunchIntent(context: Context, requestId: String): Intent {
            return Intent(context, SaveLocationPickerActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_ID, requestId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }
    }
}
