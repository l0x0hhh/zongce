// 录入页：一屏全字段。
// 证书在手上的那一刻是信息唯一完备的时刻——所以级别/等级/角色同屏展示，
// 不制造"反正可以后补"的心理许可。必填仍只有三样：五育 / 获奖名称 / 获奖时间。
//
// 字段外观统一为填充式（浅灰底、无描边）：输入框、选择框、日期框三者长得一模一样，
// 因为它们对用户而言都是"点一下填一个值"，行为一致的控件必须长得一致。
package com.zongce.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zongce.app.core.AcademicYear
import com.zongce.app.data.AwardPhoto
import com.zongce.app.data.AwardRecord
import com.zongce.app.data.LEVEL_OPTIONS
import com.zongce.app.data.ROLE_OPTIONS
import com.zongce.app.data.WUYU_LIST
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    vm: AppViewModel,
    recordId: Long,
    onDone: () -> Unit
) {
    val pendingUris by vm.pendingUris.collectAsState()

    var editingId by remember { mutableStateOf(recordId) }
    var wuyu by remember { mutableStateOf("") }
    var awardName by remember { mutableStateOf("") }
    var awardDate by remember { mutableStateOf(if (recordId == 0L) AcademicYear.today() else "") }
    var level by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val existingPhotos = remember { mutableStateListOf<AwardPhoto>() }
    val removedIds = remember { mutableStateListOf<Long>() }
    val extraUris = remember { mutableStateListOf<android.net.Uri>() }

    var showPicker by remember { mutableStateOf(false) }
    var blockedMsg by remember { mutableStateOf<String?>(null) }

    // 编辑已有记录：回填
    LaunchedEffect(recordId) {
        if (recordId != 0L) {
            vm.loadRecord(recordId)?.let { item ->
                val r = item.record
                editingId = r.id
                wuyu = r.wuyu
                awardName = r.awardName
                awardDate = r.awardDate
                level = r.level
                grade = r.grade
                role = r.role
                issuer = r.issuer
                note = r.note
                existingPhotos.clear()
                existingPhotos.addAll(item.photos)
            }
        } else {
            extraUris.clear()
            extraUris.addAll(pendingUris)
        }
    }

    val addPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> extraUris.addAll(uris) }

    val yearCheck = remember(awardDate) { AcademicYear.check(awardDate) }
    val photoTotal = existingPhotos.size + extraUris.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.page, vertical = Space.lg)
    ) {
        Text(
            if (recordId == 0L) "新增记录" else "编辑记录",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(Space.xl))

        // ---------- 证明材料 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("证明材料")
            Spacer(Modifier.width(Space.sm))
            Text(
                "$photoTotal 张",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Space.sm))
        if (photoTotal == 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "还没有照片，先拍下证书或从相册选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.lg)
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                items(existingPhotos, key = { it.id }) { photo ->
                    PhotoPreview(
                        label = "已保存",
                        onRemove = {
                            removedIds.add(photo.id)
                            existingPhotos.remove(photo)
                        }
                    ) {
                        PhotoThumb(file = vm.photoFile(photo.fileName), size = 96)
                    }
                }
                items(extraUris, key = { it.toString() }) { uri ->
                    PhotoPreview(
                        label = "待保存",
                        onRemove = { extraUris.remove(uri) }
                    ) {
                        UriPhotoThumb(uri = uri, size = 96)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        OutlinedButton(onClick = { addPhotos.launch("image/*") }) { Text("添加照片") }

        Spacer(Modifier.height(Space.xl))

        // ---------- 归到五育（必选） ----------
        FieldLabel("归到五育其一", required = true)
        Spacer(Modifier.height(Space.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            items(WUYU_LIST) { w ->
                JicunChip(text = w, selected = wuyu == w, onClick = { wuyu = w })
            }
        }

        Spacer(Modifier.height(Space.lg))

        // ---------- 获奖名称（必填） ----------
        JicunTextField(
            value = awardName,
            onValueChange = { awardName = it },
            label = "获奖名称",
            required = true,
            placeholder = "写全称，会进文件名"
        )

        Spacer(Modifier.height(Space.lg))

        // ---------- 获奖时间（必填 + 自动归属学年） ----------
        FieldLabel("获奖时间", required = true)
        Spacer(Modifier.height(Space.sm))
        DateField(value = awardDate, onClick = { showPicker = true })
        Spacer(Modifier.height(Space.sm))
        YearHint(yearCheck)
        Spacer(Modifier.height(Space.xs))
        Text(
            "对照证书上的获奖时间填写（拍摄日期 ≠ 获奖日期）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Space.lg))

        // ---------- 其余字段（同屏，可不填） ----------
        DropdownField(
            label = "获奖级别",
            value = level,
            options = LEVEL_OPTIONS,
            onChange = { level = it }
        )

        Spacer(Modifier.height(Space.lg))

        JicunTextField(
            value = grade,
            onValueChange = { grade = it },
            label = "获奖等级或名次",
            supporting = "按证书原文填写，如一等奖、金奖、第2名"
        )

        Spacer(Modifier.height(Space.lg))

        DropdownField(
            label = "本人角色或排名",
            value = role,
            options = ROLE_OPTIONS,
            onChange = { role = it },
            groups = roleOptionGroups(ROLE_OPTIONS)
        )

        Spacer(Modifier.height(Space.lg))

        JicunTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = "发证或主办单位"
        )

        Spacer(Modifier.height(Space.lg))

        JicunTextField(
            value = note,
            onValueChange = { note = it },
            label = "备注（不会进文件名和清单）"
        )

        Spacer(Modifier.height(Space.xxl))

        Button(
            onClick = {
                when {
                    wuyu.isBlank() -> blockedMsg = "请选择五育中的一项，它决定照片导出进哪个文件夹"
                    awardName.isBlank() -> blockedMsg = "获奖名称不能为空，导出文件名要用"
                    yearCheck.status == AcademicYear.Status.OUT_OF_RANGE -> blockedMsg = yearCheck.message
                    else -> {
                        vm.saveRecord(
                            AwardRecord(
                                id = editingId,
                                wuyu = wuyu,
                                awardName = awardName.trim(),
                                awardDate = awardDate,
                                level = level,
                                grade = grade,
                                role = role,
                                issuer = issuer.trim(),
                                note = note.trim()
                            ),
                            newUris = extraUris.toList(),
                            removedIds = removedIds.toList()
                        ) { failures ->
                            if (failures.isEmpty()) {
                                vm.clearPending()
                                onDone()
                            } else {
                                blockedMsg = "有 ${failures.size} 张照片导入失败：${failures.joinToString("；") { it.message }}"
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) { Text("保存") }

        Spacer(Modifier.height(Space.xxxl))
    }

    if (showPicker) {
        val initMillis = remember(awardDate) {
            runCatching {
                LocalDate.parse(awardDate)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrElse { System.currentTimeMillis() }
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        awardDate = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    }
                    showPicker = false
                }) { Text("确定") }
            }
        ) { DatePicker(state = state) }
    }

    blockedMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { blockedMsg = null },
            confirmButton = { TextButton(onClick = { blockedMsg = null }) { Text("知道了") } },
            title = { Text("先别存") },
            text = { Text(msg) }
        )
    }
}

/** 日期字段：外观与输入框、选择框完全一致，只是右侧换成日历图标。 */
@Composable
private fun DateField(value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            value.ifBlank { "请选择" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Space.sm))
        Icon(
            Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 学年归属提示。
 * 三个状态直接把 check.message 原样输出 —— message 自身已经说清了是哪一种边界情况，
 * 外面再套一层前缀会变成"边界日：边界日（2026-09-01）：…"这种重复。
 */
@Composable
private fun YearHint(check: AcademicYear.Check) {
    val color = when (check.status) {
        AcademicYear.Status.OK -> MaterialTheme.colorScheme.onSurfaceVariant
        AcademicYear.Status.BOUNDARY -> MaterialTheme.colorScheme.tertiary
        AcademicYear.Status.OUT_OF_RANGE -> MaterialTheme.colorScheme.error
    }
    Text(check.message, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun PhotoPreview(
    label: String,
    onRemove: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.small)) {
        content()
        Surface(
            color = Color.Black.copy(alpha = 0.56f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.xs)
                .size(28.dp)
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "移除照片",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Surface(
            color = Color.Black.copy(alpha = 0.56f),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs)
            )
        }
    }
}
