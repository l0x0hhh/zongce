// 组件实例状态契约测试，确保学年状态使用独立的 Preferences key。
package com.zongce.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AchievementWidgetStateContractTest {
    @Test
    fun selectedYearKeyIsPresent() {
        assertNotNull(AchievementWidgetState.selectedYear)
        assertEquals("selected_year", AchievementWidgetState.selectedYear.name)
    }

    @Test
    fun cacheSnapshotKeepsStableRecordIdentity() {
        val record = CachedAchievementRecord(42L, "智育", "竞赛", "2026-05-01", "一等奖")
        val snapshot = AchievementWidgetCacheSnapshot(listOf("2025-2026"), listOf(record))
        assertEquals(42L, snapshot.records.single().id)
        assertEquals("2025-2026", snapshot.years.single())
    }
}
