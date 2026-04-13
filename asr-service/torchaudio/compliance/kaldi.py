import librosa
import numpy as np
import torch


def fbank(
        waveform,
        num_mel_bins=80,
        frame_length=25,
        frame_shift=10,
        dither=0.0,
        energy_floor=0.0,
        window_type="hamming",
        sample_frequency=16000,
        snip_edges=True,
        **kwargs
):
    if waveform.dim() == 2:
        waveform = waveform[0]

    array = waveform.detach().cpu().numpy().astype(np.float32)
    if array.size == 0:
        return torch.zeros((0, num_mel_bins), dtype=torch.float32)

    if np.max(np.abs(array)) > 1.5:
        array = array / 32768.0

    n_fft = max(256, int(sample_frequency * frame_length / 1000.0))
    hop_length = max(1, int(sample_frequency * frame_shift / 1000.0))
    win_length = n_fft
    center = not snip_edges

    mel = librosa.feature.melspectrogram(
        y=array,
        sr=sample_frequency,
        n_fft=n_fft,
        hop_length=hop_length,
        win_length=win_length,
        n_mels=num_mel_bins,
        power=2.0,
        center=center,
        htk=True
    )
    mel = np.maximum(mel, 1.0e-10)
    features = np.log(mel).T.astype(np.float32)
    return torch.from_numpy(features)
