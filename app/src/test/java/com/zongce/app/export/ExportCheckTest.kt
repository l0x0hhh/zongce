// 覆盖导出前体检的三条规则：照片丢失必须阻塞、选填字段只提醒、跨学年只提示。
// 注意：所有用例都显式钉住 targetYear —— 这个参数一旦有默认值就会随系统日期滚动，
// 测试会在某个 9 月 1 日悄悄变红（曾经真实发生过）。
package com.zongce.app.export

import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.AwardRecord
import com.zongce.app.data.RecordWithPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCheckTest {

    @Test
    fun missingPhotoFileBlocksExport() {
        val item = RecordWithPhotos(
            record = AwardRecord(
                id = 1L,
                wuyu = "德育",
                awardName = "优秀学生",
                awardDate = "2026-05-01"
            ),
            photos = listOf(AwardPhoto(id = 2L, recordId = 1L, fileName = "missing.jpg"))
        )

        // 目标学年必须与上面的获奖日期一致，否则记录会被当作"其他学年"过滤掉，
        // 检查逻辑根本走不到照片那一层（默认值随系统日期滚动，会让测试变成时间依赖的）。
        val issues = ExportCheck.run(
            listOf(item),
            targetYear = AcademicYear.labelForDate("2026-05-01"),
            photoExists = { false }
        )

        assertTrue(issues.any { it.level == ExportCheck.Level.BLOCK && it.message.contains("文件缺失") })
    }

    @Test
    fun missingOptionalFieldsOnlyWarnSoExportIsNotBlocked() {
        val item = RecordWithPhotos(
            record = AwardRecord(
                id = 1L,
                wuyu = "德育",
                awardName = "优秀学生",
                awardDate = "2026-05-01"
                // 级别 / 等级 / 角色 留空：只该提醒，不该拦。
            ),
            photos = listOf(AwardPhoto(id = 1L, recordId = 1L, fileName = "cert.jpg"))
        )

        val issues = ExportCheck.run(listOf(item), targetYear = "2025-2026", photoExists = { true })

        assertFalse(issues.any { it.level == ExportCheck.Level.BLOCK })
        assertTrue(issues.any { it.level == ExportCheck.Level.WARN && it.message.contains("待补充") })
    }

    @Test
    fun otherAcademicYearRecordsAreReportedBeforeExport() {
        val inRange = RecordWithPhotos(
            record = AwardRecord(
                id = 1L,
                wuyu = "德育",
                awardName = "优秀学生",
                awardDate = "2026-05-01",
                level = "校级",
                grade = "一等",
                role = "本人"
            ),
            photos = listOf(AwardPhoto(id = 1L, recordId = 1L, fileName = "a.jpg"))
        )
        val otherYear = RecordWithPhotos(
            record = AwardRecord(
                id = 2L,
                wuyu = "智育",
                awardName = "数学建模",
                awardDate = "2024-10-01",
                level = "国家级",
                grade = "一等",
                role = "队长"
            ),
            photos = listOf(AwardPhoto(id = 2L, recordId = 2L, fileName = "b.jpg"))
        )
        val all = listOf(inRange, otherYear)

        // 学年判定只能有一份实现：这三处必须永远给出同一个答案。
        assertEquals(1, ExportCheck.targetItems(all, "2025-2026").size)
        assertEquals(1, ExportCheck.excludedCount(all, "2025-2026"))

        val issues = ExportCheck.run(all, targetYear = "2025-2026", photoExists = { true })

        assertFalse(issues.any { it.level == ExportCheck.Level.BLOCK })
        assertTrue(issues.any { it.level == ExportCheck.Level.WARN && it.message.contains("另有 1 条") })
    }
}
