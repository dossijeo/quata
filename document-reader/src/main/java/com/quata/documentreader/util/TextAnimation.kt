package com.quata.documentreader.util

import android.os.Handler
import android.os.Looper
import android.widget.TextView

object TextAnimation {
    @JvmStatic
    fun marqueeAfterDelay(i: Int, textView: TextView) {
        Handler(Looper.getMainLooper()).postDelayed({ textView.isSelected = true }, i.toLong())
    }
}
