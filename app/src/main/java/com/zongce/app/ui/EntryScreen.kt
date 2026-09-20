// 录入页展示照片导入结果，避免失败被用户误认为已经保存。
package com.zongce.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

/**
 * 录入页：一屏全字段。
 * 证书在手上的那一刻是信息唯一完备的时刻——所以级别/等级/角色同屏展示，
 * 不制造"反正可以后补"的心理许可。必填仍只有三样：五育 / 获奖名称 / 获奖时间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    vm: AppViewModel,
    recordId: Long,
    onDone: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(if (recordId == 0L) "新增获奖记录" else "编辑获奖记录",
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        // ---------- 照片 ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            existingPhotos.forEach { photo ->
                Box(modifier = Modifier.size(72.dp)) {
                    PhotoThumb(
                        file = com.zongce.app.data.PhotoStore(context).photoFile(photo.fileName),
                        size = 72
                    )
                    IconButton(
                        onClick = {
                            removedIds.add(photo.id)
                            existingPhotos.remove(photo)
                        },
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "移除",
                            modifier = Modifier.size(14.dp))
                    }
                }
            }
            extraUris.forEach { uri ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { extraUris.remove(uri) },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("新照片\n点击移除", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { addPhotos.launch("image/*") },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("添加照片") }

        Spacer(Modifier.height(16.dp))

        // ---------- 归属五育（必选） ----------
        Text("归属五育 *", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)) {
            WUYU_LIST.forEach { w ->
                FilterChip(
                    selected = wuyu == w,
                    onClick = { wuyu = w },
                    label = { Text(w) }
                )
            }
        }

        // ---------- 获奖名称（必填） ----------
        OutlinedTextField(
            value = awardName,
            onValueChange = { awardName = it },
            label = { Text("获奖名称 *") },
            placeholder = { Text("写全称，会进文件名") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // ---------- 获奖时间（必填 + 自动归属学年） ----------
        Text("获奖时间 *", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                awardDate.ifBlank { "请选择" },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clickable { showPicker = true }
                    .padding(vertical = 14.dp)
            )
            TextButton(onClick = { showPicker = true }) { Text("选择日期") }
        }
        YearHint(yearCheck)
        Text("请对照证书上的获奖时间确认（拍摄日期 ≠ 获奖日期）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))

        // ---------- 其余字段（同屏，可不填） ----------
        DropdownField("获奖级别", level, LEVEL_OPTIONS, onChange = { level = it })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = grade,
            onValueChange = { grade = it },
            label = { Text("获奖等级或名次") },
            supportingText = { Text("建议按证书原文填写，如一等奖、金奖、第2名") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        DropdownField("本人角色或排名", role, ROLE_OPTIONS, onChange = { role = it })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text("发证或主办单位") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("备注（不会进文件名和清单）") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    wuyu.isBlank() -> blockedMsg = "先选一个「归属五育」——它决定照片导出进哪个文件夹"
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
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }

        Spacer(Modifier.height(32.dp))
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

@Composable
private fun YearHint(check: AcademicYear.Check) {
    val (text, color) = when (check.status) {
        AcademicYear.Status.OK -> "${check.message}" to
            MaterialTheme.colorScheme.onSurfaceVariant
        AcademicYear.Status.BOUNDARY -> "边界日：${check.message}" to
            MaterialTheme.colorScheme.tertiary
        AcademicYear.Status.OUT_OF_RANGE -> check.message to
            MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
