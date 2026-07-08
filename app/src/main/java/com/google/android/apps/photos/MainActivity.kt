package com.google.android.apps.photos

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageManager.getLaunchIntentForPackage("app.alextran.immich")?.let { startActivity(it) }
        finish()
    }
}
