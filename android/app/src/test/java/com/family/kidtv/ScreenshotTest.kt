package com.family.kidtv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.family.kidtv.data.Episode
import com.family.kidtv.data.KidTvDb
import com.family.kidtv.data.Library
import com.family.kidtv.data.PlayEvent
import com.family.kidtv.data.Series
import com.family.kidtv.ui.CalendarScreen
import com.family.kidtv.ui.DoneScreen
import com.family.kidtv.ui.GateScreen
import com.family.kidtv.ui.HomeScreen
import com.family.kidtv.ui.ParentScreen
import com.family.kidtv.ui.SeriesScreen
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.UUID

/**
 * 无模拟器的截图测试：Robolectric(JVM) 渲染各屏幕，Roborazzi 存 PNG。
 * 运行：./gradlew recordRoborazziDebug
 * 输出：app/build/outputs/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var vm: AppViewModel

    private fun ep(lang: String, folder: String, n: Int, title: String, cn: String, durSec: Int) =
        Episode(
            key = "$lang/$folder/%02d $title.mp4".format(n), language = lang,
            seriesFolder = folder, num = n, title = title, cnTitle = cn,
            filename = "%02d $title.mp4".format(n), uri = "file:///tmp/fake.mp4",
            sizeBytes = 1000, durationMs = durSec * 1000L,
        )

    private fun fakeLibrary(): Library {
        val mouse = Series(
            "英语", "Mouse Boo 小鼠波波", "Mouse Boo", "小鼠波波", null, fav = true,
            episodes = listOf(
                ep("英语", "Mouse Boo 小鼠波波", 1, "Hello Boo", "你好，波波", 297),
                ep("英语", "Mouse Boo 小鼠波波", 2, "Colors", "认颜色", 297),
                ep("英语", "Mouse Boo 小鼠波波", 3, "Bath Time", "洗澡啦", 314),
                ep("英语", "Mouse Boo 小鼠波波", 4, "Counting 1-2-3", "数一数", 291),
                ep("英语", "Mouse Boo 小鼠波波", 5, "Big and Small", "大和小", 308),
                ep("英语", "Mouse Boo 小鼠波波", 6, "Yummy Fruit", "好吃的水果", 285),
                ep("英语", "Mouse Boo 小鼠波波", 7, "Bedtime", "睡觉啦", 302),
            ),
        )
        val peppa = Series(
            "英语", "Peppa Pig", "Peppa Pig", "小猪佩奇", null, fav = false,
            episodes = (1..6).map {
                ep("英语", "Peppa Pig", it,
                    listOf("Muddy Puddles", "Mr Dinosaur", "Best Friend", "Polly Parrot", "Hide and Seek", "The Playgroup")[it - 1],
                    listOf("跳泥坑", "恐龙先生不见了", "好朋友", "鹦鹉波莉", "捉迷藏", "幼儿园")[it - 1], 300)
            },
        )
        val sss = Series(
            "英语", "Super Simple Songs", "Super Simple Songs", "超级简单儿歌", null, fav = false,
            episodes = (1..4).map {
                ep("英语", "Super Simple Songs", it,
                    listOf("Hello Song", "Twinkle Twinkle", "Five Little Ducks", "Wheels on the Bus")[it - 1],
                    listOf("你好歌", "小星星", "五只小鸭", "巴士的轮子")[it - 1], 150)
            },
        )
        val tchoupi = Series(
            "法语", "T'choupi", "T'choupi", "小企鹅丘比", null, fav = false,
            episodes = (1..5).map {
                ep("法语", "T'choupi", it,
                    listOf("T'choupi fait du vélo", "T'choupi à la plage", "Ne veut pas dormir", "Prend son bain", "Au parc")[it - 1],
                    listOf("骑自行车", "去海边", "不想睡觉", "洗澡", "在公园")[it - 1], 355)
            },
        )
        return Library("/storage/usb0/启蒙视频", listOf("英语", "法语"), listOf(mouse, peppa, sss, tchoupi))
    }

    @Before
    fun setup(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        vm = AppViewModel(app)
        // 先让构造时的 rescan()（Main→IO→Main）彻底跑完，避免异步覆盖假媒体库
        repeat(30) {
            compose.waitForIdle()
            Thread.sleep(20)
        }
        val dao = KidTvDb.get(app).dao()

        // 固定 eventId：同类多个测试共享进程时重复插入会被幂等忽略
        fun event(key: String, lang: String, series: String, n: Int, date: String, sec: Int, seq: Int) = PlayEvent(
            id = "seed-$key-$seq", language = lang, series = series,
            episodeKey = key, episodeNum = n, title = "", watchedSec = sec, durationSec = 300,
            counted = true, localDate = date, occurredAtMs = 0, synced = true,
        )

        val today = LocalDate.now(TZ)
        // 星数：小鼠波波 01x5(熟) 02x4 03x3 04x2 05x1；佩奇 01x3 02x2 03x1；儿歌 01x4 02x2；丘比 01x2 02x1
        val starSeed = listOf(
            Triple("英语/Mouse Boo 小鼠波波/01 Hello Boo.mp4", "Mouse Boo 小鼠波波", 1) to 5,
            Triple("英语/Mouse Boo 小鼠波波/02 Colors.mp4", "Mouse Boo 小鼠波波", 2) to 4,
            Triple("英语/Mouse Boo 小鼠波波/03 Bath Time.mp4", "Mouse Boo 小鼠波波", 3) to 3,
            Triple("英语/Mouse Boo 小鼠波波/04 Counting 1-2-3.mp4", "Mouse Boo 小鼠波波", 4) to 2,
            Triple("英语/Mouse Boo 小鼠波波/05 Big and Small.mp4", "Mouse Boo 小鼠波波", 5) to 1,
            Triple("英语/Peppa Pig/01 Muddy Puddles.mp4", "Peppa Pig", 1) to 3,
            Triple("英语/Peppa Pig/02 Mr Dinosaur.mp4", "Peppa Pig", 2) to 2,
            Triple("英语/Peppa Pig/03 Best Friend.mp4", "Peppa Pig", 3) to 1,
            Triple("英语/Super Simple Songs/01 Hello Song.mp4", "Super Simple Songs", 1) to 4,
            Triple("英语/Super Simple Songs/02 Twinkle Twinkle.mp4", "Super Simple Songs", 2) to 2,
            Triple("法语/T'choupi/01 T'choupi fait du vélo.mp4", "T'choupi", 1) to 2,
            Triple("法语/T'choupi/02 T'choupi à la plage.mp4", "T'choupi", 2) to 1,
        )
        // 把星数事件铺到过去 40 天（构造日历与连续打卡：最近 4 天 + 今天 1 集）
        var day = 1
        for ((info, count) in starSeed) {
            val (key, series, n) = info
            val lang = key.substringBefore('/')
            repeat(count) {
                val d = if (day <= 4) today.minusDays(day.toLong())
                else today.minusDays((5 + (day * 2) % 35).toLong())
                dao.insertEvent(event(key, lang, series, n, d.toString(), 280, it))
                day++
            }
        }
        dao.insertEvent(event("英语/Mouse Boo 小鼠波波/02 Colors.mp4", "英语", "Mouse Boo 小鼠波波", 2, today.toString(), 290, 99))

        vm.library.value = fakeLibrary()
        vm.settingsRepo.update {
            it.copy(playlistJson = """["英语/Mouse Boo 小鼠波波/02 Colors.mp4",
                "法语/T'choupi/01 T'choupi fait du vélo.mp4",
                "英语/Mouse Boo 小鼠波波/03 Bath Time.mp4",
                "英语/Peppa Pig/01 Muddy Puddles.mp4"]""")
        }
        // 等 stateIn flow 吃到数据
        kotlinx.coroutines.delay(300)
        compose.waitForIdle()
        check(vm.library.value.series.isNotEmpty()) { "假媒体库被初始扫描覆盖了" }
    }

    private fun snap(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun home() = snap("tv-01-home") { HomeScreen(vm) }

    @Test fun series() = snap("tv-02-series") { SeriesScreen(vm, "英语", "Mouse Boo 小鼠波波") }

    @Test fun calendar() = snap("tv-03-calendar") { CalendarScreen(vm) }

    @Test fun gate() = snap("tv-04-gate") { GateScreen(vm) }

    @Test fun parent() = snap("tv-05-parent") { ParentScreen(vm) }

    @Test fun done() {
        compose.mainClock.autoAdvance = false
        compose.setContent { DoneScreen(vm) }
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tv-06-done.png")
    }
}
