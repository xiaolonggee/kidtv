"""HTTP Range file serving (for video download by devices / preview in admin)."""
from __future__ import annotations

import os
import re

from fastapi import HTTPException, Request
from fastapi.responses import Response, StreamingResponse

CHUNK = 1024 * 512
_RANGE_RE = re.compile(r"bytes=(\d*)-(\d*)")


def _iter_file(path: str, start: int, end: int):
    with open(path, "rb") as f:
        f.seek(start)
        remaining = end - start + 1
        while remaining > 0:
            data = f.read(min(CHUNK, remaining))
            if not data:
                break
            remaining -= len(data)
            yield data


def range_file_response(path: str, request: Request, media_type: str = "application/octet-stream"):
    if not os.path.isfile(path):
        raise HTTPException(404, "文件不存在")
    size = os.path.getsize(path)
    range_header = request.headers.get("range")
    headers = {"Accept-Ranges": "bytes"}
    if range_header:
        m = _RANGE_RE.match(range_header.strip())
        if not m:
            raise HTTPException(416, "Range 格式错误")
        start_s, end_s = m.groups()
        if start_s == "" and end_s == "":
            raise HTTPException(416, "Range 格式错误")
        if start_s == "":  # suffix: last N bytes
            length = int(end_s)
            start = max(0, size - length)
            end = size - 1
        else:
            start = int(start_s)
            end = int(end_s) if end_s else size - 1
        if start >= size:
            return Response(status_code=416, headers={"Content-Range": f"bytes */{size}"})
        end = min(end, size - 1)
        headers.update({
            "Content-Range": f"bytes {start}-{end}/{size}",
            "Content-Length": str(end - start + 1),
        })
        return StreamingResponse(_iter_file(path, start, end), status_code=206,
                                 headers=headers, media_type=media_type)
    headers["Content-Length"] = str(size)
    return StreamingResponse(_iter_file(path, 0, size - 1), headers=headers, media_type=media_type)
