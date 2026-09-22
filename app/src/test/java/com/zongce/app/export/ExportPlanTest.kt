// 导出规划的验收测试：命名、同名去重（含 ≤40 字符硬约束）、统计口径和 ZIP 落盘结构。
// 其中 longNameCollisionStaysWithin40Chars 是碰撞路径裸拼接突破 40 字符那个缺陷的
// 验收用例 —— 断言就是防它回来的。
package com.zongce.app.export

import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.AwardRecord
import com.zongce.app.data.RecordWithPhotos
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPlanTest {

    private fun photo(id: Long, recordId: Long): AwardPhoto =
        AwardPhoto(id = id, recordId = recordId, fileName = "hash_$id.jpg")

    private fun item(
        id: Long,
        wuyu: String,
        name: String,
        photoCount: Int,
        grade: String = "一等奖",
        date: String = "2026-03-15"
    ): RecordWithPhotos = RecordWithPhotos(
        record = AwardRecord(
            id = id,
            wuyu = wuyu,
            awardName = name,
            awardDate = date,
            grade = grade
        ),
        photos = (1..photoCount).map { photo(id = id * 100 + it, recordId = id) }
    )

    /** 去掉 "德育/" 这类目录前缀后的文件名本身 —— 长度约束量的是它。 */
    private fun fileNameOf(entry: ExportEntry): String = entry.zipPath.substringAfter('/')

    // ---------- 命名与去重 ----------

    @Test
    fun singleRecordPhotosAreNumberedInOrder() {
        val entries = plan(listOf(item(1, "德育", "数学建模", 3)))

        val x = "20260315_数学建模_一等奖"
        assertEquals(
            listOf("德育/$x.jpg", "德育/${x}_2.jpg", "德育/${x}_3.jpg"),
            entries.map { it.zipPath }
        )
    }

    @Test
    fun sameNameSameWuyuCollidesAndGetsNextSequence() {
        val first = item(1, "德育", "数学建模", 3)
        val second = item(2, "德育", "数学建模", 3)

        val entries = plan(listOf(first, second))

        val x = "20260315_数学建模_一等奖"
        assertEquals(
            listOf("德育/${x}_4.jpg", "德育/${x}_2_2.jpg", "德育/${x}_3_2.jpg"),
            entries.filter { it.recordId == 2L }.map { it.zipPath }
        )
    }

    @Test
    fun sameNameInDifferentWuyuDoesNotCollide() {
        val first = item(1, "德育", "数学建模", 1)
        val second = item(2, "智育", "数学建模", 1)

        val entries = plan(listOf(first, second))

        assertEquals("智育/20260315_数学建模_一等奖.jpg", entries.single { it.recordId == 2L }.zipPath)
    }

    // ---------- ≤40 字符硬约束 ----------

    @Test
    fun longNameWithoutCollisionStaysWithin40Chars() {
        // awardName 20 字符 + grade 10 字符，raw base 已超 36，靠截断压回。
        val long = item(1, "德育", "数".repeat(20), 1, grade = "级".repeat(10))

        val entries = plan(listOf(long))

        assertTrue(
            "文件名 ${fileNameOf(entries[0])} 超过 40 字符",
            fileNameOf(entries[0]).length <= 40
        )
    }

    @Test
    fun longNameCollisionStaysWithin40Chars() {
        // 核心缺陷的验收用例：碰撞分支裸拼接时是 36+2+4=42，修复后恒 ≤40。
        val first = item(1, "德育", "数".repeat(20), 2, grade = "级".repeat(10))
        val second = item(2, "德育", "数".repeat(20), 2, grade = "级".repeat(10))

        val entries = plan(listOf(first, second))

        entries.forEach { entry ->
            assertTrue(
                "文件名 ${fileNameOf(entry)} 超过 40 字符",
                fileNameOf(entry).length <= 40
            )
        }
    }

    @Test(timeout = 5000)
    fun baseNameEndingInSuffixPatternStillTerminates() {
        // baseName 满 36 且以 "_2" 结尾时再撞名：seq=2 会算出同名（去重必须再转一圈），
        // seq=3 才得到新名 —— 断言既防"优化"掉 while 去重，也防死循环。
        val name16 = "a".repeat(16)
        val first = item(1, "德育", name16, 2, grade = "0123456789")
        // second 的 base 恰好等于 first 第 2 张照片的 baseName（36 字符、以 _2 结尾）
        val second = item(2, "德育", name16, 1, grade = "01234567_2")

        val entries = plan(listOf(first, second))

        assertEquals(3, entries.size)
        val secondEntry = entries.single { it.recordId == 2L }
        assertTrue(
            "撞名后应跳到 _3，实际为 ${secondEntry.zipPath}",
            fileNameOf(secondEntry).endsWith("_3.jpg")
        )
        assertTrue(fileNameOf(secondEntry).length <= 40)
    }

    @Test
    fun duplicateZeroIdsStillProduceAllEntries() {
        // 结构性防护：当前调用链不会出现两条 id=0 的记录，但一旦出现，
        // 扁平化的 plan 不许静默合并丢照片。
        val first = item(0, "德育", "竞赛甲", 1)
        val second = item(0, "智育", "竞赛乙", 1)

        val entries = plan(listOf(first, second))

        assertEquals(2, entries.size)
        assertEquals("德育/20260315_竞赛甲_一等奖.jpg", entries[0].zipPath)
        assertEquals("智育/20260315_竞赛乙_一等奖.jpg", entries[1].zipPath)
    }

    // ---------- 统计口径 ----------

    @Test
    fun summarizeCountsZeroPhotoRecords() {
        val withPhoto = item(1, "德育", "优秀学生", 1)
        val noPhoto = item(2, "智育", "数学建模", 0)
        val items = listOf(withPhoto, noPhoto)

        val result = summarize(items, plan(items), File("fake.zip"))

        assertEquals(2, result.recordCount)
        assertEquals(mapOf("德育" to 1, "智育" to 1), result.perWuyu)
        assertEquals(1, result.photoCount)
    }

    @Test
    fun summarizeCountsPerWuyuByRecordsNotPhotos() {
        val single = item(1, "德育", "优秀学生", 3)
        val items = listOf(single)

        val result = summarize(items, plan(items), File("fake.zip"))

        assertEquals(mapOf("德育" to 1), result.perWuyu)
        assertEquals(3, result.photoCount)
    }

    // ---------- ZIP 落盘 ----------

    @Test
    fun exportToCreatesAllWuyuDirsAndBomChecklist() {
        val dir = Files.createTempDirectory("export-plan-test").toFile()
        try {
            val single = item(1, "德育", "优秀学生", 1)
            val zip = ZipExporter.exportTo(
                outDir = dir,
                zipName = "test.zip",
                plan = plan(listOf(single)),
                checklistText = "核对",
                photoBytes = { byteArrayOf(1, 2, 3) },
                onProgress = { _, _ -> }
            )

            ZipFile(zip).use { zf ->
                val names = zf.entries().asSequence().map { it.name }.toSet()
                // 五个目录项齐全，空的也建
                assertTrue(names.containsAll(listOf("德育/", "智育/", "体育/", "美育/", "劳育/")))

                // 填报核对.txt 以 UTF-8 BOM（EF BB BF）开头
                val txt = zf.getInputStream(zf.getEntry("填报核对.txt")).readBytes()
                assertTrue(txt.size >= 3)
                assertEquals(0xEF.toByte(), txt[0])
                assertEquals(0xBB.toByte(), txt[1])
                assertEquals(0xBF.toByte(), txt[2])
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---------- 包名 ----------

    @Test
    fun zipFileNameFollowsConvention() {
        assertEquals("综测证明材料_2025-2026学年.zip", zipFileName("2025-2026"))
    }

    // ---------- checklist 文案（回归评审 W1：此前零断言，接线改错不会红） ----------

    @Test
    fun checklistListsEveryPhotoWithItsZipPath() {
        val items = listOf(item(1, "德育", "数学建模", 2))

        val text = checklist(items, plan(items), "2025-2026")

        // "照片："行与 zip 内条目名逐字一致（含五育前缀）——这是用户按图索骥的索引，
        // 若 checklist 内部的 groupBy 接线改错 key 或改用 first，这里必须红。
        assertTrue(text.contains("照片：德育/20260315_数学建模_一等奖.jpg"))
        assertTrue(text.contains("照片：德育/20260315_数学建模_一等奖_2.jpg"))
    }

    @Test
    fun checklistHeaderCarriesTargetYearAndRecordCount() {
        val items = listOf(item(1, "德育", "数学建模", 1), item(2, "智育", "优秀学生", 0))

        val text = checklist(items, plan(items), "2025-2026")

        assertTrue(text.contains("综测填报核对清单 · 2025-2026 学年"))
        assertTrue(text.contains("共 2 条记录"))
    }

    @Test
    fun checklistFallsBackForBlankFields() {
        val blank = RecordWithPhotos(
            record = AwardRecord(id = 1, wuyu = "德育", awardName = "", awardDate = "", grade = ""),
            photos = emptyList()
        )
        val items = listOf(blank)

        val text = checklist(items, plan(items), "2025-2026")

        assertTrue(text.contains("（缺获奖名称）"))
        assertTrue(text.contains("时间：待补充"))
        assertTrue(text.contains("级别：待补充"))
        assertTrue(text.contains("等级：待补充"))
        assertTrue(text.contains("角色：待补充"))
        assertTrue(text.contains("发证单位：—"))
    }

    // ---------- exportTo 条目与失败路径（回归评审 W3） ----------

    @Test
    fun exportToWritesPhotoEntriesFromPlan() {
        val dir = Files.createTempDirectory("export-plan-test").toFile()
        try {
            val entries = plan(listOf(item(1, "德育", "数学建模", 1)))
            val zip = ZipExporter.exportTo(
                outDir = dir,
                zipName = "test.zip",
                plan = entries,
                checklistText = "核对",
                photoBytes = { byteArrayOf(1, 2, 3) },
                onProgress = { _, _ -> }
            )

            ZipFile(zip).use { zf ->
                assertTrue("zip 内应含照片条目 ${entries.single().zipPath}", zf.getEntry(entries.single().zipPath) != null)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun exportToCleansUpPartFileWhenPhotoFails() {
        val dir = Files.createTempDirectory("export-plan-test").toFile()
        try {
            val entries = plan(listOf(item(1, "德育", "数学建模", 1)))

            val error = assertThrows(IllegalStateException::class.java) {
                ZipExporter.exportTo(
                    outDir = dir,
                    zipName = "test.zip",
                    plan = entries,
                    checklistText = "核对",
                    photoBytes = { throw IllegalStateException("无法解码照片") },
                    onProgress = { _, _ -> }
                )
            }

            assertEquals("无法解码照片", error.message)
            // 失败路径必须清掉 .part 半成品，正式 zip 也不许存在（否则会留下可误用的坏包）
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".part") })
            assertTrue(dir.listFiles().orEmpty().none { it.name == "test.zip" })
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---------- 清空策略（回归评审 W5：钉成显式决策） ----------

    @Test
    fun exportToClearsOnlyTopLevelFilesNotDirectories() {
        val dir = Files.createTempDirectory("export-plan-test").toFile()
        try {
            File(dir, "上一版.zip").writeBytes(byteArrayOf(0))
            val subDir = File(dir, "残留子目录").apply { mkdirs() }
            File(subDir, "里面的文件.txt").writeBytes(byteArrayOf(0))

            ZipExporter.exportTo(
                outDir = dir,
                zipName = "test.zip",
                plan = emptyList(),
                checklistText = "核对",
                photoBytes = { byteArrayOf(0) },
                onProgress = { _, _ -> }
            )

            // 顶层旧文件被清掉；子目录因 File.delete() 对非空目录静默失败而保留 ——
            // 「只清一层、不递归」是显式行为决策，将来若有人改成递归删除，此测试应变红要求重新评审。
            assertTrue(!File(dir, "上一版.zip").exists())
            assertTrue(subDir.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
