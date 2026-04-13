from . import compliance, functional, transforms
import soundfile as sf
import torch

__all__ = ["compliance", "functional", "transforms", "load"]


def load(path):
    data, sample_rate = sf.read(path, dtype="float32", always_2d=True)
    waveform = torch.from_numpy(data.T.copy())
    return waveform, sample_rate
