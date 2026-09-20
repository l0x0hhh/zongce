// 通过 GitHub Release 检查最新 APK，并把下载文件放到应用私有缓存目录。
package com.zongce.app.update

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zongce.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val downloadUrl: String
)

sealed class UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
}

object UpdateChecker {
    // 更新源使用项目的 GitHub Releases，Release 资产需包含可安装 APK。
    private const val GITHUB_REPOSITORY = "l0x0hhh/zongce"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME
        val url = URL("https://api.github.com/repos/$GITHUB_REPOSITORY/releases/latest")
        val json = openConnection(url).inputStream.bufferedReader().use { it.readText() }
        val release = JSONObject(json)
        val remoteVersion = release.optString("tag_name").removePrefix("v").trim()
        if (remoteVersion.isBlank()) error("GitHub Release 没有版本号")

        if (compareVersions(remoteVersion, current) <= 0) {
            UpdateCheckResult.UpToDate(current)
        } else {
            val apk = release.optJSONArray("assets")
                ?.let { assets ->
                    (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                }
                ?: error("最新 Release 没有 APK 文件")
            UpdateCheckResult.Available(
                UpdateInfo(
                    version = remoteVersion,
                    title = release.optString("name").ifBlank { "暨存更新" },
                    notes = release.optString("body"),
                    downloadUrl = apk.getString("browser_download_url")
                )
            )
        }
    }

    fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "zongce-${info.version}.apk")
        val partial = File(dir, "${target.name}.part")
        partial.delete()

        try {
            val connection = openConnection(URL(info.downloadUrl))
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(if (total > 0) ((copied * 100) / total).toInt() else -1)
                    }
                }
            }
            if (target.exists() && !target.delete()) error("无法替换旧 APK")
            if (!partial.renameTo(target)) error("无法完成 APK 下载")
            return target
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    fun contentUri(context: Context, apk: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apk
    )

    private fun openConnection(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "zongce-android/${BuildConfig.VERSION_NAME}")
            connect()
            if (responseCode !in 200..299) {
                disconnect()
                error("GitHub 返回 HTTP $responseCode")
            }
        }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split(Regex("[^0-9]+"))
            .filter(String::isNotBlank).map { it.toIntOrNull() ?: 0 }
        val b = right.split(Regex("[^0-9]+"))
            .filter(String::isNotBlank).map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val result = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        return 0
    }
}
