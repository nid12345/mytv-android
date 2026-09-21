package top.yogiczy.mytv.tv.ui.screens.applauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

/** 可跳转的系统应用 */
data class AppLaunchItem(
    val label: String,
    val packageName: String,
    val icon: Drawable,
)

/** 查询系统里可从桌面启动的应用（不含本应用），按名称排序 */
fun queryLaunchableApps(context: Context): List<AppLaunchItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    return runCatching {
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
            .map {
                AppLaunchItem(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm),
                )
            }
    }.getOrElse { emptyList() }
}

/** 跳转到指定应用 */
fun launchApp(context: Context, packageName: String): Boolean = runCatching {
    val intent = pm(context).getLaunchIntentForPackage(packageName)
        ?: return@runCatching false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrElse { false }

private fun pm(context: Context) = context.packageManager

/** 把应用图标 Drawable 渲染成位图 */
@Composable
fun AppIcon(
    drawable: Drawable,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(drawable) {
        val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight).coerceAtLeast(64)
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }.asImageBitmap()
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp)),
    )
}

/**
 * 「系统应用」面板：频道菜单三段式的中栏，列出本机可启动的应用
 */
@Composable
fun AppLauncherPanel(
    modifier: Modifier = Modifier,
    appsProvider: () -> List<AppLaunchItem> = { emptyList() },
    onAppLaunch: (AppLaunchItem) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val apps = appsProvider()
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(0.9f))
            .padding(horizontal = 8.dp),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (apps.isEmpty()) {
            item {
                Text(
                    text = "未找到可启动的应用",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }
        }

        itemsIndexed(apps) { _, app ->
            DenseListItem(
                modifier = Modifier
                    .onFocusChanged { if (it.isFocused || it.hasFocus) onUserAction() }
                    .handleKeyEvents(onSelect = { onAppLaunch(app) }),
                colors = ListItemDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                ),
                selected = false,
                leadingContent = { AppIcon(drawable = app.icon) },
                headlineContent = {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    )
                },
                onClick = {},
            )
        }
    }
}
