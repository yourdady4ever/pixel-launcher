"""Sintetiza los efectos de sonido de Jostin OS (48 kHz, mono, 16 bits)."""
import os
import sys

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter, sosfilt

SR = 48000
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(11)


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env(dur, attack, tau):
    t = t_axis(dur)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    a = np.sin(a * np.pi / 2) ** 2  # ataque suave, sin chasquido
    return a * np.exp(-np.maximum(t - attack, 0) / tau)


def bandpass(x, lo, hi, order=2):
    sos = butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return sosfilt(sos, x)


def lowpass(x, f, order=2):
    sos = butter(order, f, btype="low", fs=SR, output="sos")
    return sosfilt(sos, x)


def highpass(x, f, order=2):
    sos = butter(order, f, btype="high", fs=SR, output="sos")
    return sosfilt(sos, x)


def bell(freq, dur, decay, ratios=(1, 2.0, 3.01, 4.17), amps=(1, .32, .11, .045), attack=0.003):
    """Nota tipo mazo sobre vidrio: parciales con caída más rápida en los agudos."""
    t = t_axis(dur)
    out = np.zeros_like(t)
    for i, (r, a) in enumerate(zip(ratios, amps)):
        d = decay / (1 + i * 0.9)
        out += a * np.sin(2 * np.pi * freq * r * t + i * 0.7) * np.exp(-t / d)
    a_env = np.clip(t / attack, 0, 1)
    return out * np.sin(a_env * np.pi / 2) ** 2


def reverb(x, wet=0.18, size=1.0):
    combs = [int(SR * s * size) for s in (0.0297, 0.0371, 0.0411, 0.0437)]
    y = np.zeros(len(x) + int(SR * 0.6))
    xp = np.concatenate([x, np.zeros(len(y) - len(x))])
    acc = np.zeros_like(y)
    for d, g in zip(combs, (0.78, 0.76, 0.74, 0.72)):
        a = np.zeros(d + 1); a[0] = 1; a[d] = -g
        c = lfilter([1], a, xp)
        acc += lowpass(c, 5500)
    acc /= len(combs)
    for d, g in ((int(SR * 0.005), 0.6), (int(SR * 0.0017), 0.6)):
        b = np.zeros(d + 1); b[0] = -g; b[d] = 1
        a = np.zeros(d + 1); a[0] = 1; a[d] = -g
        acc = lfilter(b, a, acc)
    return xp * (1 - wet) + acc * wet * 3.2


def place(buf, sig, at):
    i = int(at * SR)
    end = min(len(buf), i + len(sig))
    buf[i:end] += sig[: end - i]


def finish(name, x, peak_db):
    x = highpass(x, 60)
    # Recorta el silencio final y aplica un desvanecimiento corto
    thr = np.max(np.abs(x)) * 10 ** (-60 / 20)
    nz = np.nonzero(np.abs(x) > thr)[0]
    x = x[: nz[-1] + 1] if len(nz) else x
    fade = min(len(x), int(SR * 0.012))
    x[-fade:] *= np.linspace(1, 0, fade) ** 2
    x = x / np.max(np.abs(x)) * 10 ** (peak_db / 20)
    wavfile.write(os.path.join(OUT, name + ".wav"), SR, (x * 32767).astype(np.int16))
    print(f"{name:8s} {len(x) / SR * 1000:6.0f} ms  pico {peak_db} dBFS")


# tap: tic de vidrio, corto y discreto
d = 0.06
noise = bandpass(rng.standard_normal(int(SR * d)), 2800, 7000) * env(d, 0.0004, 0.0018)
tick = np.sin(2 * np.pi * 2350 * t_axis(d)) * env(d, 0.0006, 0.007)
body = np.sin(2 * np.pi * 640 * t_axis(d)) * env(d, 0.001, 0.012)
finish("tap", 0.5 * noise + 0.6 * tick + 0.35 * body, -15)

# key: tecla de calculadora, un "pop" suave con caída de tono
d = 0.08
t = t_axis(d)
f = 520 + 380 * np.exp(-t / 0.012)
phase = 2 * np.pi * np.cumsum(f) / SR
pop = (np.sin(phase) + 0.18 * np.sin(2 * phase)) * env(d, 0.0008, 0.016)
click = bandpass(rng.standard_normal(len(t)), 2000, 6000) * env(d, 0.0003, 0.0012)
finish("key", pop + 0.25 * click, -13)

