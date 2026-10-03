// 桌面组件学年存储的契约测试，防止与 App 成果页偏好重新共用同一份 SP。
package com.zongce.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WidgetYearStoreContractTest {
    @Test
    fun usesASeparatePreferenceFile() {
        val field = WidgetYearStore::class.java.getDeclaredField("PREFS")
        field.isAccessible = true
        assertEquals("jicun_achievement_widget_selection", field.get(null))
    }

    @Test
    fun keepsTheSameSelectionKey() {
        val field = WidgetYearStore::class.java.getDeclaredField("KEY")
        field.isAccessible = true
        assertEquals("selected_year", field.get(null))
    }

    @Test
    fun exposesMoveForDesktopArrowActions() {
        assertNotNull(
            WidgetYearStore::class.java.getDeclaredMethod(
                "move", android.content.Context::class.java, List::class.java, Int::class.javaPrimitiveType
            )
        )
    }
}
