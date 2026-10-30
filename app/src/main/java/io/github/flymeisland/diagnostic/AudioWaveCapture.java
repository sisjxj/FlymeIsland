package io.github.flymeisland.diagnostic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.Arrays;

/** Optional output-mix visualization. Never records, logs, or stores audio samples. */
public final class AudioWaveCapture {
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Visualizer visualizer;
    private byte[] samples;
    private long retryAfter;
    private final Runnable poll = this::sample;
    public AudioWaveCapture(Context context) { app = context.getApplicationContext(); }

    public void update(boolean playing) {
        if (!playing || !MusicPrefs.realWave(app)
                || app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stop(); return;
        }
        if (visualizer != null || android.os.SystemClock.elapsedRealtime() < retryAfter) return;
        try {
            Visualizer next = new Visualizer(0); // global playback output mix; not microphone input
            visualizer = next;
            int[] range = Visualizer.getCaptureSizeRange();
            int size = Math.max(range[0], Math.min(256, range[1]));
            if (next.setCaptureSize(size) != Visualizer.SUCCESS) throw new IllegalStateException("capture size");
            samples = new byte[size];
            if (next.setEnabled(true) != Visualizer.SUCCESS) throw new IllegalStateException("capture enable");
            main.post(poll);
        } catch (RuntimeException unavailable) {
            Log.w("FlymeIslandMusic", "Output visualization unavailable");
            stop();
            retryAfter = android.os.SystemClock.elapsedRealtime() + 30000L;
        }
    }
    private void sample() {
        if (visualizer == null || samples == null) return;
        if (!MusicPrefs.realWave(app) || app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { stop(); return; }
        try {
            if (visualizer.getFft(samples) == Visualizer.SUCCESS) {
                byte[] bars = WaveSpectrum.fft(samples, visualizer.getSamplingRate());
                Arrays.fill(samples, (byte)0);
                SystemUiAicyClient.wave(app, bars);
            } else throw new IllegalStateException("capture unavailable");
            main.postDelayed(poll, 125L);
        } catch (RuntimeException unavailable) {
            stop();
            retryAfter = android.os.SystemClock.elapsedRealtime() + 30000L;
        }
    }
    public void stop() {
        main.removeCallbacks(poll);
        if (samples != null) Arrays.fill(samples, (byte)0);
        samples = null;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (RuntimeException ignored) {}
            try { visualizer.release(); } catch (RuntimeException ignored) {}
        }
        visualizer = null;
    }
}
