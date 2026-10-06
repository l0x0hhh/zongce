// 我的成果组件的数据缓存：箭头切换只读缓存，避免每次点击重新查询 Room。
package com.zongce.app.widget

import android.content.Context
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AppDatabase
import com.zongce.app.data.RecordWithPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CachedAchievementRecord(
    val id: Long,
    val wuyu: String,
    val awardName: String,
    val awardDate: String,
    val grade: String
)

data class AchievementWidgetCacheSnapshot(
    val years: List<String>,
    val records: List<CachedAchievementRecord>
)

object AchievementWidgetCache {
    private const val PREFS = "jicun_achievement_widget_cache"
    private const val KEY_PAYLOAD = "payload"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): AchievementWidgetCacheSnapshot? = runCatching {
        val payload = prefs(context).getString(KEY_PAYLOAD, null) ?: return null
        val root = JSONObject(payload)
        val years = buildList {
            val values = root.optJSONArray("years") ?: JSONArray()
            repeat(values.length()) { add(values.getString(it)) }
        }
        val records = buildList {
            val values = root.optJSONArray("records") ?: JSONArray()
            repeat(values.length()) {
                val item = values.getJSONObject(it)
                add(
                    CachedAchievementRecord(
                        id = item.getLong("id"),
                        wuyu = item.optString("wuyu"),
                        awardName = item.optString("awardName"),
                        awardDate = item.optString("awardDate"),
                        grade = item.optString("grade")
                    )
                )
            }
        }
        AchievementWidgetCacheSnapshot(years, records)
    }.getOrNull()

    suspend fun rebuild(context: Context): AchievementWidgetCacheSnapshot = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val items = AppDatabase.get(app).awardDao().allWithPhotos().first()
        val snapshot = AchievementWidgetCacheSnapshot(
            years = AcademicYear.yearsOf(items.map { it.record.awardDate }),
            records = items.map { it.toCachedRecord() }
        )
        write(app, snapshot)
        snapshot
    }

    private fun write(context: Context, snapshot: AchievementWidgetCacheSnapshot) {
        val root = JSONObject()
            .put("years", JSONArray(snapshot.years))
            .put(
                "records",
                JSONArray().also { array ->
                    snapshot.records.forEach { record ->
                        array.put(
                            JSONObject()
                                .put("id", record.id)
                                .put("wuyu", record.wuyu)
                                .put("awardName", record.awardName)
                                .put("awardDate", record.awardDate)
                                .put("grade", record.grade)
                        )
                    }
                }
            )
        prefs(context).edit().putString(KEY_PAYLOAD, root.toString()).commit()
    }

    private fun RecordWithPhotos.toCachedRecord(): CachedAchievementRecord =
        CachedAchievementRecord(
            id = record.id,
            wuyu = record.wuyu,
            awardName = record.awardName,
            awardDate = record.awardDate,
            grade = record.grade
        )
}
