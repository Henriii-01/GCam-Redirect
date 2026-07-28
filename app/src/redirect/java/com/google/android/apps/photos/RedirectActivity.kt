package com.google.android.apps.photos

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class RedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = prefs().target()
        val data = intent?.data
        val view = if (data != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(data, intent.type ?: "image/*")
                if (target != SYSTEM_DEFAULT) setPackage(target)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else null
        val launched = view?.let { runCatching { startActivity(it) }.isSuccess } == true
        if (!launched && target != SYSTEM_DEFAULT) {
            packageManager.getLaunchIntentForPackage(target)
                ?.let { runCatching { startActivity(it) } }
        }
        finish()
    }
}
