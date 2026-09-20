// 图片解码、缩略图和导出压缩工具，统一处理文件与 Uri 来源。
package com.zongce.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 图片压缩与转码：
 *  - 学校系统硬约束：仅图片格式、单个文件最大 4M → 目标压到 3.5MB 以内（留余量）
 *  - iPhone 拍的 HEIC 必须转 JPG（系统多半不认 HEIC）
 *  - 保分辨率优先：先缩长边到 2400px，质量 88 → 80 → 70，仍超再降分辨率
 */
object ImageTools {

    private const val TARGET_BYTES = 3_500_000  // 3.5MB，比系统的 4M 留余量
    private const val FIRST_EDGE = 2400
    private val QUALITIES = intArrayOf(88, 80, 70)
    private val EDGES = intArrayOf(2400, 2000, 1600)

    /** 原图若是 jpg 且已足够小，直接返回，避免二次有损编码 */
    fun shouldPassThrough(file: File): Boolean {
        if (!file.name.endsWith(".jpg", true)) return false
        if (file.length() > TARGET_BYTES) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        return longEdge in 1..FIRST_EDGE
    }

    /**
     * 压缩并统一转为 JPG。
     * @return ByteArray（JPEG 字节）
     */
    fun toJpeg(file: File): ByteArray {
        if (shouldPassThrough(file)) return file.readBytes()

        val rotation = readRotation(file)

        for (edge in EDGES) {
            val bitmap = decodeSampled(file, edge) ?: continue
            val rotated = rotate(bitmap, rotation)
            for (q in QUALITIES) {
                val bytes = rotated.toJpegBytes(q)
                if (bytes.size <= TARGET_BYTES) {
                    if (rotated !== bitmap) rotated.recycle()
                    bitmap.recycle()
                    return bytes
                }
            }
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()
        }
        // 极端情况：直接按最小尺寸、最低质量出一张
        val last = decodeSampled(file, 1280) ?: throw IllegalStateException("无法解码照片")
        val out = last.toJpegBytes(65)
        last.recycle()
        return out
    }

    /** 列表预览用缩略图（长边约 400px） */
    fun thumbnail(file: File, edge: Int = 400): Bitmap? {
        val bitmap = decodeSampled(file, edge) ?: return null
        return rotate(bitmap, readRotation(file))
    }

    /** 从系统相册或系统相机 Uri 解码缩略图，避免录入页只显示占位文字。 */
    fun thumbnail(context: Context, uri: Uri, edge: Int = 400): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / sample > edge) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null
        return rotate(bitmap, readRotation(context, uri))
    }

    private fun decodeSampled(file: File, targetEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        val longEdge = maxOf(w, h)
        while (longEdge / sample > targetEdge) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, opts)
    }

    private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun readRotation(file: File): Int = try {
        when (ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    private fun readRotation(context: Context, uri: Uri): Int = try {
        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
