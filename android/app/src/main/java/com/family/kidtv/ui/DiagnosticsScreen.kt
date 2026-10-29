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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.kidtv.AppViewModel
import com.family.kidtv.storage.StoragePermissions

@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val text by vm.diagnosticsText.collectAsState()
    LaunchedEffect(Unit) { vm.refreshDiagnostics() }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton("⬅ 返回", onClick = { vm.pop() }, requestInitialFocus = true)
            Spacer(Modifier.width(12.dp))
            Text("🛠 诊断", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            TvButton("🔄 刷新", onClick = { vm.refreshDiagnostics() })
            if (StoragePermissions.canManageAllFilesIntent(context)) {
                Spacer(Modifier.width(8.dp))
                TvButton("所有文件权限", onClick = {
                    runCatching {
                        context.startActivity(StoragePermissions.manageAllFilesIntent(context))
                    }.onFailure {
                        Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            color = C.PathText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(C.Panel, RoundedCornerShape(14.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}
