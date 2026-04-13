from __future__ import annotations

import sys

from app import CONFIG, MODEL_CACHE, _load_model


def main() -> int:
    print("[Voice Punish ASR] model cache:", MODEL_CACHE)
    print("[Voice Punish ASR] loading model:", CONFIG.model)
    try:
        _load_model()
    except Exception as exc:
        print(f"[Voice Punish ASR] model download/load failed: {type(exc).__name__}: {exc}")
        return 1

    print("[Voice Punish ASR] model is ready.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
