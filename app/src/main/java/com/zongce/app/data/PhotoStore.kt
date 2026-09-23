// 负责照片导入、文件存在性检查和私有目录文件操作。
//
// 删文件这件事只由 RecordDeletion 在"确认 0 引用"后发起 —— 本类不判断引用，
// 也不许被别处直接拿来清照片（跨学年共用同一张照片时，误删是不可逆的丢证材料）。
package com.zongce.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 照片仓库：原图存 App 私有目录 filesDir/photos/，
 * 文件名用内容哈希前 8 位 + 原始扩展名（防重名），导出时才映射成规范名。
 * 原图永久保留，不提示清理（v1.4 决策：存储比后悔便宜）。
 */
class PhotoStore(context: Context) {

    private val dir: File = File(context.filesDir, "photos").apply { mkdirs() }

    fun photoFile(fileName: String): File = File(dir, fileName)

    fun exists(fileName: String): Boolean = photoFile(fileName).isFile

    /**
     * 把 uri（相机拍摄 / Photo Picker 选中）复制进私有目录。
     * @return Pair(存储文件名, EXIF 拍摄日期 yyyy-MM-dd 或空)
     */
    fun import(context: Context, uri: Uri): Pair<String, String> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取照片")
        val hash = MessageDigest.getInstance("MD5")
            .digest(bytes).joinToString("") { "%02x".format(it) }.take(8)
        val ext = guessExt(context, uri)
        val fileName = "$hash$ext"
        File(dir, fileName).writeBytes(bytes)
        return fileName to readTakenAt(File(dir, fileName))
    }

    /**
     * 删除底层文件。
     * @return File.delete() 的结果：false 只代表"这次没删掉"（被占用 / 已不存在），
     * 不算删除失败，调用方不该因此回滚已经提交的数据库事务 ——
     * 最坏结果是残留一个孤儿文件，而回滚的代价是"记录还在、照片没了"。
     */
    fun delete(fileName: String): Boolean = photoFile(fileName).delete()

    /**
     * 批量删除，返回删除失败的文件名。
     * 先去重：同一张照片可能被同批多条记录引用，只删一次也只需要报一次失败。
     */
    fun deleteAll(fileNames: Iterable<String>): List<String> {
        val failed = fileNames.distinct().filterNot { delete(it) }
        if (failed.isNotEmpty()) Log.w(TAG, "照片文件删除失败（记录已删除，残留孤儿文件）：$failed")
        return failed
    }

    private companion object {
        const val TAG = "PhotoStore"
    }

    private fun guessExt(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: ""
        return when {
            mime.contains("heic") || mime.contains("heif") -> ".heic"
            mime.contains("png") -> ".png"
            mime.contains("webp") -> ".webp"
            mime.contains("gif") -> ".gif"
            else -> ".jpg"
        }
    }

    private fun readTakenAt(file: File): String = try {
        ExifInterface(file.path).getAttribute(ExifInterface.TAG_DATETIME)
            ?.takeIf { it.length >= 10 }
            ?.let { "${it.substring(0, 4)}-${it.substring(5, 7)}-${it.substring(8, 10)}" }
            ?: ""
    } catch (e: Exception) {
        ""
    }
}
