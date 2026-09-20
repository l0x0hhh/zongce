// 记录照片引用查询集中在 DAO，删除底层文件前先确认没有其他引用。
package com.zongce.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
