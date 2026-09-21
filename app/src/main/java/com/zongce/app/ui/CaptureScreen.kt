// 首屏即相机。
// 用户打开 App 的全部意图就是"我手里有个证书"——服务这个意图，别打断它。
// 所以这一页只做两件事：把快门放在视觉中心，把"我攒了多少"缩成一行摘要（明细交给成果页）。
package com.zongce.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.zongce.app.R
import com.zongce.app.WidgetActions
import java.io.File

@Composable
fun CaptureScreen(
    vm: AppViewModel,
    recordCount: Int,
    widgetAction: String? = null,
    onWidgetActionConsumed: () -> Unit = {},
    onGoEntry: () -> Unit,
    onGoAchievement: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tmpUri by remember { mutableStateOf<Uri?>(null) }
    var pressed by remember { mutableStateOf(false) }
    // HIG：按压反馈 scale 0.97，临界阻尼不弹跳 —— 弹跳会让人怀疑界面没做完。
    val shutterScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Motion.DAMPING, stiffness = Motion.STIFFNESS),
        label = "shutterScale"
    )

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        pressed = false
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

    LaunchedEffect(widgetAction) {
        when (widgetAction) {
            WidgetActions.CAPTURE -> {
                val (uri, _) = createCameraUri(context)
                tmpUri = uri
                takePicture.launch(uri)
                onWidgetActionConsumed()
            }
            WidgetActions.PICK_PHOTOS -> {
                pickPhotos.launch("image/*")
                onWidgetActionConsumed()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.page)
            .padding(top = Space.xl, bottom = Space.xxl)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "暨存 Logo",
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(Space.md))
            Column {
                Text("暨存", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Space.xxs))
                Text(
                    stringResource(R.string.app_slogan),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 上下的弹性空白让快门落在视觉重心，而不是死居中。
        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("拍下证书", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.xs))
            Text(
                "归到五育其一，需要填报时再导出材料包",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.xxl))
            ShutterButton(
                scale = shutterScale,
                onPress = {
                    pressed = true
                    val (uri, _) = createCameraUri(context)
                    tmpUri = uri
                    takePicture.launch(uri)
                }
            )
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = { pickPhotos.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Space.sm))
            Text("从相册选择照片")
        }

        Spacer(Modifier.height(Space.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onGoAchievement) {
                Text("已存 $recordCount 条")
                Spacer(Modifier.width(Space.xxs))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCheckUpdate) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Space.xs))
                Text("检查更新")
            }
        }
    }
}

/** 快门：实心品牌圆 + 一圈浅色环，外环是"按下去会回弹"的唯一视觉暗示。 */
@Composable
private fun ShutterButton(scale: Float, onPress: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        modifier = Modifier
            .size(148.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(3.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .clickable(onClick = onPress)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(Space.xs))
                Text("拍照", style = MaterialTheme.typography.titleMedium)
            }
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
