# 🧸 宝宝动画屋 · Little Ears

给 2 岁宝宝的英语/法语启蒙动画电视应用 + 自托管服务端。全家自用，离线优先，
不依赖 Google 服务，适配国产电视盒子。界面与交互以 `docs/prototype/index.html`
原型为准（键盘方向键可操作预览）。

```
├── android/   Android TV / 手机 App（Kotlin + Jetpack Compose + Media3 + Room）
├── server/    自托管服务端（FastAPI + SQLite）+ 中文管理后台（手机可用）
└── docs/      原型、截图、扩展说明
```

## 功能一览

- **本周片单**：自动从各系列挑 4 集（可调），每集看满 5 次（可调）就「熟了」，
  移入复习区，同系列下一集自动补进片单。
- **看完才算数**：实际播放时长 ≥ 阈值（默认 75%）才 +1⭐，快进不算。
- **每日上限**：默认每天 2 集，到了上限温柔地说「今天的动画看完啦，明天再见」。
- **打卡日历**：每天看过就有贴纸，连续打卡、每周分钟数一目了然；
  按真实日期在凌晨（Asia/Shanghai）自动重置。
- **家长门**：加法题或长按 OK 3 秒，防止宝宝误入设置。
- **离线可用**：所有数据存本地（Room）；配置服务器后自动同步。
- **远程管理**：手机打开管理后台就能看观看统计、改设置、上传新动画，
  电视后台自动下载到 U 盘。

---

## 一、U 盘视频怎么放（重要！）

在 U 盘（或电视内置存储）根目录建一个 **`启蒙视频`** 文件夹，按
**`启蒙视频/<语言>/<动画名>/<集号>[ 标题].mp4`** 存放：

```
U盘根目录/
└── 启蒙视频/
    ├── 英语/
    │   ├── 小鼠波波/                ← 动画名随意，一个文件夹一部动画
    │   │   ├── 01 Colors.mp4        ← 集号开头，后面可带标题
    │   │   ├── 02 Bath Time.mp4
    │   │   ├── 03.mp4               ← 也可以只有集号
    │   │   ├── cover.jpg            ← 可选：卡片封面
    │   │   └── info.json            ← 可选：显示名（见下）
    │   └── Super Simple Songs/
    │       ├── 01 Hello Song.mp4
    │       └── ...
    └── 法语/
        └── T'choupi/
            ├── 01 T'choupi fait du vélo.mp4
            └── ...
```

规则说明：

- **语言文件夹**：至少支持 `英语`、`法语`（也认 `English` / `French`），
  新建其它语言文件夹（如 `西语`）会自动出现在首页标签里。
- **集号**：文件名必须以数字开头（`01`、`1`、`001` 都行），按数字自然排序；
  没有数字开头的文件会被忽略。
- **格式**：mp4（推荐，H.264+AAC 兼容性最好）、mkv、webm、m4v、mov、avi、ts。
- **cover.jpg**（可选）：系列卡片封面；没有就显示可爱的渐变色 + emoji。
- **info.json**（可选）：
  ```json
  {"name": "Mouse Boo", "cn": "小鼠波波", "fav": true,
   "episodes": {"1": "认颜色", "2": "洗澡啦"}}
  ```
  `fav: true` 会显示「❤️ 宝宝最爱」并在排片单时优先。
- 插拔 U 盘自动重新扫描；也可在「家长设置 → 重新扫描」手动触发。

> 第一次使用：进「家长设置 → 📂 选择媒体库文件夹」，在系统文件选择器里选中
> U 盘（或其中的 `启蒙视频` 文件夹）并允许访问。这一步授权后 App 才能在 U 盘上
> **读和写**（后台下载新动画也写到这里）。如果不选，App 会退而扫描内置存储
> `/sdcard/启蒙视频`。

---

## 二、构建 APK

环境：JDK 17+、Android SDK（`platforms;android-35`、`build-tools;35.0.0`）。

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug        # 调试包 → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # 签名发布包 → app/build/outputs/apk/release/app-release.apk
```

发布包用仓库里自带的家庭自用密钥 `android/keystore/kidtv-release.jks`
（口令 `kidtv-family`，可用环境变量 `KIDTV_KEYSTORE_PASSWORD` 覆盖）签名。
想换成自己的密钥：

```bash
keytool -genkeypair -keystore android/keystore/kidtv-release.jks -alias kidtv \
  -keyalg RSA -keysize 2048 -validity 10950
