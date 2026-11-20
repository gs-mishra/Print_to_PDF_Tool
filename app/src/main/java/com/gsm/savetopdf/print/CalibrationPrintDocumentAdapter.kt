package com.gsm.savetopdf.print

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a vector 600 DPI calibration test sheet via Android's [PrintedPdfDocument]
 * so users can immediately test the "Save as PDF" virtual printer end-to-end.
 */
class CalibrationPrintDocumentAdapter(
    private val context: Context,
) : PrintDocumentAdapter() {

    private var currentAttributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        currentAttributes = newAttributes
        val info = PrintDocumentInfo.Builder("SaveAsPDF_Calibration_Sheet.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val attributes = currentAttributes ?: run {
            callback.onWriteFailed("Print attributes unavailable")
            return
        }

        val pdfDocument = PrintedPdfDocument(context, attributes)
        try {
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }

            val page = pdfDocument.startPage(0)
            drawVectorCalibrationSheet(page.canvas, attributes)
            pdfDocument.finishPage(page)

            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }

            FileOutputStream(destination.fileDescriptor).use { output ->
                pdfDocument.writeTo(output)
                output.flush()
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.localizedMessage ?: "Failed writing test sheet PDF")
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawVectorCalibrationSheet(
        canvas: android.graphics.Canvas,
        attributes: PrintAttributes,
    ) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val margin = 36f

        val bgPaint = Paint().apply {
            color = Color.parseColor("#F7F5F0")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // Subtle vector drafting grid
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DDD7CB")
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        val step = 24f
        var x = margin
        while (x <= width - margin) {
            canvas.drawLine(x, margin, x, height - margin, gridPaint)
            x += step
        }
        var y = margin
        while (y <= height - margin) {
            canvas.drawLine(margin, y, width - margin, y, gridPaint)
            y += step
        }

        // Border frame
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14213D")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(margin, margin, width - margin, height - margin, framePaint)

        // Vermilion corner registration marks
        val regPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D94E2A")
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }
        drawRegistrationMark(canvas, margin + 18f, margin + 18f, 9f, regPaint)
        drawRegistrationMark(canvas, width - margin - 18f, margin + 18f, 9f, regPaint)
        drawRegistrationMark(canvas, margin + 18f, height - margin - 18f, 9f, regPaint)
        drawRegistrationMark(canvas, width - margin - 18f, height - margin - 18f, 9f, regPaint)

        // Header banner
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14213D")
            style = Paint.Style.FILL
        }
        val bannerTop = margin + 44f
        val bannerHeight = 82f
        canvas.drawRect(margin + 24f, bannerTop, width - margin - 24f, bannerTop + bannerHeight, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("SAVE AS PDF · VECTOR TEST SHEET", margin + 42f, bannerTop + 38f, titlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F27452")
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        val mediaName = attributes.mediaSize?.id ?: "UNKNOWN"
        val hDpi = attributes.resolution?.horizontalDpi ?: 600
        val vDpi = attributes.resolution?.verticalDpi ?: 600
        canvas.drawText(
            "MEDIA: $mediaName   |   RESOLUTION: ${hDpi}x${vDpi} DPI   |   STREAM: LOSSLESS VECTOR",
            margin + 42f,
            bannerTop + 62f,
            subtitlePaint,
        )

        // Vector sharpness specimen lines & circles
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#161B22")
            textSize = 12f
            typeface = Typeface.SANS_SERIF
        }
        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#525E70")
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }

        val infoStartY = bannerTop + bannerHeight + 36f
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        canvas.drawText("Generated by Save as PDF PrintService ($timestamp)", margin + 28f, infoStartY, bodyPaint)
        canvas.drawText(
            "Zoom to 800% in any PDF viewer to verify razor-sharp lossless vector paths & text.",
            margin + 28f,
            infoStartY + 20f,
            monoPaint,
        )

        // Concentric vector precision target
        val centerX = width / 2f
        val centerY = infoStartY + 130f
        listOf(16f, 32f, 48f, 64f, 80f).forEachIndexed { idx, radius ->
            canvas.drawCircle(
                centerX,
                centerY,
                radius,
                if (idx % 2 == 0) framePaint else regPaint,
            )
        }
        canvas.drawLine(centerX - 96f, centerY, centerX + 96f, centerY, regPaint)
        canvas.drawLine(centerX, centerY - 96f, centerX, centerY + 96f, regPaint)
    }

    private fun drawRegistrationMark(
        canvas: android.graphics.Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        paint: Paint,
    ) {
        canvas.drawCircle(cx, cy, radius * 0.55f, paint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)
    }
}
