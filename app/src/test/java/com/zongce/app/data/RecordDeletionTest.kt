// 删除管线的单测：顺序铁律与"跨学年引用不能误删文件"是这个类的全部存在理由。
//
// 之所以能测：RecordDeletion 是纯 Kotlin 类，三个端口可注入，JVM 上不需要 Robolectric、
// 也不碰 Room。删除是不可逆动作，PRD 里点名的引用计数陷阱必须在这里被钉死。
package com.zongce.app.data

import com.zongce.app.core.AcademicYear
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordDeletionTest {

    // 学年显式钉死：任何"当前学年"式的取值都会在某个 9 月 1 日把测试变红。
    private val year = "2025-2026"

    @Test
    fun crossYearSharedPhotoFileIsKept() {
        // A 属于 2025-2026、B 属于 2024-2025，两者共用同一张照片 ab12.jpg。
        val a = record(1L, "2026-03-10", "ab12.jpg")
        val b = record(2L, "2025-03-10", "ab12.jpg")

        // 删除范围按严格学年口径算（成果页/多选/学年删除三处共用），只圈进 A。
        val inScope = AcademicYear.inYear(listOf(a, b), year) { it.record.awardDate }
        assertEquals(listOf(1L), inScope.map { it.record.id })

        // 删掉 A 之后，库里 B 还在引用 ab12.jpg —— 计数为 1。
        val ports = FakePorts(counts = mapOf("ab12.jpg" to 1))
        val outcome = ports.delete(inScope)

        assertEquals(listOf(listOf(1L)), ports.deletedRowIds)
        assertEquals(listOf("ab12.jpg"), ports.countedNames)
        // 关键断言：还有引用的文件一次都不许进 deleteFiles。
        assertTrue("跨学年引用的照片不能被删", ports.deletedFileSets.isEmpty())
        assertEquals(0, outcome.deletedFileCount)
        assertEquals(1, outcome.recordCount)
        assertEquals(1, outcome.photoRowCount)
    }

    @Test
    fun dbFailureLeavesAllFilesIntact() {
        val ports = FakePorts(rowsFail = true)

        // DB 事务失败必须原样向外抛：调用方据此提示"数据未改动"。
        assertThrows(RuntimeException::class.java) {
            ports.delete(listOf(record(1L, "2026-03-10", "a.jpg", "b.jpg")))
        }

        // 失败方向只能是"残留孤儿文件"：库没删成，文件一个都不动，连计数都不用查。
        assertTrue(ports.countedNames.isEmpty())
        assertTrue("DB 失败时不允许删任何文件", ports.deletedFileSets.isEmpty())
    }

    @Test
    fun orphanFilesAreDeleted() {
        val ports = FakePorts()
        val outcome = ports.delete(
            listOf(
                record(1L, "2026-03-10", "a.jpg"),
                record(2L, "2026-05-01", "b.jpg")
            )
        )

        assertEquals(listOf(setOf("a.jpg", "b.jpg")), ports.deletedFileSets)
        assertEquals(2, outcome.deletedFileCount)
        assertEquals(2, outcome.recordCount)
        assertEquals(2, outcome.photoRowCount)
        assertTrue(outcome.failedFiles.isEmpty())
    }

    @Test
    fun fileDeleteFailureDoesNotFailWholeDeletion() {
        // b.jpg 让 File.delete() 返回 false：只记名字，不算删除失败。
        val ports = FakePorts(undeletable = setOf("b.jpg"))
        val outcome = ports.delete(
            listOf(record(1L, "2026-03-10", "a.jpg", "b.jpg"))
        )

        assertEquals(listOf("b.jpg"), outcome.failedFiles)
        assertEquals(1, outcome.deletedFileCount)
        assertEquals(2, outcome.photoRowCount)
    }

    @Test
    fun duplicateFileNamesAreQueriedOnce() {
        // 同批两条记录引用同一张照片：计数只查一次、文件只删一次。
        val ports = FakePorts()
        val outcome = ports.delete(
            listOf(
                record(1L, "2026-03-10", "same.jpg"),
                record(2L, "2026-05-01", "same.jpg")
            )
        )

        assertEquals(listOf("same.jpg"), ports.countedNames)
        assertEquals(listOf(setOf("same.jpg")), ports.deletedFileSets)
        assertEquals(1, outcome.deletedFileCount)
        assertEquals(2, outcome.photoRowCount)
    }

    @Test
    fun outcomeCountsPhotoRowsNotFiles() {
        // 用户文案里的「M 张照片」= 随记录删掉的 photo 行数，不是实际删掉的文件数。
        // 这里 three.jpg 被别的记录引用，行数 3、文件只删 2。
        val ports = FakePorts(counts = mapOf("three.jpg" to 1))
        val outcome = ports.delete(
            listOf(record(1L, "2026-03-10", "one.jpg", "two.jpg", "three.jpg"))
        )

        assertEquals(3, outcome.photoRowCount)
        assertEquals(2, outcome.deletedFileCount)
        assertEquals(1, outcome.recordCount)
    }

    @Test
    fun rowsAreDeletedBeforeFilesAreConsidered() {
        val ports = FakePorts()
        ports.delete(listOf(record(1L, "2026-03-10", "a.jpg")))

        // 顺序铁律：先删库 → 再按删除后的库态查计数 → 最后才碰文件。
        assertEquals(listOf("rows", "count", "files"), ports.callOrder)
    }

    @Test
    fun deletingNothingTouchesNoFile() {
        val ports = FakePorts()

        val outcome = ports.delete(emptyList())

        assertEquals(0, outcome.recordCount)
        assertEquals(0, outcome.photoRowCount)
        assertTrue(ports.deletedFileSets.isEmpty())
        assertTrue(ports.countedNames.isEmpty())
    }

    private fun record(id: Long, date: String, vararg photoNames: String): RecordWithPhotos =
        RecordWithPhotos(
            record = AwardRecord(id = id, wuyu = Wuyu.ZHI, awardName = "记录$id", awardDate = date),
            photos = photoNames.mapIndexed { index, name ->
                AwardPhoto(id = id * 100 + index, recordId = id, fileName = name)
            }
        )

    /**
     * 三个端口的假实现，把调用轨迹留下来给断言用。
     *
     * @param counts 删除后的库态里各文件名的引用数（未登记即 0 引用）
     * @param rowsFail 注入 DB 事务失败
     * @param undeletable 模拟 File.delete() 返回 false 的文件
     */
    private class FakePorts(
        private val counts: Map<String, Int> = emptyMap(),
        private val rowsFail: Boolean = false,
        private val undeletable: Set<String> = emptySet()
    ) {
        val callOrder = mutableListOf<String>()
        val deletedRowIds = mutableListOf<List<Long>>()
        val countedNames = mutableListOf<String>()
        val deletedFileSets = mutableListOf<Set<String>>()

        private val deletion = RecordDeletion(
            deleteRows = { ids ->
                callOrder += "rows"
                deletedRowIds += ids
                if (rowsFail) throw RuntimeException("DB 事务失败（测试注入）")
            },
            referenceCount = { name ->
                callOrder += "count"
                countedNames += name
                counts[name] ?: 0
            },
            deleteFiles = { names ->
                callOrder += "files"
                deletedFileSets += names
                names.filter { it in undeletable }
            }
        )

        fun delete(records: List<RecordWithPhotos>): RecordDeletion.Outcome =
            runBlocking { deletion.delete(records) }
    }
}
