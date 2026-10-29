"""管理后台 API：登录、配对码、仪表盘统计、设备设置、内容管理（分块上传）。"""
from __future__ import annotations

import hashlib
import json
import os
import re
import secrets
import shutil
import uuid
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, Request, UploadFile
from pydantic import BaseModel
from sqlalchemy import func
from sqlalchemy.orm import Session

from ..auth import ADMIN_PASSWORD, get_db, require_admin
from ..db import (
    AdminSession, ContentItem, Device, DeviceSyncState, Event, MEDIA_DIR,
    PairingCode, SeriesMeta, Upload, UPLOAD_TMP_DIR, new_token, utcnow,
)
from ..files import range_file_response

router = APIRouter(prefix="/api/admin", tags=["admin"])
TZ = ZoneInfo("Asia/Shanghai")
_SAFE_SEG = re.compile(r"^[^/\\\x00]{1,200}$")


def _check_seg(*segs: str):
    for s in segs:
        if not _SAFE_SEG.match(s) or s in (".", ".."):
            raise HTTPException(400, f"非法名称: {s!r}")


# ---------------------------------------------------------------- auth
class LoginBody(BaseModel):
    password: str


@router.post("/login")
def login(body: LoginBody, db: Session = Depends(get_db)):
    if not secrets.compare_digest(body.password, ADMIN_PASSWORD):
        raise HTTPException(401, "密码错误")
    sess = AdminSession(token=new_token())
    db.add(sess)
    db.commit()
    return {"token": sess.token}


# ---------------------------------------------------------------- pairing codes
class PairingBody(BaseModel):
    name: str = "客厅电视"


@router.post("/pairing-codes")
def create_pairing_code(body: PairingBody, _=Depends(require_admin), db: Session = Depends(get_db)):
    code = f"{secrets.randbelow(1000000):06d}"
    db.add(PairingCode(code=code, device_name=body.name,
                       expires_at=utcnow() + timedelta(minutes=15)))
    db.commit()
    return {"code": code, "expiresInMinutes": 15}


# ---------------------------------------------------------------- devices & settings
@router.get("/devices")
def list_devices(_=Depends(require_admin), db: Session = Depends(get_db)):
    today = datetime.now(TZ).strftime("%Y-%m-%d")
    out = []
    for d in db.query(Device).all():
        today_eps, today_sec = _day_totals(db, d.id, today)
        out.append({
            "id": d.id, "name": d.name,
            "lastSeen": d.last_seen.isoformat() if d.last_seen else None,
            "settingsVersion": d.settings_version,
            "settings": json.loads(d.settings_json),
            "todayEps": today_eps, "todaySec": today_sec,
            "streak": _streak(db, d.id),
        })
    return {"devices": out}


class DevicePatch(BaseModel):
    name: str | None = None
    settings: dict | None = None


@router.patch("/devices/{device_id}")
def patch_device(device_id: str, body: DevicePatch, _=Depends(require_admin),
                 db: Session = Depends(get_db)):
    dev = db.get(Device, device_id)
    if not dev:
        raise HTTPException(404, "设备不存在")
    if body.name:
        dev.name = body.name
    if body.settings is not None:
        cur = json.loads(dev.settings_json)
        cur.update(body.settings)
        if "playbackMode" in cur:
            cur["autoplay"] = cur.get("playbackMode") == "sequential"
        elif "autoplay" in body.settings and "playbackMode" not in cur:
            cur["playbackMode"] = "sequential" if body.settings["autoplay"] else "stop"
        dev.settings_json = json.dumps(cur)
        dev.settings_version += 1
    db.commit()
    return {"ok": True, "settingsVersion": dev.settings_version}


@router.delete("/devices/{device_id}")
def delete_device(device_id: str, _=Depends(require_admin), db: Session = Depends(get_db)):
    dev = db.get(Device, device_id)
    if not dev:
        raise HTTPException(404, "设备不存在")
    db.delete(dev)
    db.query(DeviceSyncState).filter(DeviceSyncState.device_id == device_id).delete()
    db.commit()
    return {"ok": True}


# ---------------------------------------------------------------- stats
def _day_totals(db: Session, device_id: str, day: str) -> tuple[int, int]:
    row = (db.query(func.count(Event.id), func.coalesce(func.sum(Event.watched_sec), 0))
           .filter(Event.device_id == device_id, Event.local_date == day,
                   Event.counted.is_(True)).one())
    return int(row[0]), int(row[1])


def _watched_dates(db: Session, device_id: str) -> set[str]:
    rows = (db.query(Event.local_date).filter(Event.device_id == device_id,
                                              Event.counted.is_(True)).distinct().all())
    return {r[0] for r in rows if r[0]}


def _streak(db: Session, device_id: str) -> int:
    dates = _watched_dates(db, device_id)
    d = datetime.now(TZ).date()
    if d.strftime("%Y-%m-%d") not in dates:
        d -= timedelta(days=1)   # 今天还没看：连续天数仍从昨天算起
    n = 0
    while d.strftime("%Y-%m-%d") in dates:
        n += 1
        d -= timedelta(days=1)
    return n


