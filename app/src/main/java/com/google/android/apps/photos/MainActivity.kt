package com.google.android.apps.photos

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(packageManager.getLaunchIntentForPackage("app.alextran.immich"))
        finish()
    }
}
