package com.quata.core.captions.templates

enum class CaptionFontFamily { SansBlack, SansCondensed, Monospace }

data class CaptionTemplateSpec(
    val textSizeRatio: Float, val maxWidthRatio: Float, val maxLines: Int, val verticalPosition: Float,
    val lineHeightMultiplier: Float, val fontFamily: CaptionFontFamily, val uppercase: Boolean,
    val textColorArgb: Long, val activeTextColorArgb: Long, val strokeColorArgb: Long,
    val strokeWidthRatio: Float, val activeBackgroundColorArgb: Long? = null,
    val segmentBackgroundColorArgb: Long? = null, val cornerRadiusRatio: Float = 0.018f,
    val shadowRadiusRatio: Float = 0.012f
)

object CaptionTemplateSpecs {
    fun get(style: CaptionTemplateStyle): CaptionTemplateSpec = when (style) {
        CaptionTemplateStyle.Karaoke -> CaptionTemplateSpec(.058f, .84f, 2, .74f, 1.12f, CaptionFontFamily.SansBlack, true, 0xffffffff, 0xffff7a18, 0xff000000, 0f, segmentBackgroundColorArgb = 0xb0000000, shadowRadiusRatio = 0f)
        CaptionTemplateStyle.PopWord -> CaptionTemplateSpec(.092f, .92f, 1, .67f, 1f, CaptionFontFamily.SansBlack, true, 0xffffffff, 0xff000000, 0xff000000, 0f, activeBackgroundColorArgb = 0xffff8a1a, cornerRadiusRatio = .026f, shadowRadiusRatio = 0f)
        CaptionTemplateStyle.Hormozi -> CaptionTemplateSpec(.066f, .88f, 2, .70f, 1.14f, CaptionFontFamily.SansCondensed, true, 0xffffffff, 0xff000000, 0xff000000, 0f, activeBackgroundColorArgb = 0xffffe500, segmentBackgroundColorArgb = 0xd2000000, shadowRadiusRatio = 0f)
        CaptionTemplateStyle.Typewriter -> CaptionTemplateSpec(.06f, .82f, 2, .76f, 1.16f, CaptionFontFamily.Monospace, false, 0xffffffff, 0xffffffff, 0xff12141e, 0f, segmentBackgroundColorArgb = 0xcd262932, shadowRadiusRatio = 0f)
    }
}