# open: dos notas que suben, con un soplo de aire debajo
buf = np.zeros(int(SR * 0.9))
place(buf, bell(880.0, 0.8, 0.32), 0.0)
place(buf, bell(1318.5, 0.8, 0.36), 0.045)
air = lowpass(rng.standard_normal(int(SR * 0.25)), 1800) * env(0.25, 0.09, 0.07) * 0.18
place(buf, air, 0.0)
finish("open", reverb(buf, 0.2), -11)

# back: el mismo par de notas pero bajando, más corto
buf = np.zeros(int(SR * 0.6))
place(buf, bell(1318.5, 0.5, 0.18), 0.0)
place(buf, bell(880.0, 0.5, 0.22), 0.04)
finish("back", reverb(buf, 0.15, 0.8), -14)

# equals: acorde tipo campana rasgueado (do, sol, do)
buf = np.zeros(int(SR * 1.0))
for i, fr in enumerate((1046.5, 1568.0, 2093.0)):
    place(buf, bell(fr, 0.9, 0.42 - i * 0.06) * (1 - i * 0.18), i * 0.018)
finish("equals", reverb(buf, 0.22), -12)

# theme: barrido de aire que sube, con un brillo al final
d = 0.6
n = int(SR * d)
pink = lfilter([0.049922035, -0.095993537, 0.050612699, -0.004408786],
               [1, -2.494956002, 2.017265875, -0.522189400], rng.standard_normal(n))
centers = 300 * (2500 / 300) ** (np.arange(n) / n)
# Filtro de estado variable que se mueve muestra por muestra (sin cortes entre bloques)
sweep = np.zeros(n)
low = band = 0.0
q = 0.9
for i in range(n):
    fc = 2 * np.sin(np.pi * centers[i] / SR)
    high = pink[i] - low - q * band
    band += fc * high
    low += fc * band
    sweep[i] = band
t = t_axis(d)
shape = np.sin(np.clip(t / 0.22, 0, 1) * np.pi / 2) ** 2 * np.exp(-np.maximum(t - 0.22, 0) / 0.14)
shimmer = bell(2637.0, d, 0.25, amps=(1, .2, .05, .02)) * 0.12
buf = np.zeros(int(SR * 0.9))
place(buf, sweep * shape, 0)
place(buf, shimmer, 0.2)
finish("theme", reverb(buf, 0.25), -12)

# toggle: dos tics rápidos que suben
buf = np.zeros(int(SR * 0.14))
for i, fr in enumerate((1800, 2500)):
    tt = t_axis(0.05)
    place(buf, np.sin(2 * np.pi * fr * tt) * env(0.05, 0.0006, 0.008), i * 0.055)
finish("toggle", buf, -16)

# check: pendiente terminado, pequeño pluck brillante que sube
buf = np.zeros(int(SR * 0.5))
place(buf, bell(1568.0, 0.4, 0.14), 0)
place(buf, bell(2349.3, 0.4, 0.18), 0.06)
finish("check", reverb(buf, 0.15, 0.7), -14)

# hold: golpe grave para menús al mantener presionado
d = 0.18
t = t_axis(d)
f = 105 + 70 * np.exp(-t / 0.03)
phase = 2 * np.pi * np.cumsum(f) / SR
thud = np.sin(phase) * env(d, 0.002, 0.05)
tk = bandpass(rng.standard_normal(len(t)), 1500, 4000) * env(d, 0.0004, 0.002)
finish("hold", thud + 0.3 * tk, -12)

# error: dos pulsos graves y suaves
buf = np.zeros(int(SR * 0.3))
for i, fr in enumerate((300, 240)):
    tt = t_axis(0.09)
    tone = np.sin(2 * np.pi * fr * tt) + 0.25 * np.sin(2 * np.pi * fr * 2 * tt)
    place(buf, tone * env(0.09, 0.004, 0.03), i * 0.11)
finish("error", buf, -15)
