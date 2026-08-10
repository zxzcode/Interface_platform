from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="调用 Interface Platform 开放接口")
    parser.add_argument("path", help="开放接口路径，例如 /open-api/sql/system-query")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--method", default="POST")
    parser.add_argument("--query", default="", help="原始查询串，不包含 ?")
    parser.add_argument("--body", default="", help="UTF-8 JSON 或文本请求体")
    parser.add_argument("--body-file", help="从文件读取原始请求体")
    parser.add_argument("--app-key", default=os.getenv("INTERFACE_APP_KEY"))
    parser.add_argument("--app-secret", default=os.getenv("INTERFACE_APP_SECRET"))
    parser.add_argument("--content-type", default="application/json; charset=utf-8")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.app_key or not args.app_secret:
        print("请传入 --app-key/--app-secret，或设置 INTERFACE_APP_KEY/INTERFACE_APP_SECRET。", file=sys.stderr)
        return 2

    path = args.path if args.path.startswith("/") else f"/{args.path}"
    method = args.method.upper()
    if args.body_file:
        with open(args.body_file, "rb") as body_file:
            body = body_file.read()
    else:
        body = args.body.encode("utf-8")

    timestamp = str(int(time.time() * 1000))
    nonce = secrets.token_hex(16)
    body_hash = hashlib.sha256(body).hexdigest()
    canonical = "\n".join((method, path, args.query, timestamp, nonce, body_hash))
    signature = hmac.new(
        args.app_secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256
    ).hexdigest()

    url = args.base_url.rstrip("/") + path
    if args.query:
        url += "?" + args.query
    request = urllib.request.Request(
        url,
        data=body if body else None,
        method=method,
        headers={
            "Content-Type": args.content_type,
            "X-App-Key": args.app_key,
            "X-Timestamp": timestamp,
            "X-Nonce": nonce,
            "X-Signature": signature,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            response_body = response.read().decode("utf-8", errors="replace")
            print(f"HTTP {response.status}")
            print(f"X-Trace-Id: {response.headers.get('X-Trace-Id', '')}")
            print(pretty(response_body))
            return 0
    except urllib.error.HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        print(f"HTTP {error.code}")
        print(f"X-Trace-Id: {error.headers.get('X-Trace-Id', '')}")
        print(pretty(response_body))
        return 1
    except urllib.error.URLError as error:
        print(f"请求失败: {error.reason}", file=sys.stderr)
        return 1


def pretty(value: str) -> str:
    try:
        return json.dumps(json.loads(value), ensure_ascii=False, indent=2)
    except (json.JSONDecodeError, TypeError):
        return value


if __name__ == "__main__":
    raise SystemExit(main())
