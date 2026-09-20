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
}
