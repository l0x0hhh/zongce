// 覆盖更新检查的两条纯逻辑：版本比较、以及在"镜像 + GitHub"两个来源里挑较新的那个。
// 这两条都不碰网络，所以能在 JVM 单测里直接跑。
package com.zongce.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private fun payload(version: String, from: String) = ReleasePayload(
        version = version,
        title = from,
        notes = "",
        downloadUrl = "https://example.com/$from-$version.apk"
    )

    // ---------- 版本比较 ----------

    @Test
    fun higherVersionComparesGreater() {
        assertTrue(UpdateChecker.compareVersions("1.1.0", "1.0.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.1.0") < 0)
        assertEquals(0, UpdateChecker.compareVersions("1.1.0", "1.1.0"))
    }

    @Test
    fun versionSegmentsAreComparedNumericallyNotAsText() {
        // 字符串比较会把 "1.10" 判成小于 "1.9"，这是版本号比较最经典的坑。
        assertTrue(UpdateChecker.compareVersions("1.10", "1.9") > 0)
        assertTrue(UpdateChecker.compareVersions("2.0", "1.9.9") > 0)
    }

    @Test
    fun missingTrailingSegmentsCountAsZero() {
        assertEquals(0, UpdateChecker.compareVersions("1.0", "1.0.0"))
        assertTrue(UpdateChecker.compareVersions("1.1", "1.0.9") > 0)
    }

    @Test
    fun leadingVTolerated() {
        assertEquals(0, UpdateChecker.compareVersions("v1.1.0", "1.1.0"))
    }

    // ---------- 两个来源里挑较新的 ----------

    @Test
    fun staleMirrorDoesNotHideTheNewerGitHubRelease() {
        // 这是本次修改要防的核心故障：镜像忘了同步、版本停在 1.1.0，
        // 而 GitHub 已经到 1.2.0。旧逻辑"镜像可达即权威"会让用户永远停在 1.1.0。
        val mirror = payload("1.1.0", "mirror")
        val github = payload("1.2.0", "github")

        assertSame(github, UpdateChecker.pickNewest(mirror, github))
    }

    @Test
    fun newerMirrorWinsSoDomesticUsersGetTheFastSource() {
        val mirror = payload("1.3.0", "mirror")
        val github = payload("1.2.0", "github")

        assertSame(mirror, UpdateChecker.pickNewest(mirror, github))
    }

    @Test
    fun equalVersionsKeepTheMirrorFirst() {
        val mirror = payload("1.2.0", "mirror")
        val github = payload("1.2.0", "github")

        assertSame(mirror, UpdateChecker.pickNewest(mirror, github))
    }

    @Test
    fun singleSourceStillWorks() {
        val mirror = payload("1.2.0", "mirror")
        val github = payload("1.2.0", "github")

        assertSame(mirror, UpdateChecker.pickNewest(mirror, null))
        assertSame(github, UpdateChecker.pickNewest(null, github))
    }

    @Test
    fun noSourceAtAllReturnsNull() {
        assertNull(UpdateChecker.pickNewest(null, null))
    }

    // ---------- 下载地址必须是 HTTPS ----------

    @Test
    fun httpsUrlIsAccepted() {
        val url = "https://gitee.com/l0x0hhh/zongce/releases/download/v1.2.0/jicun-1.2.0.apk"
        assertEquals(url, UpdateChecker.requireHttps(url, "下载地址"))
    }

    @Test
    fun httpsSchemeCheckIsCaseInsensitive() {
        val url = "HTTPS://example.com/a.apk"
        assertEquals(url, UpdateChecker.requireHttps(url, "下载地址"))
    }

    @Test
    fun cleartextUrlIsRejectedWithAReadableMessage() {
        // targetSdk 35 默认禁明文，http 地址会在下载时被系统拦掉、报错很难懂；
        // 所以要在这里提前拦下，并且错误信息里要说清是什么地址不合规。
        val error = runCatching { UpdateChecker.requireHttps("http://example.com/a.apk", "镜像清单 apkUrl") }
            .exceptionOrNull()

        assertTrue("应当抛出异常", error != null)
        val message = error!!.message.orEmpty()
        assertTrue("错误信息应指明是 HTTPS 要求：$message", message.contains("HTTPS"))
        assertTrue("错误信息应带上原始地址：$message", message.contains("http://example.com/a.apk"))
    }
}
