package com.gsm.savetopdf.print

import android.print.PrintAttributes

enum class PaperSeries {
    ISO_A,
    ISO_BC,
    NORTH_AMERICA,
    PHOTO_CARD,
}

/**
 * 26 standard paper media sizes registered by [SaveAsPdfPrintService] at 600 DPI.
 */
enum class MediaFormat(
    val id: String,
    val displayName: String,
    val chipLabel: String,
    val dimensionsLabel: String,
    val series: PaperSeries,
    val mediaSize: PrintAttributes.MediaSize,
) {
    // ISO A Series (8)
    ISO_A4("ISO_A4", "ISO A4", "A4", "210 × 297 mm", PaperSeries.ISO_A, PrintAttributes.MediaSize.ISO_A4),
    ISO_A3("ISO_A3", "ISO A3", "A3", "297 × 420 mm", PaperSeries.ISO_A, PrintAttributes.MediaSize.ISO_A3),
    ISO_A2("ISO_A2", "ISO A2", "A2", "420 × 594 mm", PaperSeries.ISO_A, PrintAttributes.MediaSize.ISO_A2),
    ISO_A1("ISO_A1", "ISO A1", "A1", "594 × 841 mm", PaperSeries.ISO_A, PrintAttributes.MediaSize.ISO_A1),
    ISO_A0("ISO_A0", "ISO A0", "A0", "841 × 1189 mm", PaperSeries.ISO_A, PrintAttributes.MediaSize.ISO_A0),
    ISO_A5("ISO_A5", "ISO A5", "A5", "148 × 210 mm", PaperSeries.ISO_A, PrintAttributes.MediaSize.ISO_A5),
    ISO_A6("ISO_A6", "ISO A6", "A6", "105 × 148 mm", PaperSeries.ISO_A, PrintAttributes.MediaSize.ISO_A6),
    ISO_A7("ISO_A7", "ISO A7", "A7", "74 × 105 mm", PaperSeries.ISO_A, PrintAttributes.MediaSize.ISO_A7),

    // ISO B, JIS B & ISO C Series (8)
    ISO_B3("ISO_B3", "ISO B3", "B3", "353 × 500 mm", PaperSeries.ISO_BC, PrintAttributes.MediaSize.ISO_B3),
    ISO_B4("ISO_B4", "ISO B4", "B4", "250 × 353 mm", PaperSeries.ISO_BC, PrintAttributes.MediaSize.ISO_B4),
    ISO_B5("ISO_B5", "ISO B5", "B5", "176 × 250 mm", PaperSeries.ISO_BC, PrintAttributes.MediaSize.ISO_B5),
    JIS_B4("JIS_B4", "JIS B4", "JIS B4", "257 × 364 mm", PaperSeries.ISO_BC, PrintAttributes.MediaSize.JIS_B4),
    JIS_B5("JIS_B5", "JIS B5", "JIS B5", "182 × 257 mm", PaperSeries.ISO_BC, PrintAttributes.MediaSize.JIS_B5),
    ISO_C4("ISO_C4", "ISO C4", "C4", "229 × 324 mm", PaperSeries.ISO_BC, PrintAttributes.MediaSize.ISO_C4),
    ISO_C5("ISO_C5", "ISO C5", "C5", "162 × 229 mm", PaperSeries.ISO_BC, PrintAttributes.MediaSize.ISO_C5),
    ISO_C6("ISO_C6", "ISO C6", "C6", "114 × 162 mm", PaperSeries.ISO_BC, PrintAttributes.MediaSize.ISO_C6),

    // North American / US & ANSI (7)
    NA_LETTER("NA_LETTER", "Letter", "Letter", "8.5 × 11 in", PaperSeries.NORTH_AMERICA, PrintAttributes.MediaSize.NA_LETTER),
    NA_LEGAL("NA_LEGAL", "Legal", "Legal", "8.5 × 14 in", PaperSeries.NORTH_AMERICA, PrintAttributes.MediaSize.NA_LEGAL),
    NA_TABLOID("NA_TABLOID", "Tabloid", "Tabloid", "11 × 17 in", PaperSeries.NORTH_AMERICA, PrintAttributes.MediaSize.NA_TABLOID),
    NA_LEDGER("NA_LEDGER", "Ledger", "Ledger", "17 × 11 in", PaperSeries.NORTH_AMERICA, PrintAttributes.MediaSize.NA_LEDGER),
    NA_FOOLSCAP("NA_FOOLSCAP", "Foolscap", "Foolscap", "8 × 13 in", PaperSeries.NORTH_AMERICA, PrintAttributes.MediaSize.NA_FOOLSCAP),
    NA_GOVT_LETTER("NA_GOVT_LETTER", "Govt Letter", "Govt Ltr", "8 × 10.5 in", PaperSeries.NORTH_AMERICA, PrintAttributes.MediaSize.NA_GOVT_LETTER),
    NA_JUNIOR_LEGAL("NA_JUNIOR_LEGAL", "Junior Legal", "Jr Legal", "5 × 8 in", PaperSeries.NORTH_AMERICA, PrintAttributes.MediaSize.NA_JUNIOR_LEGAL),

    // Photo & Index Cards (3)
    NA_INDEX_4X6("NA_INDEX_4X6", "4×6 Photo", "4×6 in", "4 × 6 in", PaperSeries.PHOTO_CARD, PrintAttributes.MediaSize.NA_INDEX_4X6),
    NA_INDEX_5X8("NA_INDEX_5X8", "5×8 Card", "5×8 in", "5 × 8 in", PaperSeries.PHOTO_CARD, PrintAttributes.MediaSize.NA_INDEX_5X8),
    NA_INDEX_3X5("NA_INDEX_3X5", "3×5 Index", "3×5 in", "3 × 5 in", PaperSeries.PHOTO_CARD, PrintAttributes.MediaSize.NA_INDEX_3X5);

    companion object {
        private val bySeriesCache: Map<PaperSeries, List<MediaFormat>> = entries.groupBy { it.series }

        fun fromId(id: String?): MediaFormat = entries.firstOrNull { it.id == id } ?: ISO_A4

        fun bySeries(series: PaperSeries): List<MediaFormat> = bySeriesCache[series].orEmpty()
    }
}
