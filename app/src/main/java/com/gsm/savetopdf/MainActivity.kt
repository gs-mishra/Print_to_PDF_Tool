package com.gsm.savetopdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.gsm.savetopdf.print.CalibrationPrintDocumentAdapter
import com.gsm.savetopdf.print.PendingPdfSaveRegistry
import com.gsm.savetopdf.print.SaveAsPdfPrintService
import com.gsm.savetopdf.print.SaveLocationPickerActivity
import com.gsm.savetopdf.ui.MainViewModel
import com.gsm.savetopdf.ui.SaveAsPdfScreen
import com.gsm.savetopdf.ui.theme.SaveAsPdfTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaveAsPdfTheme {
                SaveAsPdfScreen(
                    viewModel = viewModel,
                    onOpenPrintSettings = ::openPrintSettings,
                    onPrintTestSheet = ::launchTestPrintJob,
                    onResumePendingSave = ::launchPendingSavePicker,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PendingPdfSaveRegistry.pendingCount.value > 0) {
            launchPendingSavePicker()
        }
    }

    private fun launchPendingSavePicker() {
        val pending = PendingPdfSaveRegistry.firstPending() ?: return
        startActivity(SaveLocationPickerActivity.createLaunchIntent(this, pending.requestId))
    }

    private fun openPrintSettings() {
        val printSettingsIntent = Intent(Settings.ACTION_PRINT_SETTINGS)
        try {
            startActivity(printSettingsIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                viewModel.showMessage(getString(R.string.snackbar_print_settings_error))
            }
        }
    }

    private fun launchTestPrintJob() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val defaultMedia = viewModel.uiState.value.defaultMediaFormat
        val resolution = PrintAttributes.Resolution(
            SaveAsPdfPrintService.RESOLUTION_ID_600_DPI,
            getString(R.string.resolution_600_dpi_label),
            SaveAsPdfPrintService.RESOLUTION_600_DPI,
            SaveAsPdfPrintService.RESOLUTION_600_DPI,
        )
        val attributes = PrintAttributes.Builder()
            .setMediaSize(defaultMedia.mediaSize)
            .setResolution(resolution)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(
            getString(R.string.test_job_name),
            CalibrationPrintDocumentAdapter(this),
            attributes,
        )
    }
}