@router.get("/stats")
def stats(device_id: str = Query(...), month: str | None = Query(None, pattern=r"^\d{4}-\d{2}$"),
          _=Depends(require_admin), db: Session = Depends(get_db)):
    dev = db.get(Device, device_id)
    if not dev:
        raise HTTPException(404, "设备不存在")
    now = datetime.now(TZ)
    today = now.strftime("%Y-%m-%d")
    today_eps, today_sec = _day_totals(db, device_id, today)

    monday = (now.date() - timedelta(days=now.weekday()))
    week_days = [(monday + timedelta(days=i)).strftime("%Y-%m-%d") for i in range(7)]
    week = {d: _day_totals(db, device_id, d) for d in week_days}

    ym = month or now.strftime("%Y-%m")
    cal_rows = (db.query(Event.local_date, func.count(Event.id),
                         func.coalesce(func.sum(Event.watched_sec), 0))
                .filter(Event.device_id == device_id, Event.counted.is_(True),
                        Event.local_date.like(ym + "-%"))
                .group_by(Event.local_date).all())
    calendar = {r[0]: {"eps": int(r[1]), "sec": int(r[2])} for r in cal_rows}

    series_rows = (db.query(Event.language, Event.series, func.count(Event.id),
                            func.coalesce(func.sum(Event.watched_sec), 0))
                   .filter(Event.device_id == device_id, Event.counted.is_(True))
                   .group_by(Event.language, Event.series).all())
    episode_rows = (db.query(Event.language, Event.series, Event.episode_num, Event.title,
                             func.count(Event.id))
                    .filter(Event.device_id == device_id, Event.counted.is_(True))
                    .group_by(Event.language, Event.series, Event.episode_num, Event.title)
                    .all())
    return {
        "device": {"id": dev.id, "name": dev.name},
        "today": {"date": today, "eps": today_eps, "sec": today_sec},
        "week": [{"date": d, "eps": week[d][0], "sec": week[d][1]} for d in week_days],
        "streak": _streak(db, device_id),
        "month": ym,
        "calendar": calendar,
        "series": [{"language": r[0], "series": r[1], "plays": int(r[2]), "sec": int(r[3])}
                   for r in series_rows],
        "episodes": [{"language": r[0], "series": r[1], "episodeNum": r[2],
                      "title": r[3], "plays": int(r[4])} for r in episode_rows],
    }


# ---------------------------------------------------------------- content tree
@router.get("/content")
def content_tree(_=Depends(require_admin), db: Session = Depends(get_db)):
    items = db.query(ContentItem).filter(ContentItem.deleted.is_(False)).all()
    metas = {(m.language, m.folder): m for m in db.query(SeriesMeta).all()}
    sync = db.query(DeviceSyncState).filter(DeviceSyncState.status == "synced").all()
    synced_by: dict[str, list[str]] = {}
    for s in sync:
        synced_by.setdefault(s.content_id, []).append(s.device_id)
    tree: dict[str, dict[str, list]] = {}
    for i in sorted(items, key=lambda x: (x.language, x.series, x.filename)):
        tree.setdefault(i.language, {}).setdefault(i.series, []).append({
            "id": i.id, "filename": i.filename, "title": i.title, "size": i.size,
            "sha256": i.sha256, "updatedAt": i.updated_at.isoformat() if i.updated_at else None,
            "syncedDevices": synced_by.get(i.id, []),
        })
    return {
        "tree": tree,
        "seriesMeta": [{
            "id": m.id, "language": m.language, "folder": m.folder,
            "displayName": m.display_name, "cnName": m.cn_name,
            "hasCover": bool(m.cover_file),
        } for m in metas.values()],
    }


class ItemPatch(BaseModel):
    title: str | None = None


@router.patch("/content/{content_id}")
def patch_item(content_id: str, body: ItemPatch, _=Depends(require_admin),
               db: Session = Depends(get_db)):
    item = db.get(ContentItem, content_id)
    if not item:
        raise HTTPException(404, "内容不存在")
    if body.title is not None:
        item.title = body.title
    item.updated_at = utcnow()
    db.commit()
    return {"ok": True}


@router.delete("/content/{content_id}")
def delete_item(content_id: str, _=Depends(require_admin), db: Session = Depends(get_db)):
    """标记删除：清单里保留 deleted=true，开启了“同步删除”的设备会删掉本地文件。"""
    item = db.get(ContentItem, content_id)
    if not item:
        raise HTTPException(404, "内容不存在")
    path = os.path.join(MEDIA_DIR, item.rel_path)
    if os.path.isfile(path):
        os.remove(path)
    item.deleted = True
    item.updated_at = utcnow()
    db.commit()
    return {"ok": True}


# ---------------------------------------------------------------- series meta / cover
class SeriesMetaBody(BaseModel):
    language: str
    folder: str
    displayName: str = ""
    cnName: str = ""


