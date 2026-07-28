package com.google.android.apps.photos

import android.Manifest
import android.app.Activity
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
import android.widget.FrameLayout
import android.widget.ImageView
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
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
