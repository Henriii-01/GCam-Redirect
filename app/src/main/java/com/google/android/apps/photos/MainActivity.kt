package com.google.android.apps.photos

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val target = if (data != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(data, intent.type ?: "image/*")
                setPackage("app.alextran.immich")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            packageManager.getLaunchIntentForPackage("app.alextran.immich")
        }
        target?.let { runCatching { startActivity(it) } }
        finish()
    }
}
