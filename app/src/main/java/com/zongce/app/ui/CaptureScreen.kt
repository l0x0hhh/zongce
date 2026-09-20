package com.zongce.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/**
 * 首屏即相机。
 * 用户打开 App 的全部意图就是"我手里有个证书"——服务这个意图，别打断它。
 */
@Composable
fun CaptureScreen(
    vm: AppViewModel,
    recordCount: Int,
    onGoEntry: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tmpUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok && tmpUri != null) {
            vm.setPendingUris(listOf(tmpUri!!))
            onGoEntry()
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.setPendingUris(uris)
            onGoEntry()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text("综测材料夹", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "拍下证书，随手归到某一育\n9 月一键导出，电脑上照着填",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 大快门
        Box(
            modifier = Modifier
                .size(200.dp)
                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(10.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable {
                    val (uri, _) = createCameraUri(context)
                    tmpUri = uri
                    takePicture.launch(uri)
                },
            contentAlignment = Alignment.Center
        ) {
            Text("拍照", color = MaterialTheme.colorScheme.onPrimary)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedButton(
                onClick = { pickPhotos.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("从相册选（可多选）")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "已存 $recordCount 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 拍照临时文件（FileProvider，不需要 CAMERA 权限） */
private fun createCameraUri(context: Context): Pair<Uri, File> {
    val dir = File(context.cacheDir, "camera_tmp").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    return uri to file
}
