// 我的成果组件的实例状态：分别保存学年和选择器展开状态，多个组件互不影响。
package com.zongce.app.widget

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

object AchievementWidgetState {
    val selectedYear = stringPreferencesKey("selected_year")
    val yearPickerVisible = booleanPreferencesKey("year_picker_visible")
    val contentRevision = longPreferencesKey("content_revision")
}
