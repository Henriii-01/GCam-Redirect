package com.google.android.apps.photos

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Intent
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import android.widget.VideoView
import kotlin.concurrent.thread
import kotlin.math.abs

class StubActivity : Activity() {

    private data class Item(val uri: Uri, val isVideo: Boolean)

    private lateinit var imageView: ImageView
    private lateinit var videoView: VideoView
    private var items: List<Item> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)

        val initial = intent?.data
        if (initial == null) {
            finish()
            return
        }
        val initialIsVideo = (intent?.type ?: "").startsWith("video") ||
            initial.path?.contains("/video/") == true

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER)
        }
        videoView.setOnPreparedListener { it.isLooping = false; videoView.start() }
        videoView.setOnErrorListener { _, _, _ -> true }
        root.addView(imageView)
        root.addView(videoView)

        val margin = (16 * resources.displayMetrics.density).toInt()
        val openButton = Button(this).apply {
            text = "Open in app"
            alpha = 0.9f
            layoutParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply { setMargins(margin, margin, margin, margin * 3) }
            setOnClickListener { onOpenClicked() }
            setOnLongClickListener { withUnlock { chooseTarget() }; true }
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            (openButton.layoutParams as FrameLayout.LayoutParams).bottomMargin = margin + bars.bottom
            openButton.requestLayout()
            v.onApplyWindowInsets(insets)
        }
        root.addView(openButton)
        setContentView(root)

        items = listOf(Item(initial, initialIsVideo))
        index = 0
        showCurrent()

        val detector = GestureDetector(this, FlingListener())
        root.setOnTouchListener { _, e -> detector.onTouchEvent(e) }

        val wanted = ArrayList<String>()
        if (!granted(Manifest.permission.READ_MEDIA_IMAGES)) wanted.add(Manifest.permission.READ_MEDIA_IMAGES)
        if (!granted(Manifest.permission.READ_MEDIA_VIDEO)) wanted.add(Manifest.permission.READ_MEDIA_VIDEO)
        if (wanted.isEmpty()) loadLibrary(initial) else requestPermissions(wanted.toTypedArray(), REQ_MEDIA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode == REQ_MEDIA && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            showCurrent()
            intent?.data?.let { loadLibrary(it) }
        }
    }

    private fun onOpenClicked() {
        val target = prefs.getString(KEY_TARGET, null)
        if (target.isNullOrEmpty()) chooseTarget() else openInTarget(target)
    }

    private fun withUnlock(action: () -> Unit) {
        val km = getSystemService(KeyguardManager::class.java)
        if (km?.isKeyguardLocked == true) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = action()
            })
        } else {
            action()
        }
    }

    private fun chooseTarget() {
        val apps = photoApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, "No app can open this", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Open in…")
            .setItems(apps.map { it.second }.toTypedArray()) { _, which ->
                val pkg = apps[which].first
                prefs.edit().putString(KEY_TARGET, pkg).apply()
                openInTarget(pkg)
            }
            .show()
    }

    private fun photoApps(): List<Pair<String, String>> {
        val probe = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(items.getOrNull(index)?.uri ?: intent.data, currentMime())
        }
        return packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != packageName }
            .map { pkg -> pkg to appLabel(pkg) }
            .sortedBy { it.second.lowercase() }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun openInTarget(pkg: String) {
        val uri = items.getOrNull(index)?.uri ?: return
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, currentMime())
            setPackage(pkg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val opened = runCatching { startActivity(view); true }.getOrDefault(false)
        if (!opened) {
            packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { startActivity(it) } }
                ?: Toast.makeText(this, appLabel(pkg) + " can't open this", Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentMime() = if (items.getOrNull(index)?.isVideo == true) "video/*" else "image/*"

    private val prefs get() = getSharedPreferences("stub", MODE_PRIVATE)

    private fun granted(perm: String) =
        checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun loadLibrary(current: Uri) {
        thread {
            val list = queryMedia()
            if (list.isEmpty()) return@thread
            val start = list.indexOfFirst { it.uri == current }
                .let { if (it >= 0) it else list.indexOfFirst { i -> sameId(i.uri, current) } }
                .coerceAtLeast(0)
            runOnUiThread {
                items = list
                index = start
            }
        }
    }

    private fun sameId(a: Uri, b: Uri): Boolean {
        val ia = runCatching { ContentUris.parseId(a) }.getOrNull()
        val ib = runCatching { ContentUris.parseId(b) }.getOrNull()
        return ia != null && ia == ib
    }

    private fun queryMedia(): List<Item> {
        val out = ArrayList<Pair<Long, Item>>()
        if (granted(Manifest.permission.READ_MEDIA_IMAGES)) {
            collect(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, false, out)
        }
        if (granted(Manifest.permission.READ_MEDIA_VIDEO)) {
            collect(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED, true, out)
        }
        return out.sortedWith(compareByDescending<Pair<Long, Item>> { it.first }.thenByDescending { ContentUris.parseId(it.second.uri) })
            .map { it.second }
    }

    private fun collect(collection: Uri, idCol: String, dateCol: String, isVideo: Boolean, out: MutableList<Pair<Long, Item>>) {
        runCatching {
            contentResolver.query(collection, arrayOf(idCol, dateCol), null, null, null)?.use { c ->
                val idx = c.getColumnIndexOrThrow(idCol)
                val didx = c.getColumnIndexOrThrow(dateCol)
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, c.getLong(idx))
                    out.add(c.getLong(didx) to Item(uri, isVideo))
                }
            }
        }
    }

    private fun showCurrent() {
        val item = items.getOrNull(index) ?: return
        if (item.isVideo) {
            imageView.visibility = ImageView.GONE
            imageView.setImageDrawable(null)
            videoView.visibility = VideoView.VISIBLE
            videoView.setVideoURI(item.uri)
        } else {
            videoView.stopPlayback()
            videoView.visibility = VideoView.GONE
            imageView.visibility = ImageView.VISIBLE
            val uri = item.uri
            thread {
                val bmp = runCatching {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val longest = maxOf(info.size.width, info.size.height)
                        decoder.setTargetSampleSize(maxOf(1, longest / MAX_EDGE_PX))
                        decoder.isMutableRequired = false
                    }
                }.getOrNull()
                runOnUiThread { if (items.getOrNull(index)?.uri == uri) bmp?.let { imageView.setImageBitmap(it) } }
            }
        }
    }

    private inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (items.getOrNull(index)?.isVideo == true) {
                if (videoView.isPlaying) videoView.pause() else videoView.start()
                return true
            }
            return false
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            val dx = e2.x - (e1?.x ?: return false)
            if (abs(dx) < MIN_FLING_PX || abs(velocityX) < abs(velocityY)) return false
            if (dx < 0) {
                if (index < items.size - 1) { index++; showCurrent() }
            } else {
                if (index > 0) { index--; showCurrent() }
            }
            return true
        }
    }

    private companion object {
        const val REQ_MEDIA = 1
        const val MAX_EDGE_PX = 2048
        const val MIN_FLING_PX = 100
        const val KEY_TARGET = "open_target"
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
