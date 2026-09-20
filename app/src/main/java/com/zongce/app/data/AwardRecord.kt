package com.zongce.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** 五育（与学校综测口径一致） */
object Wuyu {
    const val DE = "德育"
    const val ZHI = "智育"
    const val TI = "体育"
    const val MEI = "美育"
    const val LAO = "劳育"
    val ALL = listOf(DE, ZHI, TI, MEI, LAO)
}

/** 一条「个人获奖记录」——与学校系统填报单位同名同层级 */
@Entity(
    tableName = "award_records",
    indices = [Index("wuyu"), Index("awardDate")]
)
data class AwardRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wuyu: String,              // 归属五育（必选，决定导出进哪个文件夹）
    val awardName: String,         // 获奖名称（必填，写全称——文件名公开可读）
    val awardDate: String,         // 获奖时间 yyyy-MM-dd（必填，学年窗口强制校验）
    val level: String = "",        // 获奖级别（选填，导出前提醒补）
    val grade: String = "",        // 获奖等级或名次（选填）
    val role: String = "",         // 本人角色或排名（选填）
    val issuer: String = "",       // 发证或主办单位（选填）
    val note: String = "",         // 备注（不进文件名、不进清单）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 待补充字段（导出前提醒，不阻塞） */
    fun missingFields(): List<String> {
        val miss = mutableListOf<String>()
        if (level.isBlank()) miss += "级别"
        if (grade.isBlank()) miss += "等级"
        if (role.isBlank()) miss += "角色"
        return miss
    }
}

/** 证明材料照片（一条记录 1..N 张，原图存 filesDir/photos/） */
@Entity(
    tableName = "award_photos",
    indices = [Index("recordId")],
    foreignKeys = [
        ForeignKey(
            entity = AwardRecord::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AwardPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val fileName: String,          // filesDir/photos/ 内的文件名（哈希命名，导出时映射规范名）
    val takenAt: String = ""       // 照片拍摄时间（EXIF，仅参考）
)

/** 记录 + 其全部照片 */
data class RecordWithPhotos(
    @Embedded val record: AwardRecord,
    @Relation(parentColumn = "id", entityColumn = "recordId")
    val photos: List<AwardPhoto>
)

/** 五育顺序（导出文件夹按这个顺序） */
val WUYU_LIST = Wuyu.ALL

/** 下拉选项（后期可换成学院规则库；当前为通用档位） */
val LEVEL_OPTIONS = listOf("国际级", "国家级", "省级", "市级", "校级", "院（系）级")
val GRADE_OPTIONS = listOf("第1等级", "第2等级", "第3等级", "优胜奖", "第1名", "第2名", "第3名")
// 角色值与 v1.5 官方综测口径保持一致，录入时直接复用证书/项目表述。
val ROLE_OPTIONS = listOf(
    "负责人（排名第一）", "主要成员（第二、三）", "一般成员",
    "主力队员", "一般队员", "第一作者", "第二作者", "其他作者", "个人项目"
)
