package com.gsm.savetopdf.ui

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsm.savetopdf.R
import com.gsm.savetopdf.data.PrinterPreferences
import com.gsm.savetopdf.print.MediaFormat
import com.gsm.savetopdf.print.PendingPdfSaveRegistry
import com.gsm.savetopdf.print.PrintJobEvent
import com.gsm.savetopdf.print.PrintServiceStatusBus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class MainUiState(
    val defaultMediaFormat: MediaFormat = MediaFormat.ISO_A4,
    val askLocationEveryTime: Boolean = true,
    val pendingSaveCount: Int = 0,
)

class MainViewModel(
    private val preferences: PrinterPreferences,
    private val stringProvider: (Int, Array<out Any>) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            defaultMediaFormat = preferences.getDefaultMediaFormat(),
            askLocationEveryTime = preferences.isAskLocationEveryTime(),
            pendingSaveCount = PendingPdfSaveRegistry.pendingCount.value,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    init {
        observePrintEvents()
        observePendingSaves()
    }

    private fun observePrintEvents() {
        viewModelScope.launch {
            PrintServiceStatusBus.events.collect { event ->
                val message = when (event) {
                    is PrintJobEvent.Completed -> stringProvider(R.string.snackbar_job_saved, arrayOf(event.fileName))
                    is PrintJobEvent.Failed -> event.message
                    PrintJobEvent.Cancelled -> stringProvider(R.string.snackbar_job_cancelled, emptyArray())
                }
                _snackbarMessages.tryEmit(message)
            }
        }
    }

    private fun observePendingSaves() {
        viewModelScope.launch {
            PendingPdfSaveRegistry.pendingCount.collect { count ->
                _uiState.update { it.copy(pendingSaveCount = count) }
            }
        }
    }

    fun selectDefaultMediaFormat(format: MediaFormat) {
        preferences.setDefaultMediaFormat(format)
        _uiState.update { it.copy(defaultMediaFormat = format) }
        _snackbarMessages.tryEmit(
            stringProvider(R.string.snackbar_default_media_updated, arrayOf(format.displayName)),
        )
    }

    fun setAskLocationEveryTime(enabled: Boolean) {
        preferences.setAskLocationEveryTime(enabled)
        _uiState.update { it.copy(askLocationEveryTime = enabled) }
        val messageRes = if (enabled) {
            R.string.snackbar_save_mode_ask
        } else {
            R.string.snackbar_save_mode_downloads
        }
        _snackbarMessages.tryEmit(stringProvider(messageRes, emptyArray()))
    }

    fun showMessage(message: String) {
        _snackbarMessages.tryEmit(message)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            return MainViewModel(
                preferences = PrinterPreferences(appContext),
                stringProvider = { resId, args ->
                    if (args.isEmpty()) appContext.getString(resId)
                    else appContext.getString(resId, *args)
                },
            ) as T
        }
    }
}
