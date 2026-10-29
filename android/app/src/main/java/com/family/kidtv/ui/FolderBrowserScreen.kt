package com.family.kidtv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.kidtv.AppViewModel
import com.family.kidtv.storage.StorageProbe
import java.io.File

@Composable
fun FolderBrowserScreen(vm: AppViewModel, startPath: String) {
    val context = LocalContext.current
    val initial = remember(startPath) {
        val given = startPath.trim()
        when {
            given.isNotEmpty() && File(given).isDirectory -> given
            else -> StorageProbe.browseStartPaths(context).firstOrNull()?.absolutePath
                ?: "/storage/emulated/0"
        }
    }
    var path by remember(initial) { mutableStateOf(initial) }
    val dir = File(path)
    val children = remember(path) { StorageProbe.listReadableChildren(dir) }
    val parents = remember(path) {
        StorageProbe.browseStartPaths(context).map { it.absolutePath }.distinct()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton("⬅ 返回", onClick = { vm.pop() }, requestInitialFocus = true)
            Spacer(Modifier.width(12.dp))
            Text("📂 浏览文件夹", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            TvButton("✅ 选用此目录为媒体库", primary = true, onClick = {
                vm.setLibraryPath(path)
                Toast.makeText(context, "已设置媒体库路径，正在扫描…", Toast.LENGTH_SHORT).show()
                vm.pop()
            })
        }
        Spacer(Modifier.height(8.dp))
        Text(path, color = C.PathText, fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(C.Panel, RoundedCornerShape(10.dp))
                .padding(10.dp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton("⬆ 上级", onClick = {
                dir.parentFile?.absolutePath?.let { path = it }
            })
            parents.take(4).forEach { p ->
                val label = File(p).name.ifBlank { p }
                TvButton(label.take(10), onClick = { path = p })
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(children, key = { it.absolutePath }) { f ->
                Focusable(
                    onClick = {
                        if (f.isDirectory) path = f.absolutePath
                        else Toast.makeText(context, "请选择文件夹", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (f.isDirectory) "📁" else "📄", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (f.isDirectory) Text("打开 ›", color = C.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
