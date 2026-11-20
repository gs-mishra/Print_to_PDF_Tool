package com.gsm.savetopdf.print

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface PrintJobEvent {
    data class Completed(val fileName: String) : PrintJobEvent
    data class Failed(val message: String) : PrintJobEvent
    data object Cancelled : PrintJobEvent
}

/**
 * Zero-retained-state event bus between [SaveAsPdfPrintService] and the UI.
 */
object PrintServiceStatusBus {
    private val _events = MutableSharedFlow<PrintJobEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<PrintJobEvent> = _events.asSharedFlow()

    fun emitCompleted(fileName: String) {
        _events.tryEmit(PrintJobEvent.Completed(fileName))
    }

    fun emitFailed(message: String) {
        _events.tryEmit(PrintJobEvent.Failed(message))
    }

    fun emitCancelled() {
        _events.tryEmit(PrintJobEvent.Cancelled)
    }
}