```

> 注意：卸载重装时签名必须一致，换密钥前先卸载旧版。

## 三、装到电视上（免应用商店）

**方法 A：U 盘安装（最简单）**

1. 把 `app-release.apk` 拷到 U 盘，插电视；
2. 用电视自带的「文件管理器」（没有就先装一个，如小白文件管理）打开 APK；
3. 系统提示「允许安装未知来源应用」→ 允许 → 安装完成。

**方法 B：adb 安装**

1. 电视「设置 → 关于 → 连续点击版本号」打开开发者模式，开启「USB/网络调试」；
2. 电脑与电视同一局域网：
   ```bash
   adb connect 电视IP:5555
   adb install -r kidtv.apk
   ```

App 也能直接装在**手机/平板**上（同一个 APK），触屏可点，适合出门用。

## 四、部署服务端（可选，但推荐）

一台小 VPS / NAS / 家里闲置 Linux 盒子都行：

```bash
cd server
ADMIN_PASSWORD=换成你的密码 docker compose up -d
```

- 管理后台：`http://主机IP:8600/admin/`（手机浏览器可用）
- 数据（SQLite 数据库 + 视频文件）在 docker volume `kidtv-data` 里
- 想用 Postgres：在 compose 里设置 `DATABASE_URL`

**管理后台登录（务必修改默认密码）**

| 项目 | 说明 |
|------|------|
| 默认密码 | `admin123`（未设置环境变量时） |
| 修改方式 | 启动前设置环境变量 **`ADMIN_PASSWORD`**，例如 `ADMIN_PASSWORD='你的强密码' docker compose up -d` |
| 本地直跑 | 同样：`ADMIN_PASSWORD=你的密码 uvicorn app.main:app --host 0.0.0.0 --port 8600` |
| 登录入口 | 浏览器打开 `/admin/`，输入密码即可（会话 token 存在浏览器 localStorage） |

不用 Docker 也行：

```bash
cd server && pip install -r requirements.txt
ADMIN_PASSWORD=你的密码 uvicorn app.main:app --host 0.0.0.0 --port 8600
```

跑测试：`cd server && python -m pytest tests/ -q`

端到端冒烟（上传+配对+模拟电视同步）：

```bash
python scripts/simulate_device.py http://127.0.0.1:8600 /路径/启蒙视频
```

## 五、电视配对服务器

1. 管理后台「📺 设备」→「➕ 生成配对码」→ 得到 6 位数字（15 分钟有效）；
2. 电视上「🔒 家长 → 家长设置 → 🌐 服务器与配对」：
   填服务器地址（如 `http://192.168.1.10:8600`）和配对码 → 「🔗 配对」；
   遥控器输入不便时也可用 adb 一条命令完成配对：
   ```bash
   adb shell am start -n com.family.kidtv/.MainActivity \
     --es kidtv.serverUrl http://192.168.1.10:8600 --es kidtv.pairCode 123456
   ```
3. 配对后自动双向同步：
   - 电视 → 服务器：观看/打卡事件实时上传（断网排队，联网补传，事件幂等不重复计数）；
   - 服务器 → 电视：后台改的设置下次同步生效；后台上传的新动画自动断点下载
     （默认仅 Wi-Fi/网线，sha256 校验后写入 U 盘对应目录）；
   - 后台删除的动画默认**不**动电视本地文件，家长设置里可打开「同步删除」。

## 六、家长设置项

| 设置 | 默认 | 说明 |
|---|---|---|
| 每日集数上限 | 2 集 | 到上限后显示「明天再见」 |
| 完成判定阈值 | 75% | 实际播放时长占比，快进不算 |
| “熟了”所需次数 | 5 次 | 满星移入复习，新集补入片单 |
| 本周片单数量 | 4 集 | 首页片单长度 |
| 自动播放下一集 | 开 | 5 秒倒计时，可取消 |
| 仅 Wi-Fi 下载 | 开 | 同步新内容时（网线也算） |
| 同步删除本地文件 | 关 | 后台删除是否同步删 U 盘文件 |

## 七、已知限制

- 后台下载写 U 盘依赖系统 SAF 授权；个别电视盒子的魔改系统可能不支持对
  USB 存储的持久授权，此时下载功能不可用（播放不受影响），真实盒子上请实测。
- **不选 SAF 目录**（直接路径回退扫描）时，Android 10+ 的分区存储只允许直读
  视频/图片等媒体文件：`info.json` 读不到（系列中文名/最爱标记失效），
  且后台下载也写不进去。**推荐始终在家长设置里选择一次媒体库文件夹**；
  老电视盒子（Android 7–9）无此问题。
- 事件上传与内容下载在 App 运行期间进行（电视场景 App 常驻前台）；
  完全退出 App 后不做后台同步，下次打开自动补传。
- 未做多宝宝档案；一台设备 = 一个宝宝。

## 八、测试与截图

- 服务端：`cd server && python -m pytest tests/ -q`（配对/幂等上报/设置/分块上传/清单/Range 下载）。
- App 截图测试（无需模拟器，JVM 渲染）：`cd android && ./gradlew recordRoborazziDebug`，
  输出在 `android/app/build/outputs/roborazzi/`。
- 实机验证过的截图在 `docs/screenshots/`（`tv-*` 为 JVM 渲染，`emu-*` 为
  Android TV 模拟器实拍，`admin-*` 为管理后台）。

## 扩展计划

「媳妇儿锻炼打卡」等家庭成员模块的预留接口见 `docs/EXTENDING.md`。
