// 删除编排：先删库、后清文件；文件只在"删除后的库里已无引用"时才删。
//
// 抽出这个纯 Kotlin 类的理由（也是它必须零 Android / Room / ViewModel 依赖的理由）：
// 本项目的 JVM 单测是纯 JUnit4，没有 Robolectric、没有 androidx.test，
// AndroidViewModel 与 Room 在 JVM 上根本跑不起来。只有把这条顺序铁律抽成可注入端口的
// 纯类，"跨学年引用同一张照片时文件不能被删"这种不可逆风险才能真正被单测覆盖。
package com.zongce.app.data

/**
 * 记录删除的唯一执行入口。单条、多选、整个学年三条路径都只经过 [delete]。
 *
 * 三个端口都由调用方注入：
 * - [deleteRows]：一个 Room 事务，删掉 award_photos 行 + award_records 行；
 * - [referenceCount]：按文件名查当前库里的引用数；
 * - [deleteFiles]：删磁盘文件，返回删除失败的文件名。
 */
class RecordDeletion(
    private val deleteRows: suspend (List<Long>) -> Unit,
    private val referenceCount: suspend (String) -> Int,
    private val deleteFiles: (Set<String>) -> List<String>
) {

    data class Outcome(
        /** 删掉的记录数。 */
        val recordCount: Int,
        /** 随记录一起删掉的 award_photos 行数 —— 弹窗/Toast 里的「M 张照片」用它。 */
        val photoRowCount: Int,
        /** 真正从磁盘删掉的文件数（只进日志，不进用户文案）。 */
        val deletedFileCount: Int,
        /** 删除失败的文件名（File.delete() 返回 false），不影响整体成功。 */
        val failedFiles: List<String>
    )

    /**
     * 顺序铁律：
     *  1) [deleteRows] —— 一个 Room 事务，全成或全不成；抛异常则本次删除完全不发生，
     *     文件一个都不动。**失败方向必须是"残留孤儿文件"，绝不能是"丢照片"**：
     *     旧实现「先删文件后删库」在 DB 失败时会造成永久缺图且不可逆。
     *  2) 对「本次涉及到的 fileName 去重集合」逐个**重新查**引用计数。
     *     必须用第 1 步之后的库态，绝不能用删除前的快照，也不能"先删 photo 行再统计" ——
     *     反例：A（2025-2026）与 B（2024-2025）共用 ab12.jpg，删掉 A 所属范围后计数为 1，
     *     用旧快照会误删 B 的照片。
     *  3) 只有计数 == 0 才删文件；[deleteFiles] 返回的失败名只记录，不抛异常。
     *
     * @param records 本次要删的记录（含各自照片，用来算候选文件名与 photo 行数）
     * @throws Throwable [deleteRows] 抛出的任何异常都原样向外抛，由调用方决定如何提示
     */
    suspend fun delete(records: List<RecordWithPhotos>): Outcome {
        val ids = records.map { it.record.id }
        // 去重：两条记录引用同一张照片时，引用计数只查一次、文件只删一次。
        val candidates = records
            .asSequence()
            .flatMap { it.photos.asSequence() }
            .map { it.fileName }
            .toSet()

        deleteRows(ids)

        val orphan = candidates.filterTo(mutableSetOf()) { referenceCount(it) == 0 }
        val failed = if (orphan.isEmpty()) emptyList() else deleteFiles(orphan)

        return Outcome(
            recordCount = ids.size,
            photoRowCount = records.sumOf { it.photos.size },
            deletedFileCount = orphan.size - failed.size,
            failedFiles = failed
        )
    }
}
