// 删除管线的"学年范围"用例：用一个会真的增删行的假库来跑，而不是直接喂一个写死的计数。
//
// 为什么还要再写一层：RecordDeletionTest 里的计数是测试自己声明的(mapOf("ab12.jpg" to 1))，
// 它证明了"计数为 1 时不删文件"，却没有证明"删完之后计数真的会是 1"。
// 本类的假库会真的按 recordId 删 award_photos 行并据此算计数 ——
// 于是「删掉整个 2025-2026 之后，被 2024-2025 引用的那张照片必须还在」是被推导出来的，
// 不是被声明出来的。跨学年误删是不可逆的丢证材料，这条链必须跑通才作数。
package com.zongce.app.data

import com.zongce.app.core.AcademicYear
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordDeletionYearScopeTest {

    // 学年显式钉死：任何"当前学年"式的取值都会在某个 9 月 1 日把测试变红。
    private val currentYear = "2025-2026"
    private val previousYear = "2024-2025"

    @Test
    fun deletingOneYearKeepsPhotoStillReferencedByAnotherYear() {
        val db = FakePhotoDb()
        // A 属于 2025-2026，B 属于 2024-2025，两者共用 shared.jpg。
        db.add(1L, "2026-03-10", "shared.jpg", "onlyA.jpg")
        db.add(2L, "2025-03-10", "shared.jpg", "onlyB.jpg")

        val scope = AcademicYear.inYear(db.snapshot(), currentYear) { it.record.awardDate }
        assertEquals(listOf(1L), scope.map { it.record.id })

        val outcome = db.delete(scope)

        // 库里 B 还在引用 shared.jpg —— 计数是删除后重算出来的 1，不是测试声明的 1。
        assertEquals("删掉整个 2025-2026 后，shared.jpg 仍被 2024-2025 引用", 1, db.count("shared.jpg"))
        assertEquals(listOf("onlyA.jpg"), db.deletedFiles.toList().sorted())
        assertTrue("跨学年引用的照片绝对不能被删", "shared.jpg" !in db.deletedFiles)
        assertEquals(1, outcome.recordCount)
        assertEquals(2, outcome.photoRowCount)
        assertEquals(1, outcome.deletedFileCount)
    }

    @Test
    fun photoIsDeletedOnlyAfterTheLastReferencingYearIsGone() {
        val db = FakePhotoDb()
        db.add(1L, "2026-03-10", "shared.jpg")
        db.add(2L, "2025-03-10", "shared.jpg")

        // 第一段：删 2025-2026，文件必须活着。
        db.delete(AcademicYear.inYear(db.snapshot(), currentYear) { it.record.awardDate })
        assertEquals(1, db.count("shared.jpg"))
        assertTrue(db.deletedFiles.isEmpty())

        // 第二段：再删 2024-2025，这是最后一个引用者，此时才允许删文件。
        val outcome = db.delete(AcademicYear.inYear(db.snapshot(), previousYear) { it.record.awardDate })

        assertEquals(0, db.count("shared.jpg"))
        assertEquals(listOf("shared.jpg"), db.deletedFiles.toList())
        assertEquals(1, outcome.deletedFileCount)
    }

    @Test
    fun photoSharedByTwoRecordsInsideTheSameYearIsDeleted() {
        // 反例钉子：旧实现是"删除前快照 + photoReferenceCount(name) <= 1"。
        // 同一个学年里两条记录共用一张照片时，删除前的计数是 2 —— 旧逻辑会判定"还有别人引用"
        // 从而留下孤儿文件；新逻辑按删除后的库态重查（0），文件才被正确清掉。
        val db = FakePhotoDb()
        db.add(1L, "2026-03-10", "shared.jpg")
        db.add(2L, "2026-05-01", "shared.jpg")

        val outcome = db.delete(AcademicYear.inYear(db.snapshot(), currentYear) { it.record.awardDate })

        assertEquals(0, db.count("shared.jpg"))
        assertEquals(listOf("shared.jpg"), db.deletedFiles.toList())
        assertEquals(2, outcome.recordCount)
        assertEquals(2, outcome.photoRowCount)
        assertEquals(1, outcome.deletedFileCount)
    }

    @Test
    fun deletingAYearNeverTouchesOtherYearsRecords() {
        val db = FakePhotoDb()
        db.add(1L, "2026-03-10", "a.jpg")
        db.add(2L, "2025-03-10", "b.jpg")   // 2024-2025：不在范围
        db.add(3L, "2026-09-10", "c.jpg")   // 2026-2027：不在范围
        db.add(4L, "", "d.jpg")             // 日期空：删除口径必须排除（导出口径会收，删除不能收）

        val outcome = db.delete(AcademicYear.inYear(db.snapshot(), currentYear) { it.record.awardDate })

        assertEquals("只有明确归属该学年的记录才进删除范围", 1, outcome.recordCount)
        assertEquals(listOf("a.jpg"), db.deletedFiles.toList())
        // 范围外的记录与它们的照片一个都不能少。
        assertEquals(setOf(2L, 3L, 4L), db.remainingRecordIds())
        assertEquals(1, db.count("b.jpg"))
        assertEquals(1, db.count("c.jpg"))
        assertEquals("日期非法的记录连它的照片都不能被碰", 1, db.count("d.jpg"))
    }

    @Test
    fun dbFailureKeepsEveryRowAndFile() {
        val db = FakePhotoDb(rowsFail = true)
        db.add(1L, "2026-03-10", "a.jpg")
        db.add(2L, "2025-03-10", "shared.jpg")

        val before = db.snapshot()

        val thrown = runCatching {
            db.delete(AcademicYear.inYear(before, currentYear) { it.record.awardDate })
        }.exceptionOrNull()

        assertTrue("DB 事务失败必须向上抛，让调用方提示「数据未改动」", thrown is RuntimeException)
        // 失败方向只能是"残留孤儿文件"：库一行没删、文件一个没动。
        assertEquals(setOf(1L, 2L), db.remainingRecordIds())
        assertEquals(1, db.count("a.jpg"))
        assertEquals(1, db.count("shared.jpg"))
        assertTrue(db.deletedFiles.isEmpty())
        assertTrue("失败时连计数都不该查", db.countedNames.isEmpty())
    }

    /**
     * 一个会真的增删 award_photos 行的假库。
     *
     * 与 RecordDeletionTest.FakePorts 的分工：那边断言"调用轨迹"，这边断言"删除后的库态"。
     * 计数由剩余行实时算出来，因此"跨学年引用文件幸存"是被推导的结论而不是写死的期望。
     */
    private class FakePhotoDb(private val rowsFail: Boolean = false) {

        /** 仍在库里的 award_photos 行：(photoId, recordId, fileName)。 */
        private val photoRows = mutableListOf<PhotoRow>()
        private val recordDates = mutableMapOf<Long, String>()

        val deletedFiles = mutableSetOf<String>()
        val countedNames = mutableListOf<String>()

        private val deletion = RecordDeletion(
            deleteRows = { ids ->
                if (rowsFail) throw RuntimeException("DB 事务失败（测试注入）")
                val dead = ids.toSet()
                photoRows.removeAll { it.recordId in dead }
                recordDates.keys.removeAll(dead)
            },
            referenceCount = { name ->
                countedNames += name
                count(name)
            },
            deleteFiles = { names ->
                deletedFiles += names
                emptyList()
            }
        )

        fun add(recordId: Long, awardDate: String, vararg fileNames: String) {
            recordDates[recordId] = awardDate
            fileNames.forEachIndexed { index, name ->
                photoRows += PhotoRow(recordId * 100 + index, recordId, name)
            }
        }

        /** 当前库里的全部记录（含照片），供学年范围过滤用。 */
        fun snapshot(): List<RecordWithPhotos> = recordDates.map { (recordId, date) ->
            RecordWithPhotos(
                record = AwardRecord(
                    id = recordId,
                    wuyu = Wuyu.ZHI,
                    awardName = "记录$recordId",
                    awardDate = date
                ),
                photos = photoRows.filter { it.recordId == recordId }
                    .map { AwardPhoto(id = it.photoId, recordId = recordId, fileName = it.fileName) }
            )
        }

        fun count(fileName: String): Int = photoRows.count { it.fileName == fileName }

        fun remainingRecordIds(): Set<Long> = photoRows.map { it.recordId }.toSet() + recordDates.keys

        fun delete(records: List<RecordWithPhotos>): RecordDeletion.Outcome =
            runBlocking { deletion.delete(records) }

        private data class PhotoRow(val photoId: Long, val recordId: Long, val fileName: String)
    }
}
