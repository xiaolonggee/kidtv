"""端到端演示/冒烟脚本：
1) 管理员登录，分块上传视频库（语言/系列/集），上传封面与系列中文名
2) 生成配对码并模拟电视配对
3) 模拟电视上报观看事件（含历史打卡数据，构造连续打卡）
4) 模拟电视拉取清单、Range 断点下载一个文件并校验 sha256、回报同步状态

用法：python scripts/simulate_device.py http://127.0.0.1:8600 /path/to/启蒙视频
"""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
import uuid
from datetime import date, timedelta

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8600"
MEDIA = sys.argv[2] if len(sys.argv) > 2 else "/tmp/demo-videos/启蒙视频"
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "admin123")
CHUNK = 2 * 1024 * 1024

SERIES_CN = {"Mouse Boo 小鼠波波": ("Mouse Boo", "小鼠波波"),
             "Super Simple Songs": ("Super Simple Songs", "超级简单儿歌"),
             "T'choupi": ("T'choupi", "小企鹅丘比")}


def req(method, path, token=None, body=None, raw=None, headers=None):
    h = dict(headers or {})
    if token:
        h["Authorization"] = f"Bearer {token}"
    if body is not None:
        raw = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    r = urllib.request.Request(BASE + path, data=raw, method=method, headers=h)
    with urllib.request.urlopen(r) as res:
        ct = res.headers.get("content-type", "")
        data = res.read()
        return json.loads(data) if "json" in ct else data


def sha256_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def upload_file(admin, lang, series, path, title=""):
    size = os.path.getsize(path)
    digest = sha256_file(path)
    init = req("POST", "/api/admin/uploads", admin, {
        "language": lang, "series": series, "filename": os.path.basename(path),
        "title": title, "totalSize": size, "sha256": digest,
    })
    uid, off = init["uploadId"], 0
    with open(path, "rb") as f:
        while off < size:
            chunk = f.read(CHUNK)
            r = req("PUT", f"/api/admin/uploads/{uid}/chunk?offset={off}", admin,
                    raw=chunk, headers={"Content-Type": "application/octet-stream"})
            off = r["received"]
    done = req("POST", f"/api/admin/uploads/{uid}/complete", admin)
    print(f"  已上传 {lang}/{series}/{os.path.basename(path)} ({size//1024} KB)")
    return done["contentId"]


def main():
    print("1) 管理员登录 …")
    admin = req("POST", "/api/admin/login", body={"password": ADMIN_PASSWORD})["token"]

    print("2) 分块上传媒体库 …")
    content_ids = []
    for lang in sorted(os.listdir(MEDIA)):
        lp = os.path.join(MEDIA, lang)
        if not os.path.isdir(lp):
            continue
        for series in sorted(os.listdir(lp)):
            sp = os.path.join(lp, series)
            if not os.path.isdir(sp):
                continue
            name, cn = SERIES_CN.get(series, (series, ""))
            req("POST", "/api/admin/series-meta", admin,
                {"language": lang, "folder": series, "displayName": name, "cnName": cn})
            videos = sorted(x for x in os.listdir(sp) if x.lower().endswith((".mp4", ".mkv")))
            for v in videos:
                title = v.rsplit(".", 1)[0].split(" ", 1)[-1] if " " in v else ""
                content_ids.append(upload_file(admin, lang, series, os.path.join(sp, v), title))
            # 用第一集截帧做封面
            if videos:
                cover = os.path.join(tempfile.gettempdir(), "cover.jpg")
                subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i",
                                os.path.join(sp, videos[0]), "-vframes", "1", "-q:v", "4", cover],
                               check=True)
                import urllib.parse
                q = urllib.parse.urlencode({"language": lang, "folder": series})
                boundary = uuid.uuid4().hex
                data = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
                        f"filename=\"cover.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").encode() \
                    + open(cover, "rb").read() + f"\r\n--{boundary}--\r\n".encode()
                req("POST", f"/api/admin/series-cover?{q}", admin, raw=data,
                    headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})

    print("3) 生成配对码并模拟电视配对 …")
    code = req("POST", "/api/admin/pairing-codes", admin, {"name": "客厅电视"})["code"]
    pair = req("POST", "/api/pair", body={"code": code, "name": "客厅电视"})
    dev = pair["deviceToken"]
    print(f"  配对码 {code} → 设备 {pair['deviceId']}（{pair['name']}）")

    print("4) 上报模拟观看事件（历史打卡 + 今天）…")
    today = date.today()
    eps_pool = [
        ("英语", "Mouse Boo 小鼠波波", 1, "Hello Boo", 300),
        ("英语", "Mouse Boo 小鼠波波", 2, "Colors", 290),
        ("英语", "Mouse Boo 小鼠波波", 3, "Bath Time", 310),
        ("英语", "Super Simple Songs", 1, "Hello Song", 150),
        ("法语", "T'choupi", 1, "T'choupi fait du velo", 360),
        ("法语", "T'choupi", 2, "T'choupi a la plage", 355),
    ]
    events, k = [], 0
    # 过去 30 天：约 2/3 的天数有观看；最近 5 天连续（含今天），构成连续打卡
    for back in range(30, -1, -1):
        d = today - timedelta(days=back)
        if back > 4 and (back % 3 == 0):
            continue
        n = 1 if back % 4 == 1 else 2
        for i in range(n):
            lang, series, num, title, dur = eps_pool[k % len(eps_pool)]
            k += 1
            events.append({
                "eventId": f"sim-{d.isoformat()}-{i}-{uuid.uuid4().hex[:8]}",
                "type": "play_complete", "language": lang, "series": series,
                "episodeKey": f"{lang}/{series}/{num:02d}.mp4", "episodeNum": num,
                "title": title, "watchedSec": int(dur * 0.9), "durationSec": dur,
                "counted": True, "localDate": d.isoformat(),
                "occurredAtMs": 0,
            })
    r = req("POST", "/api/events", dev, {"events": events})
    print(f"  已接收 {len(r['accepted'])} 条；重传幂等验证 → ", end="")
    r2 = req("POST", "/api/events", dev, {"events": events[:3]})
    assert r2["accepted"] == [] and len(r2["duplicates"]) == 3
    print("OK（全部判重）")

    print("5) 模拟电视内容同步：清单 → Range 下载 → 校验 → 回报 …")
    mf = req("GET", "/api/manifest", dev)
    items = [i for i in mf["items"] if not i["deleted"]]
    print(f"  清单 {len(items)} 项")
    it = items[0]
    # 分两段 Range 下载验证断点续传
    half = it["size"] // 2
    p1 = req("GET", f"/api/files/{it['id']}", dev, headers={"Range": f"bytes=0-{half-1}"})
    p2 = req("GET", f"/api/files/{it['id']}", dev, headers={"Range": f"bytes={half}-"})
    got = hashlib.sha256(p1 + p2).hexdigest()
    assert got == it["sha256"], "sha256 不匹配！"
    print(f"  {it['filename']}：两段 Range 下载拼接 sha256 校验通过 ✓")
    req("POST", "/api/sync-state", dev,
        {"items": [{"contentId": i["id"], "status": "synced"} for i in items]})
    print("  已回报全部条目同步完成")

    print("\n全部通过 ✅  管理后台:", BASE + "/admin/", "密码:", ADMIN_PASSWORD)


if __name__ == "__main__":
    main()
