package korrektor.pdf

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer

/**
 * The functions of PDFium's C interface that korrektor uses, bound with JNA's direct mapping (fast calls).
 * The Kotlin names are mapped to the C names by [NAMES]; the C signatures are in PDFium's `fpdfview.h` and
 * `fpdf_text.h`. Nothing outside this package calls these functions: [Pdfium] guards them with a lock.
 *
 * `unsigned long` is [NativeLong] (4 bytes on Windows, 8 on Linux and macOS); `FPDF_BOOL` is an `Int`;
 * `FPDF_STRING` is a NUL-terminated byte array; pointers to numbers are small `Pointer`s the caller owns.
 * The declarations mirror the C functions one to one, so their long parameter lists are not ours to shorten.
 */
@Suppress("LongParameterList", "TooManyFunctions")
internal object PdfiumLib {
    private val NAMES =
        mapOf(
            "initLibrary" to "FPDF_InitLibrary",
            "loadDocument" to "FPDF_LoadDocument",
            "closeDocument" to "FPDF_CloseDocument",
            "getPageCount" to "FPDF_GetPageCount",
            "loadPage" to "FPDF_LoadPage",
            "closePage" to "FPDF_ClosePage",
            "getPageWidth" to "FPDF_GetPageWidthF",
            "getPageHeight" to "FPDF_GetPageHeightF",
            "getPageBoundingBox" to "FPDF_GetPageBoundingBox",
            "getPageRotation" to "FPDFPage_GetRotation",
            "loadTextPage" to "FPDFText_LoadPage",
            "closeTextPage" to "FPDFText_ClosePage",
            "countChars" to "FPDFText_CountChars",
            "getUnicode" to "FPDFText_GetUnicode",
            "isGenerated" to "FPDFText_IsGenerated",
            "isHyphen" to "FPDFText_IsHyphen",
            "getFontSize" to "FPDFText_GetFontSize",
            "getCharBox" to "FPDFText_GetCharBox",
            "createBitmap" to "FPDFBitmap_Create",
            "fillRect" to "FPDFBitmap_FillRect",
            "renderPage" to "FPDF_RenderPageBitmap",
            "bitmapBuffer" to "FPDFBitmap_GetBuffer",
            "bitmapStride" to "FPDFBitmap_GetStride",
            "destroyBitmap" to "FPDFBitmap_Destroy",
        )

    /** Loads the library (found through `jna.library.path`, see the build) and binds the functions. */
    fun load() {
        val mapper = FunctionMapper { _, method -> NAMES.getValue(method.name) }
        val library = NativeLibrary.getInstance("pdfium", mapOf(Library.OPTION_FUNCTION_MAPPER to mapper))
        Native.register(PdfiumLib::class.java, library)
    }

    @JvmStatic external fun initLibrary()

    @JvmStatic external fun loadDocument(
        path: ByteArray,
        password: ByteArray?,
    ): Pointer?

    @JvmStatic external fun closeDocument(document: Pointer)

    @JvmStatic external fun getPageCount(document: Pointer): Int

    @JvmStatic external fun loadPage(
        document: Pointer,
        index: Int,
    ): Pointer?

    @JvmStatic external fun closePage(page: Pointer)

    @JvmStatic external fun getPageWidth(page: Pointer): Float

    @JvmStatic external fun getPageHeight(page: Pointer): Float

    @JvmStatic external fun getPageBoundingBox(
        page: Pointer,
        rect: Pointer,
    ): Int

    @JvmStatic external fun getPageRotation(page: Pointer): Int

    @JvmStatic external fun loadTextPage(page: Pointer): Pointer?

    @JvmStatic external fun closeTextPage(textPage: Pointer)

    @JvmStatic external fun countChars(textPage: Pointer): Int

    @JvmStatic external fun getUnicode(
        textPage: Pointer,
        index: Int,
    ): Int

    @JvmStatic external fun isGenerated(
        textPage: Pointer,
        index: Int,
    ): Int

    @JvmStatic external fun isHyphen(
        textPage: Pointer,
        index: Int,
    ): Int

    @JvmStatic external fun getFontSize(
        textPage: Pointer,
        index: Int,
    ): Double

    @JvmStatic external fun getCharBox(
        textPage: Pointer,
        index: Int,
        left: Pointer,
        right: Pointer,
        bottom: Pointer,
        top: Pointer,
    ): Int

    @JvmStatic external fun createBitmap(
        width: Int,
        height: Int,
        alpha: Int,
    ): Pointer?

    @JvmStatic external fun fillRect(
        bitmap: Pointer,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: NativeLong,
    ): Int

    @JvmStatic external fun renderPage(
        bitmap: Pointer,
        page: Pointer,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int,
    )

    @JvmStatic external fun bitmapBuffer(bitmap: Pointer): Pointer?

    @JvmStatic external fun bitmapStride(bitmap: Pointer): Int

    @JvmStatic external fun destroyBitmap(bitmap: Pointer)
}
