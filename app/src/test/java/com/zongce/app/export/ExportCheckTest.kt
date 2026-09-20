// 覆盖导出前发现底层照片丢失时必须阻塞的规则。
package com.zongce.app.export

import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.AwardRecord
import com.zongce.app.data.RecordWithPhotos
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

        val issues = ExportCheck.run(listOf(item), photoExists = { false })

        assertTrue(issues.any { it.level == ExportCheck.Level.BLOCK && it.message.contains("文件缺失") })
    }
}
