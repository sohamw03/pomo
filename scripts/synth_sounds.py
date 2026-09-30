"""Synthesize Pomo timer sounds: crisp work-start alert + warm break-start chime.

Usage: python scripts/synth_sounds.py [--out DIR]
Tweak the partials/timings below to taste, re-run, re-listen.
"""
import argparse
import numpy as np
import wave
import struct

SR = 44100


def bell_strike(f0, dur, partials, attack=0.003, brightness=1.0):
    """Additive bell: partials = [(ratio, amp, decay_sec), ...]."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for ratio, amp, decay in partials:
        env = np.exp(-t / decay)
        out += amp * np.sin(2 * np.pi * f0 * ratio * t) * env
    # fast attack to avoid clicks, keep it snappy
    a = max(1, int(attack * SR))
    out[:a] *= np.linspace(0, 1, a)
    return out * brightness


def place(track, snippet, at_sec):
    i = int(at_sec * SR)
    j = min(len(track), i + len(snippet))
    track[i:j] += snippet[: j - i]


def normalize(x, peak=0.8):
    m = np.max(np.abs(x))
    return x / m * peak if m > 0 else x


# Bright bell partials: strong fundamental, quick-decaying highs = crisp.
BRIGHT = [(1.0, 1.0, 0.55), (2.01, 0.45, 0.30), (2.74, 0.30, 0.22),
          (3.76, 0.18, 0.15), (5.40, 0.10, 0.10)]
# Warm partials: near-harmonic, slow decay, gentle attack = mellow.
WARM = [(1.0, 1.0, 1.60), (2.0, 0.35, 1.00), (3.0, 0.15, 0.60),
        (4.2, 0.07, 0.40)]


def work_start():
    """Bright double ding: G6, twice, 220 ms apart. Total ~1.1 s."""
    track = np.zeros(int(1.2 * SR))
    ding = bell_strike(1567.98, 0.9, BRIGHT, attack=0.002)
    place(track, ding, 0.0)
    place(track, ding * 0.9, 0.22)
    return normalize(track)


def break_start():
    """Warm C-major reward: C5+E5+G5+C6 struck together, ~2 s bloom."""
    track = np.zeros(int(2.2 * SR))
    for f in (523.25, 659.25, 783.99, 1046.50):
        amp = 1.0 if f < 800 else 0.6
        place(track, bell_strike(f, 2.0, WARM, attack=0.015) * amp, 0.0)
    return normalize(track)


def write_wav(path, data, sr=SR):
    pcm = (data * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="sfx-preview")
    args = ap.parse_args()
    import os
    os.makedirs(args.out, exist_ok=True)
    for name, fn in (("work-start", work_start), ("break-start", break_start)):
        data = fn()
        p = os.path.join(args.out, name + ".wav")
        write_wav(p, data)
        print("wrote", p, round(len(data) / SR, 2), "s")


if __name__ == "__main__":
    main()
