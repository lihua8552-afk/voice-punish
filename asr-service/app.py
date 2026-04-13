from __future__ import annotations

import os
import tempfile
import time
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import JSONResponse

from config import ServiceConfig, load_config, model_cache_dir

APP_VERSION = "0.1.0"
CONFIG = load_config()
MODEL_CACHE = model_cache_dir()

os.environ.setdefault("MODELSCOPE_CACHE", str(MODEL_CACHE))
os.environ.setdefault("HF_HOME", str(MODEL_CACHE / "hf"))

app = FastAPI(title="Voice Punish ASR Service", version=APP_VERSION)
_MODEL: Any = None
_MODEL_ERROR: str | None = None


def _load_model() -> Any:
    global _MODEL, _MODEL_ERROR
    if _MODEL is not None:
        return _MODEL

    try:
        from funasr import AutoModel

        _MODEL = AutoModel(
            model=CONFIG.model,
            vad_model=CONFIG.vad_model,
            punc_model=CONFIG.punc_model,
            device=CONFIG.device,
            disable_update=True,
        )
        _MODEL_ERROR = None
        return _MODEL
    except Exception as exc:  # pragma: no cover - runtime dependency path
        _MODEL_ERROR = f"{type(exc).__name__}: {exc}"
        raise


def _normalize_result(raw_result: Any, duration_ms: int) -> dict[str, Any]:
    if isinstance(raw_result, list) and raw_result:
        raw_result = raw_result[0]
    if not isinstance(raw_result, dict):
        return {
            "text": "",
            "sentence_text": "",
            "segments": [],
            "duration_ms": duration_ms,
            "provider": "local_funasr",
            "model": CONFIG.model,
            "confidence": None,
        }

    text = str(raw_result.get("text") or "").strip()
    sentence_text = str(raw_result.get("sentence_info") or raw_result.get("sentence_text") or text).strip()

    segments: list[dict[str, Any]] = []
    sentence_info = raw_result.get("sentence_info")
    if isinstance(sentence_info, list):
        for item in sentence_info:
            if not isinstance(item, dict):
                continue
            segments.append(
                {
                    "text": str(item.get("text") or "").strip(),
                    "start_ms": item.get("start") if item.get("start") is not None else item.get("start_ms"),
                    "end_ms": item.get("end") if item.get("end") is not None else item.get("end_ms"),
                }
            )

    return {
        "text": text,
        "sentence_text": sentence_text or text,
        "segments": segments,
        "duration_ms": duration_ms,
        "provider": "local_funasr",
        "model": CONFIG.model,
        "confidence": raw_result.get("confidence"),
    }


def _transcribe_wav_bytes(wav_bytes: bytes, duration_ms: int) -> dict[str, Any]:
    model = _load_model()
    temp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
            temp_file.write(wav_bytes)
            temp_path = Path(temp_file.name)

        started_at = time.perf_counter()
        result = model.generate(input=str(temp_path))
        normalized = _normalize_result(result, duration_ms)
        normalized["elapsed_ms"] = round((time.perf_counter() - started_at) * 1000)
        return normalized
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)


@app.get("/healthz")
async def healthz() -> Response:
    try:
        _load_model()
        return JSONResponse(
            {
                "ok": True,
                "service": "voice-punish-asr-service",
                "version": APP_VERSION,
                "model": CONFIG.model,
                "vad_model": CONFIG.vad_model,
                "punc_model": CONFIG.punc_model,
                "device": CONFIG.device,
                "model_cache": str(MODEL_CACHE),
                "error": None,
            }
        )
    except Exception:
        return JSONResponse(
            {
                "ok": False,
                "service": "voice-punish-asr-service",
                "version": APP_VERSION,
                "model": CONFIG.model,
                "vad_model": CONFIG.vad_model,
                "punc_model": CONFIG.punc_model,
                "device": CONFIG.device,
                "model_cache": str(MODEL_CACHE),
                "error": _MODEL_ERROR,
            },
            status_code=503,
        )


@app.get("/v1/info")
async def info() -> dict[str, Any]:
    return {
        "service": "voice-punish-asr-service",
        "version": APP_VERSION,
        "provider": "local_funasr",
        "model": CONFIG.model,
        "vad_model": CONFIG.vad_model,
        "punc_model": CONFIG.punc_model,
        "device": CONFIG.device,
        "model_cache": str(MODEL_CACHE),
        "auto_download_models": CONFIG.auto_download_models,
    }


@app.post("/v1/transcribe")
async def transcribe(request: Request) -> dict[str, Any]:
    wav_bytes = await request.body()
    if not wav_bytes:
        raise HTTPException(status_code=400, detail="Request body is empty")

    try:
        duration_ms = int(request.headers.get("X-VoicePunish-Duration-Ms", "0"))
    except ValueError:
        duration_ms = 0

    try:
        return _transcribe_wav_bytes(wav_bytes, duration_ms)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Transcription failed: {type(exc).__name__}: {exc}") from exc
