// 记录照片引用查询集中在 DAO，删除底层文件前先确认没有其他引用。
package com.zongce.app.data

import androidx.room.Dao
import androidx.room.Delete
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

    @Delete
    suspend fun deleteRecord(record: AwardRecord)

    @Insert
    suspend fun insertPhotos(photos: List<AwardPhoto>)

    @Query("DELETE FROM award_photos WHERE id IN (:ids)")
    suspend fun deletePhotos(ids: List<Long>)

    @Query("DELETE FROM award_photos WHERE recordId = :recordId")
    suspend fun deletePhotosOf(recordId: Long)

    @Query("SELECT COUNT(*) FROM award_photos WHERE fileName = :fileName")
    suspend fun photoReferenceCount(fileName: String): Int
}
