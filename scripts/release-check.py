#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""源码公开前的文件、图片、品牌、授权及私有值核查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SKIP = {".git", "node_modules", "target", "dist", "__pycache__", ".venv"}
PROTECTED = {
    "LICENSE": "41e43385c4e6d08a272891060ab73695e5de1f63ab1d2405ba79f23284cc580f",
    "frontend/public/brand/logo.jpg": "90bf87ff294f5ee8cdaef4b10155139173d97d93a88d7ea9679b9721ba05c962",
    "docs/images/wechat-zhuatech.png": "a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1",
    "docs/images/wechat-zhuatech2.png": "98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215",
}


def public_files():
    """只枚举可公开文件，排除运行环境与构建产物。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    return sorted(
        p
        for p in ROOT.rglob("*")
        if p.is_file()
        and not set(p.relative_to(ROOT).parts) & SKIP
        and p.name != ".DS_Store"
        and not p.name.endswith((".log", ".pyc"))
        and (not p.name.startswith(".env") or p.name == ".env.example")
    )


def main():
    """失败即停止，不输出检测到的私有值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    for name, digest in PROTECTED.items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, (
            "Protected file changed: " + name
        )
    cn = (ROOT / "README.md").read_text()
    en = (ROOT / "README.en.md").read_text()
    for text in [cn, en]:
        assert "[中文](README.md) | [English](README.en.md)" in text
        assert "https://www.zhuatech.cn/" in text and "LICENSE" in text
    for name in [
        "wechat-zhuatech.png",
        "wechat-zhuatech2.png",
        "zhuatech2",
        "商业授权或深度定制开发请联系知华科技",
        "上海如静知华信息科技有限公司",
    ]:
        assert name in cn, "Chinese README requirement missing"
    for name in [
        "han@zhuatech.cn",
        "jack@zhuatech.cn",
        "https://wa.me/8617521234993",
        "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
        "noncommercial",
    ]:
        assert name in en, "English README requirement missing"
    assert "wechat-zhuatech" not in en, "English QR must be absent"
    files = public_files()
    known = []
    if (ROOT / ".env").exists():
        known = [
            line.split("=", 1)[1]
            for line in (ROOT / ".env").read_text().splitlines()
            if line.startswith(
                ("ADMIN_PASSWORD=", "DATABASE_PASSWORD=", "MYSQL_ROOT_PASSWORD=")
            )
            and len(line.split("=", 1)[1]) >= 12
        ]
    for p in files:
        rel = p.relative_to(ROOT).as_posix()
        assert (
            not rel.endswith((".sql.gz", ".dump"))
            and "backup.sql" not in rel
            and "quality-state.json" not in rel
        ), "Private file detected"
        if p.suffix.lower() in {".jpg", ".jpeg", ".png"}:
            continue
        text = p.read_text(encoding="utf-8")
        assert not any(value in text for value in known), (
            "Private value detected in " + rel
        )
        assert not re.search(
            r"(?:ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----)",
            text,
        ), (
            "Credential pattern detected in " + rel
        )
        if p.suffix in {
            ".java",
            ".js",
            ".vue",
            ".css",
            ".py",
            ".yml",
            ".yaml",
            ".sql",
        } or p.name in {
            "Dockerfile",
            "nginx.conf",
            ".dockerignore",
            ".gitignore",
            ".env.example",
        }:
            assert (
                "https://www.zhuatech.cn/" in text[:1200] and "zhuatech2" in text[:1200]
            ), ("Source attribution missing in " + rel)
        if p.suffix == ".md":
            for link in re.findall(r"!?\[[^\]]*\]\(([^)]+)\)", text):
                if re.match(r"^(https?://|mailto:|#)", link):
                    continue
                target = link.split("#")[0]
                assert (p.parent / target).is_file(), (
                    "Broken document link in " + rel + ": " + target
                )
    screenshots = list((ROOT / "docs/screenshots").glob("*.jpg"))
    assert len(screenshots) >= 6, "At least six actual screenshots required"
    for p in screenshots:
        assert p.read_bytes()[:2] == b"\xff\xd8", "Screenshot encoding mismatch"
    print(
        json.dumps(
            {
                "result": "passed",
                "publicFiles": len(files),
                "screenshots": len(screenshots),
                "protectedFiles": len(PROTECTED),
                "privateValues": "not found",
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
