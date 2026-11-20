package com.gsm.savetopdf.print

import android.print.PrintAttributes
import android.print.PrintJobId
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import com.gsm.savetopdf.R
import com.gsm.savetopdf.data.PdfDownloadsRepository
import com.gsm.savetopdf.data.PrinterPreferences
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Memory-optimized on-demand [PrintService] registering the "Save as PDF" virtual printer.
 *
 * Streams raw vector PDF data sequentially using a single 16 KB buffer to keep peak memory
 * flat regardless of document size.
 */
class SaveAsPdfPrintService : PrintService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val activeJobs = ConcurrentHashMap<PrintJobId, Job>(2)

    private lateinit var preferences: PrinterPreferences
    private lateinit var downloadsRepository: PdfDownloadsRepository

    private var cachedPrinterFormat: MediaFormat? = null
    private var cachedPrinterInfo: PrinterInfo? = null

    override fun onCreate() {
        super.onCreate()
        preferences = PrinterPreferences(this)
        downloadsRepository = PdfDownloadsRepository(contentResolver)
        serviceScope.launch(ioDispatcher) {
            downloadsRepository.cleanupStaleSpoolFiles(cacheDir)
        }
    }

    override fun onDisconnected() {
        super.onDisconnected()
        cachedPrinterInfo = null
        cachedPrinterFormat = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        activeJobs.clear()
        cachedPrinterInfo = null
        cachedPrinterFormat = null
        super.onDestroy()
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return SaveAsPdfDiscoverySession()
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        activeJobs.remove(printJob.id)?.cancel()
        PendingPdfSaveRegistry.cancelByPrintJobId(printJob.id)
        if (!printJob.isCancelled && !printJob.isCompleted && !printJob.isFailed) {
            printJob.cancel()
        }
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        if (printJob.isQueued) {
            printJob.start()
        }

        val rawPdfFd = printJob.document.data
        if (rawPdfFd == null) {
            val errorMsg = getString(R.string.error_no_document_data)
            Log.e(TAG, errorMsg)
            PrintServiceStatusBus.emitFailed(errorMsg)
            printJob.fail(errorMsg)
            return
        }

        val jobLabel = printJob.info.label
            ?.takeIf { it.isNotBlank() }
            ?: printJob.document.info.name
        val jobId = printJob.id
        val askLocationEveryTime = preferences.isAskLocationEveryTime()

        val job = serviceScope.launch {
            try {
                if (askLocationEveryTime) {
                    val requestId = UUID.randomUUID().toString()
                    val suggestedFileName = PdfDownloadsRepository.buildPdfFileName(
                        rawJobName = jobLabel,
                        timestampMillis = System.currentTimeMillis(),
                    )
                    val spoolFile = withContext(ioDispatcher) {
                        downloadsRepository.spoolToCacheFile(
                            inputFd = rawPdfFd,
                            cacheDir = cacheDir,
                            requestId = requestId,
                        )
                    }

                    if (printJob.isCancelled) {
                        runCatching { spoolFile.delete() }
                        return@launch
                    }

                    val pendingRequest = PendingPdfSaveRequest(
                        requestId = requestId,
                        printJobId = jobId,
                        suggestedFileName = suggestedFileName,
                        spoolFile = spoolFile,
                        onSaved = { _, displayName, _ ->
                            serviceScope.launch {
                                if (!printJob.isCancelled && !printJob.isCompleted && !printJob.isFailed) {
                                    printJob.complete()
                                }
                                PrintServiceStatusBus.emitCompleted(displayName)
                            }
                        },
                        onCancelled = {
                            serviceScope.launch {
                                if (!printJob.isCancelled && !printJob.isCompleted && !printJob.isFailed) {
                                    printJob.cancel()
                                }
                                PrintServiceStatusBus.emitCancelled()
                            }
                        },
                        onFailed = { message ->
                            serviceScope.launch {
                                if (!printJob.isCancelled && !printJob.isCompleted && !printJob.isFailed) {
                                    printJob.fail(message)
                                }
                                PrintServiceStatusBus.emitFailed(message)
                            }
                        },
                    )
                    PendingPdfSaveRegistry.register(pendingRequest)

                    val pickerIntent = SaveLocationPickerActivity.createLaunchIntent(
                        context = this@SaveAsPdfPrintService,
                        requestId = requestId,
                    )
                    try {
                        startActivity(pickerIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct File Manager start deferred until app opens", e)
                    }
                } else {
                    val savedResult = withContext(ioDispatcher) {
                        downloadsRepository.saveRawPdfStream(
                            inputFd = rawPdfFd,
                            rawJobName = jobLabel,
                        )
                    }
                    if (!printJob.isCancelled) {
                        printJob.complete()
                        PrintServiceStatusBus.emitCompleted(savedResult.displayName)
                    }
                }
            } catch (cancelled: CancellationException) {
                runCatching { rawPdfFd.close() }
                if (!printJob.isCancelled && !printJob.isCompleted && !printJob.isFailed) {
                    printJob.cancel()
                }
                throw cancelled
            } catch (e: Exception) {
                val detail = e.localizedMessage ?: e.javaClass.simpleName
                val failureMessage = getString(R.string.error_save_failed, detail)
                Log.e(TAG, failureMessage, e)
                PrintServiceStatusBus.emitFailed(failureMessage)
                if (!printJob.isCancelled && !printJob.isCompleted && !printJob.isFailed) {
                    printJob.fail(failureMessage)
                }
            } finally {
                activeJobs.remove(jobId)
            }
        }

        activeJobs[jobId] = job
    }

    internal fun buildVirtualPrinterInfo(printerId: PrinterId): PrinterInfo {
        val defaultMediaFormat = preferences.getDefaultMediaFormat()
        val cached = cachedPrinterInfo
        if (cached != null && cachedPrinterFormat == defaultMediaFormat && cached.id == printerId) {
            return cached
        }

        val capabilities = buildPrinterCapabilities(
            printerId = printerId,
            resolutionLabel = getString(R.string.resolution_600_dpi_label),
            defaultMediaFormat = defaultMediaFormat,
        )

        val printerInfo = PrinterInfo.Builder(
            printerId,
            getString(R.string.printer_name),
            PrinterInfo.STATUS_IDLE,
        )
            .setDescription(getString(R.string.printer_description))
            .setIconResourceId(R.drawable.ic_printer_pdf)
            .setCapabilities(capabilities)
            .build()

        cachedPrinterFormat = defaultMediaFormat
        cachedPrinterInfo = printerInfo
        return printerInfo
    }

    private inner class SaveAsPdfDiscoverySession : PrinterDiscoverySession() {
        private val virtualPrinterId: PrinterId = generatePrinterId(PRINTER_LOCAL_ID)

        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
            publishVirtualPrinter()
        }

        override fun onStopPrinterDiscovery() = Unit

        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
            if (printerIds.contains(virtualPrinterId)) {
                publishVirtualPrinter()
            }
        }

        override fun onStartPrinterStateTracking(printerId: PrinterId) {
            if (printerId == virtualPrinterId) {
                publishVirtualPrinter()
            }
        }

        override fun onStopPrinterStateTracking(printerId: PrinterId) = Unit

        override fun onDestroy() = Unit

        private fun publishVirtualPrinter() {
            addPrinters(listOf(buildVirtualPrinterInfo(virtualPrinterId)))
        }
    }

    companion object {
        private const val TAG = "SaveAsPdfPrintService"
        const val PRINTER_LOCAL_ID = "save_as_pdf_virtual_printer"
        const val RESOLUTION_ID_600_DPI = "res_600_dpi"
        const val RESOLUTION_600_DPI = 600

        fun buildPrinterCapabilities(
            printerId: PrinterId,
            resolutionLabel: String,
            defaultMediaFormat: MediaFormat = MediaFormat.ISO_A4,
        ): PrinterCapabilitiesInfo {
            val builder = PrinterCapabilitiesInfo.Builder(printerId)
                .addResolution(
                    PrintAttributes.Resolution(
                        RESOLUTION_ID_600_DPI,
                        resolutionLabel,
                        RESOLUTION_600_DPI,
                        RESOLUTION_600_DPI,
                    ),
                    true,
                )
                .setColorModes(
                    PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                    PrintAttributes.COLOR_MODE_COLOR,
                )
                .setDuplexModes(
                    PrintAttributes.DUPLEX_MODE_NONE,
                    PrintAttributes.DUPLEX_MODE_NONE,
                )
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)

            MediaFormat.entries.forEach { format ->
                builder.addMediaSize(format.mediaSize, format == defaultMediaFormat)
            }

            return builder.build()
        }
    }
}
