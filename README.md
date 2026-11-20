# Save to PDF — Native Android Print Service

A lightweight, native Android `PrintService` built with **Kotlin** and **Jetpack Compose** that adds a lossless **Save to PDF** virtual printer directly into Android's system Print dialog. Designed for Android 10+ (API 29+) and OEM ROMs (such as Realme UI / ColorOS) where the built-in PDF printer is missing, disabled, or restricted.

---

## Features

- **Lossless Vector PDF Export:** Streams raw PDF bytes directly from Android's `PrintSpooler` at up to **600 DPI** with zero rasterization or bitmap compression—keeping text and vector graphics sharp at any zoom level.
- **Scoped Storage & Custom Save Locations:** Saves PDFs directly into your device's `Downloads/` directory via `MediaStore` (no legacy storage permissions required) or lets you select any folder using Android's Storage Access Framework (`ACTION_CREATE_DOCUMENT`).
- **Paper Sizes & Print Profiles:** Supports standard paper formats (**ISO A4**, **A3**, **A5**, **US Letter**) in both **Color** and **Monochrome** modes, plus built-in print calibration testing.
- **Modern Jetpack Compose UI:** Clean Material 3 interface to check print service status, launch system Print Settings in one tap, configure default page formats, and browse saved PDFs.
- **100% Offline & Private:** Zero ads, zero analytics, and zero network permissions—all documents are processed strictly on-device.

---

## Installation & Setup

1. Download the latest signed APK from the [Releases](../../releases) page and install it on your Android device (Android 10 / API 29 or higher).
2. Open **Save to PDF** and tap **Open Print Settings** (or navigate to **Settings > Connected devices / System > Printing**).
3. Toggle **Save to PDF** **ON**.
4. *(Realme / Oppo / ColorOS devices)* Open **Settings > App Management > Save to PDF > Battery Usage** and enable **Allow Background Activity** and **Allow Auto-launch** so Android does not kill the background print service after a reboot.

### Usage
1. Open **Google Chrome** or any app that supports printing.
2. Tap **Share > Print** (or **Menu > Print**).
3. Select **Save to PDF** from the printer dropdown at the top of the screen.
4. Choose your preferred paper size/orientation and tap the **Print / Save** button.

---

## Project Architecture

```text
app/src/main/java/com/gsm/savetopdf/
├── MainActivity.kt                          # Entry point & Compose host
├── data/
│   ├── PdfDownloadsRepository.kt            # MediaStore & SAF PDF file persistence
│   └── PrinterPreferences.kt                # User defaults (paper size, DPI, save mode)
├── print/
│   ├── SaveAsPdfPrintService.kt             # Core android.printservice.PrintService
│   ├── SaveLocationPickerActivity.kt        # System folder/document picker bridge
│   ├── PendingPdfSaveRegistry.kt            # Active print job stream handoff
│   ├── MediaFormat.kt                       # Paper sizes & resolution definitions
│   ├── CalibrationPrintDocumentAdapter.kt   # Built-in test page print adapter
│   └── PrintServiceStatusBus.kt             # Live service state observer
└── ui/
    ├── SaveAsPdfScreen.kt                   # Main Compose UI screen
    ├── PrinterComponents.kt                 # Reusable Material 3 UI cards & controls
    ├── MainViewModel.kt                     # UI state & repository coordination
    └── theme/                               # Color palette, typography & Material theme
