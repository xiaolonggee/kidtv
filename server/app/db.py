"""Database models. SQLite by default; set DATABASE_URL for Postgres."""
from __future__ import annotations

import os
import secrets
from datetime import datetime, timezone

from sqlalchemy import (
    Boolean, Column, DateTime, Integer, String, Text, create_engine,
)
from sqlalchemy.orm import declarative_base, sessionmaker

DATA_DIR = os.environ.get("KIDTV_DATA_DIR", os.path.join(os.path.dirname(__file__), "..", "data"))
DATA_DIR = os.path.abspath(DATA_DIR)
os.makedirs(DATA_DIR, exist_ok=True)
MEDIA_DIR = os.path.join(DATA_DIR, "media")
os.makedirs(MEDIA_DIR, exist_ok=True)
UPLOAD_TMP_DIR = os.path.join(DATA_DIR, "uploads_tmp")
os.makedirs(UPLOAD_TMP_DIR, exist_ok=True)

DATABASE_URL = os.environ.get("DATABASE_URL", f"sqlite:///{os.path.join(DATA_DIR, 'kidtv.db')}")

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {},
)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
Base = declarative_base()


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def new_token(nbytes: int = 24) -> str:
    return secrets.token_urlsafe(nbytes)


DEFAULT_DEVICE_SETTINGS = {
    "dailyLimit": 2,        # 每日集数上限
    "threshold": 75,        # 完成判定阈值（%）
    "masterStars": 5,       # “熟了”所需次数
    "playlistSize": 4,      # 本周片单数量
    "autoplay": True,       # 兼容旧客户端；与 playbackMode 同步
    "playbackMode": "sequential",  # sequential | loop | stop
    "wifiOnly": True,       # 仅 Wi-Fi 下载
    "allowDelete": False,   # 允许同步删除本地文件
}


class Device(Base):
    __tablename__ = "devices"
    id = Column(String(40), primary_key=True)
    name = Column(String(120), default="客厅电视")
    token = Column(String(80), unique=True, index=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    last_seen = Column(DateTime(timezone=True), default=utcnow)
    settings_json = Column(Text, nullable=False)   # JSON of DEFAULT_DEVICE_SETTINGS shape
    settings_version = Column(Integer, default=1)


class PairingCode(Base):
    __tablename__ = "pairing_codes"
    code = Column(String(12), primary_key=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    device_name = Column(String(120), default="客厅电视")
    used_by = Column(String(40), nullable=True)


class Event(Base):
    """Play / check-in events uploaded by devices. Primary key = client event id
    so re-uploads are idempotent."""
    __tablename__ = "events"
    id = Column(String(64), primary_key=True)
    device_id = Column(String(40), index=True, nullable=False)
    type = Column(String(30), nullable=False)       # play_complete | play_partial
    language = Column(String(40), default="")
    series = Column(String(200), default="")
    episode_key = Column(String(400), default="")   # 语言/系列目录/文件名
    episode_num = Column(Integer, default=0)
    title = Column(String(300), default="")
    watched_sec = Column(Integer, default=0)
    duration_sec = Column(Integer, default=0)
    counted = Column(Boolean, default=False)        # 是否计入一次“看完”
    local_date = Column(String(10), index=True)     # 设备本地日期 YYYY-MM-DD (Asia/Shanghai)
    occurred_at = Column(DateTime(timezone=True), default=utcnow)
    received_at = Column(DateTime(timezone=True), default=utcnow)


class SeriesMeta(Base):
    __tablename__ = "series_meta"
    id = Column(Integer, primary_key=True, autoincrement=True)
    language = Column(String(40), nullable=False)
    folder = Column(String(200), nullable=False)      # directory name on disk / USB
    display_name = Column(String(200), default="")
    cn_name = Column(String(200), default="")
    cover_file = Column(String(300), default="")      # relative path under media/


class ContentItem(Base):
    __tablename__ = "content_items"
    id = Column(String(40), primary_key=True)
    language = Column(String(40), nullable=False)      # 英语 / 法语 / ...
    series = Column(String(200), nullable=False)       # series folder name
    filename = Column(String(300), nullable=False)     # e.g. "01 Colors.mp4"
    title = Column(String(300), default="")
    size = Column(Integer, default=0)
    sha256 = Column(String(64), default="")
    deleted = Column(Boolean, default=False)
    updated_at = Column(DateTime(timezone=True), default=utcnow)

    @property
    def rel_path(self) -> str:
        return f"{self.language}/{self.series}/{self.filename}"


class DeviceSyncState(Base):
    """Which content items each device reports as present locally."""
    __tablename__ = "device_sync_state"
    device_id = Column(String(40), primary_key=True)
    content_id = Column(String(40), primary_key=True)
    status = Column(String(20), default="synced")     # synced | deleted
    updated_at = Column(DateTime(timezone=True), default=utcnow)


class Upload(Base):
    """Resumable chunked upload sessions (admin)."""
    __tablename__ = "uploads"
    id = Column(String(40), primary_key=True)
    language = Column(String(40), nullable=False)
    series = Column(String(200), nullable=False)
    filename = Column(String(300), nullable=False)
    title = Column(String(300), default="")
    total_size = Column(Integer, nullable=False)
    sha256 = Column(String(64), default="")           # optional, verified on complete
    received = Column(Integer, default=0)
    done = Column(Boolean, default=False)
    created_at = Column(DateTime(timezone=True), default=utcnow)


class AdminSession(Base):
    __tablename__ = "admin_sessions"
    token = Column(String(80), primary_key=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)


# ---------------------------------------------------------------------------
# 预留扩展点：媳妇儿锻炼打卡（workout check-in）。
# 未来新增独立的 WorkoutEvent 表 + /api/workout/* 路由即可，与儿童观看数据完全隔离。
# 见 docs/EXTENDING.md。
# ---------------------------------------------------------------------------


def init_db() -> None:
    Base.metadata.create_all(engine)
