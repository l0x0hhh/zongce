// 严格学年口径 AcademicYear.inYear 的用例。
//
// 它管的是"展示 / 删除"范围的判定，与导出口径 ExportCheck.targetItems 是两份刻意不合并的
// 实现 —— 最后一条用例专门把两者的差异钉住。
package com.zongce.app.core

import com.zongce.app.data.AwardRecord
import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.RecordWithPhotos
import com.zongce.app.export.ExportCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicYearYearScopeTest {

    // 学年显式钉死，不依赖 LocalDate.now()。
    private val year = "2025-2026"

    @Test
    fun inYearKeepsOnlyRecordsOfThePinnedYear() {
        val items = records(
            "2025-10-01",  // 2025-2026：在
            "2026-03-01",  // 2025-2026：在
            "2026-09-01",  // 2026-2027：不在
            "2024-10-01"   // 2024-2025：不在
        )

        val inYear = AcademicYear.inYear(items, year) { it.record.awardDate }

        assertEquals(listOf("2025-10-01", "2026-03-01"), inYear.map { it.record.awardDate })
    }

    @Test
    fun inYearDropsRecordsWithoutAUsableDate() {
        // 日期空 / 日期非法：删除不可逆，只认明确归属，一律排除。
        val items = records("2025-10-01", "", "2026/03/01", "not-a-date")

        val inYear = AcademicYear.inYear(items, year) { it.record.awardDate }

        assertEquals(listOf("2025-10-01"), inYear.map { it.record.awardDate })
    }

    @Test
    fun inYearIsStricterThanExportScope() {
        // 导出口径会把"日期空 / 非法"的记录也收进来（交给体检阻断）；
        // 删除不能跟着收 —— 收进来就意味着可能删掉用户来不及看清的东西。
        val items = records("2025-10-01", "", "2026/03/01")

        val exportScope = ExportCheck.targetItems(items, year)
        val deleteScope = AcademicYear.inYear(items, year) { it.record.awardDate }

        assertEquals(3, exportScope.size)
        assertEquals(1, deleteScope.size)
    }

    @Test
    fun inYearPreservesOriginalOrder() {
        // 成果页按日期倒序展示，过滤器不该重排 —— 排序是 UI 的事。
        val items = records("2026-03-01", "2025-09-01", "2025-10-01")

        val inYear = AcademicYear.inYear(items, year) { it.record.awardDate }

        assertEquals(listOf("2026-03-01", "2025-09-01", "2025-10-01"), inYear.map { it.record.awardDate })
    }

    @Test
    fun inYearReturnsEmptyWhenNoRecordBelongsToTheYear() {
        val items = records("2026-09-01", "2024-10-01")

        assertTrue(AcademicYear.inYear(items, year) { it.record.awardDate }.isEmpty())
    }

    @Test
    fun inYearWorksOnAnyItemType() {
        // 泛型化是为了让学年判定不必依赖 Room 实体，纯字符串也能直接过一遍。
        val dates = listOf("2025-10-01", "2026-09-01", "2024-10-01")

        assertEquals(listOf("2025-10-01"), AcademicYear.inYear(dates, year) { it })
    }

    private fun records(vararg dates: String): List<RecordWithPhotos> =
        dates.mapIndexed { index, date ->
            RecordWithPhotos(
                record = AwardRecord(
                    id = index + 1L,
                    wuyu = "智育",
                    awardName = "记录${index + 1}",
                    awardDate = date
                ),
                photos = listOf(AwardPhoto(id = index + 1L, recordId = index + 1L, fileName = "p$index.jpg"))
            )
        }
}
