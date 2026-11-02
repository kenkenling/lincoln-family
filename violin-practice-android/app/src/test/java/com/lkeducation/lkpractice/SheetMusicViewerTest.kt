package com.lkeducation.lkpractice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetMusicViewerTest {
    @Test
    fun supportedSheetMusicFormatsAreNarrowAndPredictable() {
        assertEquals(
            SheetMusicDocumentKind.PDF,
            sheetMusicDocumentKind("application/pdf", "music.bin"),
        )
        assertEquals(
            SheetMusicDocumentKind.PDF,
            sheetMusicDocumentKind(null, "Boreas Violin 2.PDF"),
        )
        assertEquals(
            SheetMusicDocumentKind.IMAGE,
            sheetMusicDocumentKind("image/jpeg", "page"),
        )
        assertEquals(
            SheetMusicDocumentKind.IMAGE,
            sheetMusicDocumentKind(null, "page.webp"),
        )
        assertEquals(
            SheetMusicDocumentKind.UNSUPPORTED,
            sheetMusicDocumentKind("image/gif", "animated.gif"),
        )
        assertEquals(
            SheetMusicDocumentKind.UNSUPPORTED,
            sheetMusicDocumentKind("application/msword", "music.doc"),
        )
    }

    @Test
    fun pickerOnlyOffersSupportedPdfAndImageTypes() {
        assertArrayEquals(
            arrayOf(
                "application/pdf",
                "image/png",
                "image/jpeg",
                "image/webp",
            ),
            sheetMusicPickerMimeTypes(),
        )
    }

    @Test
    fun pageLabelClampsInvalidIndexes() {
        assertEquals("Page 1 of 1", sheetMusicPageLabel(-4, 0))
        assertEquals("Page 1 of 4", sheetMusicPageLabel(-1, 4))
        assertEquals("Page 4 of 4", sheetMusicPageLabel(10, 4))
        assertEquals("Page 2 of 4", sheetMusicPageLabel(1, 4))
    }

    @Test
    fun renderWidthBalancesSharpZoomAndMemoryUse() {
        assertEquals(1_200, sheetMusicRenderWidth(320))
        assertEquals(1_600, sheetMusicRenderWidth(800))
        assertEquals(2_000, sheetMusicRenderWidth(1_500))
    }
}
