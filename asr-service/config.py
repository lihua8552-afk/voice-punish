from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(slots=True)
class ServiceConfig:
    model: str = "paraformer-zh"
    vad_model: str = "fsmn-vad"
    punc_model: str = "ct-punc"
    device: str = "cuda"
    host: str = "127.0.0.1"
    port: int = 47831
    auto_download_models: bool = True


CONFIG_PATH = Path(__file__).resolve().parent / "voice-punish-asr-service.json"


def load_config() -> ServiceConfig:
    if not CONFIG_PATH.exists():
        config = ServiceConfig()
        CONFIG_PATH.write_text(
            json.dumps(
                {
                    "model": config.model,
                    "vadModel": config.vad_model,
                    "puncModel": config.punc_model,
                    "device": config.device,
                    "host": config.host,
                    "port": config.port,
                    "autoDownloadModels": config.auto_download_models,
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
        return config

    raw = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    return ServiceConfig(
        model=str(raw.get("model", "paraformer-zh")).strip() or "paraformer-zh",
        vad_model=str(raw.get("vadModel", "fsmn-vad")).strip() or "fsmn-vad",
        punc_model=str(raw.get("puncModel", "ct-punc")).strip() or "ct-punc",
        device=str(raw.get("device", "cuda")).strip() or "cuda",
        host=str(raw.get("host", "127.0.0.1")).strip() or "127.0.0.1",
        port=max(1, min(65535, int(raw.get("port", 47831)))),
        auto_download_models=bool(raw.get("autoDownloadModels", True)),
    )


def model_cache_dir() -> Path:
    env_value = os.getenv("VOICE_PUNISH_ASR_CACHE")
    if env_value:
        path = Path(env_value).expanduser().resolve()
    else:
        path = Path.home() / ".voice-punish-asr-models"
    path.mkdir(parents=True, exist_ok=True)
    return path
