package dev.phantomwatch.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dev.phantomwatch.R

object PushArt {
    private const val W = 1024
    private const val H = 512

    // artwork carries own type, this only crops it to 2:1 notification draws
    fun banner(context: Context): Bitmap {
        val art = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(art)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val photo = BitmapFactory.decodeResource(context.resources, R.drawable.notify_banner) ?: return art
        val src = Rect()
        crop(photo.width, photo.height, src)
        canvas.drawBitmap(photo, src, RectF(0f, 0f, W.toFloat(), H.toFloat()), paint)
        photo.recycle()
        return art
    }

    fun thumb(context: Context): Bitmap {
        val side = 256
        val art = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val photo = BitmapFactory.decodeResource(context.resources, R.drawable.notify_banner) ?: return art
        val src = Rect()
        // ghost sits right of centre, wordmark left of it
        val keep = (photo.height * 0.86f).toInt()
        val cx = (photo.width * 0.76f).toInt()
        val top = (photo.height - keep) / 2
        src.set(cx - keep / 2, top, cx + keep / 2, top + keep)
        Canvas(art).drawBitmap(photo, src, RectF(0f, 0f, side.toFloat(), side.toFloat()), paint)
        photo.recycle()
        return art
    }

    private fun crop(width: Int, height: Int, out: Rect) {
        if (width.toFloat() / height > W.toFloat() / H) {
            val keep = (height * W.toFloat() / H).toInt()
            val left = (width - keep) / 2
            out.set(left, 0, left + keep, height)
        } else {
            val keep = (width * H.toFloat() / W).toInt()
            val top = (height - keep) / 2
            out.set(0, top, width, top + keep)
        }
    }
}
