# 扩展点：媳妇儿锻炼打卡（未来模块）

本仓库刻意为“家庭其他成员的打卡模块”留了干净的扩展位，但**现在不实现**。

## 服务端

- 事件表 `events` 只存宝宝观看数据；锻炼打卡请**新建独立表**（如 `workout_events`），
  不要混用，避免统计口径互相污染。
- 路由挂载点已留好（`server/app/main.py`）：

  ```python
  # from .routers import workout
  # app.include_router(workout.router)
  ```

  新建 `server/app/routers/workout.py`，以 `/api/workout/*` 为前缀：
  - `POST /api/workout/checkin`（幂等 eventId，与设备事件同套路）
  - `GET  /api/workout/stats`（连续天数、每周次数——可直接复用 `admin.py` 里的
    `_streak` 日期算法，抽到公共模块即可）
- 管理后台导航是纯前端 tab（`server/admin/index.html` 的 `.tabs`），
  加一个 `data-view="workout"` 的 tab + 对应 `<main id="view-workout">` 即可。

## 客户端

- 锻炼打卡更适合用手机访问管理后台网页，不建议做进电视 App；
  若要做进 App，可在 `Screen` sealed class 里加新屏幕，复用 `CalendarScreen`
  的日历组件（贴纸/连续天数逻辑已参数化为 `dailyStats` Map）。

## 原则

- 宝宝数据与大人数据物理隔离（不同表、不同 API 前缀）。
- 共享的只有：鉴权、日历/连续天数算法、UI 组件。
