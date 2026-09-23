// 记录照片引用查询集中在 DAO，删除底层文件前先确认没有其他引用。
package com.zongce.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AwardDao {

    @Transaction
    @Query("SELECT * FROM award_records ORDER BY awardDate ASC, id ASC")
    fun allWithPhotos(): Flow<List<RecordWithPhotos>>

    @Transaction
    @Query("SELECT * FROM award_records WHERE id = :id")
    suspend fun byId(id: Long): RecordWithPhotos?

    @Query("SELECT COUNT(*) FROM award_records")
    suspend fun count(): Int

    // 用默认的 ABORT，绝不能改成 REPLACE：REPLACE 在 SQLite 里是「先删旧行、再插新行」，
    // 而 award_photos 对 award_records 是 ON DELETE CASCADE —— 一旦主键撞上，
    // 会静默级联删掉该记录的全部照片，换来的"覆盖"根本不是用户想要的。
    // 新建记录 id=0 走自增主键，本就不会冲突，无需 REPLACE/IGNORE 的幂等语义。
    @Insert
    suspend fun insertRecord(record: AwardRecord): Long

    @Update
    suspend fun updateRecord(record: AwardRecord)

    // 单条记录的删除原语（@Delete deleteRecord / deletePhotosOf）已在 v1.4.0 移除：
    // 删除现在只有 RecordDeletion.delete() 一个入口。留着这两条"绕过引用计数"的捷径，
    // 迟早会有人顺手用它们写出"先删文件后删库"的旧顺序 —— 那正是本期要根治的风险。
    @Insert
    suspend fun insertPhotos(photos: List<AwardPhoto>)

    /** 移除记录里的某几张照片（编辑页删照片），不是删除记录 —— 记录删除走下面那个事务。 */
    @Query("DELETE FROM award_photos WHERE id IN (:ids)")
    suspend fun deletePhotos(ids: List<Long>)

    /**
     * 一次删除的全部 DB 写入：award_photos 行 + award_records 行，同一事务。
     *
     * 顺序不可颠倒：award_photos 对 award_records 是 ON DELETE CASCADE，而插入用的是
     * 默认 ABORT —— 先删父表会撞外键约束，整批失败。
     *
     * chunked(500)：SQLite 单条语句的变量数上限是 999，整学年删除很容易越过。
     * 每块各自「先 photos 后 records」，块之间由事务兜住，要么全成要么全不成。
     */
    @Transaction
    suspend fun deleteRecordsAndPhotos(recordIds: List<Long>) {
        recordIds.chunked(500).forEach { chunk ->
            deletePhotosOfRecords(chunk)
            deleteRecordsByIds(chunk)
        }
    }

    @Query("DELETE FROM award_photos WHERE recordId IN (:recordIds)")
    suspend fun deletePhotosOfRecords(recordIds: List<Long>)

    @Query("DELETE FROM award_records WHERE id IN (:recordIds)")
    suspend fun deleteRecordsByIds(recordIds: List<Long>)

    @Query("SELECT COUNT(*) FROM award_photos WHERE fileName = :fileName")
    suspend fun photoReferenceCount(fileName: String): Int

    /**
     * 按照片 id 取文件名。
     *
     * 编辑记录时移除某张照片要用它：fileName 必须在 DELETE 那条语句**之前**抓住，
     * 而"从 UI 侧的 Flow 缓存里反查"是不可靠的 —— 那条缓存随时可能被 Room 的失效
     * 通知刷掉（尤其刚执行完一次 DELETE），取到的可能是 null 于是文件永远不会被清理。
     * 名字属于"删行之前的事实"，直接问库最稳。
     */
    @Query("SELECT fileName FROM award_photos WHERE id = :photoId")
    suspend fun photoFileName(photoId: Long): String?
}
