package com.gsm.savetopdf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.print.PrintAttributes
import android.print.PrinterId
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.gsm.savetopdf.data.PdfDownloadsRepository
import com.gsm.savetopdf.data.PrinterPreferences
import com.gsm.savetopdf.print.MediaFormat
import com.gsm.savetopdf.print.SaveAsPdfPrintService
import com.gsm.savetopdf.print.SaveLocationPickerActivity
import com.gsm.savetopdf.ui.MainUiState
import com.gsm.savetopdf.ui.SaveAsPdfContent
import com.gsm.savetopdf.ui.theme.SaveAsPdfTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `package name and manifest components match com gsm savetopdf`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.gsm.savetopdf", context.packageName)
        assertEquals("Save as PDF", context.getString(R.string.app_name))
        assertEquals("Save as PDF", context.getString(R.string.printer_name))

        val serviceComponent = ComponentName(context, SaveAsPdfPrintService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(
            serviceComponent,
            PackageManager.GET_META_DATA,
        )
        assertEquals("android.permission.BIND_PRINT_SERVICE", serviceInfo.permission)
        assertTrue(serviceInfo.exported)
        assertNotNull(serviceInfo.metaData?.getInt("android.printservice"))

        val pickerComponent = ComponentName(context, SaveLocationPickerActivity::class.java)
        val pickerInfo = context.packageManager.getActivityInfo(pickerComponent, 0)
        assertFalse(pickerInfo.exported)

        val resolveList = context.packageManager.queryIntentServices(
            Intent("android.printservice.PrintService"),
            0,
        )
        assertTrue(
            resolveList.any { it.serviceInfo.name == SaveAsPdfPrintService::class.java.name },
        )
    }

    @Test
    fun `ask save location defaults to enabled every time`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PrinterPreferences(context)
        assertTrue(prefs.isAskLocationEveryTime())
        prefs.setAskLocationEveryTime(false)
        assertFalse(prefs.isAskLocationEveryTime())
        prefs.setAskLocationEveryTime(true)
        assertTrue(prefs.isAskLocationEveryTime())
    }

    @Test
    fun `printer capabilities expose 600 DPI and all 26 paper formats`() {
        val service = Robolectric.buildService(SaveAsPdfPrintService::class.java).create().get()
        val printerId: PrinterId = service.generatePrinterId(SaveAsPdfPrintService.PRINTER_LOCAL_ID)

        val capabilities = SaveAsPdfPrintService.buildPrinterCapabilities(
            printerId = printerId,
            resolutionLabel = "600 DPI",
            defaultMediaFormat = MediaFormat.ISO_A4,
        )

        val defaultResolution = capabilities.defaults.resolution
        assertNotNull(defaultResolution)
        assertEquals(600, defaultResolution!!.horizontalDpi)
        assertEquals(600, defaultResolution.verticalDpi)

        val mediaSizes = capabilities.mediaSizes
        assertEquals(26, mediaSizes.size)
        assertTrue(mediaSizes.contains(PrintAttributes.MediaSize.ISO_A4))
        assertTrue(mediaSizes.contains(PrintAttributes.MediaSize.ISO_A3))
        assertTrue(mediaSizes.contains(PrintAttributes.MediaSize.ISO_A2))
        assertTrue(mediaSizes.contains(PrintAttributes.MediaSize.NA_LETTER))
        assertEquals(PrintAttributes.MediaSize.ISO_A4, capabilities.defaults.mediaSize)
    }

    @Test
    fun `pdf filename sanitization produces clean pdf name`() {
        val name = PdfDownloadsRepository.buildPdfFileName(
            rawJobName = "Q4 / Architecture: Blueprint #1.pdf",
            timestampMillis = 1700000000000L,
        )
        assertTrue(name.startsWith("Q4_Architecture_Blueprint_1_"))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `native settings screen supports print settings, switch, paper dialog, and footer`() {
        var printSettingsClicked = false
        var askLocationToggledTo: Boolean? = null
        var selectedFormat: MediaFormat? = null

        composeTestRule.setContent {
            SaveAsPdfTheme {
                SaveAsPdfContent(
                    uiState = MainUiState(
                        defaultMediaFormat = MediaFormat.ISO_A4,
                        askLocationEveryTime = true,
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onOpenPrintSettings = { printSettingsClicked = true },
                    onPrintTestSheet = {},
                    onResumePendingSave = {},
                    onToggleAskEveryTime = { askLocationToggledTo = it },
                    onSelectMediaFormat = { selectedFormat = it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("open_print_settings_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(printSettingsClicked)

        composeTestRule
            .onNodeWithTag("ask_save_location_switch")
            .assertIsDisplayed()
            .performClick()
        assertEquals(false, askLocationToggledTo)

        composeTestRule
            .onNodeWithTag("paper_size_preference_row")
            .assertIsDisplayed()
            .performClick()

        composeTestRule
            .onNodeWithTag("media_format_iso_a2")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(MediaFormat.ISO_A2, selectedFormat)

        composeTestRule
            .onNodeWithTag("footer_developed_by")
            .assertIsDisplayed()
            .assertTextEquals("Developed by GS Mishra")
    }
}
