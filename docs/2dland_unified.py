#!/usr/bin/env python3
"""
2dland unified runtime:
- Proxy endpoints: /scan /info /play /stream /delete
- STRM scan/generate/cleanup pipeline
- Auto-restore deleted files by content_identity
- CLI subcommands: serve / scan-once / proxy-only / validate-config
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import datetime as dt
import errno
import threading
import hashlib
import hmac
import inspect
import ipaddress
import json
import logging
import os
import socket
import time
import urllib.parse
import uuid
from collections import OrderedDict
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, AsyncGenerator, Dict, List, Optional, Set, Tuple

import httpx
import websockets
from fastapi import FastAPI, HTTPException, Query, Request, WebSocket, WebSocketDisconnect
from fastapi.responses import Response, StreamingResponse
from starlette.middleware.cors import CORSMiddleware
from starlette.background import BackgroundTask
from starlette.websockets import WebSocketState


# Legacy fallback credentials to preserve zero-config startup compatibility
# with older deployments that did not store credentials in config.json.
LEGACY_DEFAULT_CLIENT_ID = "puc_d217b8fe246d4c14bb43822e61854900_mkq5xkwv_v1"
LEGACY_DEFAULT_CLIENT_SECRET = "e4c82132cdf84bd7902203feca934148"


# File type groups
MEDIA_EXTS = {
    "mp4",
    "mkv",
    "avi",
    "mov",
    "ts",
    "wmv",
    "flv",
    "webm",
    "m4v",
    "iso",
    "rmvb",
    "m2ts",
    "mts",
    "m2t",
    "tp",
    "trp",
    "3gp",
    "mpg",
    "mpeg",
    "mp3",
    "flac",
    "wav",
    "aac",
    "ogg",
    "m4a",
    "wma",
    "ape",
    "opus",
}
IMAGE_EXTS = {"jpg", "jpeg", "png", "bmp", "webp", "gif", "nfo"}
SUB_EXTS = {"srt", "ass", "ssa", "vtt", "sub", "idx", "smi"}
GARBAGE_EXTS = {
    "html",
    "htm",
    "url",
    "txt",
    "website",
    "lnk",
    "zip",
    "rar",
    "mht",
    "apk",
    "chm",
    "exe",
    "torrent",
    "7z",
    "gz",
}


class ConfigError(Exception):
    pass


class ApiError(Exception):
    def __init__(self, message: str, *, uri: str = "", status_code: int = 0):
        super().__init__(message)
        self.uri = uri
        self.status_code = status_code


class PathNotFoundError(Exception):
    pass


@dataclass
class RetryConfig:
    max_attempts: int = 4
    backoff_base_sec: float = 0.6
    backoff_max_sec: float = 6.0
    retry_statuses: Tuple[int, ...] = (429, 500, 502, 503, 504)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "RetryConfig":
        statuses = data.get(
            "retry_statuses", data.get("statuses", [429, 500, 502, 503, 504])
        )
        if not isinstance(statuses, list):
            statuses = [429, 500, 502, 503, 504]
        return cls(
            max_attempts=int(data.get("max_attempts", 4)),
            backoff_base_sec=float(data.get("backoff_base_sec", 0.6)),
            backoff_max_sec=float(data.get("backoff_max_sec", 6.0)),
            retry_statuses=tuple(int(s) for s in statuses),
        )


PLAY_SOURCE_QUERY_PARAM = "play_source"
PLAY_SOURCE_EMBY_PROXY = "emby_proxy"
PLAY_REDIRECT_SCOPE_ALL = "all"
PLAY_REDIRECT_SCOPE_EMBY_ONLY = "emby_only"


@dataclass
class MappingConfig:
    remote: str
    local: str
    enabled: bool = True
    extras_mode: Optional[str] = None
    media_mode: Optional[str] = None
    comment: str = ""


@dataclass
class AppConfig:
    client_id: str
    client_secret: str
    api_host: str = "openapi.2dland.cn"

    proxy_port: int = 8899
    proxy_url: Optional[str] = None
    public_strm_host: str = "http://127.0.0.1:8899"
    admin_port: int = 8898  # 管理面板端口，0 = 关闭

    loop_interval: int = 0
    min_video_size_mb: int = 80
    extras_mode: str = "keep"
    media_mode: str = "keep"
    auto_confirm: bool = False
    output_dir: str = "emby_strm"

    restore_dir: str = "/Temp/AutoRestore"
    restore_ttl_hours: float = 3.0

    request_timeout_sec: float = 30.0
    stream_timeout_sec: float = 120.0
    log_level: str = "INFO"
    path_cache_ttl_sec: int = 3600
    file_info_cache_ttl_sec: int = 600
    slice_address_cache_ttl_sec: int = 120
    play_prefetch_concurrency: int = 3
    play_prefetch_queue_size: int = 3
    play_max_active_requests: int = 8
    play_admission_wait_ms: int = 800
    restore_create_max_concurrency: int = 2
    slice_global_download_limit: int = 24
    play_initial_addr_batch: int = 24
    play_disconnected_warn_grace_ms: int = 1500
    play_force_restore_before_stream: bool = True
    play_no_cid_strategy: str = "lookup_and_restore"
    play_compat_enabled: bool = True
    play_compat_user_agents: List[str] = field(default_factory=list)
    play_compat_user_agent_fingerprints: List[str] = field(default_factory=list)
    play_compat_auto_promote: bool = True
    play_compat_auto_promote_threshold: int = 3
    play_compat_window_sec: int = 20
    play_compat_ttl_sec: int = 1800
    play_compat_prefetch_concurrency: int = 6
    play_compat_prefetch_queue_size: int = 6
    play_compat_initial_addr_batch: int = 64
    play_compat_admission_wait_ms: int = 1500
    play_compat_range_relaxed: bool = True
    play_compat_initial_probe_max_bytes: int = 256 * 1024 * 1024
    play_compat_quick_disconnect_ms: int = 3000
    play_compat_quick_disconnect_max_bytes: int = 2 * 1024 * 1024
    play_compat_tail_probe_threshold_bytes: int = 256 * 1024
    play_compat_tail_probe_expand_bytes: int = 8 * 1024 * 1024
    play_mode: str = "proxy"
    play_webdav_enabled: bool = False
    play_webdav_base_url: str = ""
    play_webdav_username: str = ""
    play_webdav_password: str = ""
    play_webdav_cache_ttl_sec: int = 30
    play_redirect_status: int = 302
    play_redirect_scope: str = PLAY_REDIRECT_SCOPE_ALL
    emby_proxy_enabled: bool = False
    emby_server_url: str = ""
    emby_proxy_playback_cache_ttl_sec: int = 300
    emby_proxy_redirect_status: int = 307

    mappings: List[MappingConfig] = field(default_factory=list)
    retry: RetryConfig = field(default_factory=RetryConfig)
    credential_source: str = "config"

    @classmethod
    def from_dict(cls, raw: Dict[str, Any]) -> "AppConfig":
        """从字典构建 AppConfig（供管理 API 使用，逻辑与 from_json 一致）"""
        return cls._from_raw(raw)

    @classmethod
    def from_json(cls, path: str) -> "AppConfig":
        with open(path, "r", encoding="utf-8") as f:
            raw = json.load(f)
        return cls._from_raw(raw, config_path=path)

    @classmethod
    def _from_raw(cls, raw: Dict[str, Any], *, config_path: Optional[str] = None) -> "AppConfig":

        def _pick(d: Dict[str, Any], keys: List[str]) -> str:
            for k in keys:
                v = d.get(k)
                if v is None:
                    continue
                s = str(v).strip()
                if s:
                    return s
            return ""

        mappings_raw = raw.get("mappings", [])
        mappings: List[MappingConfig] = []
        for item in mappings_raw:
            if not isinstance(item, dict):
                continue
            mappings.append(
                MappingConfig(
                    remote=str(item.get("remote", "")).strip() or "/",
                    local=str(item.get("local", "")).strip() or "emby_strm",
                    enabled=bool(item.get("enabled", True)),
                    extras_mode=item.get("extras_mode"),
                    media_mode=item.get("media_mode"),
                    comment=str(item.get("comment", "")),
                )
            )

        retry_cfg = RetryConfig.from_dict(raw.get("retry", {}))
        compat_user_agents_raw = raw.get("play_compat_user_agents", [])
        compat_user_agents: List[str] = []
        if isinstance(compat_user_agents_raw, list):
            for item in compat_user_agents_raw:
                s = str(item).strip()
                if s:
                    compat_user_agents.append(s)
        compat_user_agent_fingerprints_raw = raw.get(
            "play_compat_user_agent_fingerprints", []
        )
        compat_user_agent_fingerprints: List[str] = []
        if isinstance(compat_user_agent_fingerprints_raw, list):
            for item in compat_user_agent_fingerprints_raw:
                s = str(item).strip().lower()
                if s:
                    compat_user_agent_fingerprints.append(s)

        client_id = _pick(raw, ["client_id", "clientId", "CLIENT_ID"])
        client_secret = _pick(raw, ["client_secret", "clientSecret", "CLIENT_SECRET"])
        credential_source = "config"
        used_non_config_source = False

        if not client_id:
            client_id = os.getenv("TWOLAND_CLIENT_ID", "").strip()
            if client_id:
                used_non_config_source = True
        if not client_secret:
            client_secret = os.getenv("TWOLAND_CLIENT_SECRET", "").strip()
            if client_secret:
                used_non_config_source = True

        if not client_id:
            client_id = LEGACY_DEFAULT_CLIENT_ID
            used_non_config_source = True
        if not client_secret:
            client_secret = LEGACY_DEFAULT_CLIENT_SECRET
            used_non_config_source = True

        if used_non_config_source:
            if os.getenv("TWOLAND_CLIENT_ID") or os.getenv("TWOLAND_CLIENT_SECRET"):
                credential_source = "env_or_legacy_fallback"
            else:
                credential_source = "legacy_fallback"

        proxy_url = _pick(raw, ["proxy_url", "proxyUrl", "PROXY_URL"])
        proxy_port = int(raw.get("proxy_port", 8899))
        if "proxy_port" not in raw and proxy_url:
            try:
                parsed = urllib.parse.urlparse(proxy_url)
                if parsed.port:
                    proxy_port = int(parsed.port)
            except Exception:
                pass

        public_strm_host = _pick(
            raw, ["public_strm_host", "publicStrmHost", "PUBLIC_STRM_HOST"]
        ).rstrip("/")
        if not public_strm_host and proxy_url:
            public_strm_host = proxy_url.rstrip("/")
        if not public_strm_host:
            public_strm_host = f"http://127.0.0.1:{proxy_port}"

        _play_webdav_enabled = bool(raw.get("play_webdav_enabled", False))
        _play_mode = str(raw.get("play_mode", "")).strip().lower()
        if not _play_mode:
            _play_mode = "hybrid" if _play_webdav_enabled else "proxy"
        if _play_mode not in {"proxy", "hybrid", "redirect"}:
            _play_mode = "proxy"
        _play_redirect_scope = str(
            raw.get("play_redirect_scope", PLAY_REDIRECT_SCOPE_ALL)
        ).strip().lower()
        if _play_redirect_scope not in {
            PLAY_REDIRECT_SCOPE_ALL,
            PLAY_REDIRECT_SCOPE_EMBY_ONLY,
        }:
            _play_redirect_scope = PLAY_REDIRECT_SCOPE_ALL

        cfg = cls(
            client_id=client_id,
            client_secret=client_secret,
            api_host=str(raw.get("api_host", "openapi.2dland.cn")).strip()
            or "openapi.2dland.cn",
            proxy_port=proxy_port,
            proxy_url=proxy_url or None,
            public_strm_host=public_strm_host,
            admin_port=int(raw.get("admin_port", 8898)),
            loop_interval=int(raw.get("loop_interval", 0)),
            min_video_size_mb=int(raw.get("min_video_size_mb", 80)),
            extras_mode=str(raw.get("extras_mode", "keep")),
            media_mode=str(raw.get("media_mode", "keep")),
            auto_confirm=bool(raw.get("auto_confirm", False)),
            output_dir=str(raw.get("output_dir", "emby_strm")),
            restore_dir=str(raw.get("restore_dir", "/Temp/AutoRestore")),
            restore_ttl_hours=float(raw.get("restore_ttl_hours", 3.0)),
            request_timeout_sec=float(raw.get("request_timeout_sec", 30.0)),
            stream_timeout_sec=float(raw.get("stream_timeout_sec", 120.0)),
            log_level=str(raw.get("log_level", "INFO")).upper(),
            path_cache_ttl_sec=int(raw.get("path_cache_ttl_sec", 3600)),
            file_info_cache_ttl_sec=int(raw.get("file_info_cache_ttl_sec", 600)),
            slice_address_cache_ttl_sec=int(
                raw.get("slice_address_cache_ttl_sec", 120)
            ),
            play_prefetch_concurrency=int(raw.get("play_prefetch_concurrency", 3)),
            play_prefetch_queue_size=int(raw.get("play_prefetch_queue_size", 3)),
            play_max_active_requests=int(raw.get("play_max_active_requests", 8)),
            play_admission_wait_ms=int(raw.get("play_admission_wait_ms", 800)),
            restore_create_max_concurrency=int(
                raw.get("restore_create_max_concurrency", 2)
            ),
            slice_global_download_limit=int(raw.get("slice_global_download_limit", 24)),
            play_initial_addr_batch=int(raw.get("play_initial_addr_batch", 24)),
            play_disconnected_warn_grace_ms=int(
                raw.get("play_disconnected_warn_grace_ms", 1500)
            ),
            play_force_restore_before_stream=bool(
                raw.get("play_force_restore_before_stream", True)
            ),
            play_no_cid_strategy=str(
                raw.get("play_no_cid_strategy", "lookup_and_restore")
            ).strip()
            or "lookup_and_restore",
            play_compat_enabled=bool(raw.get("play_compat_enabled", True)),
            play_compat_user_agents=compat_user_agents,
            play_compat_user_agent_fingerprints=compat_user_agent_fingerprints,
            play_compat_auto_promote=bool(raw.get("play_compat_auto_promote", True)),
            play_compat_auto_promote_threshold=int(
                raw.get("play_compat_auto_promote_threshold", 3)
            ),
            play_compat_window_sec=int(raw.get("play_compat_window_sec", 20)),
            play_compat_ttl_sec=int(raw.get("play_compat_ttl_sec", 1800)),
            play_compat_prefetch_concurrency=int(
                raw.get("play_compat_prefetch_concurrency", 6)
            ),
            play_compat_prefetch_queue_size=int(
                raw.get("play_compat_prefetch_queue_size", 6)
            ),
            play_compat_initial_addr_batch=int(
                raw.get("play_compat_initial_addr_batch", 64)
            ),
            play_compat_admission_wait_ms=int(
                raw.get("play_compat_admission_wait_ms", 1500)
            ),
            play_compat_range_relaxed=bool(raw.get("play_compat_range_relaxed", True)),
            play_compat_initial_probe_max_bytes=int(
                raw.get("play_compat_initial_probe_max_bytes", 256 * 1024 * 1024)
            ),
            play_compat_quick_disconnect_ms=int(
                raw.get("play_compat_quick_disconnect_ms", 3000)
            ),
            play_compat_quick_disconnect_max_bytes=int(
                raw.get("play_compat_quick_disconnect_max_bytes", 2 * 1024 * 1024)
            ),
            play_compat_tail_probe_threshold_bytes=int(
                raw.get("play_compat_tail_probe_threshold_bytes", 256 * 1024)
            ),
            play_compat_tail_probe_expand_bytes=int(
                raw.get("play_compat_tail_probe_expand_bytes", 8 * 1024 * 1024)
            ),
            play_mode=_play_mode,
            play_webdav_enabled=_play_webdav_enabled,
            play_webdav_base_url=str(
                raw.get("play_webdav_base_url", "")
            ).strip(),
            play_webdav_username=str(raw.get("play_webdav_username", "")).strip(),
            play_webdav_password=str(raw.get("play_webdav_password", "")).strip(),
            play_webdav_cache_ttl_sec=int(raw.get("play_webdav_cache_ttl_sec", 30)),
            play_redirect_status=int(raw.get("play_redirect_status", 302)),
            play_redirect_scope=_play_redirect_scope,
            emby_proxy_enabled=bool(raw.get("emby_proxy_enabled", False)),
            emby_server_url=str(raw.get("emby_server_url", "")).strip().rstrip("/"),
            emby_proxy_playback_cache_ttl_sec=int(
                raw.get("emby_proxy_playback_cache_ttl_sec", 300)
            ),
            emby_proxy_redirect_status=int(
                raw.get("emby_proxy_redirect_status", 307)
            ),
            mappings=mappings,
            retry=retry_cfg,
            credential_source=credential_source,
        )

        if not cfg.proxy_url:
            cfg.proxy_url = f"http://127.0.0.1:{cfg.proxy_port}"
        if not cfg.public_strm_host:
            cfg.public_strm_host = f"http://127.0.0.1:{cfg.proxy_port}"

        cfg.validate()
        return cfg

    def effective_play_mode(self) -> str:
        mode = str(self.play_mode or "").strip().lower()
        if mode in {"hybrid", "redirect"}:
            return mode
        if self.play_webdav_enabled:
            return "hybrid"
        return "proxy"

    def webdav_redirect_enabled(self) -> bool:
        return self.effective_play_mode() in {"hybrid", "redirect"}

    def validate(self) -> None:
        errors: List[str] = []
        if not self.client_id:
            errors.append("Missing required config: client_id")
        if not self.client_secret:
            errors.append("Missing required config: client_secret")
        if self.extras_mode not in {"keep", "download", "delete"}:
            errors.append("extras_mode must be one of: keep/download/delete")
        if self.media_mode not in {"keep", "delete"}:
            errors.append("media_mode must be one of: keep/delete")
        if self.min_video_size_mb < 0:
            errors.append("min_video_size_mb must be >= 0")
        if self.proxy_port <= 0:
            errors.append("proxy_port must be > 0")
        if self.admin_port < 0:
            errors.append("admin_port must be >= 0")
        if self.admin_port > 0 and self.admin_port == self.proxy_port:
            errors.append("admin_port must differ from proxy_port")
        if self.request_timeout_sec <= 0:
            errors.append("request_timeout_sec must be > 0")
        if self.stream_timeout_sec <= 0:
            errors.append("stream_timeout_sec must be > 0")
        if self.path_cache_ttl_sec <= 0:
            errors.append("path_cache_ttl_sec must be > 0")
        if self.file_info_cache_ttl_sec <= 0:
            errors.append("file_info_cache_ttl_sec must be > 0")
        if self.slice_address_cache_ttl_sec <= 0:
            errors.append("slice_address_cache_ttl_sec must be > 0")
        if self.play_prefetch_concurrency <= 0:
            errors.append("play_prefetch_concurrency must be > 0")
        if self.play_prefetch_queue_size <= 0:
            errors.append("play_prefetch_queue_size must be > 0")
        if self.play_prefetch_queue_size < self.play_prefetch_concurrency:
            errors.append(
                "play_prefetch_queue_size must be >= play_prefetch_concurrency"
            )
        if self.play_max_active_requests <= 0:
            errors.append("play_max_active_requests must be > 0")
        if self.play_admission_wait_ms < 0:
            errors.append("play_admission_wait_ms must be >= 0")
        if self.restore_create_max_concurrency <= 0:
            errors.append("restore_create_max_concurrency must be > 0")
        if self.slice_global_download_limit <= 0:
            errors.append("slice_global_download_limit must be > 0")
        if self.play_initial_addr_batch <= 0:
            errors.append("play_initial_addr_batch must be > 0")
        if self.play_disconnected_warn_grace_ms < 0:
            errors.append("play_disconnected_warn_grace_ms must be >= 0")
        if self.play_no_cid_strategy not in {"lookup_and_restore", "error"}:
            errors.append(
                "play_no_cid_strategy must be one of: lookup_and_restore/error"
            )
        if self.play_compat_auto_promote_threshold <= 0:
            errors.append("play_compat_auto_promote_threshold must be > 0")
        if self.play_compat_window_sec <= 0:
            errors.append("play_compat_window_sec must be > 0")
        if self.play_compat_ttl_sec <= 0:
            errors.append("play_compat_ttl_sec must be > 0")
        if self.play_compat_prefetch_concurrency <= 0:
            errors.append("play_compat_prefetch_concurrency must be > 0")
        if self.play_compat_prefetch_queue_size <= 0:
            errors.append("play_compat_prefetch_queue_size must be > 0")
        if self.play_compat_prefetch_queue_size < self.play_compat_prefetch_concurrency:
            errors.append(
                "play_compat_prefetch_queue_size must be >= play_compat_prefetch_concurrency"
            )
        if self.play_compat_initial_addr_batch <= 0:
            errors.append("play_compat_initial_addr_batch must be > 0")
        if self.play_compat_admission_wait_ms < 0:
            errors.append("play_compat_admission_wait_ms must be >= 0")
        if self.play_compat_initial_probe_max_bytes <= 0:
            errors.append("play_compat_initial_probe_max_bytes must be > 0")
        if self.play_compat_quick_disconnect_ms < 0:
            errors.append("play_compat_quick_disconnect_ms must be >= 0")
        if self.play_compat_quick_disconnect_max_bytes <= 0:
            errors.append("play_compat_quick_disconnect_max_bytes must be > 0")
        if self.play_compat_tail_probe_threshold_bytes <= 0:
            errors.append("play_compat_tail_probe_threshold_bytes must be > 0")
        if self.play_compat_tail_probe_expand_bytes <= 0:
            errors.append("play_compat_tail_probe_expand_bytes must be > 0")
        if (
            self.play_compat_tail_probe_expand_bytes
            < self.play_compat_tail_probe_threshold_bytes
        ):
            errors.append(
                "play_compat_tail_probe_expand_bytes must be >= play_compat_tail_probe_threshold_bytes"
            )
        if self.play_webdav_cache_ttl_sec <= 0:
            errors.append("play_webdav_cache_ttl_sec must be > 0")
        if self.play_redirect_status not in {302, 307}:
            errors.append("play_redirect_status must be one of: 302/307")
        if self.play_redirect_scope not in {
            PLAY_REDIRECT_SCOPE_ALL,
            PLAY_REDIRECT_SCOPE_EMBY_ONLY,
        }:
            errors.append("play_redirect_scope must be one of: all/emby_only")
        if self.emby_proxy_playback_cache_ttl_sec <= 0:
            errors.append("emby_proxy_playback_cache_ttl_sec must be > 0")
        if self.emby_proxy_redirect_status not in {302, 307}:
            errors.append("emby_proxy_redirect_status must be one of: 302/307")
        if self.play_mode not in {"proxy", "hybrid", "redirect"}:
            errors.append("play_mode must be one of: proxy/hybrid/redirect")
        if self.webdav_redirect_enabled():
            if not self.play_webdav_base_url:
                errors.append(
                    "play_webdav_base_url is required when play_mode is hybrid/redirect"
                )
            else:
                parsed_webdav = urllib.parse.urlparse(self.play_webdav_base_url)
                if parsed_webdav.scheme not in {"http", "https"} or not parsed_webdav.netloc:
                    errors.append(
                        "play_webdav_base_url must be a valid http/https URL"
                    )
            if not self.play_webdav_username:
                errors.append(
                    "play_webdav_username is required when play_mode is hybrid/redirect"
                )
            if not self.play_webdav_password:
                errors.append(
                    "play_webdav_password is required when play_mode is hybrid/redirect"
                )
        if self.emby_proxy_enabled:
            if not self.emby_server_url:
                errors.append(
                    "emby_server_url is required when emby_proxy_enabled=true"
                )
            else:
                parsed_emby = urllib.parse.urlparse(self.emby_server_url)
                if parsed_emby.scheme not in {"http", "https"} or not parsed_emby.netloc:
                    errors.append("emby_server_url must be a valid http/https URL")
        if self.retry.max_attempts < 1:
            errors.append("retry.max_attempts must be >= 1")

        for idx, m in enumerate(self.mappings):
            if m.extras_mode is not None and m.extras_mode not in {
                "keep",
                "download",
                "delete",
            }:
                errors.append(
                    f"mappings[{idx}].extras_mode must be keep/download/delete"
                )
            if m.media_mode is not None and m.media_mode not in {"keep", "delete"}:
                errors.append(f"mappings[{idx}].media_mode must be keep/delete")

        if errors:
            raise ConfigError("; ".join(errors))

    def apply_cli_overrides(self, args: argparse.Namespace) -> None:
        if getattr(args, "min_size", None) is not None:
            self.min_video_size_mb = int(args.min_size)

        if getattr(args, "extras", None):
            self.extras_mode = args.extras

        if getattr(args, "media", None):
            self.media_mode = args.media

        if getattr(args, "yes", False):
            self.auto_confirm = True

        if getattr(args, "loop", None) is not None:
            self.loop_interval = int(args.loop)

        if getattr(args, "public_strm_host", None):
            self.public_strm_host = str(args.public_strm_host).rstrip("/")

        if getattr(args, "proxy_port", None) is not None:
            self.proxy_port = int(args.proxy_port)
            self.proxy_url = f"http://127.0.0.1:{self.proxy_port}"

        if getattr(args, "admin_port", None) is not None:
            self.admin_port = int(args.admin_port)

        if getattr(args, "log_level", None):
            self.log_level = str(args.log_level).upper()

        if getattr(args, "root", None):
            self.mappings = [
                MappingConfig(
                    remote=str(args.root),
                    local=self.output_dir,
                    enabled=True,
                    extras_mode=None,
                    media_mode=self.media_mode,
                    comment="from --root override",
                )
            ]

        self.validate()


@dataclass
class FileSliceInfo:
    cids: List[str]
    size: int
    chunk_sizes: List[Dict[str, int]]
    content_identity: str


@dataclass
class SliceAddressInfo:
    identity: str
    url: str
    key: int
    expire_at: float


# ─── 持久化统计 ────────────────────────────────────────────────


class StatsStore:
    """轻量级持久化统计，JSON 文件落盘，启动加载、关闭保存、定时刷写。"""

    _DEFAULTS: Dict[str, int] = {
        "play_requests_total": 0,
        "play_rejected_total": 0,
        "play_bytes_sent": 0,
        "webdav_redirect_hits": 0,
        "webdav_redirect_misses": 0,
        "emby_redirect_total": 0,
        "emby_playback_cache_hits": 0,
        "emby_playback_cache_misses": 0,
        "scan_total": 0,
        "scan_strm_created": 0,
        "scan_strm_deleted": 0,
    }

    def __init__(self, path: str, flush_interval_sec: float = 60.0):
        self._path = Path(path)
        self._flush_interval = flush_interval_sec
        self._dirty = False
        self._lock = threading.Lock()
        self._data: Dict[str, int] = dict(self._DEFAULTS)
        self._load()
        self._flush_thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()

    def _load(self) -> None:
        if self._path.is_file():
            try:
                raw = json.loads(self._path.read_text(encoding="utf-8"))
                if isinstance(raw, dict):
                    for k in self._data:
                        if k in raw:
                            self._data[k] = int(raw[k])
            except Exception:
                pass  # 文件损坏则从零开始

    def _save(self) -> None:
        try:
            tmp = self._path.with_suffix(".tmp")
            tmp.write_text(
                json.dumps(self._data, indent=2) + "\n",
                encoding="utf-8",
            )
            tmp.replace(self._path)
        except Exception:
            pass

    def inc(self, key: str, delta: int = 1) -> None:
        with self._lock:
            self._data[key] = self._data.get(key, 0) + delta
            self._dirty = True

    def get(self, key: str) -> int:
        with self._lock:
            return self._data.get(key, 0)

    def snapshot(self) -> Dict[str, int]:
        with self._lock:
            return dict(self._data)

    def flush(self) -> None:
        with self._lock:
            if self._dirty:
                self._save()
                self._dirty = False

    def start_background_flush(self) -> None:
        if self._flush_thread is not None:
            return

        def _loop():
            while not self._stop_event.wait(self._flush_interval):
                self.flush()

        self._flush_thread = threading.Thread(target=_loop, daemon=True, name="stats-flush")
        self._flush_thread.start()

    def stop(self) -> None:
        self._stop_event.set()
        if self._flush_thread is not None:
            self._flush_thread.join(timeout=5)
            self._flush_thread = None
        self.flush()


@dataclass
class ScanRecord:
    """单次扫描的完整记录，用于持久化历史。"""
    task_id: str
    trigger: str  # "scheduled" | "manual"
    started_at: float  # time.time()
    finished_at: Optional[float] = None
    duration_sec: Optional[float] = None
    status: str = "running"  # "running" | "completed" | "error"
    error: Optional[str] = None
    scanned: int = 0
    created_strm: int = 0
    downloaded_extras: int = 0
    skipped_exists: int = 0
    skipped_small: int = 0
    deleted: int = 0
    errors: int = 0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "task_id": self.task_id,
            "trigger": self.trigger,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "duration_sec": self.duration_sec,
            "status": self.status,
            "error": self.error,
            "scanned": self.scanned,
            "created_strm": self.created_strm,
            "downloaded_extras": self.downloaded_extras,
            "skipped_exists": self.skipped_exists,
            "skipped_small": self.skipped_small,
            "deleted": self.deleted,
            "errors": self.errors,
        }

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ScanRecord":
        return cls(
            task_id=d.get("task_id", "unknown"),
            trigger=d.get("trigger", "unknown"),
            started_at=d.get("started_at", 0),
            finished_at=d.get("finished_at"),
            duration_sec=d.get("duration_sec"),
            status=d.get("status", "completed"),
            error=d.get("error"),
            scanned=d.get("scanned", 0),
            created_strm=d.get("created_strm", 0),
            downloaded_extras=d.get("downloaded_extras", 0),
            skipped_exists=d.get("skipped_exists", 0),
            skipped_small=d.get("skipped_small", 0),
            deleted=d.get("deleted", 0),
            errors=d.get("errors", 0),
        )


class ScanHistoryStore:
    """扫描历史持久化存储，JSON 文件落盘，保留最近 N 条记录。"""

    DEFAULT_MAX_RECORDS = 100

    def __init__(self, path: str, max_records: int = DEFAULT_MAX_RECORDS):
        self._path = Path(path)
        self._max_records = max_records
        self._lock = threading.Lock()
        self._records: List[ScanRecord] = []
        self._load()

    def _load(self) -> None:
        if self._path.is_file():
            try:
                raw = json.loads(self._path.read_text(encoding="utf-8"))
                if isinstance(raw, list):
                    self._records = [ScanRecord.from_dict(r) for r in raw if isinstance(r, dict)]
            except Exception:
                pass  # 文件损坏则从空开始

    def _save(self) -> None:
        try:
            tmp = self._path.with_suffix(".tmp")
            data = [r.to_dict() for r in self._records]
            tmp.write_text(
                json.dumps(data, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            tmp.replace(self._path)
        except Exception:
            pass

    def add(self, record: ScanRecord) -> None:
        with self._lock:
            self._records.append(record)
            # 保留最近 N 条
            if len(self._records) > self._max_records:
                self._records = self._records[-self._max_records:]
            self._save()

    def update(self, task_id: str, **kwargs: Any) -> None:
        """更新指定 task_id 的记录字段。"""
        with self._lock:
            for r in self._records:
                if r.task_id == task_id:
                    for k, v in kwargs.items():
                        if hasattr(r, k):
                            setattr(r, k, v)
                    self._save()
                    break

    def get_history(self, limit: int = 50, offset: int = 0) -> List[ScanRecord]:
        with self._lock:
            # 按时间倒序
            sorted_records = sorted(self._records, key=lambda r: r.started_at, reverse=True)
            return sorted_records[offset:offset + limit]

    def count(self) -> int:
        with self._lock:
            return len(self._records)

    def flush(self) -> None:
        with self._lock:
            self._save()


@dataclass
class RestoreEntry:
    content_identity: str
    original_path: str
    restore_path: str
    identity: str
    last_touch_ts: float
    expire_at_ts: float
    active_leases: int = 0


@dataclass
class PlayAdmissionResult:
    accepted: bool
    wait_ms: float
    active_requests: int
    rejected_total: int


@dataclass
class PlayProfileDecision:
    profile: str
    ua_fingerprint: str
    compat_promoted: bool
    reason: str


class PlayAdmissionController:
    def __init__(self, max_active_requests: int, wait_ms: int, *, stats: Optional[StatsStore] = None):
        self.max_active_requests = max(1, int(max_active_requests))
        self.wait_ms = max(0, int(wait_ms))
        self._semaphore = asyncio.Semaphore(self.max_active_requests)
        self._lock = asyncio.Lock()
        self._active_requests = 0
        self._rejected_total = 0
        self._stats = stats

    async def acquire(self, wait_ms: Optional[int] = None) -> PlayAdmissionResult:
        started = time.perf_counter()
        timeout_source = self.wait_ms if wait_ms is None else max(0, int(wait_ms))
        timeout_sec = timeout_source / 1000.0
        try:
            if timeout_sec <= 0:
                if self._semaphore.locked():
                    raise asyncio.TimeoutError()
                await self._semaphore.acquire()
            else:
                await asyncio.wait_for(self._semaphore.acquire(), timeout=timeout_sec)
        except asyncio.TimeoutError:
            async with self._lock:
                self._rejected_total += 1
                active = self._active_requests
                rejected_total = self._rejected_total
            if self._stats:
                self._stats.inc("play_rejected_total")
            return PlayAdmissionResult(
                accepted=False,
                wait_ms=round((time.perf_counter() - started) * 1000, 3),
                active_requests=active,
                rejected_total=rejected_total,
            )

        async with self._lock:
            self._active_requests += 1
            active = self._active_requests
            rejected_total = self._rejected_total

        if self._stats:
            self._stats.inc("play_requests_total")

        return PlayAdmissionResult(
            accepted=True,
            wait_ms=round((time.perf_counter() - started) * 1000, 3),
            active_requests=active,
            rejected_total=rejected_total,
        )

    async def release(self) -> int:
        self._semaphore.release()
        async with self._lock:
            self._active_requests = max(0, self._active_requests - 1)
            return self._active_requests


class PlayProfileResolver:
    PROFILE_STANDARD = "standard"
    PROFILE_COMPAT = "compat_ios_like"
    _MAX_TRACKED_UA = 4096

    def __init__(self, config: AppConfig):
        self.enabled = bool(config.play_compat_enabled)
        self.keywords = [
            x.strip().lower() for x in config.play_compat_user_agents if str(x).strip()
        ]
        self.fingerprints = {
            str(x).strip().lower()
            for x in config.play_compat_user_agent_fingerprints
            if str(x).strip()
        }
        self.auto_promote = bool(config.play_compat_auto_promote)
        self.threshold = max(1, int(config.play_compat_auto_promote_threshold))
        self.window_sec = max(1, int(config.play_compat_window_sec))
        self.ttl_sec = max(1, int(config.play_compat_ttl_sec))
        self.initial_probe_max_bytes = max(
            1, int(config.play_compat_initial_probe_max_bytes)
        )
        self.quick_disconnect_ms = max(0, int(config.play_compat_quick_disconnect_ms))
        self.quick_disconnect_max_bytes = max(
            1, int(config.play_compat_quick_disconnect_max_bytes)
        )

        self._lock = asyncio.Lock()
        self._recent_failures: Dict[str, List[float]] = {}
        self._promoted_until: Dict[str, float] = {}

    @staticmethod
    def normalize_user_agent(user_agent: Optional[str]) -> str:
        return str(user_agent or "").strip().lower()

    @staticmethod
    def fingerprint(user_agent: Optional[str]) -> str:
        normalized = PlayProfileResolver.normalize_user_agent(user_agent)
        if not normalized:
            return "none"
        return hashlib.sha1(normalized.encode("utf-8")).hexdigest()[:12]

    def _keyword_match(self, normalized_ua: str) -> bool:
        if not normalized_ua or not self.keywords:
            return False
        return any(keyword in normalized_ua for keyword in self.keywords)

    async def resolve(self, user_agent: Optional[str]) -> PlayProfileDecision:
        normalized_ua = self.normalize_user_agent(user_agent)
        ua_fingerprint = self.fingerprint(normalized_ua)
        if not self.enabled:
            return PlayProfileDecision(
                profile=self.PROFILE_STANDARD,
                ua_fingerprint=ua_fingerprint,
                compat_promoted=False,
                reason="standard",
            )

        if ua_fingerprint in self.fingerprints:
            return PlayProfileDecision(
                profile=self.PROFILE_COMPAT,
                ua_fingerprint=ua_fingerprint,
                compat_promoted=False,
                reason="fingerprint",
            )

        if self._keyword_match(normalized_ua):
            return PlayProfileDecision(
                profile=self.PROFILE_COMPAT,
                ua_fingerprint=ua_fingerprint,
                compat_promoted=False,
                reason="keyword",
            )

        now = time.time()
        async with self._lock:
            promoted_until = self._promoted_until.get(ua_fingerprint, 0.0)
            if promoted_until > now:
                return PlayProfileDecision(
                    profile=self.PROFILE_COMPAT,
                    ua_fingerprint=ua_fingerprint,
                    compat_promoted=True,
                    reason="auto_promoted",
                )
            if ua_fingerprint in self._promoted_until:
                self._promoted_until.pop(ua_fingerprint, None)

        return PlayProfileDecision(
            profile=self.PROFILE_STANDARD,
            ua_fingerprint=ua_fingerprint,
            compat_promoted=False,
            reason="standard",
        )

    async def promote_user_agent(
        self, user_agent: Optional[str], *, ttl_sec: Optional[float] = None
    ) -> bool:
        if not self.enabled:
            return False
        normalized_ua = self.normalize_user_agent(user_agent)
        if not normalized_ua:
            return False
        ua_fingerprint = self.fingerprint(normalized_ua)
        if ua_fingerprint == "none":
            return False

        now = time.time()
        promote_ttl = float(self.ttl_sec if ttl_sec is None else ttl_sec)
        async with self._lock:
            self._prune(now, now - float(self.window_sec))
            self._promoted_until[ua_fingerprint] = now + max(1.0, promote_ttl)
            self._recent_failures.pop(ua_fingerprint, None)
        return True

    async def observe_probe(
        self,
        *,
        user_agent: Optional[str],
        disconnect_before_first_byte: bool,
        first_byte_ms: Any,
        admission_rejected: bool,
        disconnected: bool = False,
        request_elapsed_ms: float = 0.0,
        range_start: int = 0,
        range_max_bytes: Optional[int] = None,
        bytes_sent: int = 0,
    ) -> bool:
        if not self.enabled or not self.auto_promote:
            return False
        if admission_rejected:
            return False
        range_probe_like = bool(
            disconnected
            and range_start == 0
            and range_max_bytes is not None
            and 0 < int(range_max_bytes) <= 8 * 1024 * 1024
            and request_elapsed_ms <= 120000
        )
        quick_disconnect_probe = bool(
            disconnected
            and first_byte_ms is not None
            and range_start == 0
            and 0 <= request_elapsed_ms <= float(self.quick_disconnect_ms)
            and 0 <= int(bytes_sent) <= int(self.quick_disconnect_max_bytes)
        )
        if not (
            disconnect_before_first_byte
            or first_byte_ms is None
            or range_probe_like
            or quick_disconnect_probe
        ):
            return False

        normalized_ua = self.normalize_user_agent(user_agent)
        if not normalized_ua:
            return False
        ua_fingerprint = self.fingerprint(normalized_ua)
        if self._keyword_match(normalized_ua) or ua_fingerprint in self.fingerprints:
            return False

        now = time.time()
        cutoff = now - float(self.window_sec)

        async with self._lock:
            self._prune(now, cutoff)
            if range_probe_like and request_elapsed_ms >= 5000:
                self._promoted_until[ua_fingerprint] = now + float(self.ttl_sec)
                self._recent_failures.pop(ua_fingerprint, None)
                return True
            history = self._recent_failures.get(ua_fingerprint, [])
            history = [ts for ts in history if ts >= cutoff]
            history.append(now)
            self._recent_failures[ua_fingerprint] = history
            if len(history) >= self.threshold:
                self._promoted_until[ua_fingerprint] = now + float(self.ttl_sec)
                self._recent_failures.pop(ua_fingerprint, None)
                return True
        return False

    def _prune(self, now: float, cutoff: float) -> None:
        for fp in list(self._promoted_until.keys()):
            if self._promoted_until.get(fp, 0.0) <= now:
                self._promoted_until.pop(fp, None)
        for fp in list(self._recent_failures.keys()):
            samples = [ts for ts in self._recent_failures[fp] if ts >= cutoff]
            if samples:
                self._recent_failures[fp] = samples
            else:
                self._recent_failures.pop(fp, None)

        # Keep upper bound in pathological probe storms.
        if len(self._recent_failures) > self._MAX_TRACKED_UA:
            over = len(self._recent_failures) - self._MAX_TRACKED_UA
            for fp in list(self._recent_failures.keys())[:over]:
                self._recent_failures.pop(fp, None)


@dataclass
class ScanStats:
    scanned: int = 0
    created_strm: int = 0
    downloaded_extras: int = 0
    skipped_exists: int = 0
    skipped_small: int = 0
    deleted: int = 0
    errors: int = 0


@dataclass
class DeleteCandidate:
    path: str
    identity: str


@dataclass
class MediaHandleResult:
    processed: bool
    delete_after_scan: bool = False


class FileInfoCache:
    def __init__(self, ttl_seconds: int = 600, max_entries: int = 4096):
        self.ttl_seconds = ttl_seconds
        self.max_entries = max_entries
        self._store: "OrderedDict[str, Tuple[FileSliceInfo, float]]" = OrderedDict()

    def get(self, identity: str) -> Optional[FileSliceInfo]:
        item = self._store.get(identity)
        if not item:
            return None
        value, expire_at = item
        if expire_at <= time.time():
            self._store.pop(identity, None)
            return None
        self._store.move_to_end(identity)
        return value

    def set(self, identity: str, info: FileSliceInfo) -> None:
        expire_at = time.time() + self.ttl_seconds
        self._store[identity] = (info, expire_at)
        self._store.move_to_end(identity)
        self._evict()

    def invalidate(self, identity: str) -> None:
        self._store.pop(identity, None)

    def _evict(self) -> None:
        while len(self._store) > self.max_entries:
            self._store.popitem(last=False)


class SliceAddressCache:
    def __init__(self, ttl_seconds: int = 120, max_entries: int = 20000):
        self.ttl_seconds = ttl_seconds
        self.max_entries = max_entries
        self._store: "OrderedDict[str, SliceAddressInfo]" = OrderedDict()

    def get(self, identity: str) -> Optional[SliceAddressInfo]:
        info = self._store.get(identity)
        if not info:
            return None
        if info.expire_at <= time.time():
            self._store.pop(identity, None)
            return None
        self._store.move_to_end(identity)
        return info

    def set_many(
        self, addresses: List[Dict[str, Any]], expire_at_ms: Optional[str]
    ) -> None:
        now = time.time()
        local_expire = now + self.ttl_seconds
        upstream_expire = None
        try:
            if expire_at_ms:
                upstream_expire = int(expire_at_ms) / 1000.0
        except Exception:
            upstream_expire = None

        for item in addresses:
            if not isinstance(item, dict):
                continue
            identity = str(item.get("identity", "")).strip()
            url = str(
                item.get("download_address")
                or item.get("address")
                or item.get("url")
                or ""
            ).strip()
            if not identity or not url:
                continue
            key = int(item.get("encrypt", 0) or 0)
            expire_at = local_expire
            if upstream_expire:
                expire_at = min(expire_at, upstream_expire - 2.0)
            if expire_at <= now:
                continue
            self._store[identity] = SliceAddressInfo(
                identity=identity,
                url=url,
                key=key,
                expire_at=expire_at,
            )
            self._store.move_to_end(identity)

        self._evict()

    def invalidate(self, identity: str) -> None:
        self._store.pop(identity, None)

    def _evict(self) -> None:
        while len(self._store) > self.max_entries:
            self._store.popitem(last=False)


class WebDavRedirectCache:
    def __init__(self, ttl_seconds: int = 30, max_entries: int = 4096):
        self.ttl_seconds = ttl_seconds
        self.max_entries = max_entries
        self._store: "OrderedDict[str, Tuple[str, float]]" = OrderedDict()

    def get(self, target_path: str) -> Optional[str]:
        item = self._store.get(target_path)
        if not item:
            return None
        url, expire_at = item
        if expire_at <= time.time():
            self._store.pop(target_path, None)
            return None
        self._store.move_to_end(target_path)
        return url

    def set(self, target_path: str, redirect_url: str) -> None:
        expire_at = time.time() + self.ttl_seconds
        self._store[target_path] = (redirect_url, expire_at)
        self._store.move_to_end(target_path)
        self._evict()

    def invalidate(self, target_path: str) -> None:
        self._store.pop(target_path, None)

    def _evict(self) -> None:
        while len(self._store) > self.max_entries:
            self._store.popitem(last=False)


@dataclass(frozen=True)
class WebDavRedirectProbeOutcome:
    location: Optional[str]
    miss_reason: str = ""
    status_code: int = 0
    probe_method: str = ""


@dataclass(frozen=True)
class EmbyPlaybackRedirectInfo:
    item_id: str
    media_source_id: str
    play_target: str
    expire_at: float


class EmbyPlaybackInfoCache:
    def __init__(
        self,
        ttl_seconds: int = 300,
        max_entries: int = 4096,
        *,
        time_provider: Any = None,
    ):
        self.ttl_seconds = ttl_seconds
        self.max_entries = max_entries
        self._time = time_provider or time.time
        self._store: "OrderedDict[str, EmbyPlaybackRedirectInfo]" = OrderedDict()

    @staticmethod
    def _key(item_id: str, media_source_id: str) -> str:
        return f"{item_id}:{media_source_id}"

    def get(self, item_id: str, media_source_id: str) -> Optional[str]:
        key = self._key(item_id, media_source_id)
        info = self._store.get(key)
        if not info:
            return None
        if info.expire_at <= self._time():
            self._store.pop(key, None)
            return None
        self._store.move_to_end(key)
        return info.play_target

    def set(self, item_id: str, media_source_id: str, play_target: str) -> None:
        key = self._key(item_id, media_source_id)
        info = EmbyPlaybackRedirectInfo(
            item_id=str(item_id).strip(),
            media_source_id=str(media_source_id).strip(),
            play_target=str(play_target).strip(),
            expire_at=self._time() + self.ttl_seconds,
        )
        self._store[key] = info
        self._store.move_to_end(key)
        self._evict()

    def invalidate(self, item_id: str, media_source_id: str) -> None:
        self._store.pop(self._key(item_id, media_source_id), None)

    def _evict(self) -> None:
        while len(self._store) > self.max_entries:
            self._store.popitem(last=False)


def setup_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    # 挂载管理面板日志 handler
    root_logger = logging.getLogger()
    if not any(isinstance(h, _AdminLogHandler) for h in root_logger.handlers):
        root_logger.addHandler(_AdminLogHandler())


def log_json(logger: logging.Logger, level: int, event: str, **kwargs: Any) -> None:
    payload = {"event": event, **kwargs}
    logger.log(level, json.dumps(payload, ensure_ascii=False, sort_keys=True))


def encode_path_b64(path: str) -> str:
    return base64.urlsafe_b64encode(path.encode("utf-8")).decode("utf-8")


def decode_path_b64(path_b64: str) -> str:
    padded = path_b64 + "=" * (-len(path_b64) % 4)
    return base64.urlsafe_b64decode(padded.encode("utf-8")).decode("utf-8")


def ensure_dir(path: str) -> None:
    Path(path).mkdir(parents=True, exist_ok=True)


def normalize_remote_path(path: str) -> str:
    if not path:
        return "/"
    if not path.startswith("/"):
        path = "/" + path
    path = path.rstrip("/")
    return path if path else "/"


def ext_of(filename: str) -> str:
    if "." not in filename:
        return ""
    return filename.rsplit(".", 1)[-1].lower()


def media_type_for_path(path: str) -> str:
    ext = ext_of(path)
    mapping = {
        "mp4": "video/mp4",
        "m4v": "video/mp4",
        "mkv": "video/x-matroska",
        "webm": "video/webm",
        "mov": "video/quicktime",
        "avi": "video/x-msvideo",
        "wmv": "video/x-ms-wmv",
        "flv": "video/x-flv",
        "ts": "video/mp2t",
        "m2ts": "video/mp2t",
        "mts": "video/mp2t",
        "m2t": "video/mp2t",
        "tp": "video/mp2t",
        "trp": "video/mp2t",
        "mpg": "video/mpeg",
        "mpeg": "video/mpeg",
        "mp3": "audio/mpeg",
        "m4a": "audio/mp4",
        "flac": "audio/flac",
        "wav": "audio/wav",
        "aac": "audio/aac",
        "ogg": "audio/ogg",
        "opus": "audio/opus",
        "wma": "audio/x-ms-wma",
        "ape": "audio/ape",
    }
    return mapping.get(ext, "application/octet-stream")


def build_play_url(public_host: str, remote_path: str, content_identity: str) -> str:
    encoded_path = encode_path_b64(remote_path)
    quoted_cid = urllib.parse.quote(content_identity)
    return (
        f"{public_host.rstrip('/')}/play/{encoded_path}?content_identity={quoted_cid}"
    )


def append_query_params(url: str, params: Dict[str, str]) -> str:
    value = str(url or "").strip()
    if not value or not params:
        return value
    parsed = urllib.parse.urlparse(value)
    merged = dict(urllib.parse.parse_qsl(parsed.query, keep_blank_values=True))
    for key, raw_value in params.items():
        text = str(key or "").strip()
        if not text:
            continue
        merged[text] = str(raw_value)
    encoded_query = urllib.parse.urlencode(merged)
    return urllib.parse.urlunparse(parsed._replace(query=encoded_query))


def sync_strm_file(strm_path: str, play_url: str) -> str:
    """Returns: created | updated | unchanged"""
    path = Path(strm_path)
    if path.exists():
        existing = path.read_text(encoding="utf-8").strip()
        if existing == play_url:
            return "unchanged"
        path.write_text(play_url, encoding="utf-8")
        return "updated"

    path.write_text(play_url, encoding="utf-8")
    return "created"


class AuthSigner:
    def __init__(
        self,
        client_id: str,
        client_secret: str,
        api_host: str,
        now_provider: Optional[Any] = None,
        nonce_provider: Optional[Any] = None,
    ):
        self.client_id = client_id
        self.client_secret = client_secret
        self.api_host = api_host
        self._now_provider = now_provider
        self._nonce_provider = nonce_provider

    @staticmethod
    def _sign(key: bytes, msg: str) -> bytes:
        return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()

    def _signature_key(self, secret: str, date_stamp: str, access_token: str) -> bytes:
        k_date = self._sign(("HL6" + secret).encode("utf-8"), date_stamp)
        k_ak = self._sign(k_date, access_token)
        return self._sign(k_ak, "hl6_request")

    def create_auth_headers(
        self, method: str, uri: str, payload: str
    ) -> Dict[str, str]:
        now = self._now_provider() if self._now_provider else dt.datetime.utcnow()
        timestamp = int(now.timestamp())
        date_stamp = now.strftime("%Y%m%d")
        nonce = self._nonce_provider() if self._nonce_provider else str(uuid.uuid4())

        headers_to_sign = {
            "host": self.api_host,
            "x-hl-nonce": nonce,
            "x-hl-timestamp": str(timestamp),
        }
        sorted_keys = sorted(headers_to_sign.keys())
        canonical_headers = "".join(f"{k}:{headers_to_sign[k]}\n" for k in sorted_keys)
        signed_headers = ";".join(sorted_keys)

        payload_hash = hashlib.sha256(payload.encode("utf-8")).hexdigest().lower()
        canonical_request = (
            f"{method}\n{uri}\n\n{canonical_headers}\n{signed_headers}\n{payload_hash}"
        )

        credential_scope = f"{date_stamp}/{self.client_id}/hl6_request"
        hashed_req = (
            hashlib.sha256(canonical_request.encode("utf-8")).hexdigest().lower()
        )
        string_to_sign = (
            f"HL6-HMAC-SHA256\n{timestamp}\n{credential_scope}\n{hashed_req}"
        )

        signing_key = self._signature_key(
            self.client_secret, date_stamp, self.client_id
        )
        signature = (
            hmac.new(signing_key, string_to_sign.encode("utf-8"), hashlib.sha256)
            .hexdigest()
            .lower()
        )

        auth_header = (
            f"HL6-HMAC-SHA256 Credential={self.client_id}/{credential_scope}, "
            f"SignedHeaders={signed_headers}, Signature={signature}"
        )

        return {
            "Host": self.api_host,
            "x-hl-nonce": nonce,
            "x-hl-timestamp": str(timestamp),
            "Authorization": auth_header,
            "Content-Type": "application/json",
        }


class PathResolverCache:
    def __init__(self, ttl_seconds: int = 3600):
        self.ttl_seconds = ttl_seconds
        self._store: Dict[str, Tuple[str, float]] = {
            "": ("", float("inf")),
            "/": ("", float("inf")),
        }

    def get(self, path: str) -> Optional[str]:
        key = normalize_remote_path(path)
        item = self._store.get(key)
        if not item:
            return None

        identity, expires_at = item
        if expires_at < time.time():
            self._store.pop(key, None)
            return None
        return identity

    def set(self, path: str, identity: str) -> None:
        key = normalize_remote_path(path)
        self._store[key] = (identity, time.time() + self.ttl_seconds)

    def invalidate(self, path: str) -> None:
        key = normalize_remote_path(path)
        self._store.pop(key, None)

    def invalidate_tree(self, path: str) -> None:
        key = normalize_remote_path(path)
        for cached_path in list(self._store.keys()):
            if cached_path == key or cached_path.startswith(key + "/"):
                self._store.pop(cached_path, None)


class TwoLandApiClient:
    MAX_SLICE_READ_TIMEOUT_SEC = 20.0

    def __init__(
        self,
        config: AppConfig,
        signer: AuthSigner,
        *,
        file_info_cache: Optional[FileInfoCache] = None,
        slice_addr_cache: Optional[SliceAddressCache] = None,
    ):
        self.config = config
        self.signer = signer
        self.file_info_cache = file_info_cache
        self.slice_addr_cache = slice_addr_cache
        self.logger = logging.getLogger("twoland.api")
        self._download_client_lock = asyncio.Lock()
        timeout = httpx.Timeout(config.request_timeout_sec)
        self.http_client = httpx.AsyncClient(timeout=timeout)
        self.download_client = self._build_download_client()

    def _build_download_client(self) -> httpx.AsyncClient:
        slice_read_timeout = min(
            float(self.config.stream_timeout_sec), self.MAX_SLICE_READ_TIMEOUT_SEC
        )
        return httpx.AsyncClient(
            timeout=httpx.Timeout(
                connect=self.config.request_timeout_sec,
                read=slice_read_timeout,
                write=self.config.request_timeout_sec,
                pool=self.config.request_timeout_sec,
            ),
            follow_redirects=True,
        )

    async def close(self) -> None:
        await self.http_client.aclose()
        await self.download_client.aclose()

    async def reset_download_client(self, *, reason: str) -> None:
        async with self._download_client_lock:
            old_client = self.download_client
            self.download_client = self._build_download_client()
        log_json(self.logger, logging.WARNING, "download_client_reset", reason=reason)
        try:
            await old_client.aclose()
        except Exception as exc:
            log_json(
                self.logger,
                logging.WARNING,
                "download_client_reset_close_failed",
                reason=reason,
                error=str(exc),
            )

    async def post(self, uri: str, payload_data: Dict[str, Any]) -> Dict[str, Any]:
        url = f"https://{self.config.api_host}{uri}"
        payload = json.dumps(payload_data, separators=(",", ":"), ensure_ascii=False)

        max_attempts = self.config.retry.max_attempts
        for attempt in range(1, max_attempts + 1):
            headers = self.signer.create_auth_headers("POST", uri, payload)
            try:
                resp = await self.http_client.post(
                    url, headers=headers, content=payload
                )
            except (httpx.TimeoutException, httpx.TransportError) as exc:
                if attempt < max_attempts:
                    await asyncio.sleep(self._next_backoff(attempt))
                    continue
                raise ApiError(f"request failed after retries: {exc}", uri=uri) from exc

            if resp.status_code == 200:
                try:
                    return resp.json()
                except Exception as exc:
                    raise ApiError(
                        "invalid upstream json", uri=uri, status_code=200
                    ) from exc

            if (
                resp.status_code in self.config.retry.retry_statuses
                and attempt < max_attempts
            ):
                log_json(
                    self.logger,
                    logging.WARNING,
                    "api_retry",
                    uri=uri,
                    attempt=attempt,
                    status=resp.status_code,
                )
                await asyncio.sleep(self._next_backoff(attempt))
                continue

            msg = resp.text[:500]
            raise ApiError(msg, uri=uri, status_code=resp.status_code)

        raise ApiError("unreachable retry state", uri=uri)

    def _next_backoff(self, attempt: int) -> float:
        sec = self.config.retry.backoff_base_sec * (2 ** (attempt - 1))
        return min(sec, self.config.retry.backoff_max_sec)

    async def list_files_by_id(self, identity: str) -> List[Dict[str, Any]]:
        uri = "/v6/userfile/list"
        all_files: List[Dict[str, Any]] = []
        token = ""

        while True:
            payload = {
                "parent": {"identity": identity},
                "list_info": {"limit": "100"},
            }
            if token:
                payload["list_info"]["token"] = token

            data = await self.post(uri, payload)
            page = data.get("files", [])
            if not page:
                break

            all_files.extend(page)
            next_token = data.get("list_info", {}).get("token", "")
            if not next_token or next_token == token:
                break
            token = next_token

        return all_files

    async def resolve_path(self, path: str, cache: PathResolverCache) -> str:
        normalized = normalize_remote_path(path)
        cached = cache.get(normalized)
        if cached is not None:
            return cached

        if normalized == "/":
            return ""

        parts = [p for p in normalized.split("/") if p]
        current_path = ""
        current_identity = ""

        for part in parts:
            next_path = f"{current_path}/{part}" if current_path else f"/{part}"
            cached_next = cache.get(next_path)
            if cached_next is not None:
                current_identity = cached_next
                current_path = next_path
                continue

            children = await self.list_files_by_id(current_identity)
            found = None
            for child in children:
                if child.get("name") == part:
                    found = child
                    break

            if not found:
                raise PathNotFoundError(f"path component not found: {next_path}")

            current_identity = str(found.get("identity", ""))
            current_path = next_path
            cache.set(current_path, current_identity)

        return current_identity

    async def get_file_info(self, file_identity: str) -> FileSliceInfo:
        info, _ = await self.get_file_info_with_meta(file_identity)
        return info

    async def get_file_info_with_meta(
        self, file_identity: str
    ) -> Tuple[FileSliceInfo, Dict[str, Any]]:
        start = time.perf_counter()
        if self.file_info_cache:
            cached = self.file_info_cache.get(file_identity)
            if cached:
                return cached, {
                    "cache_hit": True,
                    "file_info_ms": round((time.perf_counter() - start) * 1000, 3),
                }

        uri = "/v6/userfile/parse_file_slice"
        data = await self.post(uri, {"identity": file_identity})

        cids: List[str] = []
        if isinstance(data.get("slices"), list):
            cids = [
                str(x.get("identity"))
                for x in data["slices"]
                if isinstance(x, dict) and x.get("identity")
            ]
        elif isinstance(data.get("raw_nodes"), list):
            cids = [str(x) for x in data["raw_nodes"] if x]

        size_raw = data.get("size", data.get("file_size", 0))
        try:
            size = int(size_raw)
        except Exception:
            size = 0

        chunk_sizes: List[Dict[str, int]] = []
        for item in data.get("sizes", []) or []:
            try:
                chunk_sizes.append(
                    {
                        "end_index": int(item.get("end_index", 0)),
                        "size": int(item.get("size", 0)),
                    }
                )
            except Exception:
                continue

        info = FileSliceInfo(
            cids=cids,
            size=size,
            chunk_sizes=chunk_sizes,
            content_identity=str(data.get("content_identity", "")),
        )
        if self.file_info_cache:
            self.file_info_cache.set(file_identity, info)
        return info, {
            "cache_hit": False,
            "file_info_ms": round((time.perf_counter() - start) * 1000, 3),
        }

    async def delete_item(self, *, identity: str, file_path: str) -> Dict[str, Any]:
        payload = {"source": [{"identity": identity, "path": file_path}]}
        return await self.post("/v6/userfile/delete", payload)

    async def restore_file_by_content_identity(
        self, *, path: str, content_identity: str
    ) -> Dict[str, Any]:
        payload = {"path": path, "content_identity": content_identity}
        return await self.post("/v6/userfile/create", payload)

    async def get_slice_download_address(
        self,
        identities: List[str],
        *,
        force_refresh: bool = False,
    ) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
        if not identities:
            return [], {"cache_hit_count": 0, "addr_batch_ms": 0.0}

        start = time.perf_counter()
        hit_count = 0
        resolved: Dict[str, SliceAddressInfo] = {}
        missing: List[str] = []

        if not force_refresh and self.slice_addr_cache:
            for identity in identities:
                cached = self.slice_addr_cache.get(identity)
                if cached:
                    hit_count += 1
                    resolved[identity] = cached
                else:
                    missing.append(identity)
        else:
            missing = list(identities)

        if missing:
            data = await self.post(
                "/v6/userfile/get_slice_download_address",
                {"identity": missing, "version": 1},
            )
            addresses = data.get("addresses", [])
            if not isinstance(addresses, list):
                addresses = []
            if self.slice_addr_cache:
                self.slice_addr_cache.set_many(addresses, data.get("expire_at"))
                for identity in missing:
                    cached = self.slice_addr_cache.get(identity)
                    if cached:
                        resolved[identity] = cached
            else:
                for item in addresses:
                    if not isinstance(item, dict):
                        continue
                    identity = str(item.get("identity", "")).strip()
                    url = str(
                        item.get("download_address")
                        or item.get("address")
                        or item.get("url")
                        or ""
                    ).strip()
                    if not identity or not url:
                        continue
                    resolved[identity] = SliceAddressInfo(
                        identity=identity,
                        url=url,
                        key=int(item.get("encrypt", 0) or 0),
                        expire_at=time.time() + 60.0,
                    )

        ordered: List[Dict[str, Any]] = []
        for identity in identities:
            info = resolved.get(identity)
            if not info:
                continue
            ordered.append({"identity": identity, "url": info.url, "key": info.key})

        return ordered, {
            "cache_hit_count": hit_count,
            "addr_batch_ms": round((time.perf_counter() - start) * 1000, 3),
        }

    def invalidate_slice_address(self, identity: str) -> None:
        if self.slice_addr_cache:
            self.slice_addr_cache.invalidate(identity)


class SliceStreamer:
    _EVENT_CHUNK = "chunk"
    _EVENT_DONE = "done"
    _EVENT_ERROR = "error"
    CANCEL_WAIT_TIMEOUT_SEC = 3.0
    QUEUE_PUT_TIMEOUT_SEC = 2.0

    def __init__(
        self,
        api_client: TwoLandApiClient,
        *,
        prefetch_concurrency: int = 3,
        prefetch_queue_size: int = 3,
        global_download_limit: int = 24,
        initial_addr_batch: int = 24,
        global_download_semaphore: Optional[asyncio.Semaphore] = None,
    ):
        self.api_client = api_client
        self.prefetch_concurrency = prefetch_concurrency
        self.prefetch_queue_size = prefetch_queue_size
        self.initial_addr_batch = max(1, int(initial_addr_batch))
        self.slice_chunk_queue_size = 8
        # Cross-request guardrail to avoid exhausting upstream connections
        # when clients rapidly seek/switch videos.
        # Keep this reasonably high; too small can starve first-byte delivery
        # when one client opens many probe/play requests in parallel.
        global_limit = max(1, int(global_download_limit))
        self.global_download_semaphore = global_download_semaphore or asyncio.Semaphore(
            global_limit
        )
        self.logger = logging.getLogger("twoland.stream")

    async def iter_slices(
        self,
        cids: List[str],
        *,
        skip_bytes: int = 0,
        max_bytes: Optional[int] = None,
        metrics: Optional[Dict[str, Any]] = None,
    ) -> AsyncGenerator[bytes, None]:
        remaining_skip = max(0, skip_bytes)
        remaining_out = max_bytes
        stream_start = time.perf_counter()

        if metrics is not None:
            metrics.setdefault("addr_batch_ms", 0.0)
            metrics.setdefault("cache_hit_slice_addr", 0)
            metrics.setdefault("slice_addr_total", 0)
            metrics.setdefault("first_byte_ms", None)
            metrics.setdefault("prefetch_inflight", 0)

        batch_size = self.initial_addr_batch
        for idx in range(0, len(cids), batch_size):
            batch = cids[idx : idx + batch_size]
            ordered, batch_meta = await self.api_client.get_slice_download_address(
                batch
            )
            if len(ordered) != len(batch):
                known = {x.get("identity") for x in ordered}
                missing = [cid for cid in batch if cid not in known]
                if missing:
                    (
                        refreshed,
                        refresh_meta,
                    ) = await self.api_client.get_slice_download_address(
                        missing, force_refresh=True
                    )
                    ordered.extend(refreshed)
                    batch_meta["cache_hit_count"] += int(
                        refresh_meta.get("cache_hit_count", 0)
                    )
                    batch_meta["addr_batch_ms"] += float(
                        refresh_meta.get("addr_batch_ms", 0.0)
                    )
                if len(ordered) != len(batch):
                    raise HTTPException(status_code=502, detail="slice address missing")

            if metrics is not None:
                metrics["addr_batch_ms"] += float(batch_meta.get("addr_batch_ms", 0.0))
                metrics["cache_hit_slice_addr"] += int(
                    batch_meta.get("cache_hit_count", 0)
                )
                metrics["slice_addr_total"] += len(batch)

            async for slice_bytes in self._iter_prefetched_batch(ordered, metrics):
                if remaining_skip:
                    if len(slice_bytes) <= remaining_skip:
                        remaining_skip -= len(slice_bytes)
                        continue
                    slice_bytes = slice_bytes[remaining_skip:]
                    remaining_skip = 0

                if remaining_out is not None:
                    if remaining_out <= 0:
                        return
                    if len(slice_bytes) > remaining_out:
                        if metrics is not None and metrics.get("first_byte_ms") is None:
                            metrics["first_byte_ms"] = round(
                                (time.perf_counter() - stream_start) * 1000, 3
                            )
                        yield slice_bytes[:remaining_out]
                        return
                    remaining_out -= len(slice_bytes)

                if slice_bytes:
                    if metrics is not None and metrics.get("first_byte_ms") is None:
                        metrics["first_byte_ms"] = round(
                            (time.perf_counter() - stream_start) * 1000, 3
                        )
                    yield slice_bytes

    async def _iter_prefetched_batch(
        self,
        ordered_addrs: List[Dict[str, Any]],
        metrics: Optional[Dict[str, Any]],
    ) -> AsyncGenerator[bytes, None]:
        semaphore = asyncio.Semaphore(max(1, self.prefetch_concurrency))
        window_size = max(1, self.prefetch_queue_size)
        inflight: Dict[int, asyncio.Task[None]] = {}
        slice_queues: Dict[int, asyncio.Queue[Tuple[str, Any]]] = {}
        next_launch = 0
        next_yield = 0
        launched_full_window = False

        def _launch(idx: int) -> None:
            item = ordered_addrs[idx]
            cid = str(item.get("identity", ""))
            url = str(item.get("url", ""))
            key = int(item.get("key", 0) or 0)
            queue: asyncio.Queue[Tuple[str, Any]] = asyncio.Queue(
                maxsize=self.slice_chunk_queue_size
            )
            slice_queues[idx] = queue
            inflight[idx] = asyncio.create_task(
                self._prefetch_slice_to_queue(cid, url, key, semaphore, queue)
            )

        async def _fill_window(limit: int) -> None:
            nonlocal next_launch
            target = max(1, min(window_size, limit))
            while next_launch < len(ordered_addrs) and len(inflight) < target:
                _launch(next_launch)
                next_launch += 1
                if metrics is not None:
                    metrics["prefetch_inflight"] = max(
                        int(metrics.get("prefetch_inflight", 0)), len(inflight)
                    )

        try:
            # Keep startup fan-out to 1 so concurrent client probes do not starve
            # each other before first byte is produced.
            await _fill_window(1)
            while next_yield < len(ordered_addrs):
                queue = slice_queues.get(next_yield)
                task = inflight.get(next_yield)
                if not queue or not task:
                    raise HTTPException(
                        status_code=502, detail=f"prefetch gap at index={next_yield}"
                    )

                while True:
                    event_type, payload = await queue.get()
                    if event_type == self._EVENT_CHUNK:
                        chunk = payload
                        if chunk:
                            if not launched_full_window and window_size > 1:
                                launched_full_window = True
                                await _fill_window(window_size)
                            yield chunk
                        continue
                    if event_type == self._EVENT_DONE:
                        if not launched_full_window and window_size > 1:
                            launched_full_window = True
                            await _fill_window(window_size)
                        break
                    if event_type == self._EVENT_ERROR:
                        if isinstance(payload, HTTPException):
                            raise payload
                        raise HTTPException(
                            status_code=502, detail=f"slice fetch failed: {payload}"
                        )
                    raise HTTPException(
                        status_code=502, detail=f"unknown prefetch event={event_type}"
                    )

                inflight.pop(next_yield, None)
                slice_queues.pop(next_yield, None)
                if not task:
                    raise HTTPException(
                        status_code=502, detail=f"prefetch gap at index={next_yield}"
                    )
                await task
                next_yield += 1
                await _fill_window(window_size if launched_full_window else 1)
        finally:
            if inflight:
                cancelled_count = len(inflight)
                for task in inflight.values():
                    task.cancel()
                timed_out = False
                try:
                    await asyncio.wait_for(
                        asyncio.gather(*inflight.values(), return_exceptions=True),
                        timeout=self.CANCEL_WAIT_TIMEOUT_SEC,
                    )
                except asyncio.TimeoutError:
                    timed_out = True
                    log_json(
                        self.logger,
                        logging.WARNING,
                        "slice_prefetch_cancel_timeout",
                        inflight=cancelled_count,
                        timeout_sec=self.CANCEL_WAIT_TIMEOUT_SEC,
                    )
                    # Force-release potentially leaked global semaphore permits.
                    # Tasks that timed out may still hold the semaphore; releasing
                    # here prevents permanent starvation of all future requests.
                    for _ in range(cancelled_count):
                        try:
                            self.global_download_semaphore.release()
                        except ValueError:
                            break
                    log_json(
                        self.logger,
                        logging.WARNING,
                        "slice_prefetch_force_release_semaphore",
                        cancelled_count=cancelled_count,
                    )
                    reset_client = getattr(self.api_client, "reset_download_client", None)
                    if reset_client is not None:
                        try:
                            await reset_client(reason="slice_prefetch_cancel_timeout")
                        except Exception as exc:
                            log_json(
                                self.logger,
                                logging.WARNING,
                                "slice_prefetch_cancel_timeout_reset_failed",
                                error=str(exc),
                            )

    async def _queue_put_safe(
        self,
        queue: "asyncio.Queue[Tuple[str, Any]]",
        item: "Tuple[str, Any]",
    ) -> bool:
        """Put an item into the queue with a timeout to prevent indefinite blocking.

        Returns True if the item was placed, False if the timeout elapsed.
        Raises CancelledError immediately if the task is cancelled.
        """
        try:
            await asyncio.wait_for(
                queue.put(item), timeout=self.QUEUE_PUT_TIMEOUT_SEC
            )
            return True
        except asyncio.TimeoutError:
            return False

    async def _prefetch_slice_to_queue(
        self,
        cid: str,
        url: str,
        key: int,
        semaphore: asyncio.Semaphore,
        queue: "asyncio.Queue[Tuple[str, Any]]",
    ) -> None:
        current_url = url
        current_key = key
        emitted_any = False

        for attempt in range(2):
            # Use explicit acquire/release instead of 'async with' to guarantee
            # that semaphores are released even when CancelledError is raised
            # while we are blocked on queue.put() inside the context body.
            local_acquired = False
            global_acquired = False
            try:
                await semaphore.acquire()
                local_acquired = True
                await self.global_download_semaphore.acquire()
                global_acquired = True
                gen = self._download_slice_chunks(
                    current_url, current_key
                )
                try:
                    async for chunk in gen:
                        if not chunk:
                            continue
                        emitted_any = True
                        put_ok = await self._queue_put_safe(
                            queue, (self._EVENT_CHUNK, chunk)
                        )
                        if not put_ok:
                            # Consumer is gone; abort gracefully
                            break
                except asyncio.CancelledError:
                    try:
                        await asyncio.shield(gen.aclose())
                    except Exception:
                        pass
                    raise
                except Exception:
                    try:
                        await gen.aclose()
                    except Exception:
                        pass
                    raise
            except asyncio.CancelledError:
                raise
            except HTTPException as exc:
                if emitted_any:
                    await self._queue_put_safe(
                        queue,
                        (self._EVENT_ERROR, self._to_terminal_http_error(cid, exc)),
                    )
                    return
                if attempt == 0 and exc.status_code in {401, 403, 404}:
                    self.api_client.invalidate_slice_address(cid)
                    refreshed, _ = await self.api_client.get_slice_download_address(
                        [cid], force_refresh=True
                    )
                    if refreshed:
                        current_url = str(refreshed[0].get("url", ""))
                        current_key = int(refreshed[0].get("key", 0) or 0)
                        await asyncio.sleep(0.1)
                        continue
                if attempt == 0 and exc.status_code in {429, 500, 502, 503, 504}:
                    await asyncio.sleep(0.2)
                    continue
                await self._queue_put_safe(
                    queue,
                    (self._EVENT_ERROR, self._to_terminal_http_error(cid, exc)),
                )
                return
            except (httpx.TimeoutException, httpx.TransportError) as exc:
                if emitted_any:
                    await self._queue_put_safe(
                        queue,
                        (self._EVENT_ERROR, self._to_terminal_http_error(cid, exc)),
                    )
                    return
                if attempt == 0:
                    await asyncio.sleep(0.2)
                    continue
                await self._queue_put_safe(
                    queue,
                    (self._EVENT_ERROR, self._to_terminal_http_error(cid, exc)),
                )
                return
            except Exception as exc:
                if emitted_any:
                    await self._queue_put_safe(
                        queue,
                        (self._EVENT_ERROR, self._to_terminal_http_error(cid, exc)),
                    )
                    return
                if attempt == 0:
                    await asyncio.sleep(0.2)
                    continue
                await self._queue_put_safe(
                    queue,
                    (self._EVENT_ERROR, self._to_terminal_http_error(cid, exc)),
                )
                return
            finally:
                # Guarantee semaphore release regardless of how we exit.
                # This is the critical fix: 'async with' can miss release
                # when CancelledError hits during queue.put() inside the body.
                if global_acquired:
                    self.global_download_semaphore.release()
                if local_acquired:
                    semaphore.release()
            await self._queue_put_safe(queue, (self._EVENT_DONE, None))
            return

        await self._queue_put_safe(
            queue,
            (
                self._EVENT_ERROR,
                HTTPException(
                    status_code=502, detail=f"slice fetch retries exhausted: {cid}"
                ),
            ),
        )

    def _to_terminal_http_error(self, cid: str, exc: Exception) -> HTTPException:
        if isinstance(exc, HTTPException) and exc.status_code == 502:
            return exc

        if isinstance(exc, HTTPException):
            return HTTPException(
                status_code=502,
                detail=f"slice fetch failed after output: cid={cid}, upstream_status={exc.status_code}",
            )

        if isinstance(exc, (httpx.TimeoutException, httpx.TransportError)):
            return HTTPException(
                status_code=502, detail=f"slice fetch transport error: {exc}"
            )

        return HTTPException(
            status_code=502, detail=f"slice fetch unexpected error: {exc}"
        )

    async def _download_slice_chunks(
        self, url: str, key: int
    ) -> AsyncGenerator[bytes, None]:
        req = self.api_client.download_client.build_request("GET", url)
        resp = await self.api_client.download_client.send(req, stream=True)
        try:
            if resp.status_code not in range(200, 300):
                if resp.status_code in {401, 403, 404}:
                    raise HTTPException(
                        status_code=resp.status_code,
                        detail=f"slice fetch failed: {resp.status_code}",
                    )
                raise HTTPException(
                    status_code=502, detail=f"slice fetch failed: {resp.status_code}"
                )

            if key > 0:
                byte_key = key & 0xFF
                trans = bytes([i ^ byte_key for i in range(256)])
                async for chunk in resp.aiter_bytes():
                    if chunk:
                        yield chunk.translate(trans)
            else:
                async for chunk in resp.aiter_bytes():
                    if chunk:
                        yield chunk
        except asyncio.CancelledError:
            try:
                await asyncio.shield(resp.aclose())
            except Exception:
                pass
            raise
        except Exception:
            try:
                await resp.aclose()
            except Exception:
                pass
            raise
        else:
            await resp.aclose()


class RestoreManager:
    ACTIVE_TTL_RECHECK_SEC = 60.0

    def __init__(
        self,
        config: AppConfig,
        api_client: TwoLandApiClient,
        path_cache: PathResolverCache,
    ):
        self.config = config
        self.api_client = api_client
        self.path_cache = path_cache
        self.logger = logging.getLogger("twoland.restore")
        self.scheduled: Dict[str, asyncio.Task[Any]] = {}
        self.play_entries: Dict[str, RestoreEntry] = {}
        self._cid_locks: Dict[str, asyncio.Lock] = {}
        self.create_semaphore = asyncio.Semaphore(
            max(1, int(config.restore_create_max_concurrency))
        )

    async def shutdown(self) -> None:
        for task in list(self.scheduled.values()):
            task.cancel()
        self.scheduled.clear()

    async def restore_file_from_cid(
        self, original_path: str, content_identity: str
    ) -> Optional[str]:
        return await self.ensure_restored_for_play(original_path, content_identity)

    def _ttl_seconds(self) -> float:
        return max(0.0, float(self.config.restore_ttl_hours) * 3600.0)

    def _find_matching_entries(
        self,
        *,
        content_identity: Optional[str] = None,
        original_path: Optional[str] = None,
        restore_path: Optional[str] = None,
        identity: Optional[str] = None,
    ) -> List[Tuple[str, RestoreEntry]]:
        cid = str(content_identity or "").strip()
        matched: List[Tuple[str, RestoreEntry]] = []
        seen: Set[str] = set()

        def _matches(entry: RestoreEntry) -> bool:
            return bool(
                (cid and entry.content_identity == cid)
                or (original_path and entry.original_path == original_path)
                or (restore_path and entry.restore_path == restore_path)
                or (identity and entry.identity == identity)
            )

        if cid:
            entry = self.play_entries.get(cid)
            if entry:
                matched.append((cid, entry))
                seen.add(cid)

        for existing_cid, entry in list(self.play_entries.items()):
            if existing_cid in seen:
                continue
            if _matches(entry):
                matched.append((existing_cid, entry))
                seen.add(existing_cid)

        return matched

    def invalidate_restore_state(
        self,
        content_identity: Optional[str] = None,
        *,
        original_path: Optional[str] = None,
        restore_path: Optional[str] = None,
        identity: Optional[str] = None,
        reason: Optional[str] = None,
        cancel_scheduled: bool = True,
    ) -> None:
        matched = self._find_matching_entries(
            content_identity=content_identity,
            original_path=original_path,
            restore_path=restore_path,
            identity=identity,
        )
        current_task = asyncio.current_task()

        restore_paths: Set[str] = set()
        original_paths: Set[str] = set()
        identities: Set[str] = set()
        invalidated_cids: List[str] = []

        for cid, entry in matched:
            invalidated_cids.append(cid)
            restore_paths.add(entry.restore_path)
            original_paths.add(entry.original_path)
            identities.add(entry.identity)
            self.play_entries.pop(cid, None)

        if restore_path:
            restore_paths.add(restore_path)
        if original_path:
            original_paths.add(original_path)
        if identity:
            identities.add(identity)

        for path in restore_paths:
            self.path_cache.invalidate(path)
            if cancel_scheduled:
                task = self.scheduled.get(path)
                if task and task is not current_task:
                    task.cancel()
                    self.scheduled.pop(path, None)

        for path in original_paths:
            self.path_cache.invalidate(path)

        file_info_cache = getattr(self.api_client, "file_info_cache", None)
        if file_info_cache:
            for item_identity in identities:
                file_info_cache.invalidate(item_identity)

        if reason:
            log_json(
                self.logger,
                logging.INFO,
                "restore_cache_invalidated",
                content_identity=str(content_identity or ""),
                restore_path=restore_path,
                original_path=original_path,
                identity=identity,
                invalidated_cids=invalidated_cids,
                reason=reason,
            )

    async def ensure_restored_for_play(
        self, original_path: str, content_identity: str
    ) -> Optional[str]:
        cid = str(content_identity or "").strip()
        if not cid:
            return None

        lock = self._cid_locks.setdefault(cid, asyncio.Lock())

        async with lock:
            inmem_entry = self.play_entries.get(cid)
            if inmem_entry:
                existing_identity = await self._resolve_restore_identity(
                    inmem_entry.restore_path, refresh=True
                )
                if existing_identity == inmem_entry.identity:
                    await self._touch_restore_entry(
                        original_path,
                        cid,
                        inmem_entry.restore_path,
                        inmem_entry.identity,
                    )
                    log_json(
                        self.logger,
                        logging.INFO,
                        "play_restore_hit",
                        original_path=original_path,
                        restore_path=inmem_entry.restore_path,
                        content_identity=cid,
                        identity=inmem_entry.identity,
                        source="memory",
                    )
                    return inmem_entry.identity

                stale_reason = (
                    "missing_remote_restore"
                    if not existing_identity
                    else "restore_identity_changed"
                )
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_restore_stale_memory",
                    original_path=original_path,
                    restore_path=inmem_entry.restore_path,
                    content_identity=cid,
                    identity=inmem_entry.identity,
                    refreshed_identity=existing_identity,
                    reason=stale_reason,
                )
                self.invalidate_restore_state(
                    cid,
                    original_path=inmem_entry.original_path,
                    restore_path=inmem_entry.restore_path,
                    identity=inmem_entry.identity,
                    reason=stale_reason,
                )
                if existing_identity:
                    await self._touch_restore_entry(
                        original_path,
                        cid,
                        inmem_entry.restore_path,
                        existing_identity,
                    )
                    log_json(
                        self.logger,
                        logging.INFO,
                        "play_restore_hit",
                        original_path=original_path,
                        restore_path=inmem_entry.restore_path,
                        content_identity=cid,
                        identity=existing_identity,
                        source="remote",
                    )
                    return existing_identity

            restore_path = self._build_restore_path(original_path, cid)
            existing_identity = await self._resolve_restore_identity(restore_path)
            if existing_identity:
                await self._touch_restore_entry(
                    original_path, cid, restore_path, existing_identity
                )
                log_json(
                    self.logger,
                    logging.INFO,
                    "play_restore_hit",
                    original_path=original_path,
                    restore_path=restore_path,
                    content_identity=cid,
                    identity=existing_identity,
                    source="remote",
                )
                return existing_identity

            log_json(
                self.logger,
                logging.INFO,
                "play_restore_create",
                original_path=original_path,
                restore_path=restore_path,
                content_identity=cid,
            )
            log_json(
                self.logger,
                logging.INFO,
                "restore_attempt",
                original_path=original_path,
                restore_path=restore_path,
            )
            create_error: Optional[str] = None
            try:
                async with self.create_semaphore:
                    resp = await self.api_client.restore_file_by_content_identity(
                        path=restore_path, content_identity=cid
                    )
                identity = str(resp.get("identity", ""))
                if identity:
                    await self._touch_restore_entry(
                        original_path, cid, restore_path, identity
                    )
                    return identity
                create_error = f"create missing identity, response={resp}"
            except Exception as exc:
                create_error = str(exc)

            fallback_identity = await self._resolve_restore_identity(restore_path)
            if fallback_identity:
                await self._touch_restore_entry(
                    original_path, cid, restore_path, fallback_identity
                )
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_restore_reuse_after_create_error",
                    original_path=original_path,
                    restore_path=restore_path,
                    content_identity=cid,
                    identity=fallback_identity,
                    error=create_error,
                )
                return fallback_identity

            log_json(
                self.logger,
                logging.ERROR,
                "restore_failed",
                error=create_error or "unknown restore error",
                path=original_path,
                restore_path=restore_path,
                content_identity=cid,
            )
            return None

    def invalidate_play_entry(self, content_identity: str) -> None:
        self.invalidate_restore_state(content_identity, reason="invalidate_play_entry")

    async def acquire_play_lease(
        self,
        content_identity: str,
        *,
        original_path: Optional[str] = None,
        restore_path: Optional[str] = None,
        identity: Optional[str] = None,
    ) -> bool:
        cid = str(content_identity or "").strip()
        if not cid:
            return False

        lock = self._cid_locks.setdefault(cid, asyncio.Lock())
        scheduled_path = ""
        scheduled_identity = ""

        async with lock:
            now = time.time()
            expire_at_ts = now + self._ttl_seconds()
            entry = self.play_entries.get(cid)
            if not entry:
                if not restore_path or not identity:
                    return False
                entry = RestoreEntry(
                    content_identity=cid,
                    original_path=original_path or "",
                    restore_path=restore_path,
                    identity=identity,
                    last_touch_ts=now,
                    expire_at_ts=expire_at_ts,
                    active_leases=0,
                )
                self.play_entries[cid] = entry
            else:
                if original_path:
                    entry.original_path = original_path
                if restore_path:
                    entry.restore_path = restore_path
                if identity:
                    entry.identity = identity
                entry.last_touch_ts = now
                entry.expire_at_ts = expire_at_ts

            entry.active_leases += 1
            if entry.original_path:
                self.path_cache.set(entry.original_path, entry.identity)
            self.path_cache.set(entry.restore_path, entry.identity)
            scheduled_path = entry.restore_path
            scheduled_identity = entry.identity

        if scheduled_path and scheduled_identity:
            await self._schedule_delete(
                scheduled_path, scheduled_identity, self.config.restore_ttl_hours
            )
        return True

    async def release_play_lease(self, content_identity: str) -> bool:
        cid = str(content_identity or "").strip()
        if not cid:
            return False

        lock = self._cid_locks.setdefault(cid, asyncio.Lock())
        async with lock:
            entry = self.play_entries.get(cid)
            if not entry:
                return False
            entry.active_leases = max(0, int(entry.active_leases) - 1)
            return True

    async def _touch_restore_entry(
        self,
        original_path: str,
        content_identity: str,
        restore_path: str,
        identity: str,
    ) -> None:
        now = time.time()
        existing = self.play_entries.get(content_identity)
        active_leases = existing.active_leases if existing else 0
        self.play_entries[content_identity] = RestoreEntry(
            content_identity=content_identity,
            original_path=original_path,
            restore_path=restore_path,
            identity=identity,
            last_touch_ts=now,
            expire_at_ts=now + self._ttl_seconds(),
            active_leases=active_leases,
        )
        self.path_cache.set(original_path, identity)
        self.path_cache.set(restore_path, identity)
        await self._schedule_delete(
            restore_path, identity, self.config.restore_ttl_hours
        )

    async def _resolve_restore_identity(
        self, restore_path: str, *, refresh: bool = False
    ) -> Optional[str]:
        try:
            if refresh:
                self.path_cache.invalidate(restore_path)
            identity = await self.api_client.resolve_path(restore_path, self.path_cache)
            return identity or None
        except PathNotFoundError:
            return None
        except Exception:
            return None

    def _build_restore_path(self, original_path: str, content_identity: str) -> str:
        filename = original_path.rsplit("/", 1)[-1]
        ext = ext_of(filename) or "bin"
        return f"{self.config.restore_dir.rstrip('/')}/{content_identity}.{ext}"

    async def _schedule_delete(
        self, file_path: str, identity: str, delay_hours: float
    ) -> None:
        existing_task = self.scheduled.get(file_path)
        current_task = asyncio.current_task()
        if existing_task and existing_task is not current_task:
            existing_task.cancel()

        task: Optional[asyncio.Task[Any]] = None

        async def _run() -> None:
            try:
                await asyncio.sleep(max(0.0, delay_hours) * 3600)
                matching_entries = self._find_matching_entries(
                    restore_path=file_path, identity=identity
                )
                active_leases = sum(
                    max(0, int(entry.active_leases)) for _, entry in matching_entries
                )
                if active_leases > 0:
                    log_json(
                        self.logger,
                        logging.INFO,
                        "restore_ttl_skip_active",
                        path=file_path,
                        identity=identity,
                        active_leases=active_leases,
                    )
                    await self._schedule_delete(
                        file_path,
                        identity,
                        self.ACTIVE_TTL_RECHECK_SEC / 3600.0,
                    )
                    return

                await self.api_client.delete_item(
                    identity=identity, file_path=file_path
                )
                self.invalidate_restore_state(
                    restore_path=file_path,
                    identity=identity,
                    reason="restore_ttl_deleted",
                    cancel_scheduled=False,
                )
                log_json(
                    self.logger,
                    logging.INFO,
                    "restore_ttl_deleted",
                    path=file_path,
                    identity=identity,
                )
            except asyncio.CancelledError:
                return
            except Exception as exc:
                existing_identity = await self._resolve_restore_identity(
                    file_path, refresh=True
                )
                if not existing_identity:
                    self.invalidate_restore_state(
                        restore_path=file_path,
                        identity=identity,
                        reason="restore_ttl_missing_remote",
                        cancel_scheduled=False,
                    )
                else:
                    log_json(
                        self.logger,
                        logging.ERROR,
                        "restore_ttl_delete_failed",
                        path=file_path,
                        identity=identity,
                        error=str(exc),
                    )
            finally:
                if self.scheduled.get(file_path) is task:
                    self.scheduled.pop(file_path, None)

        task = asyncio.create_task(_run())
        self.scheduled[file_path] = task

    async def scan_existing_restore_files(self) -> None:
        try:
            restore_dir_identity = await self.api_client.resolve_path(
                self.config.restore_dir, self.path_cache
            )
        except PathNotFoundError:
            log_json(
                self.logger,
                logging.INFO,
                "restore_dir_missing",
                restore_dir=self.config.restore_dir,
            )
            return

        files = await self.api_client.list_files_by_id(restore_dir_identity)
        if not files:
            return

        now_ms = int(time.time() * 1000)
        ttl_ms = int(self.config.restore_ttl_hours * 3600 * 1000)

        for f in files:
            if f.get("dir"):
                continue
            identity = str(f.get("identity", ""))
            name = str(f.get("name", ""))
            if not identity or not name:
                continue

            file_path = f"{self.config.restore_dir.rstrip('/')}/{name}"
            create_ts = int(f.get("create_ts", 0) or 0)
            if create_ts > 0:
                remaining_ms = ttl_ms - (now_ms - create_ts)
                if remaining_ms <= 0:
                    try:
                        await self.api_client.delete_item(
                            identity=identity, file_path=file_path
                        )
                        self.invalidate_restore_state(
                            restore_path=file_path,
                            identity=identity,
                            reason="restore_startup_expired",
                        )
                    except Exception as exc:
                        existing_identity = await self._resolve_restore_identity(
                            file_path, refresh=True
                        )
                        if not existing_identity:
                            self.invalidate_restore_state(
                                restore_path=file_path,
                                identity=identity,
                                reason="restore_startup_missing_remote",
                            )
                        else:
                            log_json(
                                self.logger,
                                logging.ERROR,
                                "restore_startup_delete_failed",
                                path=file_path,
                                identity=identity,
                                error=str(exc),
                            )
                    continue
                await self._schedule_delete(file_path, identity, remaining_ms / 3600000)
            else:
                await self._schedule_delete(
                    file_path, identity, self.config.restore_ttl_hours
                )


class StrmPipeline:
    def __init__(
        self,
        config: AppConfig,
        api_client: TwoLandApiClient,
        path_cache: PathResolverCache,
        streamer: SliceStreamer,
        *,
        stats: Optional[StatsStore] = None,
    ):
        self.config = config
        self.api_client = api_client
        self.path_cache = path_cache
        self.streamer = streamer
        self._stats = stats
        self.logger = logging.getLogger("twoland.pipeline")
        self._run_lock = asyncio.Lock()

    @property
    def min_size_bytes(self) -> int:
        return int(self.config.min_video_size_mb * 1024 * 1024)

    async def run_once(
        self, mappings: List[MappingConfig], auto_confirm: bool
    ) -> ScanStats:
        async with self._run_lock:
            start = time.time()
            stats = ScanStats()
            files_to_delete: Dict[str, DeleteCandidate] = {}

            log_file_path = Path(self.config.output_dir) / ".processed_files.log"
            ensure_dir(self.config.output_dir)
            processed = self._load_processed(log_file_path)

            with log_file_path.open("a", encoding="utf-8") as log_f:
                for mapping in mappings:
                    if not mapping.enabled:
                        continue

                    remote = normalize_remote_path(mapping.remote)
                    local_root = mapping.local
                    ensure_dir(local_root)

                    extras_mode = mapping.extras_mode or self.config.extras_mode
                    if extras_mode not in {"keep", "download", "delete"}:
                        extras_mode = self.config.extras_mode
                    media_mode = mapping.media_mode or self.config.media_mode
                    if media_mode not in {"keep", "delete"}:
                        media_mode = self.config.media_mode

                    try:
                        root_identity = await self.api_client.resolve_path(
                            remote, self.path_cache
                        )
                    except Exception as exc:
                        stats.errors += 1
                        log_json(
                            self.logger,
                            logging.ERROR,
                            "mapping_resolve_failed",
                            remote=remote,
                            error=str(exc),
                        )
                        continue

                    await self._scan_dir(
                        dir_identity=root_identity,
                        remote_dir=remote,
                        local_dir=local_root,
                        extras_mode=extras_mode,
                        media_mode=media_mode,
                        processed=processed,
                        log_f=log_f,
                        stats=stats,
                        files_to_delete=files_to_delete,
                    )

            duration = round(time.time() - start, 3)
            log_json(
                self.logger,
                logging.INFO,
                "scan_summary",
                duration_sec=duration,
                scanned=stats.scanned,
                created_strm=stats.created_strm,
                downloaded_extras=stats.downloaded_extras,
                skipped_exists=stats.skipped_exists,
                skipped_small=stats.skipped_small,
                delete_candidates=len(files_to_delete),
                errors=stats.errors,
            )

            candidates = list(files_to_delete.values())
            if candidates:
                should_delete = auto_confirm
                if not auto_confirm:
                    try:
                        answer = input("输入 'yes' 确认删除: ").strip().lower()
                        should_delete = answer == "yes"
                    except EOFError:
                        should_delete = False

                if should_delete:
                    await self._delete_candidates(candidates, stats)
                else:
                    log_json(
                        self.logger,
                        logging.INFO,
                        "delete_skipped",
                        reason="not_confirmed",
                        count=len(candidates),
                    )

            if self._stats:
                self._stats.inc("scan_total")
                self._stats.inc("scan_strm_created", stats.created_strm)
                self._stats.inc("scan_strm_deleted", stats.deleted)

            return stats

    async def _scan_dir(
        self,
        *,
        dir_identity: str,
        remote_dir: str,
        local_dir: str,
        extras_mode: str,
        media_mode: str,
        processed: set,
        log_f: Any,
        stats: ScanStats,
        files_to_delete: Dict[str, DeleteCandidate],
    ) -> None:
        try:
            items = await self.api_client.list_files_by_id(dir_identity)
        except Exception as exc:
            stats.errors += 1
            log_json(
                self.logger,
                logging.ERROR,
                "scan_dir_failed",
                remote=remote_dir,
                error=str(exc),
            )
            return

        for item in items:
            stats.scanned += 1
            name = str(item.get("name", ""))
            identity = str(item.get("identity", ""))
            if not name or not identity:
                continue

            remote_path = (
                f"{remote_dir.rstrip('/')}/{name}" if remote_dir != "/" else f"/{name}"
            )
            if item.get("dir"):
                child_local_dir = os.path.join(local_dir, name)
                ensure_dir(child_local_dir)
                self.path_cache.set(remote_path, identity)
                await self._scan_dir(
                    dir_identity=identity,
                    remote_dir=remote_path,
                    local_dir=child_local_dir,
                    extras_mode=extras_mode,
                    media_mode=media_mode,
                    processed=processed,
                    log_f=log_f,
                    stats=stats,
                    files_to_delete=files_to_delete,
                )
                continue

            ext = ext_of(name)
            if ext in MEDIA_EXTS:
                result = await self._handle_media(
                    remote_path,
                    local_dir,
                    identity,
                    name,
                    stats,
                    delete_after_scan=media_mode == "delete",
                )
                if result.processed:
                    if result.delete_after_scan:
                        files_to_delete[remote_path] = DeleteCandidate(
                            path=remote_path, identity=identity
                        )
                    self._mark_processed(remote_path, processed, log_f)
                continue

            if ext in IMAGE_EXTS or ext in SUB_EXTS:
                if extras_mode == "download":
                    ok = await self._download_extra(
                        remote_path, local_dir, identity, name, stats
                    )
                    if ok:
                        files_to_delete[remote_path] = DeleteCandidate(
                            path=remote_path, identity=identity
                        )
                        self._mark_processed(remote_path, processed, log_f)
                elif extras_mode == "delete":
                    files_to_delete[remote_path] = DeleteCandidate(
                        path=remote_path, identity=identity
                    )
                    self._mark_processed(remote_path, processed, log_f)
                continue

            if ext in GARBAGE_EXTS:
                files_to_delete[remote_path] = DeleteCandidate(
                    path=remote_path, identity=identity
                )
                self._mark_processed(remote_path, processed, log_f)

    async def _handle_media(
        self,
        remote_path: str,
        local_dir: str,
        identity: str,
        file_name: str,
        stats: ScanStats,
        *,
        delete_after_scan: bool,
    ) -> MediaHandleResult:
        try:
            info = await self.api_client.get_file_info(identity)
        except Exception as exc:
            stats.errors += 1
            log_json(
                self.logger,
                logging.ERROR,
                "media_info_failed",
                path=remote_path,
                error=str(exc),
            )
            return MediaHandleResult(processed=False)

        if info.size < self.min_size_bytes:
            stats.skipped_small += 1
            return MediaHandleResult(processed=True, delete_after_scan=False)

        if not info.content_identity:
            stats.errors += 1
            log_json(
                self.logger,
                logging.ERROR,
                "media_missing_content_identity",
                path=remote_path,
            )
            return MediaHandleResult(processed=False)

        safe_name = file_name.replace("/", "_")
        base = os.path.splitext(safe_name)[0]
        strm_path = os.path.join(local_dir, f"{base}.strm")

        play_url = build_play_url(
            self.config.public_strm_host, remote_path, info.content_identity
        )
        try:
            state = sync_strm_file(strm_path, play_url)
        except Exception as exc:
            stats.errors += 1
            log_json(
                self.logger,
                logging.ERROR,
                "strm_write_failed",
                path=strm_path,
                error=str(exc),
            )
            return MediaHandleResult(processed=False)

        if state in {"created", "updated"}:
            stats.created_strm += 1
        else:
            stats.skipped_exists += 1

        return MediaHandleResult(
            processed=True,
            delete_after_scan=delete_after_scan,
        )

    async def _download_extra(
        self,
        remote_path: str,
        local_dir: str,
        identity: str,
        file_name: str,
        stats: ScanStats,
    ) -> bool:
        safe_name = file_name.replace("/", "_")
        local_path = os.path.join(local_dir, safe_name)

        if os.path.exists(local_path):
            return True

        try:
            info = await self.api_client.get_file_info(identity)
            if not info.cids:
                raise ApiError("no slices for extra file")

            with open(local_path, "wb") as f:
                async for chunk in self.streamer.iter_slices(info.cids):
                    f.write(chunk)
        except Exception as exc:
            stats.errors += 1
            log_json(
                self.logger,
                logging.ERROR,
                "extra_download_failed",
                path=remote_path,
                error=str(exc),
            )
            try:
                if os.path.exists(local_path):
                    os.remove(local_path)
            except Exception:
                pass
            return False

        stats.downloaded_extras += 1
        return True

    async def _delete_candidates(
        self, candidates: List[DeleteCandidate], stats: ScanStats
    ) -> None:
        total = len(candidates)
        for idx, item in enumerate(candidates, start=1):
            final_identity = item.identity

            try:
                resolved = await self.api_client.resolve_path(
                    item.path, self.path_cache
                )
                if resolved and resolved != item.identity:
                    log_json(
                        self.logger,
                        logging.WARNING,
                        "delete_identity_mismatch",
                        path=item.path,
                        listed_identity=item.identity,
                        resolved_identity=resolved,
                    )
                    final_identity = resolved
            except PathNotFoundError:
                continue
            except Exception as exc:
                log_json(
                    self.logger,
                    logging.WARNING,
                    "delete_reverify_failed",
                    path=item.path,
                    error=str(exc),
                )

            try:
                await self.api_client.delete_item(
                    identity=final_identity, file_path=item.path
                )
                self.path_cache.invalidate_tree(item.path)
                stats.deleted += 1
            except Exception as exc:
                stats.errors += 1
                log_json(
                    self.logger,
                    logging.ERROR,
                    "delete_failed",
                    path=item.path,
                    error=str(exc),
                    progress=f"{idx}/{total}",
                )

    def _load_processed(self, path: Path) -> set:
        if not path.exists():
            return set()

        try:
            data = path.read_text(encoding="utf-8")
        except Exception:
            return set()

        return {line.strip() for line in data.splitlines() if line.strip()}

    def _mark_processed(self, remote_path: str, processed: set, log_f: Any) -> None:
        if remote_path in processed:
            return
        processed.add(remote_path)
        log_f.write(remote_path + "\n")


class ProxyService:
    FIRST_BYTE_WARN_MS = 8000
    TINY_PROBE_EXPLICIT_END = 1
    AUTO_PROMOTED_TAIL_EXPAND_MAX_BYTES = 8 * 1024 * 1024

    def __init__(
        self,
        config: AppConfig,
        api_client: TwoLandApiClient,
        path_cache: PathResolverCache,
        restore_manager: RestoreManager,
        streamer: SliceStreamer,
        *,
        stats: Optional[StatsStore] = None,
    ):
        self.config = config
        self.api_client = api_client
        self.path_cache = path_cache
        self.restore_manager = restore_manager
        self.streamer = streamer
        self._stats = stats
        self.logger = logging.getLogger("twoland.proxy")
        self.play_admission = PlayAdmissionController(
            max_active_requests=config.play_max_active_requests,
            wait_ms=config.play_admission_wait_ms,
            stats=stats,
        )
        self.play_profile_resolver = PlayProfileResolver(config)
        self.compat_streamer: Optional[SliceStreamer] = None
        self.play_webdav_cache: Optional[WebDavRedirectCache] = None
        self.play_webdav_base_url = str(config.play_webdav_base_url or "").rstrip("/")
        self.play_webdav_auth_header = ""
        if config.webdav_redirect_enabled() and self.play_webdav_base_url:
            auth_raw = (
                f"{config.play_webdav_username}:{config.play_webdav_password}"
            ).encode("utf-8")
            self.play_webdav_auth_header = (
                "Basic " + base64.b64encode(auth_raw).decode("ascii")
            )
            self.play_webdav_cache = WebDavRedirectCache(
                ttl_seconds=int(config.play_webdav_cache_ttl_sec)
            )
        if streamer is not None:
            self.compat_streamer = SliceStreamer(
                api_client,
                prefetch_concurrency=config.play_compat_prefetch_concurrency,
                prefetch_queue_size=config.play_compat_prefetch_queue_size,
                global_download_limit=config.slice_global_download_limit,
                initial_addr_batch=config.play_compat_initial_addr_batch,
                global_download_semaphore=streamer.global_download_semaphore,
            )

    @staticmethod
    def should_warn_first_byte(first_byte_ms: Any, threshold_ms: int) -> bool:
        if threshold_ms <= 0:
            return False
        if first_byte_ms is None:
            return True
        try:
            return float(first_byte_ms) > float(threshold_ms)
        except Exception:
            return True

    @staticmethod
    def build_play_overloaded() -> HTTPException:
        return HTTPException(
            status_code=503,
            detail="play overloaded, retry shortly",
            headers={"Retry-After": "1"},
        )

    @staticmethod
    def play_request_source(request: Optional[Request]) -> str:
        if request is None:
            return ""
        return str(request.query_params.get(PLAY_SOURCE_QUERY_PARAM, "")).strip().lower()

    def should_attempt_webdav_redirect(
        self, profile_decision: PlayProfileDecision, request: Optional[Request] = None
    ) -> bool:
        if not (
            self.config.webdav_redirect_enabled()
            and self.play_webdav_cache is not None
            and self.play_webdav_base_url
            and self.play_webdav_auth_header
            and profile_decision.profile == PlayProfileResolver.PROFILE_STANDARD
        ):
            return False
        if self.config.play_redirect_scope == PLAY_REDIRECT_SCOPE_EMBY_ONLY:
            return self.play_request_source(request) == PLAY_SOURCE_EMBY_PROXY
        return True

    def build_webdav_target_url(self, target_path: str) -> str:
        normalized = normalize_remote_path(target_path)
        quoted_path = urllib.parse.quote(normalized, safe="/")
        return f"{self.play_webdav_base_url}{quoted_path}"

    @staticmethod
    def normalize_webdav_redirect_location(
        location: str, base_url: str
    ) -> Optional[str]:
        normalized, _ = ProxyService.inspect_webdav_redirect_location(location, base_url)
        return normalized

    @staticmethod
    def inspect_webdav_redirect_location(
        location: str, base_url: str
    ) -> Tuple[Optional[str], str]:
        location_text = str(location or "").strip()
        if not location_text:
            return None, "missing_location"
        absolute = urllib.parse.urljoin(base_url, location_text)
        parsed_base = urllib.parse.urlparse(base_url)
        parsed = urllib.parse.urlparse(absolute)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            return None, "invalid_location"
        if parsed_base.scheme == "https" and parsed.scheme != "https":
            return None, "scheme_downgrade"
        if parsed.username or parsed.password:
            return None, "invalid_location"
        return absolute, ""

    @staticmethod
    def is_auth_like_webdav_redirect_location(location: str) -> bool:
        parsed = urllib.parse.urlparse(str(location or ""))
        path_segments = [segment.lower() for segment in parsed.path.split("/") if segment]
        if not path_segments:
            return False
        auth_markers = {
            "login",
            "signin",
            "sign-in",
            "oauth",
            "oauth2",
            "authorize",
            "sso",
            "cas",
            "auth",
        }
        query_keys = {key.lower() for key in urllib.parse.parse_qs(parsed.query).keys()}
        redirect_markers = {
            "next",
            "redirect",
            "redirect_uri",
            "return",
            "return_to",
            "returnurl",
            "service",
            "continue",
            "callback",
        }
        if path_segments[-1] in auth_markers:
            return True
        return bool((set(path_segments) & auth_markers) and (query_keys & redirect_markers))

    async def _probe_webdav_redirect_outcome(
        self, target_url: str
    ) -> WebDavRedirectProbeOutcome:
        if self.api_client is None:
            return WebDavRedirectProbeOutcome(
                location=None, miss_reason="api_client_unavailable"
            )

        headers = {"Authorization": self.play_webdav_auth_header}
        methods = ("HEAD", "GET")
        last_outcome = WebDavRedirectProbeOutcome(
            location=None, miss_reason="miss", probe_method="HEAD"
        )
        for method in methods:
            resp: Optional[httpx.Response] = None
            try:
                req = self.api_client.http_client.build_request(
                    method,
                    target_url,
                    headers=headers,
                )
                resp = await self.api_client.http_client.send(
                    req,
                    stream=(method == "GET"),
                    follow_redirects=False,
                )
                location, location_reason = self.inspect_webdav_redirect_location(
                    resp.headers.get("location", ""), str(resp.request.url)
                )
                if location and self.is_auth_like_webdav_redirect_location(location):
                    location = None
                    location_reason = "auth_login_redirect"
                if resp.status_code in {301, 302, 307, 308} and location:
                    return WebDavRedirectProbeOutcome(
                        location=location,
                        status_code=int(resp.status_code),
                        probe_method=method,
                    )
                if resp.status_code in {301, 302, 307, 308}:
                    last_outcome = WebDavRedirectProbeOutcome(
                        location=None,
                        miss_reason=location_reason or "missing_location",
                        status_code=int(resp.status_code),
                        probe_method=method,
                    )
                    if method == "HEAD":
                        continue
                    return last_outcome
                if resp.status_code in {401, 403}:
                    return WebDavRedirectProbeOutcome(
                        location=None,
                        miss_reason="auth_required",
                        status_code=int(resp.status_code),
                        probe_method=method,
                    )
                if resp.status_code == 404:
                    return WebDavRedirectProbeOutcome(
                        location=None,
                        miss_reason="not_found",
                        status_code=int(resp.status_code),
                        probe_method=method,
                    )
                last_outcome = WebDavRedirectProbeOutcome(
                    location=None,
                    miss_reason="unexpected_status",
                    status_code=int(resp.status_code),
                    probe_method=method,
                )
                if method == "HEAD":
                    continue
                return last_outcome
            finally:
                if resp is not None:
                    try:
                        await resp.aclose()
                    except Exception:
                        pass

        return last_outcome

    async def _probe_webdav_redirect_url(self, target_url: str) -> Optional[str]:
        outcome = await self._probe_webdav_redirect_outcome(target_url)
        return outcome.location

    async def resolve_play_webdav_redirect_url(
        self,
        target_path: str,
        *,
        request_path: str,
        profile_decision: PlayProfileDecision,
        request: Optional[Request] = None,
    ) -> Optional[str]:
        if not self.should_attempt_webdav_redirect(profile_decision, request):
            return None

        cache = self.play_webdav_cache
        if cache is None:
            return None

        cached = cache.get(target_path)
        if cached:
            log_json(
                self.logger,
                logging.INFO,
                "play_webdav_redirect_cache_hit",
                path=request_path,
                webdav_target_path=target_path,
                play_profile=profile_decision.profile,
            )
            return cached

        target_url = self.build_webdav_target_url(target_path)
        try:
            outcome = await self._probe_webdav_redirect_outcome(target_url)
        except (httpx.TimeoutException, httpx.TransportError) as exc:
            log_json(
                self.logger,
                logging.WARNING,
                "play_webdav_redirect_probe_failed",
                path=request_path,
                webdav_target_path=target_path,
                play_profile=profile_decision.profile,
                error=str(exc),
            )
            return None
        except Exception as exc:
            log_json(
                self.logger,
                logging.WARNING,
                "play_webdav_redirect_probe_failed",
                path=request_path,
                webdav_target_path=target_path,
                play_profile=profile_decision.profile,
                error=str(exc),
            )
            return None

        location = outcome.location
        if not location:
            log_json(
                self.logger,
                logging.INFO,
                "play_webdav_redirect_miss",
                path=request_path,
                webdav_target_path=target_path,
                play_profile=profile_decision.profile,
                miss_reason=outcome.miss_reason or "miss",
                probe_method=outcome.probe_method,
                probe_status=outcome.status_code,
            )
            if self._stats:
                self._stats.inc("webdav_redirect_misses")
            return None

        cache.set(target_path, location)
        log_json(
            self.logger,
            logging.INFO,
            "play_webdav_redirect_hit",
            path=request_path,
            webdav_target_path=target_path,
            play_profile=profile_decision.profile,
            redirect_status=self.config.play_redirect_status,
        )
        if self._stats:
            self._stats.inc("webdav_redirect_hits")
        return location

    def _profile_streamer_and_wait(
        self, profile_decision: PlayProfileDecision
    ) -> Tuple[Optional[SliceStreamer], int]:
        if (
            profile_decision.profile == PlayProfileResolver.PROFILE_COMPAT
            and self.compat_streamer is not None
            and not profile_decision.compat_promoted
        ):
            return self.compat_streamer, int(self.config.play_compat_admission_wait_ms)
        return self.streamer, int(self.config.play_admission_wait_ms)

    @staticmethod
    def should_use_aggressive_compat(profile_decision: PlayProfileDecision) -> bool:
        return bool(
            profile_decision.profile == PlayProfileResolver.PROFILE_COMPAT
            and not profile_decision.compat_promoted
            and profile_decision.reason == "probe_override"
        )

    @staticmethod
    def should_preserve_range_semantics(
        profile_decision: PlayProfileDecision,
    ) -> bool:
        return bool(
            profile_decision.profile == PlayProfileResolver.PROFILE_COMPAT
            and profile_decision.reason == "non_emby_direct"
        )

    @classmethod
    def compat_tail_expand_bytes(
        cls,
        profile_decision: PlayProfileDecision,
        configured_bytes: int,
        file_size: int,
    ) -> int:
        limit = max(1, int(configured_bytes))
        if profile_decision.compat_promoted:
            limit = min(limit, int(cls.AUTO_PROMOTED_TAIL_EXPAND_MAX_BYTES))
        return min(max(1, int(file_size)), limit)

    @classmethod
    def is_tiny_initial_probe_range(cls, range_shape: "RequestRangeShape") -> bool:
        requested_start = (
            int(range_shape.requested_start)
            if range_shape.requested_start is not None
            else -1
        )
        requested_end = (
            int(range_shape.requested_end)
            if range_shape.requested_end is not None
            else -1
        )
        return bool(
            range_shape.range_kind == "explicit"
            and requested_start == 0
            and requested_end == cls.TINY_PROBE_EXPLICIT_END
        )

    async def maybe_override_probe_profile(
        self,
        user_agent: Optional[str],
        profile_decision: PlayProfileDecision,
        range_shape: "RequestRangeShape",
    ) -> Tuple[PlayProfileDecision, bool]:
        if profile_decision.profile != PlayProfileResolver.PROFILE_STANDARD:
            return profile_decision, False
        if not self.config.play_compat_enabled or self.compat_streamer is None:
            return profile_decision, False
        if not self.is_tiny_initial_probe_range(range_shape):
            return profile_decision, False

        await self.play_profile_resolver.promote_user_agent(user_agent)

        return (
            PlayProfileDecision(
                profile=PlayProfileResolver.PROFILE_COMPAT,
                ua_fingerprint=profile_decision.ua_fingerprint,
                compat_promoted=profile_decision.compat_promoted,
                reason="probe_override",
            ),
            True,
        )

    def should_skip_probe_override_for_request(
        self,
        request: Optional[Request],
    ) -> bool:
        return bool(
            self.config.play_redirect_scope == PLAY_REDIRECT_SCOPE_EMBY_ONLY
            and self.play_request_source(request) != PLAY_SOURCE_EMBY_PROXY
        )

    def maybe_force_non_emby_compat_profile(
        self,
        request: Optional[Request],
        profile_decision: PlayProfileDecision,
    ) -> Tuple[PlayProfileDecision, bool]:
        if profile_decision.profile != PlayProfileResolver.PROFILE_STANDARD:
            return profile_decision, False
        if not self.config.play_compat_enabled or self.compat_streamer is None:
            return profile_decision, False
        if self.config.play_redirect_scope != PLAY_REDIRECT_SCOPE_EMBY_ONLY:
            return profile_decision, False
        if self.play_request_source(request) == PLAY_SOURCE_EMBY_PROXY:
            return profile_decision, False
        return (
            PlayProfileDecision(
                profile=PlayProfileResolver.PROFILE_COMPAT,
                ua_fingerprint=profile_decision.ua_fingerprint,
                compat_promoted=profile_decision.compat_promoted,
                reason="non_emby_direct",
            ),
            True,
        )

    def should_emit_first_byte_warn(
        self,
        first_byte_ms: Any,
        *,
        disconnected: bool,
        request_elapsed_ms: float,
    ) -> bool:
        if not self.should_warn_first_byte(first_byte_ms, self.FIRST_BYTE_WARN_MS):
            return False
        if (
            disconnected
            and first_byte_ms is None
            and request_elapsed_ms <= float(self.config.play_disconnected_warn_grace_ms)
        ):
            return False
        return True

    def should_reset_download_client_after_disconnect(
        self,
        *,
        disconnected: bool,
        first_byte_ms: Any,
        bytes_sent: int,
        request_elapsed_ms: float,
        response_max_bytes: Optional[int],
        profile_decision: PlayProfileDecision,
    ) -> bool:
        if not disconnected or first_byte_ms is not None:
            return False
        if int(bytes_sent or 0) != 0:
            return False
        if profile_decision.profile != PlayProfileResolver.PROFILE_COMPAT:
            return False
        if request_elapsed_ms < max(
            5000.0, float(self.config.play_disconnected_warn_grace_ms) * 2.0
        ):
            return False
        try:
            response_bytes = int(response_max_bytes or 0)
        except Exception:
            response_bytes = 0
        if response_bytes <= int(self.config.play_compat_initial_probe_max_bytes):
            return False
        return True

    async def _resolve_play_target_with_retry(
        self,
        path: str,
        content_identity: Optional[str],
    ) -> Tuple[str, FileSliceInfo, Dict[str, Any]]:
        max_retries = 1
        for attempt in range(max_retries + 1):
            try:
                if self.config.play_force_restore_before_stream:
                    return await self.resolve_play_target_via_restore(
                        path, content_identity
                    )
                return await self._resolve_play_target(path, content_identity)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                if attempt >= max_retries:
                    raise
                self.path_cache.invalidate(path)
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_resolve_retry",
                    path=path,
                    attempt=attempt + 1,
                    max_retries=max_retries,
                    error=str(exc),
                )
        raise PathNotFoundError("play target resolve retries exhausted")

    async def resolve_play_target_via_restore(
        self,
        path: str,
        content_identity: Optional[str],
    ) -> Tuple[str, FileSliceInfo, Dict[str, Any]]:
        perf = {
            "resolve_path_ms": 0.0,
            "file_info_ms": 0.0,
            "cache_hit_file_info": 0,
            "play_restore_source": "remote",
            "play_restore_content_identity": "",
            "play_restore_path": "",
        }

        cid = str(content_identity or "").strip()
        if cid:
            log_json(
                self.logger,
                logging.INFO,
                "play_restore_forced",
                path=path,
                has_content_identity=True,
            )
        else:
            if self.config.play_no_cid_strategy == "error":
                raise PathNotFoundError(
                    "content_identity is required for forced restore mode"
                )

            resolve_start = time.perf_counter()
            source_identity = await self.api_client.resolve_path(path, self.path_cache)
            perf["resolve_path_ms"] = round(
                (time.perf_counter() - resolve_start) * 1000, 3
            )
            source_info, _ = await self.api_client.get_file_info_with_meta(
                source_identity
            )
            cid = str(source_info.content_identity or "").strip()
            log_json(
                self.logger,
                logging.INFO,
                "play_restore_no_cid_lookup",
                path=path,
                source_identity=source_identity,
                content_identity=cid,
            )
            if not cid:
                raise PathNotFoundError(
                    "content_identity not found for forced restore play"
                )

        filename = path.rsplit("/", 1)[-1]
        ext = ext_of(filename) or "bin"
        restore_path = f"{self.config.restore_dir.rstrip('/')}/{cid}.{ext}"
        perf["play_restore_content_identity"] = cid
        perf["play_restore_path"] = restore_path

        for attempt in range(2):
            play_entries = getattr(self.restore_manager, "play_entries", {}) or {}
            restore_source = "memory" if cid in play_entries else "remote"
            restored_identity = await self.restore_manager.ensure_restored_for_play(
                path, cid
            )
            if not restored_identity:
                raise PathNotFoundError("file deleted and auto-restore failed")
            try:
                info, info_meta = await self.api_client.get_file_info_with_meta(
                    restored_identity
                )
                perf["file_info_ms"] = info_meta.get("file_info_ms", 0.0)
                perf["cache_hit_file_info"] = 1 if info_meta.get("cache_hit") else 0
                perf["play_restore_source"] = restore_source
                return restored_identity, info, perf
            except ApiError as exc:
                if attempt == 0 and exc.status_code == 404:
                    self.restore_manager.invalidate_restore_state(
                        cid,
                        original_path=path,
                        restore_path=restore_path,
                        identity=restored_identity,
                        reason="play_restore_file_info_404",
                    )
                    continue
                raise

        raise PathNotFoundError("file deleted and auto-restore failed")

    def register_routes(self, app: FastAPI) -> None:
        @app.get("/scan")
        async def scan(path: str = "/") -> List[Dict[str, Any]]:
            try:
                normalized = normalize_remote_path(path)
                identity = await self.api_client.resolve_path(
                    normalized, self.path_cache
                )
                files = await self.api_client.list_files_by_id(identity)
                out = []
                for item in files:
                    name = str(item.get("name", ""))
                    if not name:
                        continue
                    p = (
                        f"{normalized.rstrip('/')}/{name}"
                        if normalized != "/"
                        else f"/{name}"
                    )
                    out.append(
                        {
                            "name": name,
                            "type": "directory" if item.get("dir") else "file",
                            "path": p,
                        }
                    )
                return out
            except PathNotFoundError as exc:
                raise HTTPException(status_code=404, detail=str(exc)) from exc
            except HTTPException:
                raise
            except Exception as exc:
                raise HTTPException(status_code=500, detail=str(exc)) from exc

        @app.get("/info/{path_b64}")
        async def info(path_b64: str) -> Dict[str, Any]:
            path = decode_path_b64(path_b64)
            try:
                identity = await self.api_client.resolve_path(path, self.path_cache)
                file_info = await self.api_client.get_file_info(identity)
            except PathNotFoundError as exc:
                raise HTTPException(status_code=404, detail=str(exc)) from exc
            except Exception as exc:
                raise HTTPException(status_code=500, detail=str(exc)) from exc

            return {
                "path": path,
                "identity": identity,
                "content_identity": file_info.content_identity,
                "size": file_info.size,
            }

        def _build_play_headers(
            *,
            media_type: str,
            file_size: int,
            max_bytes: Optional[int],
            start: int,
            end: int,
            status_code: int,
        ) -> Dict[str, str]:
            headers: Dict[str, str] = {
                "Content-Type": media_type,
                "Accept-Ranges": "bytes",
            }
            if file_size > 0 and max_bytes is not None:
                headers["Content-Length"] = str(max_bytes)
                if status_code == 206:
                    headers["Content-Range"] = f"bytes {start}-{end}/{file_size}"
            return headers

        async def _resolve_play_metadata(
            path: str,
            content_identity: Optional[str],
            range_header: Optional[str],
            *,
            prefer_restore: bool = False,
        ) -> Dict[str, Any]:
            try:
                if prefer_restore:
                    identity, file_info, perf = await self.resolve_play_target_via_restore(
                        path, content_identity
                    )
                else:
                    identity, file_info, perf = await self._resolve_play_target_with_retry(
                        path, content_identity
                    )
            except PathNotFoundError as exc:
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_target_not_found",
                    path=path,
                    has_content_identity=bool(content_identity),
                    error=str(exc),
                )
                raise HTTPException(status_code=404, detail=str(exc)) from exc
            except HTTPException:
                raise
            except Exception as exc:
                log_json(
                    self.logger,
                    logging.ERROR,
                    "play_target_error",
                    path=path,
                    has_content_identity=bool(content_identity),
                    error=str(exc),
                )
                raise HTTPException(status_code=500, detail=str(exc)) from exc

            if not file_info.cids:
                raise HTTPException(status_code=404, detail="No slices found")

            file_size = max(0, int(file_info.size))
            start, end, status_code = parse_range_header(
                range_header,
                file_size,
                relaxed=self.config.play_compat_range_relaxed,
            )

            if start < 0 or (file_size > 0 and start >= file_size):
                raise HTTPException(status_code=416, detail="Invalid range")

            skip_bytes, relevant_cids = compute_cid_offset(
                file_info.cids,
                file_info.chunk_sizes,
                file_size,
                start,
                end,
            )
            if not relevant_cids:
                raise HTTPException(status_code=416, detail="Invalid range")

            max_bytes: Optional[int]
            if file_size > 0:
                max_bytes = end - start + 1
            else:
                max_bytes = None

            return {
                "identity": identity,
                "file_info": file_info,
                "perf": perf,
                "file_size": file_size,
                "start": start,
                "end": end,
                "status_code": status_code,
                "skip_bytes": skip_bytes,
                "relevant_cids": relevant_cids,
                "max_bytes": max_bytes,
            }

        @app.head("/play/{path_b64}")
        async def play_head(
            path_b64: str, request: Request, content_identity: Optional[str] = None
        ) -> Response:
            try:
                path = decode_path_b64(path_b64)
            except Exception as exc:
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_invalid_path_b64",
                    error=str(exc),
                    path_b64_preview=path_b64[:160],
                )
                raise HTTPException(
                    status_code=400, detail="invalid path encoding"
                ) from exc

            # Some players issue probing HEAD with tiny/partial Range and then
            # incorrectly treat that partial Content-Length as full media length.
            # Force HEAD to always expose full-length metadata.
            meta = await _resolve_play_metadata(path, content_identity, None)
            media_type = media_type_for_path(path)
            headers = _build_play_headers(
                media_type=media_type,
                file_size=int(meta["file_size"]),
                max_bytes=meta["max_bytes"],
                start=int(meta["start"]),
                end=int(meta["end"]),
                status_code=int(meta["status_code"]),
            )
            return Response(
                status_code=int(meta["status_code"]),
                headers=headers,
                media_type=media_type,
            )

        @app.get("/play/{path_b64}")
        async def play(
            path_b64: str, request: Request, content_identity: Optional[str] = None
        ) -> Response:
            request_started = time.perf_counter()
            try:
                path = decode_path_b64(path_b64)
            except Exception as exc:
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_invalid_path_b64",
                    error=str(exc),
                    path_b64_preview=path_b64[:160],
                )
                raise HTTPException(
                    status_code=400, detail="invalid path encoding"
                ) from exc

            range_header = request.headers.get("Range")
            range_shape = parse_request_range_shape(
                range_header,
                relaxed=self.config.play_compat_range_relaxed,
            )
            user_agent = request.headers.get("User-Agent", "")
            profile_decision = await self.play_profile_resolver.resolve(user_agent)
            probe_override_applied = False
            if not self.should_skip_probe_override_for_request(request):
                profile_decision, probe_override_applied = (
                    await self.maybe_override_probe_profile(
                        user_agent, profile_decision, range_shape
                    )
                )
            profile_decision, non_emby_force_compat = (
                self.maybe_force_non_emby_compat_profile(request, profile_decision)
            )
            selected_streamer, admission_wait_override = (
                self._profile_streamer_and_wait(profile_decision)
            )
            if selected_streamer is None:
                raise HTTPException(
                    status_code=500, detail="streamer is not configured"
                )
            range_header_raw = None
            if self.logger.isEnabledFor(logging.DEBUG) and range_header:
                range_header_raw = str(range_header)[:200]

            base_log_fields: Dict[str, Any] = {
                "play_profile": profile_decision.profile,
                "play_profile_reason": profile_decision.reason,
                "ua_fingerprint": profile_decision.ua_fingerprint,
                "play_compat_promoted": profile_decision.compat_promoted,
            }
            if range_header_raw is not None:
                base_log_fields["range_header_raw"] = range_header_raw
            if probe_override_applied:
                log_json(
                    self.logger,
                    logging.INFO,
                    "play_compat_probe_override",
                    path=path,
                    range_kind=range_shape.range_kind,
                    requested_start=range_shape.requested_start,
                    requested_end=range_shape.requested_end,
                    requested_range=range_shape.raw,
                    **base_log_fields,
                )
            if non_emby_force_compat:
                log_json(
                    self.logger,
                    logging.INFO,
                    "play_non_emby_force_compat",
                    path=path,
                    **base_log_fields,
                )

            admission = await self.play_admission.acquire(
                wait_ms=admission_wait_override
            )
            admission_wait_ms = admission.wait_ms
            admission_active_requests = admission.active_requests
            admission_held = admission.accepted

            if not admission.accepted:
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_admission_rejected",
                    path=path,
                    has_content_identity=bool(content_identity),
                    play_admission_wait_ms=admission_wait_ms,
                    play_active_requests=admission_active_requests,
                    play_max_active_requests=self.config.play_max_active_requests,
                    rejected_total=admission.rejected_total,
                    **base_log_fields,
                )
                raise self.build_play_overloaded()

            async def _release_admission_if_needed() -> int:
                nonlocal admission_held
                if not admission_held:
                    return max(0, admission_active_requests)
                admission_held = False
                return await self.play_admission.release()

            async def _release_admission_background() -> None:
                try:
                    await _release_admission_if_needed()
                except Exception as exc:
                    log_json(
                        self.logger,
                        logging.ERROR,
                        "play_admission_release_failed",
                        path=path,
                        error=str(exc),
                    )

            restore_lease_cid = ""
            restore_lease_held = False

            async def _release_restore_lease_if_needed() -> None:
                nonlocal restore_lease_held
                if (
                    not restore_lease_held
                    or not restore_lease_cid
                    or self.restore_manager is None
                ):
                    return
                restore_lease_held = False
                try:
                    await self.restore_manager.release_play_lease(restore_lease_cid)
                except Exception as exc:
                    log_json(
                        self.logger,
                        logging.ERROR,
                        "play_restore_lease_release_failed",
                        path=path,
                        content_identity=restore_lease_cid,
                        error=str(exc),
                    )

            async def _release_resources_background() -> None:
                await _release_restore_lease_if_needed()
                await _release_admission_background()

            try:
                attempt_webdav_redirect = self.should_attempt_webdav_redirect(
                    profile_decision, request
                )
                meta = await _resolve_play_metadata(
                    path,
                    content_identity,
                    range_header,
                    prefer_restore=attempt_webdav_redirect,
                )
                identity = str(meta["identity"])
                file_info = meta["file_info"]
                perf = dict(meta["perf"])
                file_size = int(meta["file_size"])
                start = int(meta["start"])
                end = int(meta["end"])
                status_code = int(meta["status_code"])
                skip_bytes = int(meta["skip_bytes"])
                relevant_cids = list(meta["relevant_cids"])
                max_bytes = meta["max_bytes"]
                requested_range_start = start
                requested_range_max_bytes = max_bytes
                restore_lease_cid = str(
                    perf.get("play_restore_content_identity", "")
                ).strip()

                if attempt_webdav_redirect:
                    webdav_target_path = (
                        str(perf.get("play_restore_path", "")).strip() or path
                    )
                    direct_url = await self.resolve_play_webdav_redirect_url(
                        webdav_target_path,
                        request_path=path,
                        profile_decision=profile_decision,
                        request=request,
                    )
                    if direct_url:
                        await _release_admission_if_needed()
                        return Response(
                            status_code=int(self.config.play_redirect_status),
                            headers={"Location": direct_url},
                        )
                    elif self.config.effective_play_mode() == "redirect":
                        await _release_admission_if_needed()
                        raise HTTPException(
                            status_code=404, 
                            detail="WebDAV target not found and pure redirect mode is enabled"
                        )

                if restore_lease_cid and self.restore_manager is not None:
                    restore_lease_held = await self.restore_manager.acquire_play_lease(
                        restore_lease_cid,
                        original_path=path,
                        restore_path=str(perf.get("play_restore_path", "")).strip()
                        or None,
                        identity=identity,
                    )

                preserve_range_semantics = self.should_preserve_range_semantics(
                    profile_decision
                )

                if (
                    profile_decision.profile == PlayProfileResolver.PROFILE_COMPAT
                    and not preserve_range_semantics
                    and range_header
                    and requested_range_start == 0
                    and requested_range_max_bytes is not None
                    and int(requested_range_max_bytes) > 0
                    and int(requested_range_max_bytes)
                    <= int(self.config.play_compat_initial_probe_max_bytes)
                    and file_size > int(requested_range_max_bytes)
                ):
                    # Compat fallback: some players probe a tiny initial range and
                    # then treat that segment length as full duration.
                    start = 0
                    end = file_size - 1
                    status_code = 200
                    skip_bytes = 0
                    relevant_cids = list(file_info.cids)
                    max_bytes = file_size
                    log_json(
                        self.logger,
                        logging.INFO,
                        "play_compat_ignore_initial_range",
                        path=path,
                        ua_fingerprint=profile_decision.ua_fingerprint,
                        requested_range=range_header[:120],
                        play_profile=profile_decision.profile,
                        play_profile_reason=profile_decision.reason,
                        requested_max_bytes=requested_range_max_bytes,
                        compat_initial_probe_max_bytes=self.config.play_compat_initial_probe_max_bytes,
                    )

                compat_tail_probe_threshold = int(
                    self.config.play_compat_tail_probe_threshold_bytes
                )
                aggressive_compat = self.should_use_aggressive_compat(
                    profile_decision
                )
                compat_forced_full_response = False
                if (
                    aggressive_compat
                    and status_code == 206
                    and file_size > 0
                    and max_bytes is not None
                    and int(max_bytes) > 0
                    and end == (file_size - 1)
                ):
                    max_bytes_int = int(max_bytes)
                    if (
                        start <= 1
                        or max_bytes_int <= compat_tail_probe_threshold
                        or max_bytes_int >= max(1, file_size - 1)
                    ):
                        # Compat policy: probe-like EOF ranges should not be exposed as
                        # partial responses to avoid duration mis-detection in some players.
                        status_code = 200
                        start = 0
                        end = file_size - 1
                        skip_bytes = 0
                        relevant_cids = list(file_info.cids)
                        max_bytes = file_size
                        compat_forced_full_response = True
                        log_json(
                            self.logger,
                            logging.INFO,
                            "play_compat_force_full_response",
                            path=path,
                            ua_fingerprint=profile_decision.ua_fingerprint,
                            play_profile=profile_decision.profile,
                            play_profile_reason=profile_decision.reason,
                            trigger_range_kind=range_shape.range_kind,
                            requested_start=range_shape.requested_start,
                            requested_end=range_shape.requested_end,
                            requested_max_bytes=requested_range_max_bytes,
                            file_size=file_size,
                        )

                # Backup path: keep old compat tail expansion behavior if full-response
                # normalization did not apply.
                if (
                    not compat_forced_full_response
                    and not preserve_range_semantics
                    and profile_decision.profile == PlayProfileResolver.PROFILE_COMPAT
                    and status_code == 206
                    and file_size > 0
                    and max_bytes is not None
                    and 0 < int(max_bytes) <= compat_tail_probe_threshold
                    and end == (file_size - 1)
                ):
                    expanded_tail_bytes = self.compat_tail_expand_bytes(
                        profile_decision,
                        int(self.config.play_compat_tail_probe_expand_bytes),
                        file_size,
                    )
                    start = max(0, file_size - expanded_tail_bytes)
                    end = file_size - 1
                    status_code = 206
                    skip_bytes, relevant_cids = compute_cid_offset(
                        file_info.cids,
                        file_info.chunk_sizes,
                        file_size,
                        start,
                        end,
                    )
                    if not relevant_cids:
                        raise HTTPException(status_code=416, detail="Invalid range")
                    max_bytes = expanded_tail_bytes
                    log_json(
                        self.logger,
                        logging.INFO,
                        "play_compat_expand_tail_probe",
                        path=path,
                        ua_fingerprint=profile_decision.ua_fingerprint,
                        play_profile=profile_decision.profile,
                        play_profile_reason=profile_decision.reason,
                        trigger_range_kind=range_shape.range_kind,
                        requested_start=range_shape.requested_start,
                        requested_end=range_shape.requested_end,
                        requested_max_bytes=requested_range_max_bytes,
                        expanded_tail_bytes=expanded_tail_bytes,
                        threshold_bytes=self.config.play_compat_tail_probe_threshold_bytes,
                    )

                stream_metrics: Dict[str, Any] = {}
                stream_body = selected_streamer.iter_slices(
                    relevant_cids,
                    skip_bytes=skip_bytes,
                    max_bytes=max_bytes,
                    metrics=stream_metrics,
                )

                async def body_with_metrics() -> AsyncGenerator[bytes, None]:
                    disconnected = False
                    compat_promoted_now = False
                    bytes_sent = 0
                    try:
                        async for chunk in stream_body:
                            bytes_sent += len(chunk)
                            yield chunk
                    except asyncio.CancelledError:
                        disconnected = True
                        raise
                    finally:
                        if bytes_sent > 0 and self._stats:
                            self._stats.inc("play_bytes_sent", bytes_sent)
                        if not disconnected:
                            try:
                                disconnected = await request.is_disconnected()
                            except Exception:
                                disconnected = False
                        try:
                            await stream_body.aclose()
                        except Exception:
                            pass
                        await _release_restore_lease_if_needed()
                        await _release_admission_if_needed()
                        hit_slice = int(stream_metrics.get("cache_hit_slice_addr", 0))
                        total_slice = int(stream_metrics.get("slice_addr_total", 0))
                        hit_ratio = (
                            round((hit_slice / total_slice), 3)
                            if total_slice > 0
                            else 0.0
                        )
                        first_byte_ms = stream_metrics.get("first_byte_ms")
                        request_elapsed_ms = round(
                            (time.perf_counter() - request_started) * 1000, 3
                        )
                        disconnect_before_first_byte = bool(
                            disconnected and first_byte_ms is None
                        )
                        compat_promoted_now = await self.play_profile_resolver.observe_probe(
                            user_agent=user_agent,
                            disconnect_before_first_byte=disconnect_before_first_byte,
                            first_byte_ms=first_byte_ms,
                            admission_rejected=False,
                            disconnected=disconnected,
                            request_elapsed_ms=request_elapsed_ms,
                            range_start=requested_range_start,
                            range_max_bytes=requested_range_max_bytes,
                            bytes_sent=bytes_sent,
                        )
                        compat_promoted_flag = bool(
                            profile_decision.compat_promoted or compat_promoted_now
                        )
                        log_fields = dict(base_log_fields)
                        log_fields["play_compat_promoted"] = compat_promoted_flag
                        if compat_promoted_now:
                            log_json(
                                self.logger,
                                logging.INFO,
                                "play_compat_promoted",
                                path=path,
                                **log_fields,
                            )
                        if disconnected:
                            log_json(
                                self.logger,
                                logging.INFO,
                                "play_disconnected",
                                path=path,
                                identity=identity,
                                play_disconnect_before_first_byte=disconnect_before_first_byte,
                                play_admission_wait_ms=admission_wait_ms,
                                play_active_requests=admission_active_requests,
                                response_status_code=status_code,
                                response_max_bytes=max_bytes,
                                response_start=start,
                                response_end=end,
                                bytes_sent=bytes_sent,
                                range_kind=range_shape.range_kind,
                                range_suffix_len=range_shape.suffix_len,
                                **log_fields,
                            )
                        if self.should_emit_first_byte_warn(
                            first_byte_ms,
                            disconnected=disconnected,
                            request_elapsed_ms=request_elapsed_ms,
                        ):
                            log_json(
                                self.logger,
                                logging.WARNING,
                                "play_first_byte_warn",
                                path=path,
                                identity=identity,
                                first_byte_ms=first_byte_ms,
                                threshold_ms=self.FIRST_BYTE_WARN_MS,
                                prefetch_inflight=stream_metrics.get(
                                    "prefetch_inflight"
                                ),
                                play_disconnect_before_first_byte=disconnect_before_first_byte,
                                play_admission_wait_ms=admission_wait_ms,
                                play_active_requests=admission_active_requests,
                                **{
                                    "cache_hit.file_info": perf.get(
                                        "cache_hit_file_info"
                                    ),
                                    "cache_hit.slice_addr": hit_slice,
                                    "cache_hit.slice_addr_ratio": hit_ratio,
                                    "play_restore_source": perf.get(
                                        "play_restore_source"
                                    ),
                                },
                                response_status_code=status_code,
                                response_max_bytes=max_bytes,
                                bytes_sent=bytes_sent,
                                **log_fields,
                            )
                        log_json(
                            self.logger,
                            logging.INFO,
                            "play_perf",
                            path=path,
                            identity=identity,
                            resolve_path_ms=perf.get("resolve_path_ms"),
                            file_info_ms=perf.get("file_info_ms"),
                            addr_batch_ms=round(
                                float(stream_metrics.get("addr_batch_ms", 0.0)), 3
                            ),
                            first_byte_ms=first_byte_ms,
                            prefetch_inflight=stream_metrics.get("prefetch_inflight"),
                            play_disconnect_before_first_byte=disconnect_before_first_byte,
                            play_admission_wait_ms=admission_wait_ms,
                            play_active_requests=admission_active_requests,
                            response_status_code=status_code,
                            response_max_bytes=max_bytes,
                            response_start=start,
                            response_end=end,
                            bytes_sent=bytes_sent,
                            range_kind=range_shape.range_kind,
                            range_suffix_len=range_shape.suffix_len,
                            **{
                                "cache_hit.file_info": perf.get("cache_hit_file_info"),
                                "cache_hit.slice_addr": hit_slice,
                                "cache_hit.slice_addr_ratio": hit_ratio,
                                "play_restore_source": perf.get("play_restore_source"),
                            },
                            **log_fields,
                        )
                        if self.should_reset_download_client_after_disconnect(
                            disconnected=disconnected,
                            first_byte_ms=first_byte_ms,
                            bytes_sent=bytes_sent,
                            request_elapsed_ms=request_elapsed_ms,
                            response_max_bytes=max_bytes,
                            profile_decision=profile_decision,
                        ):
                            try:
                                await self.api_client.reset_download_client(
                                    reason="play_first_byte_stall"
                                )
                            except Exception as exc:
                                log_json(
                                    self.logger,
                                    logging.WARNING,
                                    "play_first_byte_stall_reset_failed",
                                    path=path,
                                    error=str(exc),
                                )

                media_type = media_type_for_path(path)
                headers = _build_play_headers(
                    media_type=media_type,
                    file_size=file_size,
                    max_bytes=max_bytes,
                    start=start,
                    end=end,
                    status_code=status_code,
                )
                return StreamingResponse(
                    body_with_metrics(),
                    media_type=media_type,
                    headers=headers,
                    status_code=status_code,
                    background=BackgroundTask(_release_resources_background),
                )
            except HTTPException:
                await _release_restore_lease_if_needed()
                await _release_admission_if_needed()
                raise
            except Exception:
                await _release_restore_lease_if_needed()
                await _release_admission_if_needed()
                raise

        @app.get("/stream")
        async def stream_proxy(
            url: str = Query(...), key: int = 0
        ) -> StreamingResponse:
            req = self.api_client.download_client.build_request("GET", url)
            resp = await self.api_client.download_client.send(req, stream=True)
            if resp.status_code not in range(200, 300):
                await resp.aclose()
                raise HTTPException(
                    status_code=502, detail=f"upstream status={resp.status_code}"
                )

            headers: Dict[str, str] = {"Accept-Ranges": "none"}
            if "content-length" in resp.headers:
                headers["Content-Length"] = resp.headers["content-length"]

            async def _iter() -> AsyncGenerator[bytes, None]:
                try:
                    if key > 0:
                        byte_key = key & 0xFF
                        trans = bytes([i ^ byte_key for i in range(256)])
                        async for chunk in resp.aiter_bytes():
                            yield chunk.translate(trans)
                    else:
                        async for chunk in resp.aiter_bytes():
                            yield chunk
                finally:
                    await resp.aclose()

            return StreamingResponse(_iter(), media_type="video/mp4", headers=headers)

        @app.delete("/delete/{path_b64}")
        async def delete(
            path_b64: str, identity: Optional[str] = Query(None)
        ) -> Dict[str, Any]:
            path = decode_path_b64(path_b64)
            try:
                final_identity = identity
                if not final_identity:
                    final_identity = await self.api_client.resolve_path(
                        path, self.path_cache
                    )
                await self.api_client.delete_item(
                    identity=final_identity, file_path=path
                )
                self.path_cache.invalidate_tree(path)
                return {"success": True, "path": path, "identity": final_identity}
            except PathNotFoundError:
                return {"success": False, "path": path, "error": "not found"}
            except Exception as exc:
                return {"success": False, "path": path, "error": str(exc)}

    async def _resolve_play_target(
        self, path: str, content_identity: Optional[str]
    ) -> Tuple[str, FileSliceInfo, Dict[str, Any]]:
        perf = {
            "resolve_path_ms": 0.0,
            "file_info_ms": 0.0,
            "cache_hit_file_info": 0,
            "play_restore_source": "path",
        }
        resolve_start = time.perf_counter()
        identity: Optional[str] = None

        async def _load_info(
            file_identity: str,
        ) -> Tuple[FileSliceInfo, Dict[str, Any]]:
            info, info_meta = await self.api_client.get_file_info_with_meta(
                file_identity
            )
            perf["file_info_ms"] = info_meta.get("file_info_ms", 0.0)
            perf["cache_hit_file_info"] = 1 if info_meta.get("cache_hit") else 0
            return info, info_meta

        async def _restore_from_content_id(
            reason: str,
        ) -> Tuple[str, FileSliceInfo, Dict[str, Any]]:
            if not content_identity:
                raise PathNotFoundError(reason)

            restored_identity = await self.restore_manager.restore_file_from_cid(
                path, content_identity
            )
            if not restored_identity:
                raise PathNotFoundError("file deleted and auto-restore failed")

            perf["resolve_path_ms"] = round(
                (time.perf_counter() - resolve_start) * 1000, 3
            )
            info, _ = await _load_info(restored_identity)
            return restored_identity, info, perf

        try:
            identity = await self.api_client.resolve_path(path, self.path_cache)
            perf["resolve_path_ms"] = round(
                (time.perf_counter() - resolve_start) * 1000, 3
            )
            info, _ = await _load_info(identity)
            if not info.cids and content_identity:
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_empty_slices_restore",
                    path=path,
                    identity=identity,
                )
                self.path_cache.invalidate(path)
                if self.api_client.file_info_cache:
                    self.api_client.file_info_cache.invalidate(identity)
                return await _restore_from_content_id("empty slices")
            return identity, info, perf
        except PathNotFoundError:
            return await _restore_from_content_id("path not found")
        except ApiError as exc:
            if content_identity and exc.status_code in {400, 404}:
                log_json(
                    self.logger,
                    logging.WARNING,
                    "play_stale_identity_restore",
                    path=path,
                    status_code=exc.status_code,
                    error=str(exc),
                )
                self.path_cache.invalidate(path)
                if identity and self.api_client.file_info_cache:
                    self.api_client.file_info_cache.invalidate(identity)
                return await _restore_from_content_id("stale identity")
            raise


class EmbyPlaybackProxyService:
    HOP_BY_HOP_HEADERS = {
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
    }
    WEBSOCKET_REQUEST_SKIP_HEADERS = HOP_BY_HOP_HEADERS | {
        "host",
        "content-length",
        "sec-websocket-key",
        "sec-websocket-version",
        "sec-websocket-extensions",
        "sec-websocket-protocol",
    }

    def __init__(
        self,
        config: AppConfig,
        *,
        http_client: Optional[httpx.AsyncClient] = None,
        time_provider: Any = None,
        stats: Optional[StatsStore] = None,
    ):
        self.config = config
        self._stats = stats
        self.logger = logging.getLogger("twoland.emby_proxy")
        self.emby_server_url = str(config.emby_server_url or "").rstrip("/")
        self.playback_cache = EmbyPlaybackInfoCache(
            ttl_seconds=int(config.emby_proxy_playback_cache_ttl_sec),
            time_provider=time_provider,
        )
        self._owns_http_client = http_client is None
        if http_client is None:
            timeout = httpx.Timeout(config.stream_timeout_sec)
            http_client = httpx.AsyncClient(timeout=timeout, follow_redirects=False)
        self.http_client = http_client

    async def close(self) -> None:
        if self._owns_http_client:
            await self.http_client.aclose()

    @staticmethod
    def _filtered_request_headers(request: Request) -> Dict[str, str]:
        headers: Dict[str, str] = {}
        for key, value in request.headers.items():
            key_l = key.lower()
            if key_l in EmbyPlaybackProxyService.HOP_BY_HOP_HEADERS:
                continue
            if key_l in {"host", "content-length"}:
                continue
            headers[key] = value
        return headers

    @staticmethod
    def _filtered_response_headers(
        headers: httpx.Headers,
        *,
        drop_content_length: bool = False,
        drop_content_encoding: bool = False,
    ) -> Dict[str, str]:
        result: Dict[str, str] = {}
        for key, value in headers.items():
            key_l = key.lower()
            if key_l in EmbyPlaybackProxyService.HOP_BY_HOP_HEADERS:
                continue
            if drop_content_length and key_l == "content-length":
                continue
            if drop_content_encoding and key_l == "content-encoding":
                continue
            result[key] = value
        return result

    def _build_upstream_url(self, request: Request) -> str:
        url = f"{self.emby_server_url}{request.url.path}"
        if request.url.query:
            url = f"{url}?{request.url.query}"
        return url

    def _build_upstream_ws_url(self, websocket: WebSocket) -> str:
        parsed = urllib.parse.urlparse(self.emby_server_url)
        scheme = "wss" if parsed.scheme == "https" else "ws"
        base_path = str(parsed.path or "").rstrip("/")
        path = str(websocket.url.path or "")
        url = f"{scheme}://{parsed.netloc}{base_path}{path}"
        if websocket.url.query:
            url = f"{url}?{websocket.url.query}"
        return url

    @classmethod
    def _filtered_websocket_request_headers(
        cls, websocket: WebSocket
    ) -> Dict[str, str]:
        headers: Dict[str, str] = {}
        for key, value in websocket.headers.items():
            key_l = key.lower()
            if key_l in cls.WEBSOCKET_REQUEST_SKIP_HEADERS:
                continue
            headers[key] = value
        return headers

    @staticmethod
    def _requested_websocket_subprotocols(websocket: WebSocket) -> List[str]:
        raw = str(websocket.headers.get("sec-websocket-protocol", "")).strip()
        if not raw:
            return []
        return [x.strip() for x in raw.split(",") if x.strip()]

    @staticmethod
    def _websocket_connect_kwargs(headers: Dict[str, str]) -> Dict[str, Any]:
        signature = inspect.signature(websockets.connect)
        if "additional_headers" in signature.parameters:
            return {"additional_headers": headers or None}
        if "extra_headers" in signature.parameters:
            return {"extra_headers": headers or None}
        return {}

    @staticmethod
    def _request_origin(request: Request) -> str:
        parsed = urllib.parse.urlparse(str(request.base_url))
        scheme = str(parsed.scheme or "http").strip() or "http"
        netloc = str(parsed.netloc or "").strip()
        forwarded_proto = (
            str(request.headers.get("x-forwarded-proto", "")).split(",")[0].strip()
        )
        forwarded_host = (
            str(request.headers.get("x-forwarded-host", "")).split(",")[0].strip()
        )
        if forwarded_proto:
            scheme = forwarded_proto
        if forwarded_host:
            netloc = forwarded_host
        return f"{scheme}://{netloc}" if netloc else f"{scheme}://localhost"

    @staticmethod
    def _extract_play_target(candidate: str) -> str:
        value = str(candidate or "").strip()
        if not value:
            return ""
        parsed = urllib.parse.urlparse(value)
        if parsed.scheme:
            if parsed.scheme not in {"http", "https"} or not parsed.netloc:
                return ""
        elif not value.startswith("/"):
            return ""
        if not parsed.path.startswith("/play/"):
            return ""
        query = urllib.parse.parse_qs(parsed.query)
        if not query.get("content_identity"):
            return ""
        if parsed.query:
            return f"{parsed.path}?{parsed.query}"
        return parsed.path

    @staticmethod
    def _tag_play_target(play_target: str, play_source: str) -> str:
        target = str(play_target or "").strip()
        source = str(play_source or "").strip().lower()
        if not target or not source:
            return target
        return append_query_params(target, {PLAY_SOURCE_QUERY_PARAM: source})

    @classmethod
    def _build_play_url(cls, play_target: str, request: Request) -> str:
        target = str(play_target or "").strip()
        if not target:
            return ""
        origin = cls._request_origin(request).rstrip("/")
        return f"{origin}{target if target.startswith('/') else '/' + target}"

    @classmethod
    def _normalize_play_url(
        cls, candidate: str, request: Request, *, play_source: str = ""
    ) -> str:
        play_target = cls._extract_play_target(candidate)
        if not play_target:
            return ""
        if play_source:
            play_target = cls._tag_play_target(play_target, play_source)
        return cls._build_play_url(play_target, request)

    @classmethod
    def _rewrite_media_source_play_urls(
        cls, media_source: Dict[str, Any], request: Request
    ) -> bool:
        changed = False
        if not isinstance(media_source, dict):
            return changed
        for key in ("Path", "DirectStreamUrl"):
            candidate = str(media_source.get(key) or "").strip()
            if not candidate:
                continue
            rewritten = cls._normalize_play_url(
                candidate, request, play_source=PLAY_SOURCE_EMBY_PROXY
            )
            if not rewritten or rewritten == candidate:
                continue
            media_source[key] = rewritten
            changed = True
        return changed

    def _extract_play_url_from_media_source(
        self, media_source: Dict[str, Any], request: Request
    ) -> str:
        if not isinstance(media_source, dict):
            return ""
        candidates = [
            media_source.get("Path"),
            media_source.get("DirectStreamUrl"),
        ]
        for candidate in candidates:
            play_target = self._extract_play_target(str(candidate or ""))
            if play_target:
                return play_target
        return ""

    def _cache_playback_info_sources(
        self, item_id: str, payload: Dict[str, Any], request: Request
    ) -> Tuple[int, bool]:
        media_sources = payload.get("MediaSources")
        if not isinstance(media_sources, list):
            return 0, False
        cached = 0
        rewritten = False
        for media_source in media_sources:
            if not isinstance(media_source, dict):
                continue
            rewritten = (
                self._rewrite_media_source_play_urls(media_source, request) or rewritten
            )
            media_source_id = str(
                media_source.get("Id") or media_source.get("MediaSourceId") or ""
            ).strip()
            if not media_source_id:
                continue
            play_target = self._extract_play_url_from_media_source(
                media_source, request
            )
            if not play_target:
                continue
            play_target = self._tag_play_target(
                play_target, PLAY_SOURCE_EMBY_PROXY
            )
            self.playback_cache.set(item_id, media_source_id, play_target)
            if self._stats:
                self._stats.inc("emby_playback_cache_misses")
            cached += 1
        return cached, rewritten

    async def _proxy_upstream(
        self, request: Request, *, stream: bool
    ) -> httpx.Response:
        request_headers = self._filtered_request_headers(request)
        body = b""
        if request.method not in {"GET", "HEAD"}:
            body = await request.body()
        upstream_request = self.http_client.build_request(
            request.method,
            self._build_upstream_url(request),
            headers=request_headers,
            content=body,
        )
        return await self.http_client.send(upstream_request, stream=stream)

    async def handle_playback_info(
        self, request: Request, item_id: str
    ) -> Response:
        resp = await self._proxy_upstream(request, stream=False)
        try:
            content = await resp.aread()
            if resp.status_code in range(200, 300):
                try:
                    payload = json.loads(content.decode("utf-8"))
                    cached, rewritten = self._cache_playback_info_sources(
                        item_id, payload, request
                    )
                    if rewritten:
                        content = json.dumps(payload).encode("utf-8")
                    if cached:
                        log_json(
                            self.logger,
                            logging.INFO,
                            "emby_playback_info_cached",
                            item_id=item_id,
                            cached_sources=cached,
                        )
                except Exception as exc:
                    log_json(
                        self.logger,
                        logging.WARNING,
                        "emby_playback_info_cache_failed",
                        item_id=item_id,
                        error=str(exc),
                    )
            headers = self._filtered_response_headers(
                resp.headers,
                drop_content_length=True,
                drop_content_encoding=True,
            )
            return Response(content=content, status_code=resp.status_code, headers=headers)
        finally:
            await resp.aclose()

    async def handle_video_request(
        self, request: Request, item_id: str, video_name: str
    ) -> Response:
        media_source_id = str(request.query_params.get("MediaSourceId", "")).strip()
        if video_name.lower().startswith("original") and media_source_id:
            play_target = self.playback_cache.get(item_id, media_source_id)
            if play_target:
                if self._stats:
                    self._stats.inc("emby_redirect_total")
                    self._stats.inc("emby_playback_cache_hits")
                play_url = self._build_play_url(play_target, request)
                log_json(
                    self.logger,
                    logging.INFO,
                    "emby_video_redirect_hit",
                    item_id=item_id,
                    media_source_id=media_source_id,
                    redirect_status=self.config.emby_proxy_redirect_status,
                    play_url=play_url,
                )
                return Response(
                    status_code=int(self.config.emby_proxy_redirect_status),
                    headers={"Location": play_url},
                )
            log_json(
                self.logger,
                logging.INFO,
                "emby_video_redirect_miss",
                item_id=item_id,
                media_source_id=media_source_id,
                reason="playback_cache_miss",
            )

        resp = await self._proxy_upstream(request, stream=True)
        headers = self._filtered_response_headers(
            resp.headers,
            drop_content_length=True,
        )
        if request.method == "HEAD":
            try:
                content = await resp.aread()
            finally:
                await resp.aclose()
            return Response(content=content, status_code=resp.status_code, headers=headers)
        return StreamingResponse(
            resp.aiter_raw(),
            status_code=resp.status_code,
            headers=headers,
            background=BackgroundTask(resp.aclose),
        )

    async def handle_passthrough(self, request: Request) -> Response:
        upstream_request = await self._proxy_upstream(
            request,
            stream=request.method in {"GET", "HEAD"},
        )
        headers = self._filtered_response_headers(upstream_request.headers)
        if request.method == "HEAD":
            try:
                content = await upstream_request.aread()
            finally:
                await upstream_request.aclose()
            return Response(
                content=content,
                status_code=upstream_request.status_code,
                headers=headers,
            )
        if request.method == "GET":
            return StreamingResponse(
                upstream_request.aiter_raw(),
                status_code=upstream_request.status_code,
                headers=headers,
                background=BackgroundTask(upstream_request.aclose),
            )
        try:
            content = await upstream_request.aread()
            return Response(
                content=content,
                status_code=upstream_request.status_code,
                headers=headers,
            )
        finally:
            await upstream_request.aclose()

    async def handle_websocket_passthrough(self, websocket: WebSocket) -> None:
        upstream_url = self._build_upstream_ws_url(websocket)
        requested_subprotocols = self._requested_websocket_subprotocols(websocket)
        request_headers = self._filtered_websocket_request_headers(websocket)

        try:
            upstream_ws = await websockets.connect(
                upstream_url,
                subprotocols=requested_subprotocols or None,
                open_timeout=self.config.stream_timeout_sec,
                **self._websocket_connect_kwargs(request_headers),
            )
        except Exception as exc:
            log_json(
                self.logger,
                logging.WARNING,
                "emby_websocket_connect_failed",
                path=str(websocket.url.path or ""),
                upstream_url=upstream_url,
                error=str(exc),
            )
            await websocket.close(code=1011, reason="upstream websocket connect failed")
            return

        async with upstream_ws:
            await websocket.accept(subprotocol=upstream_ws.subprotocol)

            async def _client_to_upstream() -> None:
                while True:
                    try:
                        message = await websocket.receive()
                    except WebSocketDisconnect:
                        return
                    message_type = str(message.get("type") or "")
                    if message_type == "websocket.disconnect":
                        return
                    if message_type != "websocket.receive":
                        continue
                    text_data = message.get("text")
                    bytes_data = message.get("bytes")
                    if text_data is not None:
                        await upstream_ws.send(text_data)
                    elif bytes_data is not None:
                        await upstream_ws.send(bytes_data)

            async def _upstream_to_client() -> None:
                try:
                    async for message in upstream_ws:
                        if isinstance(message, bytes):
                            await websocket.send_bytes(message)
                        else:
                            await websocket.send_text(message)
                except websockets.ConnectionClosed:
                    return

            client_task = asyncio.create_task(_client_to_upstream())
            upstream_task = asyncio.create_task(_upstream_to_client())
            done, pending = await asyncio.wait(
                {client_task, upstream_task},
                return_when=asyncio.FIRST_COMPLETED,
            )
            for task in pending:
                task.cancel()
            await asyncio.gather(*pending, return_exceptions=True)
            await asyncio.gather(*done, return_exceptions=True)

            try:
                if not upstream_ws.close_code:
                    await upstream_ws.close()
            except Exception:
                pass

            if websocket.application_state != WebSocketState.DISCONNECTED:
                try:
                    await websocket.close(
                        code=int(upstream_ws.close_code or 1000),
                        reason=str(upstream_ws.close_reason or "")[:123],
                    )
                except Exception:
                    pass

    def register_routes(self, app: FastAPI) -> None:
        @app.api_route("/emby/Items/{item_id}/PlaybackInfo", methods=["GET", "POST"])
        async def emby_playback_info(item_id: str, request: Request) -> Response:
            return await self.handle_playback_info(request, item_id)

        @app.api_route("/emby/videos/{item_id}/{video_name}", methods=["GET", "HEAD"])
        async def emby_video_proxy(
            item_id: str, video_name: str, request: Request
        ) -> Response:
            return await self.handle_video_request(request, item_id, video_name)

        @app.api_route(
            "/{proxy_path:path}",
            methods=["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"],
        )
        async def emby_passthrough(proxy_path: str, request: Request) -> Response:
            return await self.handle_passthrough(request)

        @app.websocket("/{proxy_path:path}")
        async def emby_websocket_passthrough(
            proxy_path: str, websocket: WebSocket
        ) -> None:
            await self.handle_websocket_passthrough(websocket)


class JobScheduler:
    def __init__(
        self,
        pipeline: StrmPipeline,
        mappings: List[MappingConfig],
        auto_confirm: bool,
        interval_sec: int,
        scan_history: Optional[ScanHistoryStore] = None,
    ):
        self.pipeline = pipeline
        self.mappings = mappings
        self.auto_confirm = auto_confirm
        self.interval_sec = interval_sec
        self.scan_history = scan_history
        self.logger = logging.getLogger("twoland.scheduler")
        self._task: Optional[asyncio.Task[Any]] = None
        self._stop = asyncio.Event()
        # 扫描状态追踪（供管理 API 读取）
        self._scan_running: bool = False
        self._last_scan_task_id: Optional[str] = None
        self._last_scan_start: Optional[float] = None
        self._last_scan_duration: Optional[float] = None
        self._last_scan_stats: Optional[ScanStats] = None

    def _record_scan_start(self, task_id: str, trigger: str) -> None:
        """扫描开始时写入历史记录。"""
        if self.scan_history:
            record = ScanRecord(
                task_id=task_id,
                trigger=trigger,
                started_at=time.time(),
                status="running",
            )
            self.scan_history.add(record)

    def _record_scan_finish(
        self, task_id: str, stats: Optional[ScanStats], error: Optional[str] = None
    ) -> None:
        """扫描完成时更新历史记录。"""
        if self.scan_history:
            now = time.time()
            update_kwargs: Dict[str, Any] = {
                "finished_at": now,
                "status": "error" if error else "completed",
                "error": error,
            }
            if error is None and stats is not None:
                update_kwargs.update({
                    "scanned": stats.scanned,
                    "created_strm": stats.created_strm,
                    "downloaded_extras": stats.downloaded_extras,
                    "skipped_exists": stats.skipped_exists,
                    "skipped_small": stats.skipped_small,
                    "deleted": stats.deleted,
                    "errors": stats.errors,
                })
            # 计算时长
            records = self.scan_history.get_history(limit=1000)
            for r in records:
                if r.task_id == task_id:
                    update_kwargs["duration_sec"] = now - r.started_at
                    break
            self.scan_history.update(task_id, **update_kwargs)

    async def start(self) -> None:
        if self._task:
            return
        self._stop.clear()
        self._task = asyncio.create_task(self._run_loop())

    async def stop(self) -> None:
        self._stop.set()
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            self._task = None

    async def _run_loop(self) -> None:
        await self._run_once_safe()

        while not self._stop.is_set() and self.interval_sec > 0:
            try:
                await asyncio.wait_for(self._stop.wait(), timeout=self.interval_sec)
                break
            except asyncio.TimeoutError:
                await self._run_once_safe()

    async def _run_once_safe(self) -> None:
        import uuid
        task_id = str(uuid.uuid4())[:8]
        self._scan_running = True
        self._last_scan_task_id = task_id
        self._last_scan_start = time.time()
        self._record_scan_start(task_id, "scheduled")
        try:
            stats = await self.pipeline.run_once(self.mappings, self.auto_confirm)
            self._last_scan_stats = stats
            self._record_scan_finish(task_id, stats)
        except Exception as exc:
            log_json(
                self.logger, logging.ERROR, "scheduled_scan_failed", error=str(exc)
            )
            self._record_scan_finish(task_id, None, error=str(exc))
        finally:
            self._scan_running = False
            if self._last_scan_start:
                self._last_scan_duration = time.time() - self._last_scan_start

    async def trigger_now(
        self, mappings: Optional[List[MappingConfig]] = None, auto_confirm: Optional[bool] = None
    ) -> str:
        """手动触发一次扫描，返回 task_id。如果当前正在扫描则排队等待。"""
        import uuid
        task_id = f"manual-{uuid.uuid4().hex[:8]}"

        # 如果当前没有扫描在跑，立刻标记为 running
        if not self._scan_running:
            self._scan_running = True
            self._last_scan_task_id = task_id
            self._last_scan_start = time.time()
            self._last_scan_duration = None
        self._record_scan_start(task_id, "manual")

        async def _do():
            # 如果有扫描在跑（_run_lock 会被占），run_once 会排队等锁
            was_waiting = self._scan_running and self.pipeline._run_lock.locked()
            if was_waiting:
                log_json(self.logger, logging.INFO, "manual_scan_queued", task_id=task_id)

            if not self._scan_running:
                self._scan_running = True
                self._last_scan_task_id = task_id
                self._last_scan_start = time.time()
                self._last_scan_duration = None

            try:
                stats = await self.pipeline.run_once(
                    mappings or self.mappings,
                    auto_confirm if auto_confirm is not None else self.auto_confirm,
                )
                self._last_scan_stats = stats
                self._record_scan_finish(task_id, stats)
            except Exception as exc:
                log_json(
                    self.logger, logging.ERROR, "manual_scan_failed", error=str(exc)
                )
                self._record_scan_finish(task_id, None, error=str(exc))
            finally:
                self._scan_running = False
                if self._last_scan_start:
                    self._last_scan_duration = time.time() - self._last_scan_start

        asyncio.create_task(_do())
        return task_id


def parse_range_header(
    header: Optional[str],
    file_size: int,
    *,
    relaxed: bool = False,
) -> Tuple[int, int, int]:
    if file_size <= 0:
        return 0, 0, 200

    if not header:
        return 0, file_size - 1, 200

    raw_header = str(header).strip()
    if relaxed:
        compact = "".join(raw_header.split())
        if not compact.lower().startswith("bytes="):
            return 0, file_size - 1, 200
        range_part = compact[6:]
    else:
        if not raw_header.startswith("bytes="):
            return 0, file_size - 1, 200
        range_part = raw_header[6:].strip()

    if "," in range_part:
        range_part = range_part.split(",", 1)[0].strip()

    start_s, _, end_s = range_part.partition("-")

    if not start_s:
        # suffix bytes: bytes=-500
        try:
            suffix = int(end_s)
        except Exception:
            return 0, file_size - 1, 200
        if suffix <= 0:
            return 0, file_size - 1, 200
        start = max(0, file_size - suffix)
        end = file_size - 1
        return start, end, 206

    try:
        start = int(start_s)
    except Exception:
        return 0, file_size - 1, 200

    if end_s:
        try:
            end = int(end_s)
        except Exception:
            end = file_size - 1
    else:
        end = file_size - 1

    if end >= file_size:
        end = file_size - 1

    if end < start:
        end = start

    return start, end, 206


@dataclass
class RequestRangeShape:
    range_kind: str
    suffix_len: Optional[int]
    requested_start: Optional[int]
    requested_end: Optional[int]
    raw: str


def parse_request_range_shape(
    header: Optional[str],
    *,
    relaxed: bool = True,
    raw_limit: int = 200,
) -> RequestRangeShape:
    raw_header = str(header or "").strip()
    raw = raw_header[: max(1, int(raw_limit))]
    if not raw_header:
        return RequestRangeShape(
            range_kind="none",
            suffix_len=None,
            requested_start=None,
            requested_end=None,
            raw=raw,
        )

    if relaxed:
        compact = "".join(raw_header.split())
        if not compact.lower().startswith("bytes="):
            return RequestRangeShape(
                range_kind="none",
                suffix_len=None,
                requested_start=None,
                requested_end=None,
                raw=raw,
            )
        range_part = compact[6:]
    else:
        if not raw_header.startswith("bytes="):
            return RequestRangeShape(
                range_kind="none",
                suffix_len=None,
                requested_start=None,
                requested_end=None,
                raw=raw,
            )
        range_part = raw_header[6:].strip()

    if "," in range_part:
        range_part = range_part.split(",", 1)[0].strip()
    if not range_part:
        return RequestRangeShape(
            range_kind="none",
            suffix_len=None,
            requested_start=None,
            requested_end=None,
            raw=raw,
        )

    start_s, sep, end_s = range_part.partition("-")
    if sep != "-":
        return RequestRangeShape(
            range_kind="none",
            suffix_len=None,
            requested_start=None,
            requested_end=None,
            raw=raw,
        )

    if not start_s:
        try:
            suffix_len = int(end_s)
        except Exception:
            return RequestRangeShape(
                range_kind="none",
                suffix_len=None,
                requested_start=None,
                requested_end=None,
                raw=raw,
            )
        if suffix_len <= 0:
            return RequestRangeShape(
                range_kind="none",
                suffix_len=None,
                requested_start=None,
                requested_end=None,
                raw=raw,
            )
        return RequestRangeShape(
            range_kind="suffix",
            suffix_len=suffix_len,
            requested_start=None,
            requested_end=None,
            raw=raw,
        )

    try:
        start = int(start_s)
    except Exception:
        return RequestRangeShape(
            range_kind="none",
            suffix_len=None,
            requested_start=None,
            requested_end=None,
            raw=raw,
        )

    if not end_s:
        return RequestRangeShape(
            range_kind="prefix",
            suffix_len=None,
            requested_start=start,
            requested_end=None,
            raw=raw,
        )

    try:
        end = int(end_s)
    except Exception:
        end = None
    return RequestRangeShape(
        range_kind="explicit",
        suffix_len=None,
        requested_start=start,
        requested_end=end,
        raw=raw,
    )


def compute_cid_offset(
    cids: List[str],
    chunk_sizes: List[Dict[str, int]],
    file_size: int,
    start_byte: int,
    end_byte: Optional[int] = None,
) -> Tuple[int, List[str]]:
    if not cids:
        return 0, []

    chunk_size_map: Dict[int, int] = {}
    prev_end = -1
    for entry in chunk_sizes:
        end_idx = int(entry.get("end_index", 0))
        size = int(entry.get("size", 0))
        if size <= 0:
            continue
        for idx in range(prev_end + 1, end_idx + 1):
            chunk_size_map[idx] = size
        prev_end = max(prev_end, end_idx)

    default_size = 4 * 1024 * 1024
    offsets: List[Tuple[int, int, int]] = []
    current = 0
    for idx, _ in enumerate(cids):
        size = chunk_size_map.get(idx, default_size)
        if idx == len(cids) - 1 and file_size > 0:
            size = max(1, file_size - current)
        start = current
        end = current + size - 1
        offsets.append((idx, start, end))
        current += size

    start_idx = 0
    skip_bytes = 0
    for idx, start, end in offsets:
        if start <= start_byte <= end:
            start_idx = idx
            skip_bytes = start_byte - start
            break

    target_end = file_size - 1
    if end_byte is not None:
        try:
            target_end = max(start_byte, min(int(end_byte), file_size - 1))
        except Exception:
            target_end = file_size - 1

    end_idx = len(cids) - 1
    for idx, start, end in offsets[start_idx:]:
        if start <= target_end <= end:
            end_idx = idx
            break

    return skip_bytes, cids[start_idx : end_idx + 1]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="2dland unified tool")
    parser.add_argument("--config", default="config.json", help="Path to config JSON")

    subparsers = parser.add_subparsers(dest="command")

    def add_common_overrides(
        sp: argparse.ArgumentParser, *, include_loop: bool
    ) -> None:
        sp.add_argument(
            "--root",
            default=None,
            help="Override mappings and scan only this remote root",
        )
        sp.add_argument(
            "--extras", choices=["keep", "download", "delete"], default=None
        )
        sp.add_argument("--media", choices=["keep", "delete"], default=None)
        sp.add_argument("--yes", action="store_true", help="Auto confirm delete")
        sp.add_argument(
            "--min-size",
            type=int,
            default=None,
            help="Override minimum media size in MB",
        )
        sp.add_argument(
            "--public-strm-host", default=None, help="Override public STRM host"
        )
        sp.add_argument(
            "--proxy-port", type=int, default=None, help="Override proxy port"
        )
        sp.add_argument(
            "--log-level", choices=["DEBUG", "INFO", "WARNING", "ERROR"], default=None
        )
        if include_loop:
            sp.add_argument(
                "--loop",
                type=int,
                default=None,
                help="Override loop interval in seconds",
            )

    serve = subparsers.add_parser("serve", help="Run proxy service and scheduled scan")
    serve.add_argument("--host", default="auto")
    serve.add_argument("--port", type=int, default=None)
    serve.add_argument("--admin-port", type=int, default=None, help="管理面板端口（默认 8898，0=关闭）")
    add_common_overrides(serve, include_loop=True)

    scan_once = subparsers.add_parser("scan-once", help="Run one scan+cleanup round")
    add_common_overrides(scan_once, include_loop=False)

    proxy_only = subparsers.add_parser("proxy-only", help="Run proxy API only")
    proxy_only.add_argument("--host", default="auto")
    proxy_only.add_argument("--port", type=int, default=None)
    proxy_only.add_argument(
        "--log-level", choices=["DEBUG", "INFO", "WARNING", "ERROR"], default=None
    )

    subparsers.add_parser(
        "validate-config", help="Validate config and print compatibility warnings"
    )

    return parser


def validate_warnings(config: AppConfig) -> List[str]:
    warnings: List[str] = []
    if not config.mappings:
        warnings.append(
            "No mappings configured. scan-once/serve scanning will do nothing unless --root is provided."
        )
    if config.loop_interval <= 0:
        warnings.append(
            "loop_interval <= 0: scheduled scan will run only once at startup."
        )
    if not config.auto_confirm:
        warnings.append(
            "auto_confirm=false: deletion requires interactive confirmation."
        )
    if config.public_strm_host.startswith(
        "http://127.0.0.1"
    ) or config.public_strm_host.startswith("http://localhost"):
        warnings.append(
            "public_strm_host points to localhost; external players may not reach this host."
        )
    parsed_public_host = urllib.parse.urlparse(config.public_strm_host or "")
    public_hostname = str(parsed_public_host.hostname or "").strip()
    if public_hostname:
        try:
            host_ip = ipaddress.ip_address(public_hostname)
            if host_ip.is_private or host_ip.is_loopback or host_ip.is_link_local:
                warnings.append(
                    "public_strm_host points to a private/loopback address; "
                    "external players or DDNS clients may be redirected back into LAN. "
                    "Use your public domain or public IP for cross-network playback."
                )
        except ValueError:
            pass
    if config.credential_source == "legacy_fallback":
        warnings.append(
            "Credentials loaded from built-in legacy fallback. Add client_id/client_secret to config.json for explicit control."
        )
    if config.credential_source == "env_or_legacy_fallback":
        warnings.append(
            "Credentials were not fully loaded from config.json. Current run may be using environment or legacy fallback values."
        )
    if config.effective_play_mode() != config.play_mode:
        warnings.append(
            "play_webdav_enabled is using legacy compatibility mode. "
            f"effective_play_mode={config.effective_play_mode()}. "
            "Prefer setting play_mode explicitly to avoid ambiguous playback behavior."
        )
    return warnings


def resolve_server_bind_hosts(host: Optional[str]) -> List[str]:
    normalized = str(host or "").strip()
    if not normalized or normalized.lower() == "auto":
        return ["::", "0.0.0.0"]
    return [normalized]


def _socket_family_for_host(host: str) -> int:
    return socket.AF_INET6 if ":" in str(host or "") else socket.AF_INET


def _bind_server_socket(
    bind_host: str,
    port: int,
    *,
    force_ipv6_only: bool,
) -> Tuple[socket.socket, bool]:
    family = _socket_family_for_host(bind_host)
    sock = socket.socket(family=family, type=socket.SOCK_STREAM)
    dual_stack_ipv6 = False
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if family == socket.AF_INET6 and hasattr(socket, "IPPROTO_IPV6"):
            v6only_opt = getattr(socket, "IPV6_V6ONLY", None)
            if v6only_opt is not None:
                if force_ipv6_only:
                    try:
                        sock.setsockopt(socket.IPPROTO_IPV6, v6only_opt, 1)
                    except OSError:
                        pass
                try:
                    dual_stack_ipv6 = (
                        sock.getsockopt(socket.IPPROTO_IPV6, v6only_opt) == 0
                    )
                except OSError:
                    dual_stack_ipv6 = False
        sock.bind((bind_host, port))
        sock.set_inheritable(True)
        return sock, dual_stack_ipv6
    except Exception:
        try:
            sock.close()
        except Exception:
            pass
        raise


def _create_server_sockets(
    bind_hosts: List[str],
    port: int,
    server_logger: logging.Logger,
) -> List[socket.socket]:
    sockets: List[socket.socket] = []
    last_error: Optional[OSError] = None
    have_dual_stack_ipv6 = False
    total_hosts = len(bind_hosts)

    for bind_host in bind_hosts:
        try:
            log_json(
                server_logger,
                logging.INFO,
                "server_bind_attempt",
                host=bind_host,
                port=port,
            )
            sock, dual_stack_ipv6 = _bind_server_socket(
                bind_host,
                port,
                force_ipv6_only=(total_hosts > 1 and _socket_family_for_host(bind_host) == socket.AF_INET6),
            )
            sockets.append(sock)
            if dual_stack_ipv6:
                have_dual_stack_ipv6 = True
            log_json(
                server_logger,
                logging.INFO,
                "server_bind_success",
                host=bind_host,
                port=sock.getsockname()[1],
                family="ipv6" if sock.family == socket.AF_INET6 else "ipv4",
            )
        except OSError as exc:
            last_error = exc
            if (
                sockets
                and bind_host == "0.0.0.0"
                and exc.errno == errno.EADDRINUSE
                and have_dual_stack_ipv6
            ):
                log_json(
                    server_logger,
                    logging.INFO,
                    "server_bind_ipv4_covered_by_ipv6_dualstack",
                    host=bind_host,
                    port=port,
                )
                continue
            log_json(
                server_logger,
                logging.WARNING,
                "server_bind_skipped",
                host=bind_host,
                port=port,
                error=str(exc),
            )

    if sockets:
        return sockets
    if last_error is not None:
        raise last_error
    raise OSError(f"failed to bind any server socket on port {port}")


def create_runtime(
    config: AppConfig,
) -> Tuple[
    TwoLandApiClient,
    PathResolverCache,
    SliceStreamer,
    RestoreManager,
    StrmPipeline,
    ProxyService,
    Optional[EmbyPlaybackProxyService],
    StatsStore,
]:
    # 持久化统计
    config_dir = Path(getattr(config, "_config_path", "config.json")).parent
    stats = StatsStore(
        path=str(config_dir / "stats.json"),
        flush_interval_sec=60.0,
    )

    signer = AuthSigner(config.client_id, config.client_secret, config.api_host)
    path_cache = PathResolverCache(ttl_seconds=config.path_cache_ttl_sec)
    file_info_cache = FileInfoCache(
        ttl_seconds=config.file_info_cache_ttl_sec, max_entries=4096
    )
    slice_addr_cache = SliceAddressCache(
        ttl_seconds=config.slice_address_cache_ttl_sec, max_entries=20000
    )
    api_client = TwoLandApiClient(
        config,
        signer,
        file_info_cache=file_info_cache,
        slice_addr_cache=slice_addr_cache,
    )
    streamer = SliceStreamer(
        api_client,
        prefetch_concurrency=config.play_prefetch_concurrency,
        prefetch_queue_size=config.play_prefetch_queue_size,
        global_download_limit=config.slice_global_download_limit,
        initial_addr_batch=config.play_initial_addr_batch,
    )
    restore_manager = RestoreManager(config, api_client, path_cache)
    pipeline = StrmPipeline(config, api_client, path_cache, streamer, stats=stats)
    proxy_service = ProxyService(
        config, api_client, path_cache, restore_manager, streamer, stats=stats
    )
    emby_proxy_service: Optional[EmbyPlaybackProxyService] = None
    if config.emby_proxy_enabled:
        emby_proxy_service = EmbyPlaybackProxyService(config, stats=stats)
    return (
        api_client,
        path_cache,
        streamer,
        restore_manager,
        pipeline,
        proxy_service,
        emby_proxy_service,
        stats,
    )


# ─── 管理 API ───────────────────────────────────────────────

from fastapi import APIRouter

admin_router = APIRouter(prefix="/api", tags=["admin"])

# 运行时状态（在 create_app 时注入）
# 管理面板日志缓冲区（内存环形缓冲）
_admin_log_buffer: List[Dict[str, Any]] = []
_ADMIN_LOG_BUFFER_MAX = 2000


class _AdminLogHandler(logging.Handler):
    """将日志记录追加到内存环形缓冲区，供管理面板 /api/logs/recent 读取。
    
    只保留 twoland.* 的日志，过滤掉 uvicorn/httpx 等框架噪音。
    """

    # logger 名前缀 → 分类
    _CATEGORY_MAP = {
        "twoland.proxy": "play",
        "twoland.pipeline": "scan",
        "twoland.emby_proxy": "emby",
        "twoland.restore": "restore",
        "twoland.server": "system",
        "twoland.admin": "system",
        "twoland.scheduler": "scan",
    }

    def emit(self, record: logging.LogRecord) -> None:
        try:
            # 只保留 twoland.* 日志
            if not record.name.startswith("twoland"):
                return
            # 过滤掉 DEBUG 级别的 httpx/连接细节
            if record.levelno < logging.INFO:
                return

            # 推导分类
            category = "other"
            for prefix, cat in self._CATEGORY_MAP.items():
                if record.name.startswith(prefix):
                    category = cat
                    break

            entry = {
                "timestamp": dt.datetime.fromtimestamp(record.created, tz=dt.timezone.utc).isoformat(),
                "level": record.levelname,
                "logger": record.name,
                "message": record.getMessage(),
                "category": category,
            }
            _admin_log_buffer.append(entry)
            if len(_admin_log_buffer) > _ADMIN_LOG_BUFFER_MAX:
                del _admin_log_buffer[:len(_admin_log_buffer) - _ADMIN_LOG_BUFFER_MAX]
        except Exception:
            pass

_runtime_state: Dict[str, Any] = {}

SENSITIVE_KEYS = {"client_secret", "play_webdav_password"}


def _mask_value(key: str, value: str) -> str:
    if key not in SENSITIVE_KEYS or not value:
        return value
    if len(value) <= 8:
        return "***"
    return f"{value[:4]}***{value[-4:]}"


def _config_to_dict(config: AppConfig) -> Dict[str, Any]:
    """将 AppConfig 转为可序列化字典（脱敏版）"""
    d: Dict[str, Any] = {}
    for f in config.__dataclass_fields__:
        if f == "mappings":
            d["mappings"] = [
                {
                    "comment": m.comment,
                    "remote": m.remote,
                    "local": m.local,
                    "extras_mode": m.extras_mode,
                    "media_mode": m.media_mode,
                    "enabled": m.enabled,
                }
                for m in config.mappings
            ]
        elif f == "retry":
            d["retry"] = {
                "max_attempts": config.retry.max_attempts,
                "backoff_base_sec": config.retry.backoff_base_sec,
                "backoff_max_sec": config.retry.backoff_max_sec,
                "retry_statuses": config.retry.retry_statuses,
            }
        else:
            val = getattr(config, f)
            if isinstance(val, str) and f in SENSITIVE_KEYS:
                d[f] = _mask_value(f, val)
            else:
                d[f] = val
    d["effective_play_mode"] = "hybrid" if config.webdav_redirect_enabled() else config.play_mode
    d["credential_source"] = config.credential_source
    return d


def _get_config_schema() -> Dict[str, Any]:
    """返回配置元数据 schema 供前端动态渲染"""
    return {
        "groups": [
            {
                "key": "connection",
                "label": "基础连接",
                "icon": "ApiOutlined",
                "fields": [
                    {"key": "client_id", "label": "客户端 ID", "type": "string", "required": True, "secret": False, "description": "2dland API 客户端 ID，从 2dland 开放平台获取", "placeholder": "puc_xxxx_v1"},
                    {"key": "client_secret", "label": "客户端密钥", "type": "string", "required": True, "secret": True, "description": "2dland API 客户端密钥", "placeholder": "e4c8xxxx3148"},
                    {"key": "api_host", "label": "API 主机", "type": "string", "required": False, "default": "openapi.2dland.cn", "description": "2dland API 主机地址"},
                    {"key": "proxy_port", "label": "代理端口", "type": "integer", "required": False, "default": 8899, "min": 1, "max": 65535, "description": "代理服务监听端口"},
                    {"key": "admin_port", "label": "管理面板端口", "type": "integer", "required": False, "default": 8898, "min": 0, "max": 65535, "description": "管理面板监听端口，0 = 关闭管理面板"},
                    {"key": "proxy_url", "label": "代理 URL", "type": "string", "required": False, "default": "http://127.0.0.1:8899", "description": "代理服务完整 URL，自动从 proxy_port 推导"},
                    {"key": "public_strm_host", "label": "公开 STRM 主机", "type": "string", "required": False, "default": "http://127.0.0.1:8899", "description": "写入 .strm 文件的公开地址。外网客户端需要改成外网可达地址"},
                ],
            },
            {
                "key": "scan",
                "label": "扫描与清理",
                "icon": "ScanOutlined",
                "fields": [
                    {"key": "loop_interval", "label": "定时扫描间隔", "type": "integer", "unit": "秒", "required": False, "default": 0, "min": 0, "description": "0 = 仅启动时扫描一次"},
                    {"key": "min_video_size_mb", "label": "最小视频大小", "type": "integer", "unit": "MB", "required": False, "default": 80, "min": 0, "description": "小于此阈值的视频文件不生成 STRM"},
                    {"key": "extras_mode", "label": "附件处理策略", "type": "enum", "options": [{"value": "keep", "label": "保留"}, {"value": "download", "label": "下载"}, {"value": "delete", "label": "删除"}], "required": False, "default": "keep", "description": "图片、字幕、垃圾文件的处理方式"},
                    {"key": "media_mode", "label": "媒体文件策略", "type": "enum", "options": [{"value": "keep", "label": "保留"}, {"value": "delete", "label": "删除"}], "required": False, "default": "keep", "description": "媒体文件在 STRM 生成后的处理方式"},
                    {"key": "auto_confirm", "label": "自动确认删除", "type": "boolean", "required": False, "default": False, "description": "启用后删除操作无需手动确认"},
                    {"key": "output_dir", "label": "STRM 输出目录", "type": "string", "required": False, "default": "emby_strm", "description": ".strm 文件的本地输出目录"},
                ],
            },
            {
                "key": "restore",
                "label": "恢复目录",
                "icon": "SyncOutlined",
                "fields": [
                    {"key": "restore_dir", "label": "远端恢复目录", "type": "string", "required": False, "default": "/Temp/AutoRestore", "description": "远端恢复目录路径"},
                    {"key": "restore_ttl_hours", "label": "恢复文件 TTL", "type": "number", "unit": "小时", "required": False, "default": 3.0, "min": 0.1, "step": 0.5, "description": "恢复文件保留时长，活跃播放期间自动续租"},
                ],
            },
            {
                "key": "network",
                "label": "网络与超时",
                "icon": "GlobalOutlined",
                "fields": [
                    {"key": "request_timeout_sec", "label": "API 请求超时", "type": "number", "unit": "秒", "required": False, "default": 30.0, "min": 1, "description": "API 请求超时时间"},
                    {"key": "stream_timeout_sec", "label": "流传输超时", "type": "number", "unit": "秒", "required": False, "default": 120.0, "min": 1, "description": "流式传输超时时间"},
                    {"key": "log_level", "label": "日志级别", "type": "enum", "options": [{"value": "DEBUG", "label": "DEBUG - 调试"}, {"value": "INFO", "label": "INFO - 信息"}, {"value": "WARNING", "label": "WARNING - 警告"}, {"value": "ERROR", "label": "ERROR - 错误"}], "required": False, "default": "INFO", "description": "日志输出级别"},
                ],
            },
            {
                "key": "cache",
                "label": "缓存策略",
                "icon": "DatabaseOutlined",
                "fields": [
                    {"key": "path_cache_ttl_sec", "label": "路径缓存 TTL", "type": "integer", "unit": "秒", "required": False, "default": 3600, "min": 1, "description": "远端路径→文件ID 缓存有效期"},
                    {"key": "file_info_cache_ttl_sec", "label": "文件元数据缓存 TTL", "type": "integer", "unit": "秒", "required": False, "default": 600, "min": 1, "description": "分片元数据缓存有效期"},
                    {"key": "slice_address_cache_ttl_sec", "label": "切片地址缓存 TTL", "type": "integer", "unit": "秒", "required": False, "default": 120, "min": 1, "description": "切片下载地址缓存有效期"},
                ],
            },
            {
                "key": "play_concurrency",
                "label": "播放并发控制",
                "icon": "ThunderboltOutlined",
                "fields": [
                    {"key": "play_prefetch_concurrency", "label": "标准档预取并发", "type": "integer", "required": False, "default": 3, "min": 1, "description": "标准档播放器预取并发数"},
                    {"key": "play_prefetch_queue_size", "label": "标准档预取队列", "type": "integer", "required": False, "default": 3, "min": 1, "description": "标准档预取队列深度（需 ≥ 预取并发）", "constraint": ">= play_prefetch_concurrency"},
                    {"key": "play_max_active_requests", "label": "活跃播放上限", "type": "integer", "required": False, "default": 8, "min": 1, "description": "同时活跃的播放请求上限"},
                    {"key": "play_admission_wait_ms", "label": "准入等待时间", "type": "integer", "unit": "ms", "required": False, "default": 800, "min": 0, "description": "播放请求准入等待毫秒数"},
                    {"key": "restore_create_max_concurrency", "label": "转存创建并发", "type": "integer", "required": False, "default": 2, "min": 1, "description": "恢复目录转存创建并发上限"},
                    {"key": "slice_global_download_limit", "label": "全局下载并发", "type": "integer", "required": False, "default": 24, "min": 1, "description": "切片全局下载并发上限"},
                    {"key": "play_initial_addr_batch", "label": "首批地址拉取数", "type": "integer", "required": False, "default": 24, "min": 1, "description": "播放首批切片地址拉取数量"},
                    {"key": "play_disconnected_warn_grace_ms", "label": "快速断开告警豁免", "type": "integer", "unit": "ms", "required": False, "default": 1500, "min": 0, "description": "快速断开告警豁免窗口"},
                ],
            },
            {
                "key": "play_strategy",
                "label": "播放策略",
                "icon": "PlayCircleOutlined",
                "fields": [
                    {"key": "play_force_restore_before_stream", "label": "强制先转存", "type": "boolean", "required": False, "default": True, "description": "播放时强制先转存到恢复目录"},
                    {"key": "play_no_cid_strategy", "label": "无 CID 播放策略", "type": "enum", "options": [{"value": "lookup_and_restore", "label": "查找并恢复"}, {"value": "error", "label": "直接报错"}], "required": False, "default": "lookup_and_restore", "description": "当播放请求无 content_identity 时的策略"},
                ],
            },
            {
                "key": "play_webdav",
                "label": "WebDAV 重定向",
                "icon": "SwapOutlined",
                "fields": [
                    {"key": "play_mode", "label": "播放模式", "type": "enum", "options": [{"value": "proxy", "label": "proxy - 始终本地代理"}, {"value": "hybrid", "label": "hybrid - 优先直链，失败回退代理"}, {"value": "redirect", "label": "redirect - 纯直链，失败直接 404"}], "required": False, "default": "proxy", "description": "播放模式。hybrid 适合首次上线，redirect 适合已验证直链稳定的环境"},
                    {"key": "play_webdav_enabled", "label": "WebDAV 开关（旧）", "type": "boolean", "required": False, "default": False, "deprecated": True, "description": "旧开关，建议直接设置 play_mode。当未显式设置 play_mode 时等价于 hybrid"},
                    {"key": "play_webdav_base_url", "label": "WebDAV 外部入口", "type": "string", "required": False, "default": "", "description": "恢复目录对应的 WebDAV 外部可访问 URL", "visible_when": "play_mode in ['hybrid', 'redirect']"},
                    {"key": "play_webdav_username", "label": "WebDAV 用户名", "type": "string", "required": False, "default": "", "description": "WebDAV 认证用户名", "visible_when": "play_mode in ['hybrid', 'redirect']"},
                    {"key": "play_webdav_password", "label": "WebDAV 密码", "type": "string", "required": False, "default": "", "secret": True, "description": "WebDAV 认证密码", "visible_when": "play_mode in ['hybrid', 'redirect']"},
                    {"key": "play_webdav_cache_ttl_sec", "label": "WebDAV 直链缓存 TTL", "type": "integer", "unit": "秒", "required": False, "default": 30, "min": 1, "description": "WebDAV 直链探测缓存有效期"},
                    {"key": "play_redirect_status", "label": "直链跳转状态码", "type": "enum", "options": [{"value": 302, "label": "302 Found"}, {"value": 307, "label": "307 Temporary Redirect"}], "required": False, "default": 302, "description": "302 兼容性更好，307 语义更严格", "visible_when": "play_mode in ['hybrid', 'redirect']"},
                    {"key": "play_redirect_scope", "label": "302/307 适用范围", "type": "enum", "options": [{"value": "all", "label": "all - 所有请求可走 302"}, {"value": "emby_only", "label": "emby_only - 仅 Emby 反代链路"}], "required": False, "default": "all", "description": "设为 emby_only 时，其他直读 .strm 的客户端默认走本机代理", "visible_when": "play_mode in ['hybrid', 'redirect']"},
                ],
            },
            {
                "key": "compat",
                "label": "兼容档播放",
                "icon": "SafetyCertificateOutlined",
                "fields": [
                    {"key": "play_compat_enabled", "label": "开启兼容分流", "type": "boolean", "required": False, "default": True, "description": "对问题播放器 UA 自动降级为本地代理流"},
                    {"key": "play_compat_user_agents", "label": "问题播放器 UA 关键词", "type": "string[]", "required": False, "default": [], "description": "匹配这些关键词的 UA 自动走兼容档"},
                    {"key": "play_compat_user_agent_fingerprints", "label": "UA 指纹白名单", "type": "string[]", "required": False, "default": [], "description": "强制走兼容档的 UA 指纹列表"},
                    {"key": "play_compat_auto_promote", "label": "异常探测自动升级", "type": "boolean", "required": False, "default": True, "description": "在观察窗口内多次异常的 UA 自动升级到兼容档"},
                    {"key": "play_compat_auto_promote_threshold", "label": "自动升级阈值", "type": "integer", "required": False, "default": 3, "min": 1, "description": "观察窗口内触发自动升级的异常次数"},
                    {"key": "play_compat_window_sec", "label": "观察窗口", "type": "integer", "unit": "秒", "required": False, "default": 20, "min": 1, "description": "自动升级观察窗口时长"},
                    {"key": "play_compat_ttl_sec", "label": "升级保持时长", "type": "integer", "unit": "秒", "required": False, "default": 1800, "min": 1, "description": "自动升级后保持兼容档的秒数"},
                    {"key": "play_compat_prefetch_concurrency", "label": "兼容档预取并发", "type": "integer", "required": False, "default": 6, "min": 1, "description": "兼容档播放器预取并发数"},
                    {"key": "play_compat_prefetch_queue_size", "label": "兼容档预取队列", "type": "integer", "required": False, "default": 6, "min": 1, "constraint": ">= play_compat_prefetch_concurrency", "description": "兼容档预取队列深度"},
                    {"key": "play_compat_initial_addr_batch", "label": "兼容档首批地址数", "type": "integer", "required": False, "default": 64, "min": 1, "description": "兼容档首批切片地址拉取数量"},
                    {"key": "play_compat_admission_wait_ms", "label": "兼容档准入等待", "type": "integer", "unit": "ms", "required": False, "default": 1500, "min": 0, "description": "兼容档播放请求准入等待"},
                    {"key": "play_compat_range_relaxed", "label": "宽松 Range 解析", "type": "boolean", "required": False, "default": True, "description": "对不规范的 Range 头做宽松处理"},
                    {"key": "play_compat_initial_probe_max_bytes", "label": "首段探测忽略上限", "type": "integer", "unit": "字节", "required": False, "default": 268435456, "min": 1, "description": "兼容档下忽略首段探测 Range 的最大字节数（默认 256MB）"},
                    {"key": "play_compat_quick_disconnect_ms", "label": "快速断开判定窗口", "type": "integer", "unit": "ms", "required": False, "default": 3000, "min": 0, "description": "快速断开探测判定窗口"},
                    {"key": "play_compat_quick_disconnect_max_bytes", "label": "快速断开最大已发送字节", "type": "integer", "unit": "字节", "required": False, "default": 2097152, "min": 1, "description": "快速断开最大已发送字节（默认 2MB）"},
                    {"key": "play_compat_tail_probe_threshold_bytes", "label": "尾部微探测阈值", "type": "integer", "unit": "字节", "required": False, "default": 262144, "min": 1, "description": "兼容档尾部微探测判定阈值（默认 256KB）"},
                    {"key": "play_compat_tail_probe_expand_bytes", "label": "尾部扩展字节数", "type": "integer", "unit": "字节", "required": False, "default": 33554432, "min": 1, "constraint": ">= play_compat_tail_probe_threshold_bytes", "description": "兼容档尾部备用扩展字节数（默认 32MB）"},
                ],
            },
            {
                "key": "emby_proxy",
                "label": "Emby 反代",
                "icon": "CloudServerOutlined",
                "fields": [
                    {"key": "emby_proxy_enabled", "label": "开启 Emby 播放反代", "type": "boolean", "required": False, "default": False, "description": "启用后 2dland 可作为 Emby 入口，接管 PlaybackInfo 和 original.* 请求"},
                    {"key": "emby_server_url", "label": "真实 Emby 上游地址", "type": "string", "required": False, "default": "", "description": "必须指向真实 Emby，不能指向 2dland 自己", "visible_when": "emby_proxy_enabled = true"},
                    {"key": "emby_proxy_playback_cache_ttl_sec", "label": "PlaybackInfo 缓存 TTL", "type": "integer", "unit": "秒", "required": False, "default": 300, "min": 1, "description": "Emby PlaybackInfo 到 original.* 的播放映射缓存有效期"},
                    {"key": "emby_proxy_redirect_status", "label": "Emby 接管跳转状态码", "type": "enum", "options": [{"value": 302, "label": "302 Found"}, {"value": 307, "label": "307 Temporary Redirect"}], "required": False, "default": 307, "description": "Emby original.* 被接管后的跳转状态码，推荐 307"},
                ],
            },
            {
                "key": "retry",
                "label": "重试策略",
                "icon": "ReloadOutlined",
                "fields": [
                    {"key": "retry.max_attempts", "label": "最大重试次数", "type": "integer", "required": False, "default": 4, "min": 1, "description": "API 请求最大重试次数"},
                    {"key": "retry.backoff_base_sec", "label": "退避基础秒数", "type": "number", "unit": "秒", "required": False, "default": 0.6, "min": 0.1, "step": 0.1, "description": "指数退避的基础等待时间"},
                    {"key": "retry.backoff_max_sec", "label": "退避最大秒数", "type": "number", "unit": "秒", "required": False, "default": 6.0, "min": 0.1, "step": 0.5, "description": "单次退避最大等待时间"},
                    {"key": "retry.retry_statuses", "label": "触发重试的状态码", "type": "integer[]", "required": False, "default": [429, 500, 502, 503, 504], "description": "遇到这些 HTTP 状态码时触发重试"},
                ],
            },
        ]
    }


# 需要重启才能生效的字段
_RESTART_REQUIRED_KEYS = {
    "play_mode", "emby_proxy_enabled", "emby_server_url", "proxy_port",
    "play_webdav_enabled", "play_redirect_status", "play_redirect_scope",
    "emby_proxy_redirect_status", "emby_proxy_playback_cache_ttl_sec",
}


@admin_router.get("/config")
async def api_get_config():
    config: AppConfig = _runtime_state.get("config")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")
    return _config_to_dict(config)


@admin_router.get("/config/schema")
async def api_get_config_schema():
    return _get_config_schema()


@admin_router.post("/config/validate")
async def api_validate_config(body: Dict[str, Any]):
    config: AppConfig = _runtime_state.get("config")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")
    # 合并传入字段到当前配置的副本
    current_dict = _config_to_dict(config)
    # 去掉脱敏占位
    for key in SENSITIVE_KEYS:
        val = body.get(key)
        if val and "***" in str(val):
            body.pop(key, None)
    current_dict.update(body)
    try:
        test_config = AppConfig.from_dict(current_dict)
    except Exception as e:
        return {"valid": False, "errors": [str(e)], "warnings": []}
    try:
        test_config.validate()
    except ConfigError as e:
        return {"valid": False, "errors": [str(e)], "warnings": []}
    warnings = validate_warnings(test_config)
    return {"valid": True, "errors": [], "warnings": warnings}


@admin_router.put("/config")
async def api_update_config(body: Dict[str, Any]):
    config: AppConfig = _runtime_state.get("config")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")
    config_path: str = _runtime_state.get("config_path", "config.json")

    # 读取当前 config.json
    with open(config_path, "r", encoding="utf-8") as f:
        current = json.load(f)

    # 合并（跳过脱敏占位）
    for key, value in body.items():
        if key in SENSITIVE_KEYS and value and "***" in str(value):
            continue
        current[key] = value

    # 校验
    try:
        test_config = AppConfig.from_dict(current)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    try:
        test_config.validate()
    except ConfigError as e:
        raise HTTPException(status_code=400, detail=str(e))

    warnings = validate_warnings(test_config)

    # 判断是否需要重启
    old_dict = _config_to_dict(config)
    restart_required = False
    restart_reason = ""
    for key in _RESTART_REQUIRED_KEYS:
        new_val = current.get(key)
        old_val = old_dict.get(key)
        if str(new_val) != str(old_val):
            restart_required = True
            restart_reason = f"{key} changed"
            break

    # 写入
    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(current, f, indent=4, ensure_ascii=False)

    # 更新运行时状态
    _runtime_state["config"] = test_config

    return {
        "success": True,
        "warnings": warnings,
        "restart_required": restart_required,
        "restart_reason": restart_reason,
    }


@admin_router.get("/config/mappings")
async def api_get_mappings():
    config: AppConfig = _runtime_state.get("config")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")
    return [
        {
            "comment": m.comment,
            "remote": m.remote,
            "local": m.local,
            "extras_mode": m.extras_mode,
            "media_mode": m.media_mode,
            "enabled": m.enabled,
        }
        for m in config.mappings
    ]


@admin_router.post("/config/mappings")
async def api_add_mapping(body: Dict[str, Any]):
    config: AppConfig = _runtime_state.get("config")
    config_path: str = _runtime_state.get("config_path", "config.json")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")

    with open(config_path, "r", encoding="utf-8") as f:
        current = json.load(f)

    mappings = current.get("mappings", [])
    mappings.append(body)
    current["mappings"] = mappings

    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(current, f, indent=4, ensure_ascii=False)

    _runtime_state["config"] = AppConfig.from_json(config_path)
    return {"success": True}


@admin_router.put("/config/mappings/{index}")
async def api_update_mapping(index: int, body: Dict[str, Any]):
    config: AppConfig = _runtime_state.get("config")
    config_path: str = _runtime_state.get("config_path", "config.json")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")

    with open(config_path, "r", encoding="utf-8") as f:
        current = json.load(f)

    mappings = current.get("mappings", [])
    if index < 0 or index >= len(mappings):
        raise HTTPException(status_code=404, detail="Mapping index out of range")
    mappings[index] = body
    current["mappings"] = mappings

    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(current, f, indent=4, ensure_ascii=False)

    _runtime_state["config"] = AppConfig.from_json(config_path)
    return {"success": True}


@admin_router.delete("/config/mappings/{index}")
async def api_delete_mapping(index: int):
    config: AppConfig = _runtime_state.get("config")
    config_path: str = _runtime_state.get("config_path", "config.json")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")

    with open(config_path, "r", encoding="utf-8") as f:
        current = json.load(f)

    mappings = current.get("mappings", [])
    if index < 0 or index >= len(mappings):
        raise HTTPException(status_code=404, detail="Mapping index out of range")
    mappings.pop(index)
    current["mappings"] = mappings

    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(current, f, indent=4, ensure_ascii=False)

    _runtime_state["config"] = AppConfig.from_json(config_path)
    return {"success": True}


@admin_router.get("/status")
async def api_get_status():
    config: AppConfig = _runtime_state.get("config")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")
    import sys
    stats_store: Optional[StatsStore] = _runtime_state.get("stats_store")
    return {
        "running": True,
        "mode": _runtime_state.get("mode", "serve"),
        "pid": os.getpid(),
        "uptime_sec": time.time() - _runtime_state.get("start_time", time.time()),
        "bind_hosts": _runtime_state.get("bind_hosts", []),
        "proxy_port": config.proxy_port,
        "admin_port": config.admin_port,
        "version": "2026.04.23",
        "python_version": f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}",
        "stats": stats_store.snapshot() if stats_store else {},
    }


@admin_router.get("/stats")
async def api_get_stats():
    stats_store: Optional[StatsStore] = _runtime_state.get("stats_store")
    if not stats_store:
        raise HTTPException(status_code=503, detail="Stats not initialized")
    return stats_store.snapshot()


@admin_router.post("/stats/reset")
async def api_reset_stats():
    stats_store: Optional[StatsStore] = _runtime_state.get("stats_store")
    if not stats_store:
        raise HTTPException(status_code=503, detail="Stats not initialized")
    for k in list(stats_store._data.keys()):
        stats_store._data[k] = 0
    stats_store._dirty = True
    stats_store.flush()
    return {"success": True}


@admin_router.post("/service/restart")
async def api_restart_service():
    # 发送 SIGHUP 或标记重启标志
    import signal
    try:
        os.kill(os.getpid(), signal.SIGHUP)
        return {"accepted": True, "message": "Restart signal sent."}
    except Exception as e:
        return {"accepted": False, "message": str(e)}


@admin_router.post("/service/scan")
async def api_trigger_scan(body: Dict[str, Any] = {}):
    config: AppConfig = _runtime_state.get("config")
    scheduler: Optional[JobScheduler] = _runtime_state.get("scheduler")

    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")

    if not scheduler:
        raise HTTPException(status_code=503, detail="Scheduler not initialized")

    root = body.get("root")
    auto_confirm = body.get("auto_confirm")

    mappings_to_scan = None
    if root:
        mappings_to_scan = [m for m in config.mappings if m.remote == root]
        if not mappings_to_scan:
            raise HTTPException(status_code=400, detail=f"No mapping found for root: {root}")

    task_id = await scheduler.trigger_now(
        mappings=mappings_to_scan, auto_confirm=auto_confirm
    )

    return {"task_id": task_id, "status": "running"}


@admin_router.get("/service/validate")
async def api_validate_service():
    config: AppConfig = _runtime_state.get("config")
    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")
    try:
        config.validate()
    except ConfigError as e:
        return {"valid": False, "errors": [str(e)], "warnings": []}
    warnings = validate_warnings(config)
    return {"valid": True, "errors": [], "warnings": warnings}


@admin_router.get("/tasks/scan")
async def api_get_scan_task():
    scheduler: Optional[JobScheduler] = _runtime_state.get("scheduler")
    if scheduler and getattr(scheduler, "_last_scan_stats", None):
        stats = scheduler._last_scan_stats
        stats_dict = {
            "scanned": stats.scanned,
            "created_strm": stats.created_strm,
            "downloaded_extras": stats.downloaded_extras,
            "skipped_exists": stats.skipped_exists,
            "skipped_small": stats.skipped_small,
            "deleted": stats.deleted,
            "errors": stats.errors,
        }
        return {
            "task_id": getattr(scheduler, "_last_scan_task_id", None) or "unknown",
            "status": "running" if getattr(scheduler, "_scan_running", False) else "completed",
            "started_at": getattr(scheduler, "_last_scan_start", None),
            "duration_sec": getattr(scheduler, "_last_scan_duration", None),
            "stats": stats_dict,
            "current_mapping": None,
            "progress": 0.5 if getattr(scheduler, "_scan_running", False) else 1.0,
        }
    return {
        "task_id": "none",
        "status": "idle",
        "started_at": None,
        "duration_sec": None,
        "stats": {"scanned": 0, "created_strm": 0, "downloaded_extras": 0, "skipped_exists": 0, "skipped_small": 0, "deleted": 0, "errors": 0},
        "current_mapping": None,
        "progress": 0,
    }


@admin_router.get("/tasks/scan-history")
async def api_get_scan_history(limit: int = 50, offset: int = 0):
    """获取扫描历史记录（持久化）。"""
    scan_history: Optional[ScanHistoryStore] = _runtime_state.get("scan_history")
    if not scan_history:
        return {"total": 0, "records": []}
    records = scan_history.get_history(limit=limit, offset=offset)
    return {
        "total": scan_history.count(),
        "records": [r.to_dict() for r in records],
    }


@admin_router.get("/play/status")
async def api_get_play_status():
    config: AppConfig = _runtime_state.get("config")
    proxy_service: Optional[ProxyService] = _runtime_state.get("proxy_service")
    restore_manager: Optional[RestoreManager] = _runtime_state.get("restore_manager")

    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")

    # play_admission 和 play_profile_resolver 是 ProxyService 的实例属性
    play_admission: Optional[PlayAdmissionController] = None
    play_profile: Optional[PlayProfileResolver] = None
    if proxy_service:
        play_admission = getattr(proxy_service, "play_admission", None)
        play_profile = getattr(proxy_service, "play_profile_resolver", None)

    active_requests = 0
    max_active = config.play_max_active_requests
    rejected = 0

    if play_admission:
        active_requests = getattr(play_admission, "_active_requests", 0)
        rejected = getattr(play_admission, "_rejected_total", 0)

    restore_entries = 0
    active_leases = 0
    restore_list = []
    if restore_manager:
        play_entries: Dict[str, RestoreEntry] = getattr(restore_manager, "play_entries", {})
        restore_entries = len(play_entries)
        active_leases = sum(e.active_leases for e in play_entries.values())
        now = time.time()
        for e in list(play_entries.values())[-50:]:
            restore_list.append({
                "path": e.restore_path,
                "content_identity": e.content_identity,
                "leases": e.active_leases,
                "expires_in": f"{max(0, e.expire_at_ts - now):.0f}s",
            })

    compat_promoted: List[str] = []
    if play_profile:
        # _promoted_until: Dict[str, float] — UA fingerprint → expire timestamp
        promoted_until: Dict[str, float] = getattr(play_profile, "_promoted_until", {})
        now = time.time()
        compat_promoted = [fp for fp, exp in promoted_until.items() if exp > now]

    # 收集最近的播放请求记录
    recent_requests = []
    if proxy_service:
        recent = getattr(proxy_service, "_recent_play_requests", [])
        recent_requests = recent[-30:]

    webdav_cache_entries = 0
    if proxy_service:
        webdav_cache = getattr(proxy_service, "play_webdav_cache", None)
        if webdav_cache is not None:
            webdav_cache_entries = len(getattr(webdav_cache, "_store", {}))

    # 持久化累计统计
    stats_store: Optional[StatsStore] = _runtime_state.get("stats_store")
    persistent_stats = stats_store.snapshot() if stats_store else {}

    return {
        "active_requests": active_requests,
        "max_active_requests": max_active,
        "rejected_total": rejected,
        "play_mode": config.play_mode,
        "effective_play_mode": "hybrid" if config.webdav_redirect_enabled() else config.play_mode,
        "redirect_scope": config.play_redirect_scope,
        "webdav_redirect_enabled": config.webdav_redirect_enabled(),
        "webdav_cache_entries": webdav_cache_entries,
        "restore_entries": restore_entries,
        "restore_active_leases": active_leases,
        "compat_promoted_uas": compat_promoted,
        "recent_requests": recent_requests,
        "restore_list": restore_list,
        "persistent": persistent_stats,
    }


@admin_router.get("/play/redirect-summary")
async def api_get_redirect_summary():
    # 从日志缓冲区聚合 WebDAV 重定向统计
    now = time.time()
    hour_ago = now - 3600
    day_ago = now - 86400

    def _aggregate(since: float) -> Dict[str, Any]:
        hits = 0
        misses = 0
        miss_breakdown: Dict[str, int] = {}
        for entry in _admin_log_buffer:
            try:
                ts_str = entry.get("timestamp", "")
                ts = dt.datetime.fromisoformat(ts_str).timestamp()
                if ts < since:
                    continue
                msg = entry.get("message", "")
                if "play_webdav_redirect_hit" in msg:
                    hits += 1
                elif "play_webdav_redirect_miss" in msg:
                    misses += 1
                    # 尝试提取 miss_reason
                    for part in msg.split(","):
                        if "miss_reason" in part:
                            reason = part.split(":")[-1].strip().strip('"')
                            miss_breakdown[reason] = miss_breakdown.get(reason, 0) + 1
            except Exception:
                continue
        total = hits + misses
        return {
            "hits": hits,
            "misses": misses,
            "hit_rate": hits / total if total > 0 else 0,
            "miss_breakdown": miss_breakdown,
        }

    return {
        "last_hour": _aggregate(hour_ago),
        "last_24h": _aggregate(day_ago),
    }


@admin_router.get("/emby/status")
async def api_get_emby_status():
    config: AppConfig = _runtime_state.get("config")
    emby_service: Optional[EmbyPlaybackProxyService] = _runtime_state.get("emby_proxy_service")

    if not config:
        raise HTTPException(status_code=503, detail="Service not initialized")

    cache_entries = 0
    recent = []

    if emby_service:
        cache = getattr(emby_service, "playback_cache", None)
        if cache is not None:
            cache_entries = len(getattr(cache, "_store", {}))
            # 从缓存中提取最近条目
            store = getattr(cache, "_store", {})
            now = time.time()
            for key, info in list(store.items())[-30:]:
                recent.append({
                    "item_id": getattr(info, "item_id", key.split(":")[0] if ":" in key else key),
                    "media_source_id": getattr(info, "media_source_id", ""),
                    "play_url": getattr(info, "play_target", ""),
                    "redirect_status": config.emby_proxy_redirect_status,
                    "timestamp": getattr(info, "expire_at", now),
                })

    return {
        "enabled": config.emby_proxy_enabled,
        "emby_server_url": config.emby_server_url,
        "playback_cache_entries": cache_entries,
        "playback_cache_ttl_sec": config.emby_proxy_playback_cache_ttl_sec,
        "redirect_status": config.emby_proxy_redirect_status,
        "websocket_connected": False,  # WebSocket 状态无法从服务对象直接获取
        "recent_redirects": recent,
    }


@admin_router.get("/logs/recent")
async def api_get_recent_logs(count: int = 200, level: str = None, search: str = None, category: str = None):
    entries = list(_admin_log_buffer)
    if level:
        level = level.upper()
        level_order = {"DEBUG": 0, "INFO": 1, "WARNING": 2, "ERROR": 3}
        min_idx = level_order.get(level, 0)
        entries = [e for e in entries if level_order.get(e.get("level", ""), 0) >= min_idx]
    if category:
        entries = [e for e in entries if e.get("category", "") == category]
    if search:
        search_lower = search.lower()
        entries = [e for e in entries if search_lower in e.get("message", "").lower() or search_lower in e.get("logger", "").lower()]
    return entries[-count:]


@admin_router.websocket("/ws/logs")
async def ws_log_stream(websocket: WebSocket):
    await websocket.accept()
    last_idx = len(_admin_log_buffer)
    try:
        while True:
            # 推送新日志条目
            current_len = len(_admin_log_buffer)
            if current_len > last_idx:
                for entry in _admin_log_buffer[last_idx:current_len]:
                    try:
                        await websocket.send_json(entry)
                    except Exception:
                        return
                last_idx = current_len
            # 等待一小段时间再检查
            await asyncio.sleep(0.3)
    except Exception:
        pass


def create_app(
    config: AppConfig,
    api_client: TwoLandApiClient,
    restore_manager: RestoreManager,
    proxy_service: ProxyService,
    emby_proxy_service: Optional[EmbyPlaybackProxyService],
    scheduler: Optional[JobScheduler],
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(_: FastAPI):
        await restore_manager.scan_existing_restore_files()
        if scheduler:
            await scheduler.start()
        try:
            yield
        finally:
            if scheduler:
                await scheduler.stop()
            await restore_manager.shutdown()
            if emby_proxy_service:
                await emby_proxy_service.close()
            await api_client.close()
            # 关闭时刷写持久化统计
            stats_store: Optional[StatsStore] = _runtime_state.get("stats_store")
            if stats_store:
                stats_store.stop()

    app = FastAPI(title="2dland Unified Proxy", lifespan=lifespan)
    proxy_service.register_routes(app)
    if emby_proxy_service:
        emby_proxy_service.register_routes(app)

    # 注入运行时状态（供 admin router 使用）
    _runtime_state.update({
        "config": config,
        "config_path": _runtime_state.get("config_path", "config.json"),
        "api_client": api_client,
        "restore_manager": restore_manager,
        "proxy_service": proxy_service,
        "emby_proxy_service": emby_proxy_service,
        "scheduler": scheduler,
        "pipeline": _runtime_state.get("pipeline"),
        "start_time": time.time(),
        "mode": "serve",
    })

    return app


def create_admin_app() -> FastAPI:
    """创建独立的管理面板应用（独立端口运行）"""
    admin_app = FastAPI(title="2dland Admin Panel")

    # CORS：允许来自同机代理端口的请求
    admin_app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    admin_app.include_router(admin_router)

    # 静态文件（前端 dist）
    frontend_dir = Path(__file__).parent / "frontend" / "dist"
    if frontend_dir.is_dir():
        from fastapi.staticfiles import StaticFiles
        admin_app.mount("/", StaticFiles(directory=str(frontend_dir), html=True), name="frontend")

    return admin_app


async def run_scan_once(config: AppConfig) -> int:
    api_client, _, _, _, pipeline, _, emby_proxy_service, _ = create_runtime(config)
    try:
        mappings = [m for m in config.mappings if m.enabled]
        if not mappings:
            raise ConfigError(
                "No mappings enabled. Use config.mappings or pass --root."
            )
        await pipeline.run_once(mappings, config.auto_confirm)
        return 0
    finally:
        if emby_proxy_service:
            await emby_proxy_service.close()
        await api_client.close()


def run_validate_config(config: AppConfig) -> int:
    print("✅ Config is valid")
    print(f"- api_host: {config.api_host}")
    print(f"- proxy_port: {config.proxy_port}")
    print(f"- loop_interval: {config.loop_interval}")
    print(f"- mappings: {len(config.mappings)}")
    print(f"- media_mode: {config.media_mode}")
    print(f"- retry.max_attempts: {config.retry.max_attempts}")
    print(f"- credential_source: {config.credential_source}")
    print(f"- path_cache_ttl_sec: {config.path_cache_ttl_sec}")
    print(f"- file_info_cache_ttl_sec: {config.file_info_cache_ttl_sec}")
    print(f"- slice_address_cache_ttl_sec: {config.slice_address_cache_ttl_sec}")
    print(f"- play_prefetch_concurrency: {config.play_prefetch_concurrency}")
    print(f"- play_prefetch_queue_size: {config.play_prefetch_queue_size}")
    print(f"- play_max_active_requests: {config.play_max_active_requests}")
    print(f"- play_admission_wait_ms: {config.play_admission_wait_ms}")
    print(f"- restore_create_max_concurrency: {config.restore_create_max_concurrency}")
    print(f"- slice_global_download_limit: {config.slice_global_download_limit}")
    print(f"- play_initial_addr_batch: {config.play_initial_addr_batch}")
    print(
        f"- play_disconnected_warn_grace_ms: {config.play_disconnected_warn_grace_ms}"
    )
    print(
        f"- play_force_restore_before_stream: {config.play_force_restore_before_stream}"
    )
    print(f"- play_no_cid_strategy: {config.play_no_cid_strategy}")
    print(f"- play_compat_enabled: {config.play_compat_enabled}")
    print(f"- play_compat_user_agents: {len(config.play_compat_user_agents)}")
    print(
        f"- play_compat_user_agent_fingerprints: {len(config.play_compat_user_agent_fingerprints)}"
    )
    print(f"- play_compat_auto_promote: {config.play_compat_auto_promote}")
    print(
        f"- play_compat_auto_promote_threshold: {config.play_compat_auto_promote_threshold}"
    )
    print(f"- play_compat_window_sec: {config.play_compat_window_sec}")
    print(f"- play_compat_ttl_sec: {config.play_compat_ttl_sec}")
    print(
        f"- play_compat_prefetch_concurrency: {config.play_compat_prefetch_concurrency}"
    )
    print(
        f"- play_compat_prefetch_queue_size: {config.play_compat_prefetch_queue_size}"
    )
    print(f"- play_compat_initial_addr_batch: {config.play_compat_initial_addr_batch}")
    print(f"- play_compat_admission_wait_ms: {config.play_compat_admission_wait_ms}")
    print(f"- play_compat_range_relaxed: {config.play_compat_range_relaxed}")
    print(
        f"- play_compat_initial_probe_max_bytes: {config.play_compat_initial_probe_max_bytes}"
    )
    print(
        f"- play_compat_quick_disconnect_ms: {config.play_compat_quick_disconnect_ms}"
    )
    print(
        f"- play_compat_quick_disconnect_max_bytes: {config.play_compat_quick_disconnect_max_bytes}"
    )
    print(
        f"- play_compat_tail_probe_threshold_bytes: {config.play_compat_tail_probe_threshold_bytes}"
    )
    print(
        f"- play_compat_tail_probe_expand_bytes: {config.play_compat_tail_probe_expand_bytes}"
    )
    print(f"- play_mode: {config.play_mode}")
    print(f"- effective_play_mode: {config.effective_play_mode()}")
    print(f"- play_webdav_enabled (legacy): {config.play_webdav_enabled}")
    print(f"- play_webdav_base_url: {bool(config.play_webdav_base_url)}")
    print(f"- play_webdav_cache_ttl_sec: {config.play_webdav_cache_ttl_sec}")
    print(f"- play_redirect_status: {config.play_redirect_status}")
    print(f"- play_redirect_scope: {config.play_redirect_scope}")
    print(f"- emby_proxy_enabled: {config.emby_proxy_enabled}")
    print(f"- emby_server_url: {bool(config.emby_server_url)}")
    print(
        f"- emby_proxy_playback_cache_ttl_sec: {config.emby_proxy_playback_cache_ttl_sec}"
    )
    print(f"- emby_proxy_redirect_status: {config.emby_proxy_redirect_status}")

    warnings = validate_warnings(config)
    if warnings:
        print("\n⚠️ Warnings:")
        for w in warnings:
            print(f"- {w}")
    return 0


def _load_config_for_command(args: argparse.Namespace, command: str) -> AppConfig:
    config = AppConfig.from_json(args.config)
    if command in {"serve", "scan-once", "proxy-only"}:
        config.apply_cli_overrides(args)
    setup_logging(config.log_level)
    return config


def run_server(
    args: argparse.Namespace, config: AppConfig, *, with_scheduler: bool
) -> int:
    import uvicorn
    import asyncio

    (
        api_client,
        _,
        _,
        restore_manager,
        pipeline,
        proxy_service,
        emby_proxy_service,
        stats_store,
    ) = create_runtime(config)

    # 启动统计后台刷写
    stats_store.start_background_flush()

    # 扫描历史持久化
    config_dir = Path(getattr(config, "_config_path", "config.json")).parent
    scan_history = ScanHistoryStore(
        path=str(config_dir / "scan_history.json"),
        max_records=100,
    )

    # 注入运行时状态供管理 API 使用
    _runtime_state["pipeline"] = pipeline
    _runtime_state["config_path"] = getattr(config, "_config_path", getattr(args, "config", "config.json"))
    _runtime_state["stats_store"] = stats_store
    _runtime_state["scan_history"] = scan_history

    scheduler: Optional[JobScheduler] = None
    if with_scheduler:
        mappings = [m for m in config.mappings if m.enabled]
        if not mappings:
            raise ConfigError("No mappings enabled. Configure mappings or pass --root.")
        scheduler = JobScheduler(
            pipeline=pipeline,
            mappings=mappings,
            auto_confirm=config.auto_confirm,
            interval_sec=config.loop_interval,
            scan_history=scan_history,
        )

    app = create_app(
        config,
        api_client,
        restore_manager,
        proxy_service,
        emby_proxy_service,
        scheduler,
    )
    host = getattr(args, "host", "auto")
    port = int(getattr(args, "port", 0) or config.proxy_port)
    server_logger = logging.getLogger("twoland.server")
    bind_hosts = resolve_server_bind_hosts(host)

    admin_port = config.admin_port

    # 同时启动代理服务和管理面板
    if admin_port > 0:
        admin_app = create_admin_app()

        async def serve_both():
            config_proxy = uvicorn.Config(app, host=bind_hosts[0] if len(bind_hosts) == 1 else bind_hosts[0], port=port, log_level="info")
            config_admin = uvicorn.Config(admin_app, host=bind_hosts[0] if len(bind_hosts) == 1 else bind_hosts[0], port=admin_port, log_level="info")

            server_proxy = uvicorn.Server(config_proxy)
            server_admin = uvicorn.Server(config_admin)

            server_logger.info(f"代理服务启动: http://0.0.0.0:{port}")
            server_logger.info(f"管理面板启动: http://0.0.0.0:{admin_port}")

            if len(bind_hosts) > 1:
                server_sockets = _create_server_sockets(bind_hosts, port, server_logger)
                admin_sockets = _create_server_sockets(bind_hosts, admin_port, server_logger)
                try:
                    await asyncio.gather(
                        server_proxy.serve(sockets=server_sockets),
                        server_admin.serve(sockets=admin_sockets),
                    )
                finally:
                    for sock in server_sockets + admin_sockets:
                        try:
                            sock.close()
                        except Exception:
                            pass
            else:
                await asyncio.gather(
                    server_proxy.serve(),
                    server_admin.serve(),
                )

        asyncio.run(serve_both())
        return 0

    # admin_port == 0，只启动代理服务
    if len(bind_hosts) == 1:
        uvicorn.run(app, host=bind_hosts[0], port=port)
        return 0

    server_sockets = _create_server_sockets(bind_hosts, port, server_logger)
    try:
        uvicorn_config = uvicorn.Config(app, host=bind_hosts[0], port=port)
        uvicorn.Server(uvicorn_config).run(sockets=server_sockets)
    finally:
        for sock in server_sockets:
            try:
                sock.close()
            except Exception:
                pass
    return 0


def dispatch(args: argparse.Namespace) -> int:
    command = args.command or "serve"
    config = _load_config_for_command(args, command)

    if command == "validate-config":
        return run_validate_config(config)

    if command == "scan-once":
        return asyncio.run(run_scan_once(config))

    if command == "proxy-only":
        return run_server(args, config, with_scheduler=False)

    return run_server(args, config, with_scheduler=True)


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    try:
        return dispatch(args)
    except ConfigError as exc:
        print(f"❌ Config error: {exc}")
        return 2
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
