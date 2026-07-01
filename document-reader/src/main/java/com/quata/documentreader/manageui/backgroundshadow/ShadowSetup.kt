package com.quata.documentreader.manageui.backgroundshadow

import android.view.View

object ShadowSetup {
    fun setUpShadow(view: View, builder: ShadowOpacity.Builder) {
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.background = builder.build()
    }
}
