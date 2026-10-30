package io.github.flymeisland.diagnostic;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import java.util.Arrays;

/** Explicitly authorized per-app playback capture; no microphone or display capture. */
public final class AppAudioWaveService extends Service {
    public static final String START = "io.github.flymeisland.diagnostic.wave.START";
    public static final String STOP = "io.github.flymeisland.diagnostic.wave.STOP";
    public static final String RESULT_CODE = "result_code", RESULT_DATA = "result_data";
    private static final String CHANNEL = "island_audio_visualization_v038";
    private static final int NOTIFICATION_ID = 760805;
    private static AppAudioWaveService instance;
    private static String playerPackage;
    private static boolean playerPlaying;
    private HandlerThread thread;
    private Handler worker;
    private MediaProjection projection;
    private AudioRecord record;
    private short[] samples;
    private String capturePackage;
    private volatile boolean stopping;
    private final Runnable poll = this::sample;
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() { stopping = true; stopSelf(); }
    };

    /** Called by the listener when the selected media session changes. */
    public static void updatePlayer(String pkg, boolean playing) {
        playerPackage = pkg; playerPlaying = playing;
        AppAudioWaveService s = instance;
        if (s != null && s.worker != null) s.worker.post(() -> s.configure(pkg, playing));
    }
    public static boolean isRunning() { return instance != null && instance.projection != null && !instance.stopping; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        thread = new HandlerThread("IslandPlayerWave"); thread.start();
        worker = new Handler(thread.getLooper());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (!START.equals(intent.getAction()) || projection != null) return START_NOT_STICKY;
        Intent data = Build.VERSION.SDK_INT >= 33 ? intent.getParcelableExtra(RESULT_DATA, Intent.class)
                : intent.getParcelableExtra(RESULT_DATA);
        int code = intent.getIntExtra(RESULT_CODE, 0);
        if (data == null || code != android.app.Activity.RESULT_OK
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "媒体波形", NotificationManager.IMPORTANCE_LOW));
            Intent stop = new Intent(this, AppAudioWaveService.class).setAction(STOP);
            PendingIntent action = PendingIntent.getService(this, 3880, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_island).setContentTitle("应用音频波形正在运行")
                    .setContentText("仅读取当前播放应用允许捕获的音频；点击停止")
                    .setContentIntent(action).setOngoing(true).build();
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(NOTIFICATION_ID, notification);
            MediaProjectionManager pm = getSystemService(MediaProjectionManager.class);
            projection = pm == null ? null : pm.getMediaProjection(code, data);
            if (projection == null) { stopSelf(); return START_NOT_STICKY; }
            projection.registerCallback(projectionCallback, new Handler(getMainLooper()));
            // No virtual display is created; this projection is used only for audio.
            String pkg = playerPackage; boolean playing = playerPlaying;
            worker.post(() -> configure(pkg, playing));
        } catch (RuntimeException failure) {
            Log.w("FlymeIslandMusic", "Per-app playback capture unavailable: " + failure.getClass().getSimpleName());
            stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void configure(String pkg, boolean playing) {
        if (stopping) return;
        if (playing && pkg != null && pkg.equals(capturePackage) && record != null) return;
        releaseRecord();
        if (!playing || pkg == null || projection == null ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            int uid = getPackageManager().getPackageUid(pkg, 0);
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUid(uid).addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
            AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            int min = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IllegalStateException("audio format unavailable");
            record = new AudioRecord.Builder().setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(min, 17640)).setAudioPlaybackCaptureConfig(config).build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("audio capture unavailable");
            samples = new short[4410];
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("audio capture not running");
            capturePackage = pkg;
            worker.post(poll);
        } catch (PackageManager.NameNotFoundException | RuntimeException failure) {
            Log.w("FlymeIslandMusic", "Player output unavailable: " + failure.getClass().getSimpleName());
            releaseRecord();
        }
    }
    private void sample() {
        if (stopping || record == null || samples == null) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { releaseRecord(); stopSelf(); return; }
        try {
            int count = record.read(samples, 0, samples.length, AudioRecord.READ_NON_BLOCKING);
            if (count < 0) { releaseRecord(); return; }
            if (count > 0) {
                byte[] bars = WaveSpectrum.pcm(samples, count, 44100);
                Arrays.fill(samples, (short)0);
                SystemUiAicyClient.wave(this, bars);
            }
            worker.postDelayed(poll, 125L);
        } catch (RuntimeException failure) { releaseRecord(); }
    }
    private void releaseRecord() {
        if (worker != null) worker.removeCallbacks(poll);
        if (samples != null) Arrays.fill(samples, (short)0);
        samples = null; capturePackage = null;
        if (record != null) {
            try { record.stop(); } catch (RuntimeException ignored) {}
            try { record.release(); } catch (RuntimeException ignored) {}
            record = null;
        }
    }
    @Override public void onDestroy() {
        stopping = true;
        if (instance == this) instance = null;
        if (worker != null) { worker.removeCallbacksAndMessages(null); worker.post(this::releaseRecord); }
        if (thread != null) thread.quitSafely();
        if (projection != null) {
            projection.unregisterCallback(projectionCallback);
            projection.stop(); projection = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
