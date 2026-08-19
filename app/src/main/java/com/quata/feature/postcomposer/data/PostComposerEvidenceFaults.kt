package com.quata.feature.postcomposer.data

import com.quata.BuildConfig

object PostComposerEvidenceFaults {
    @Volatile
    private var failInsertAfterUploadOnce = false

    fun requestFailInsertAfterUploadOnce() {
        if (BuildConfig.DEBUG) failInsertAfterUploadOnce = true
    }

    fun consumeFailInsertAfterUploadOnce(): Boolean {
        if (!BuildConfig.DEBUG || !failInsertAfterUploadOnce) return false
        failInsertAfterUploadOnce = false
        return true
    }
}
