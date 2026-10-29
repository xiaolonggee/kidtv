"""宝宝动画屋 · 自托管服务端（FastAPI + SQLite）。

启动：uvicorn app.main:app --host 0.0.0.0 --port 8600
管理后台：http://<host>:8600/admin/
"""
from __future__ import annotations

import os

from fastapi import FastAPI
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles

from .db import init_db
from .routers import admin, device

app = FastAPI(title="宝宝动画屋服务端", version="1.0.0")
init_db()

app.include_router(device.router)
app.include_router(admin.router)

# 预留扩展点：未来“媳妇儿锻炼打卡”模块在此挂载独立 router（见 docs/EXTENDING.md）
# from .routers import workout
# app.include_router(workout.router)


@app.get("/healthz")
def healthz():
    return {"ok": True}


@app.get("/")
def index():
    return RedirectResponse("/admin/")


_ADMIN_STATIC = os.path.join(os.path.dirname(__file__), "..", "admin")
app.mount("/admin", StaticFiles(directory=_ADMIN_STATIC, html=True), name="admin")
