package com.gsm.savetopdf.print

import android.net.Uri
import android.print.PrintJobId
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingPdfSaveRequest(
    val requestId: String,
    val printJobId: PrintJobId?,
    val suggestedFileName: String,
    val spoolFile: File,
    val onSaved: (uri: Uri, displayName: String, bytesWritten: Long) -> Unit,
    val onCancelled: () -> Unit,
    val onFailed: (String) -> Unit,
)

/**
 * Thread-safe, zero-leak coordinator between [SaveAsPdfPrintService] and
 * [SaveLocationPickerActivity] so staged PDFs can be saved to any File Manager folder.
 */
object PendingPdfSaveRegistry {

    private val requests = ConcurrentHashMap<String, PendingPdfSaveRequest>()
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    fun register(request: PendingPdfSaveRequest) {
        requests[request.requestId] = request
        _pendingCount.value = requests.size
    }

    fun peek(requestId: String): PendingPdfSaveRequest? = requests[requestId]

    fun firstPending(): PendingPdfSaveRequest? = requests.values.firstOrNull()

    fun consume(requestId: String): PendingPdfSaveRequest? {
        val removed = requests.remove(requestId)
        _pendingCount.value = requests.size
        return removed
    }

    fun cancelByPrintJobId(printJobId: PrintJobId): Boolean {
        val match = requests.values.firstOrNull { it.printJobId == printJobId } ?: return false
        requests.remove(match.requestId)
        _pendingCount.value = requests.size
        runCatching { match.spoolFile.delete() }
        return true
    }

    fun clearAll() {
        val snapshot = requests.values.toList()
        requests.clear()
        _pendingCount.value = 0
        snapshot.forEach { runCatching { it.spoolFile.delete() } }
    }
}
