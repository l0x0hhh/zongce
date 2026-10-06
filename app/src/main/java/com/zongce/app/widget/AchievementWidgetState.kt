// 我的成果组件的实例状态：每个桌面组件单独记住自己选择的学年。
package com.zongce.app.widget

import androidx.datastore.preferences.core.stringPreferencesKey

object AchievementWidgetState {
    val selectedYear = stringPreferencesKey("selected_year")
}
