package com.family.kidtv

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.family.kidtv.ui.C
import com.family.kidtv.ui.CalendarScreen
import com.family.kidtv.ui.DiagnosticsScreen
import com.family.kidtv.ui.DoneScreen
import com.family.kidtv.ui.EpisodeDetailScreen
import com.family.kidtv.ui.FolderBrowserScreen
import com.family.kidtv.ui.GateScreen
import com.family.kidtv.ui.HomeScreen
import com.family.kidtv.ui.ParentScreen
import com.family.kidtv.ui.PlayerScreen
import com.family.kidtv.ui.SeriesScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** U 盘插拔时自动重新扫描媒体库。 */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { vm.rescan() }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.rescan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        registerReceiver(usbReceiver, filter)

        // 直接路径回退扫描需要媒体读取权限（SAF 模式不需要）
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(perm)
        }

        // adb 自动化配网（文档见 README）：
        // adb shell am start -n com.family.kidtv/.MainActivity \
        //   --es kidtv.serverUrl http://192.168.1.10:8600 --es kidtv.pairCode 123456
        intent?.getStringExtra("kidtv.serverUrl")?.let { url ->
            val codeExtra = intent?.getStringExtra("kidtv.pairCode") ?: ""
            if (codeExtra.isNotEmpty()) {
                lifecycleScope.launch {
                    android.widget.Toast.makeText(
                        this@MainActivity, vm.pairWithServer(url, codeExtra), android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        setContent { App(vm) }
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        vm.sync.requestSync()
    }
}

@Composable
fun App(vm: AppViewModel) {
    val backstack by vm.backstack.collectAsState()
    val screen = backstack.last()

    Box(Modifier.fillMaxSize().background(C.BgBrush)) {
        BackHandler(enabled = backstack.size > 1) { vm.pop() }
        when (screen) {
            is Screen.Home -> HomeScreen(vm)
            is Screen.SeriesPage -> SeriesScreen(vm, screen.language, screen.folder)
            is Screen.EpisodeDetail -> EpisodeDetailScreen(vm, screen.episodeKey)
            is Screen.Player -> PlayerScreen(vm, screen.episodeKey)
            is Screen.Calendar -> CalendarScreen(vm)
            is Screen.Gate -> GateScreen(vm)
            is Screen.Parent -> ParentScreen(vm)
            is Screen.Done -> DoneScreen(vm)
            is Screen.FolderBrowser -> FolderBrowserScreen(vm, screen.startPath)
            is Screen.Diagnostics -> DiagnosticsScreen(vm)
        }
    }
}
