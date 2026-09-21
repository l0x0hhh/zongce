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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val downloadUrl: String
)

internal data class ReleasePayload(
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
    // 镜像与 GitHub 两个来源一起问，取版本号更高的那个（见 pickNewestPayload）。
    private const val GITHUB_REPOSITORY = "l0x0hhh/zongce"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME
        val payload = pickNewestPayload()
        val remoteVersion = payload.version
        if (remoteVersion.isBlank()) error("更新源没有给出版本号")

        if (compareVersions(remoteVersion, current) <= 0) {
            UpdateCheckResult.UpToDate(current)
        } else {
            UpdateCheckResult.Available(
                UpdateInfo(
                    version = remoteVersion,
                    title = payload.title,
                    notes = payload.notes,
                    downloadUrl = payload.downloadUrl
                )
            )
        }
    }

    /**
     * 镜像与 GitHub 一起问，取版本号更高的那个。
     *
     * 为什么要两个都问：镜像一旦忘了同步就会一直报旧版本，如果"镜像可达即权威"，
     * 用户会被永久卡在旧版本上、再也收不到更新。取两者中较新的版本，镜像就从
     * "单点权威"退化成"可选加速"：镜像新就用镜像、GitHub 新就用 GitHub、
     * 只有一边通也照样能检查更新。
     *
     * 两边都失败才抛错，并把两边的失败原因都带上，便于判断是哪一端的问题。
     */
    private suspend fun pickNewestPayload(): ReleasePayload = coroutineScope {
        val mirrorTask = async { runCatching { readMirror() } }
        val githubTask = async { runCatching { readGitHubRelease() } }
        val mirror = mirrorTask.await()
        val github = githubTask.await()

        val candidates = listOfNotNull(mirror.getOrNull(), github.getOrNull())
        if (candidates.isEmpty()) {
            val mirrorReason = mirror.exceptionOrNull()?.message ?: "不可用"
            val githubReason = github.exceptionOrNull()?.message ?: "不可用"
            error("检查更新失败：镜像（$mirrorReason）；GitHub（$githubReason）")
        }
        return@coroutineScope pickNewest(mirror.getOrNull(), github.getOrNull())!!
    }

    /**
     * 两个来源里取版本号更高的那个；只有一边有就返回那一边；都没有返回 null。
     * 抽成纯函数是为了能被单测覆盖 —— 尤其是"镜像过期、GitHub 更新"这个关键场景。
     */
    internal fun pickNewest(mirror: ReleasePayload?, github: ReleasePayload?): ReleasePayload? {
        var best: ReleasePayload? = null
        for (candidate in listOfNotNull(mirror, github)) {
            if (best == null || compareVersions(candidate.version, best.version) > 0) {
                best = candidate
            }
        }
        return best
    }

    private fun readMirror(): ReleasePayload {
        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (manifestUrl.isBlank()) error("未配置镜像更新源")
        requireHttps(manifestUrl, "镜像清单地址")
        val json = openConnection(URL(manifestUrl), "镜像").inputStream.bufferedReader().use { it.readText() }
        val manifest = JSONObject(json)
        return ReleasePayload(
            version = manifest.optString("version").removePrefix("v").trim(),
            title = manifest.optString("title").ifBlank { "暨存更新" },
            notes = manifest.optString("notes"),
            downloadUrl = requireHttps(
                manifest.optString("apkUrl").ifBlank { error("镜像清单缺少 apkUrl") },
                "镜像清单 apkUrl"
            )
        )
    }

    private fun readGitHubRelease(): ReleasePayload {
        val url = URL("https://api.github.com/repos/$GITHUB_REPOSITORY/releases/latest")
        val json = openConnection(url, "GitHub").inputStream.bufferedReader().use { it.readText() }
        val release = JSONObject(json)
        val apk = release.optJSONArray("assets")
            ?.let { assets ->
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            }
            ?: error("最新 Release 没有 APK 文件")
        return ReleasePayload(
            version = release.optString("tag_name").removePrefix("v").trim(),
            title = release.optString("name").ifBlank { "暨存更新" },
            notes = release.optString("body"),
            downloadUrl = requireHttps(apk.getString("browser_download_url"), "GitHub Release 资产")
        )
    }

    /**
     * 下载地址必须是 HTTPS。
     * targetSdk 35 默认禁止明文流量，http 的地址会在下载时被系统拦掉、
     * 报一个很难懂的错；在这里提前拦下来，错误信息才看得懂。
     */
    internal fun requireHttps(url: String, label: String): String {
        if (!url.startsWith("https://", ignoreCase = true)) {
            error("$label 必须是 HTTPS 地址，当前为：$url")
        }
        return url
    }

    fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "zongce-${info.version}.apk")
        val partial = File(dir, "${target.name}.part")
        partial.delete()

        try {
            val connection = openConnection(URL(requireHttps(info.downloadUrl, "下载地址")), "更新下载")
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

    private fun openConnection(url: URL, label: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "zongce-android/${BuildConfig.VERSION_NAME}")
            connect()
            if (responseCode !in 200..299) {
                disconnect()
                // 用调用方给的 label，而不是写死 GitHub —— 读镜像失败时也能报对来源。
                error("$label 返回 HTTP $responseCode")
            }
        }

    /** 版本比较：按非数字切分后逐段比数值，所以 1.10 > 1.9（字符串比较会判反）。 */
    internal fun compareVersions(left: String, right: String): Int {
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
