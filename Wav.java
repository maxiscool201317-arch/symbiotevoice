package com.symbiotevoice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class Wav {
    /** 48 kHz mono 16-bit in, 16 kHz mono 16-bit WAV out (smaller upload, fine for speech). */
    static byte[] from48kTo16kWav(short[] pcm48) {
        int n = pcm48.length / 3;
        ByteBuffer bb = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes());
        bb.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
          .putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
        bb.put("data".getBytes()).putInt(n * 2);
        for (int i = 0; i < n; i++) {
            int s = (pcm48[3 * i] + pcm48[3 * i + 1] + pcm48[3 * i + 2]) / 3;
            bb.putShort((short) s);
        }
        return bb.array();
    }

    private Wav() {}
}
