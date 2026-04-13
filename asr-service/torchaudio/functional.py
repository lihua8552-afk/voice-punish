import librosa
import numpy as np
import torch


def istft(*args, **kwargs):
    return torch.istft(*args, **kwargs)


def forced_align(*args, **kwargs):
    raise NotImplementedError("forced_align is not implemented in the lightweight torchaudio shim")


def resample(waveform, orig_freq, new_freq):
    if orig_freq == new_freq:
        return waveform
    array = waveform.detach().cpu().numpy()
    if array.ndim == 1:
        resampled = librosa.resample(array, orig_sr=orig_freq, target_sr=new_freq)
        return torch.from_numpy(np.asarray(resampled, dtype=np.float32))
    channels = [
        librosa.resample(channel, orig_sr=orig_freq, target_sr=new_freq)
        for channel in array
    ]
    return torch.from_numpy(np.asarray(channels, dtype=np.float32))
