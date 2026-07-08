package com.google.android.apps.photos

import android.content.Context
import android.content.SharedPreferences

// "" = system default gallery (no explicit package)
const val SYSTEM_DEFAULT = ""
const val IMMICH = "app.alextran.immich"

fun Context.prefs(): SharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)

fun SharedPreferences.target(): String = getString("target", IMMICH) ?: IMMICH

fun SharedPreferences.customTargets(): List<String> =
    (getStringSet("custom", emptySet()) ?: emptySet()).sorted()
