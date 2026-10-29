"""设备端 API：配对、事件上报、设置拉取、内容清单、文件下载、同步状态回报。"""
from __future__ import annotations

import json
import os
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..auth import get_db, require_device
from ..db import (
    DEFAULT_DEVICE_SETTINGS, ContentItem, Device, DeviceSyncState, Event,
    MEDIA_DIR, PairingCode, SeriesMeta, new_token, utcnow,
)
from ..files import range_file_response

router = APIRouter(prefix="/api", tags=["device"])


# ---------------------------------------------------------------- pairing
class PairBody(BaseModel):
    code: str
    name: str | None = None


@router.post("/pair")
def pair(body: PairBody, db: Session = Depends(get_db)):
    pc = db.get(PairingCode, body.code.strip())
    if not pc or pc.used_by:
        raise HTTPException(400, "配对码无效")
    expires = pc.expires_at
    if expires.tzinfo is None:
        expires = expires.replace(tzinfo=timezone.utc)
    if expires < utcnow():
        raise HTTPException(400, "配对码已过期，请在管理后台重新生成")
    dev = Device(
        id=uuid.uuid4().hex[:12],
        name=body.name or pc.device_name or "客厅电视",
        token=new_token(),
        settings_json=json.dumps(DEFAULT_DEVICE_SETTINGS),
        settings_version=1,
    )
    pc.used_by = dev.id
    db.add(dev)
    db.commit()
    return {
        "deviceId": dev.id,
        "deviceToken": dev.token,
        "name": dev.name,
        "settingsVersion": dev.settings_version,
        "settings": json.loads(dev.settings_json),
    }


# ---------------------------------------------------------------- events
class EventIn(BaseModel):
    eventId: str = Field(min_length=8, max_length=64)
    type: str = "play_complete"
    language: str = ""
    series: str = ""
    episodeKey: str = ""
    episodeNum: int = 0
    title: str = ""
    watchedSec: int = 0
    durationSec: int = 0
    counted: bool = True
    localDate: str = ""            # YYYY-MM-DD（设备本地 Asia/Shanghai）
    occurredAtMs: int = 0


class EventsBody(BaseModel):
    events: list[EventIn]


@router.post("/events")
def ingest_events(body: EventsBody, dev: Device = Depends(require_device),
                  db: Session = Depends(get_db)):
    accepted, duplicates = [], []
    for ev in body.events:
        if db.get(Event, ev.eventId):
            duplicates.append(ev.eventId)
            continue
        occurred = (datetime.fromtimestamp(ev.occurredAtMs / 1000, tz=timezone.utc)
                    if ev.occurredAtMs else utcnow())
        db.add(Event(
            id=ev.eventId, device_id=dev.id, type=ev.type, language=ev.language,
            series=ev.series, episode_key=ev.episodeKey, episode_num=ev.episodeNum,
            title=ev.title, watched_sec=ev.watchedSec, duration_sec=ev.durationSec,
            counted=ev.counted, local_date=ev.localDate or occurred.strftime("%Y-%m-%d"),
            occurred_at=occurred,
        ))
        accepted.append(ev.eventId)
    db.commit()
    return {"accepted": accepted, "duplicates": duplicates}


# ---------------------------------------------------------------- settings
@router.get("/settings")
def get_settings(dev: Device = Depends(require_device)):
    return {"version": dev.settings_version, "settings": json.loads(dev.settings_json)}


# ---------------------------------------------------------------- manifest & files
@router.get("/manifest")
def manifest(dev: Device = Depends(require_device), db: Session = Depends(get_db)):
    items = db.query(ContentItem).all()
    metas = db.query(SeriesMeta).all()
    return {
        "items": [{
            "id": i.id, "language": i.language, "series": i.series,
            "filename": i.filename, "title": i.title, "size": i.size,
            "sha256": i.sha256, "deleted": i.deleted,
            "updatedAt": i.updated_at.isoformat() if i.updated_at else None,
        } for i in items],
        "series": [{
            "language": m.language, "folder": m.folder,
            "displayName": m.display_name, "cnName": m.cn_name,
            "coverUrl": f"/api/covers/{m.id}" if m.cover_file else None,
        } for m in metas],
    }


@router.get("/files/{content_id}")
def download_file(content_id: str, request: Request,
                  dev: Device = Depends(require_device), db: Session = Depends(get_db)):
    item = db.get(ContentItem, content_id)
    if not item or item.deleted:
        raise HTTPException(404, "内容不存在")
    return range_file_response(os.path.join(MEDIA_DIR, item.rel_path), request, "video/mp4")


@router.get("/covers/{meta_id}")
def download_cover(meta_id: int, request: Request,
                   dev: Device = Depends(require_device), db: Session = Depends(get_db)):
    meta = db.get(SeriesMeta, meta_id)
    if not meta or not meta.cover_file:
        raise HTTPException(404, "封面不存在")
    return range_file_response(os.path.join(MEDIA_DIR, meta.cover_file), request, "image/jpeg")


# ---------------------------------------------------------------- sync state
class SyncItem(BaseModel):
    contentId: str
    status: str = "synced"      # synced | deleted


class SyncBody(BaseModel):
    items: list[SyncItem]


@router.post("/sync-state")
def report_sync_state(body: SyncBody, dev: Device = Depends(require_device),
                      db: Session = Depends(get_db)):
    for it in body.items:
        row = db.get(DeviceSyncState, (dev.id, it.contentId))
        if row:
            row.status = it.status
            row.updated_at = utcnow()
        else:
            db.add(DeviceSyncState(device_id=dev.id, content_id=it.contentId,
                                   status=it.status, updated_at=utcnow()))
    db.commit()
    return {"ok": True}
