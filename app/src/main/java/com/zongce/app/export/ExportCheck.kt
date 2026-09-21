// 在导出前拦截字段、学年和底层照片文件问题。
package com.zongce.app.export

import com.zongce.app.core.AcademicYear
import com.zongce.app.data.RecordWithPhotos
import java.io.File

/**
 * 导出前体检。
 * 原则：一旦抄进学校系统就晚了（获奖时间填错要工程师后台改），所以在"不可逆动作"之前设卡。
 */
object ExportCheck {

    enum class Level { BLOCK, WARN }

    data class Issue(
        val level: Level,
        val recordId: Long,
        val message: String
    )

    /**
     * 本次导出范围内的记录。
     * 日期缺失或格式非法的记录留在范围内（交给字段检查拦下），跨学年的排除在外。
     * 导出与体检共用这一份判定 —— 同一个谓词各写一遍，迟早会漂移。
     */
    fun targetItems(items: List<RecordWithPhotos>, targetYear: String): List<RecordWithPhotos> =
        items.filter {
            it.record.awardDate.isBlank() ||
                AcademicYear.check(it.record.awardDate).status == AcademicYear.Status.OUT_OF_RANGE ||
                AcademicYear.belongsTo(it.record.awardDate, targetYear)
        }

    /** 属于其他评价学年、本次会被过滤掉的记录数。 */
    fun excludedCount(items: List<RecordWithPhotos>, targetYear: String): Int =
        items.count {
            it.record.awardDate.isNotBlank() &&
                AcademicYear.check(it.record.awardDate).status != AcademicYear.Status.OUT_OF_RANGE &&
                !AcademicYear.belongsTo(it.record.awardDate, targetYear)
        }

    /**
     * 体检目标评价学年。
     * [targetYear] 必须由调用方明确传入：它一旦有默认值，就会随"今天"静默滑动，
     * 让调用方（尤其是测试）在不自知的情况下换了一个档位。
     */
    fun run(
        items: List<RecordWithPhotos>,
        targetYear: String,
        photoExists: (String) -> Boolean = { true }
    ): List<Issue> {
        val issues = mutableListOf<Issue>()
        val excluded = excludedCount(items, targetYear)
        val targets = targetItems(items, targetYear)

        if (targets.isEmpty()) {
            issues += Issue(Level.BLOCK, 0L, "还没有任何记录，没有东西可以导出")
            return issues
        }

        targets.forEach { item ->
            val r = item.record

            // 1) 缺关键字段 → 无法生成文件名
            if (r.awardName.isBlank()) {
                issues += Issue(Level.BLOCK, r.id, "第 ${r.id} 条：缺「获奖名称」，无法生成导出文件名")
            }
            if (r.awardDate.isBlank()) {
                issues += Issue(Level.BLOCK, r.id, "第 ${r.id} 条：缺「获奖时间」")
            } else {
                // 2) 日期格式错误才阻塞；合法日期按学年自动归属。
                val c = AcademicYear.check(r.awardDate)
                if (c.status == AcademicYear.Status.OUT_OF_RANGE) {
                    issues += Issue(
                        Level.BLOCK, r.id,
                        "【获奖时间越界】${r.awardName}（${r.awardDate}）：${c.message}"
                    )
                }
            }

            if (r.wuyu.isBlank()) {
                issues += Issue(Level.BLOCK, r.id, "第 ${r.id} 条：未选择五育归属，照片不知道放进哪个文件夹")
            }

            // 3) 没照片 → 填系统时传什么
            if (item.photos.isEmpty()) {
                issues += Issue(Level.BLOCK, r.id, "${r.awardName}：没有任何证明材料照片")
            }
            item.photos.filterNot { photoExists(it.fileName) }.forEach { photo ->
                issues += Issue(
                    Level.BLOCK,
                    r.id,
                    "${r.awardName}：证明材料文件缺失（${photo.fileName}），请重新导入照片"
                )
            }
        }

        // 4) 待补充字段 → 只提醒
        val incomplete = targets.count { it.record.missingFields().isNotEmpty() }
        if (incomplete > 0) {
            issues += Issue(
                Level.WARN, 0L,
                "有 $incomplete 条记录缺「级别 / 等级 / 角色」，导出的清单会显示「待补充」，建议现在补齐"
            )
        }

        // 5) 边界日 → 只提醒
        val boundary = targets.filter {
            it.record.awardDate.isNotBlank() &&
                AcademicYear.check(it.record.awardDate).status == AcademicYear.Status.BOUNDARY
        }
        boundary.forEach {
            issues += Issue(
                Level.WARN, it.record.id,
                "【边界日】${it.record.awardName}（${it.record.awardDate}）落在学年首尾，请对照证书确认"
            )
        }

        if (excluded > 0) {
            issues += Issue(
                Level.WARN, 0L,
                "另有 $excluded 条记录属于其他评价学年，已从本次 $targetYear 导出中过滤"
            )
        }

        return issues
    }

    fun blocks(issues: List<Issue>): Boolean = issues.any { it.level == Level.BLOCK }
}
