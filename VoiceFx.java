package com.symbiotevoice;

/** Deep, layered, growly voice processing at 48 kHz. Pure Java, no native libs. */
final class VoiceFx {
    static final int SR = 48000;

    static short[] process(short[] tts, int rate, StrainVoice.Preset p, double depthMul) {
        float[] x = toFloat48k(tts, rate);
        if (x.length < 2048) return new short[0];

        float[] mix = new float[x.length];
        double gainSum = 0;
        for (int i = 0; i < p.pitches.length; i++) {
            double ratio = Math.max(0.40, Math.min(1.10, p.pitches[i] * depthMul));
            float[] layer = shift(x, ratio);
            float g = (float) p.gains[i];
            gainSum += g;
            for (int j = 0; j < mix.length; j++) mix[j] += g * layer[j];
        }
        float inv = (float) (1.0 / Math.max(0.0001, gainSum));
        for (int j = 0; j < mix.length; j++) mix[j] *= inv;

        // growl: soft clipping
        if (p.drive > 0) {
            double norm = Math.tanh(p.drive);
            for (int j = 0; j < mix.length; j++) mix[j] = (float) (Math.tanh(p.drive * mix[j]) / norm);
        }

        // muffle highs
        if (p.lowpassHz > 0) {
            double a = Math.exp(-2.0 * Math.PI * p.lowpassHz / SR);
            float y = 0;
            for (int j = 0; j < mix.length; j++) {
                y = (float) ((1 - a) * mix[j] + a * y);
                mix[j] = y;
            }
        }

        // eerie wobble
        if (p.tremHz > 0 && p.tremDepth > 0) {
            for (int j = 0; j < mix.length; j++) {
                double lfo = 1.0 - p.tremDepth * 0.5 * (1.0 + Math.sin(2 * Math.PI * p.tremHz * j / SR));
                mix[j] *= (float) lfo;
            }
        }

        // echo with a tail
        if (p.echoMs > 0 && p.echoMix > 0) {
            int delay = p.echoMs * SR / 1000;
            int tail = delay * 4;
            float[] out = new float[mix.length + tail];
            float[] fb = new float[out.length];
            for (int j = 0; j < out.length; j++) {
                float dry = j < mix.length ? mix[j] : 0f;
                float echoed = j >= delay ? fb[j - delay] : 0f;
                fb[j] = dry + (float) p.echoFeedback * echoed;
                out[j] = dry + (float) p.echoMix * echoed;
            }
            mix = out;
        }

        // normalise and convert
        float peak = 0.0001f;
        for (float v : mix) peak = Math.max(peak, Math.abs(v));
        float scale = 0.88f / peak;
        short[] res = new short[mix.length];
        for (int j = 0; j < mix.length; j++) {
            res[j] = (short) Math.max(-32768, Math.min(32767, Math.round(mix[j] * scale * 32767f)));
        }
        return res;
    }

    private static float[] toFloat48k(short[] in, int rate) {
        double step = (double) rate / SR;
        int outLen = (int) (in.length / step);
        float[] out = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double pos = i * step;
            int i0 = (int) pos;
            double f = pos - i0;
            float a = i0 < in.length ? in[i0] / 32768f : 0f;
            float b = i0 + 1 < in.length ? in[i0 + 1] / 32768f : a;
            out[i] = (float) (a + (b - a) * f);
        }
        return out;
    }

    /** Pitch shift by ratio while keeping duration: resample, then WSOLA time-stretch back. */
    private static float[] shift(float[] x, double ratio) {
        int resLen = (int) (x.length / ratio);
        float[] y = new float[resLen];
        for (int i = 0; i < resLen; i++) {
            double pos = i * ratio;
            int i0 = (int) pos;
            double f = pos - i0;
            float a = at(x, i0), b = at(x, i0 + 1);
            y[i] = (float) (a + (b - a) * f);
        }
        return wsola(y, x.length);
    }

    private static float[] wsola(float[] x, int outLen) {
        final int N = 2048, Hs = N / 4, tol = 384, corrLen = 1024;
        double ratio = (double) x.length / outLen;
        float[] out = new float[outLen + N];
        float[] norm = new float[outLen + N];
        float[] win = new float[N];
        for (int i = 0; i < N; i++) win[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));

        int prevPos = 0;
        boolean first = true;
        double nominal = 0;
        for (int outPos = 0; outPos < outLen; outPos += Hs) {
            int center = (int) Math.round(nominal);
            int pos;
            if (first) {
                pos = Math.max(0, center);
                first = false;
            } else {
                int natural = prevPos + Hs;
                pos = center;
                double best = -Double.MAX_VALUE;
                for (int c = center - tol; c <= center + tol; c += 4) {
                    if (c < 0) continue;
                    double s = 0;
                    for (int i = 0; i < corrLen; i += 2) s += at(x, c + i) * at(x, natural + i);
                    if (s > best) { best = s; pos = c; }
                }
            }
            for (int i = 0; i < N; i++) {
                out[outPos + i] += win[i] * at(x, pos + i);
                norm[outPos + i] += win[i];
            }
            prevPos = pos;
            nominal += Hs * ratio;
        }
        float[] res = new float[outLen];
        for (int i = 0; i < outLen; i++) res[i] = norm[i] > 1e-3f ? out[i] / norm[i] : 0f;
        return res;
    }

    private static float at(float[] x, int i) {
        return i >= 0 && i < x.length ? x[i] : 0f;
    }

    private VoiceFx() {}
}
