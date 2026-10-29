from __future__ import annotations

import os

from fastapi import Depends, Header, HTTPException
from sqlalchemy.orm import Session

from .db import AdminSession, Device, SessionLocal, utcnow

ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "admin123")


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def _bearer(authorization: str | None) -> str:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "缺少登录凭证")
    return authorization.split(" ", 1)[1].strip()


def require_admin(authorization: str | None = Header(None), db: Session = Depends(get_db)) -> AdminSession:
    token = _bearer(authorization)
    sess = db.get(AdminSession, token)
    if not sess:
        raise HTTPException(401, "登录已失效，请重新登录")
    return sess


def require_device(authorization: str | None = Header(None), db: Session = Depends(get_db)) -> Device:
    token = _bearer(authorization)
    dev = db.query(Device).filter(Device.token == token).one_or_none()
    if not dev:
        raise HTTPException(401, "设备未配对或凭证无效")
    dev.last_seen = utcnow()
    db.commit()
    return dev
