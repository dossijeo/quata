package com.quata.core.captions.templates

import androidx.annotation.StringRes
import com.quata.R

/** Android resource mapping for the platform-neutral style model. */
@get:StringRes
val CaptionTemplateStyle.labelRes: Int
    get() = when (this) {
        CaptionTemplateStyle.Karaoke -> R.string.caption_template_karaoke
        CaptionTemplateStyle.PopWord -> R.string.caption_template_pop_word
        CaptionTemplateStyle.Hormozi -> R.string.caption_template_hormozi
        CaptionTemplateStyle.Typewriter -> R.string.caption_template_typewriter
    }
