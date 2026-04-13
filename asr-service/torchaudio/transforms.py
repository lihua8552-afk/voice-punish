from . import functional


class Resample:

    def __init__(self, orig_freq, new_freq):
        self.orig_freq = orig_freq
        self.new_freq = new_freq

    def __call__(self, waveform):
        return functional.resample(waveform, self.orig_freq, self.new_freq)
