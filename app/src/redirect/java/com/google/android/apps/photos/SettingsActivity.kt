package com.google.android.apps.photos

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val group = RadioGroup(this)
        val options =
            listOf("System default gallery" to SYSTEM_DEFAULT, "Immich" to IMMICH) +
                prefs().customTargets().map { it to it }
        options.forEach { (label, pkg) ->
            group.addView(RadioButton(this).apply {
                text = label
                isChecked = pkg == prefs().target()
                setOnClickListener { prefs().edit().putString("target", pkg).apply() }
                if (pkg != SYSTEM_DEFAULT && pkg != IMMICH) {
                    setOnLongClickListener { removeCustom(pkg); true }
                }
            })
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                addView(TextView(this@SettingsActivity).apply {
                    text = "Open camera preview in: (long-press custom entry to remove)"
                })
                addView(group)
                addView(Button(this@SettingsActivity).apply {
                    text = "Add custom package"
                    setOnClickListener { addCustom() }
                })
            })
        })
    }

    private fun addCustom() {
        val input = EditText(this).apply { hint = "e.g. app.alextran.immich" }
        AlertDialog.Builder(this)
            .setTitle("Package name")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isNotEmpty()) {
                    prefs().edit()
                        .putStringSet("custom", prefs().customTargets().toSet() + pkg)
                        .putString("target", pkg)
                        .apply()
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeCustom(pkg: String) {
        val edit = prefs().edit().putStringSet("custom", prefs().customTargets().toSet() - pkg)
        if (prefs().target() == pkg) edit.putString("target", IMMICH)
        edit.apply()
        render()
    }
}