@router.post("/series-meta")
def upsert_series_meta(body: SeriesMetaBody, _=Depends(require_admin),
                       db: Session = Depends(get_db)):
    _check_seg(body.language, body.folder)
    meta = (db.query(SeriesMeta).filter_by(language=body.language, folder=body.folder)
            .one_or_none())
    if not meta:
        meta = SeriesMeta(language=body.language, folder=body.folder)
        db.add(meta)
    meta.display_name = body.displayName
    meta.cn_name = body.cnName
    db.commit()
    return {"ok": True, "id": meta.id}


@router.post("/series-cover")
async def upload_cover(language: str, folder: str, file: UploadFile,
                       _=Depends(require_admin), db: Session = Depends(get_db)):
    _check_seg(language, folder)
    meta = db.query(SeriesMeta).filter_by(language=language, folder=folder).one_or_none()
    if not meta:
        meta = SeriesMeta(language=language, folder=folder)
        db.add(meta)
        db.flush()
    rel = f"{language}/{folder}/cover.jpg"
    dst = os.path.join(MEDIA_DIR, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, "wb") as f:
        shutil.copyfileobj(file.file, f)
    meta.cover_file = rel
    db.commit()
    return {"ok": True}


# ---------------------------------------------------------------- chunked upload
class UploadInit(BaseModel):
    language: str
    series: str
    filename: str
    title: str = ""
    totalSize: int
    sha256: str = ""


@router.post("/uploads")
def init_upload(body: UploadInit, _=Depends(require_admin), db: Session = Depends(get_db)):
    _check_seg(body.language, body.series, body.filename)
    if body.totalSize <= 0:
        raise HTTPException(400, "文件大小无效")
    up = Upload(id=uuid.uuid4().hex, language=body.language, series=body.series,
                filename=body.filename, title=body.title, total_size=body.totalSize,
                sha256=body.sha256.lower())
    db.add(up)
    db.commit()
    open(os.path.join(UPLOAD_TMP_DIR, up.id), "wb").close()
    return {"uploadId": up.id, "received": 0}


@router.get("/uploads/{upload_id}")
def upload_status(upload_id: str, _=Depends(require_admin), db: Session = Depends(get_db)):
    up = db.get(Upload, upload_id)
    if not up:
        raise HTTPException(404, "上传任务不存在")
    return {"uploadId": up.id, "received": up.received,
            "totalSize": up.total_size, "done": up.done}


@router.put("/uploads/{upload_id}/chunk")
async def upload_chunk(upload_id: str, request: Request, offset: int = Query(...),
                       _=Depends(require_admin), db: Session = Depends(get_db)):
    up = db.get(Upload, upload_id)
    if not up or up.done:
        raise HTTPException(404, "上传任务不存在或已完成")
    if offset != up.received:
        # 断点续传：客户端先 GET 状态取 received，再从该偏移继续
        raise HTTPException(409, f"偏移不匹配，应为 {up.received}")
    body = await request.body()
    if up.received + len(body) > up.total_size:
        raise HTTPException(400, "数据超过声明大小")
    with open(os.path.join(UPLOAD_TMP_DIR, up.id), "r+b") as f:
        f.seek(offset)
        f.write(body)
    up.received += len(body)
    db.commit()
    return {"received": up.received}


@router.post("/uploads/{upload_id}/complete")
def complete_upload(upload_id: str, _=Depends(require_admin), db: Session = Depends(get_db)):
    up = db.get(Upload, upload_id)
    if not up or up.done:
        raise HTTPException(404, "上传任务不存在或已完成")
    if up.received != up.total_size:
        raise HTTPException(400, f"文件不完整：已收 {up.received}/{up.total_size} 字节")
    tmp = os.path.join(UPLOAD_TMP_DIR, up.id)
    h = hashlib.sha256()
    with open(tmp, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    digest = h.hexdigest()
    if up.sha256 and digest != up.sha256:
        raise HTTPException(400, "校验失败：sha256 不匹配")
    rel = f"{up.language}/{up.series}/{up.filename}"
    dst = os.path.join(MEDIA_DIR, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.move(tmp, dst)

    item = (db.query(ContentItem)
            .filter_by(language=up.language, series=up.series, filename=up.filename)
            .one_or_none())
    if not item:
        item = ContentItem(id=uuid.uuid4().hex[:16], language=up.language,
                           series=up.series, filename=up.filename)
        db.add(item)
    item.title = up.title or item.title
    item.size = up.total_size
    item.sha256 = digest
    item.deleted = False
    item.updated_at = utcnow()
    up.done = True
    db.commit()
    return {"ok": True, "contentId": item.id, "sha256": digest}


# ---------------------------------------------------------------- admin file preview
@router.get("/files/{content_id}")
def admin_file(content_id: str, request: Request, token: str = Query(...),
               db: Session = Depends(get_db)):
    """浏览器 <video> 预览无法带 Authorization 头，这里用 ?token= 鉴权。"""
    if not db.get(AdminSession, token):
        raise HTTPException(401, "登录已失效")
    item = db.get(ContentItem, content_id)
    if not item or item.deleted:
        raise HTTPException(404, "内容不存在")
    return range_file_response(os.path.join(MEDIA_DIR, item.rel_path), request, "video/mp4")
