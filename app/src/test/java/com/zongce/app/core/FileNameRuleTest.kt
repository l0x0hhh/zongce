package com.zongce.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameRuleTest {
    // 文件名公开上传，扩展名和多图序号也必须计入长度上限。
    @Test
    fun missingGradeDoesNotLeaveTrailingUnderscore() {
        val name = FileNameRule.build("2026-03-15", "CET-6", "")

        assertFalse(name.endsWith("_"))
        assertTrue(name.endsWith("CET-6"))
    }

    @Test
    fun suffixedFileNameStillFitsTheFortyCharacterLimit() {
        val base = FileNameRule.build(
            "2026-03-15",
            "一个非常非常非常非常非常长的获奖名称",
            "一等奖"
        )

        assertTrue(FileNameRule.withSuffix(base, 12).length + ".jpg".length <= 40)
    }

    @Test
    fun normalFileNameLeavesRoomForTheJpegExtension() {
        val base = FileNameRule.build("2026-03-15", "一个非常非常非常非常非常长的获奖名称", "一等奖")

        assertTrue(base.length + ".jpg".length <= 40)
    }
}
