"""核心 API 测试：配对、事件幂等、设置推送、分块上传、清单、Range 下载。"""
import hashlib
import os
import tempfile

os.environ["KIDTV_DATA_DIR"] = tempfile.mkdtemp(prefix="kidtv-test-")
os.environ["ADMIN_PASSWORD"] = "test-pass"

import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


@pytest.fixture(scope="module")
def admin():
    r = client.post("/api/admin/login", json={"password": "test-pass"})
    assert r.status_code == 200
    return {"Authorization": "Bearer " + r.json()["token"]}


@pytest.fixture(scope="module")
def device(admin):
    code = client.post("/api/admin/pairing-codes", json={"name": "测试电视"}, headers=admin).json()["code"]
    r = client.post("/api/pair", json={"code": code})
    assert r.status_code == 200
    data = r.json()
    assert data["settings"]["dailyLimit"] == 2
    # 配对码只能用一次
    assert client.post("/api/pair", json={"code": code}).status_code == 400
    return {"id": data["deviceId"], "headers": {"Authorization": "Bearer " + data["deviceToken"]}}


def test_login_wrong_password():
    assert client.post("/api/admin/login", json={"password": "nope"}).status_code == 401


def test_pair_bad_code():
    assert client.post("/api/pair", json={"code": "000000"}).status_code == 400


def test_event_ingest_idempotent(admin, device):
    ev = {
        "eventId": "evt-0001-aaaa", "type": "play_complete", "language": "英语",
        "series": "Mouse Boo 小鼠波波", "episodeKey": "英语/Mouse Boo 小鼠波波/01 Colors.mp4",
        "episodeNum": 1, "title": "Colors", "watchedSec": 240, "durationSec": 300,
        "counted": True, "localDate": "2026-10-19", "occurredAtMs": 1760856600000,
    }
    r = client.post("/api/events", json={"events": [ev]}, headers=device["headers"])
    assert r.json() == {"accepted": ["evt-0001-aaaa"], "duplicates": []}
    # 重复上传同一事件：幂等，不重复计数
    r = client.post("/api/events", json={"events": [ev]}, headers=device["headers"])
    assert r.json() == {"accepted": [], "duplicates": ["evt-0001-aaaa"]}

    st = client.get(f"/api/admin/stats?device_id={device['id']}&month=2026-10", headers=admin).json()
    assert st["calendar"]["2026-10-19"] == {"eps": 1, "sec": 240}
    assert st["series"] == [{"language": "英语", "series": "Mouse Boo 小鼠波波", "plays": 1, "sec": 240}]


def test_events_require_auth():
    r = client.post("/api/events", json={"events": []}, headers={"Authorization": "Bearer bad"})
    assert r.status_code == 401


def test_settings_push_pull(admin, device):
    r = client.get("/api/settings", headers=device["headers"]).json()
    assert r["version"] == 1
    client.patch(f"/api/admin/devices/{device['id']}",
                 json={"settings": {"dailyLimit": 3}}, headers=admin)
    r = client.get("/api/settings", headers=device["headers"]).json()
    assert r["version"] == 2 and r["settings"]["dailyLimit"] == 3


def test_chunked_upload_manifest_and_range(admin, device):
    payload = os.urandom(1024 * 1024) + b"END-OF-FILE"
    digest = hashlib.sha256(payload).hexdigest()
    init = client.post("/api/admin/uploads", json={
        "language": "英语", "series": "Mouse Boo 小鼠波波", "filename": "01 Colors.mp4",
        "title": "Colors", "totalSize": len(payload), "sha256": digest,
    }, headers=admin).json()
    uid = init["uploadId"]
    half = len(payload) // 2
    assert client.put(f"/api/admin/uploads/{uid}/chunk?offset=0", content=payload[:half],
                      headers=admin).json()["received"] == half
    # 错误偏移 → 409，客户端应查询状态后续传
    assert client.put(f"/api/admin/uploads/{uid}/chunk?offset=0", content=b"x",
                      headers=admin).status_code == 409
    assert client.get(f"/api/admin/uploads/{uid}", headers=admin).json()["received"] == half
    client.put(f"/api/admin/uploads/{uid}/chunk?offset={half}", content=payload[half:], headers=admin)
    done = client.post(f"/api/admin/uploads/{uid}/complete", headers=admin).json()
    assert done["ok"] and done["sha256"] == digest
    cid = done["contentId"]

    # 清单
    mf = client.get("/api/manifest", headers=device["headers"]).json()
    item = next(i for i in mf["items"] if i["id"] == cid)
    assert item["filename"] == "01 Colors.mp4" and item["size"] == len(payload)
    assert item["sha256"] == digest and item["deleted"] is False

    # 完整下载
    r = client.get(f"/api/files/{cid}", headers=device["headers"])
    assert r.status_code == 200 and r.content == payload
    assert r.headers["accept-ranges"] == "bytes"

    # Range：中间片段
    h = dict(device["headers"], Range="bytes=100-199")
    r = client.get(f"/api/files/{cid}", headers=h)
    assert r.status_code == 206 and r.content == payload[100:200]
    assert r.headers["content-range"] == f"bytes 100-199/{len(payload)}"

    # Range：开区间（断点续传典型用法）
    h = dict(device["headers"], Range=f"bytes={len(payload) - 11}-")
    r = client.get(f"/api/files/{cid}", headers=h)
    assert r.status_code == 206 and r.content == b"END-OF-FILE"

    # Range 越界 → 416
    h = dict(device["headers"], Range=f"bytes={len(payload) + 5}-")
    assert client.get(f"/api/files/{cid}", headers=h).status_code == 416

    # 设备回报同步状态 → 管理端能看到
    client.post("/api/sync-state", json={"items": [{"contentId": cid, "status": "synced"}]},
                headers=device["headers"])
    tree = client.get("/api/admin/content", headers=admin).json()["tree"]
    ep = tree["英语"]["Mouse Boo 小鼠波波"][0]
    assert device["id"] in ep["syncedDevices"]

    # 删除 → 清单标记 deleted，供设备可选同步删除
    client.delete(f"/api/admin/content/{cid}", headers=admin)
    mf = client.get("/api/manifest", headers=device["headers"]).json()
    assert next(i for i in mf["items"] if i["id"] == cid)["deleted"] is True
    assert client.get(f"/api/files/{cid}", headers=device["headers"]).status_code == 404


def test_upload_rejects_path_traversal(admin):
    r = client.post("/api/admin/uploads", json={
        "language": "..", "series": "x", "filename": "a.mp4", "totalSize": 10,
    }, headers=admin)
    assert r.status_code == 400


def test_upload_sha_mismatch(admin):
    init = client.post("/api/admin/uploads", json={
        "language": "英语", "series": "S", "filename": "02.mp4",
        "totalSize": 4, "sha256": "0" * 64,
    }, headers=admin).json()
    client.put(f"/api/admin/uploads/{init['uploadId']}/chunk?offset=0", content=b"abcd", headers=admin)
    r = client.post(f"/api/admin/uploads/{init['uploadId']}/complete", headers=admin)
    assert r.status_code == 400 and "sha256" in r.json()["detail"]
