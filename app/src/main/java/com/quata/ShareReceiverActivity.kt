package com.quata

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Concrete Direct Share entry point required by Android's Sharing Shortcuts.
 * Forwarding into the single MainActivity avoids creating two independent
 * copies of Qüata when the app was already open.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(intent).setClass(this, MainActivity::class.java))
        finish()
    }
}
