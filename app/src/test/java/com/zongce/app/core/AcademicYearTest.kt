package com.zongce.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicYearTest {
    // 学年按每年 9 月 1 日滚动，日期本身决定归属。
    @Test
    fun dateAfterSeptemberBelongsToTheAcademicYearStartingThatYear() {
        assertEquals("2026-2027", AcademicYear.labelForDate("2026-09-20"))
    }

    @Test
    fun dateBeforeSeptemberBelongsToTheAcademicYearEndingThatYear() {
        assertEquals("2025-2026", AcademicYear.labelForDate("2026-08-31"))
    }

    @Test
    fun targetAcademicYearUsesTheCurrentDateWhenExportStarts() {
        assertEquals("2026-2027", AcademicYear.targetLabel("2026-09-20"))
    }

    @Test
    fun targetYearMembershipIsExplicit() {
        assertTrue(AcademicYear.belongsTo("2026-08-31", "2025-2026"))
        assertFalse(AcademicYear.belongsTo("2026-09-01", "2025-2026"))
    }

    @Test
    fun validDateOutsideThePreviousFixedWindowIsNotBlocked() {
        assertFalse(AcademicYear.blocked("2026-09-20"))
        assertEquals("2026-2027", AcademicYear.check("2026-09-20").academicYear)
    }

    @Test
    fun firstAndLastDayOfAcademicYearAreBoundaryWarnings() {
        assertEquals(AcademicYear.Status.BOUNDARY, AcademicYear.check("2026-09-01").status)
        assertEquals(AcademicYear.Status.BOUNDARY, AcademicYear.check("2027-08-31").status)
    }

    // 断言只涉及相对顺序、不涉及 LABEL 的具体值 ——
    // 任何"当前学年等于某个固定字符串"的断言都会在某个 9 月 1 日悄悄变红。
    @Test
    fun yearsOfCollectsEveryYearPresentInRecordsAndSortsDescending() {
        val years = AcademicYear.yearsOf(listOf("2026-05-01", "2024-10-01", "2024-09-01"))

        assertTrue(years.contains(AcademicYear.LABEL))
        assertTrue(years.containsAll(listOf("2025-2026", "2024-2025")))
        // 两次 2024 学年要合并成一条，且近的排在前面。
        assertEquals(1, years.count { it == "2024-2025" })
        assertTrue(years.indexOf("2025-2026") < years.indexOf("2024-2025"))
    }

    @Test
    fun yearsOfSkipsUnparsableDatesInsteadOfThrowing() {
        // 格式不合法的日期本来就该由导出体检拦下，这里不能让整页崩掉。
        val years = AcademicYear.yearsOf(listOf("2026/05/01", "", "2026-05-01"))

        assertTrue(years.contains(AcademicYear.LABEL))
        assertTrue(years.contains("2025-2026"))
    }
}
