package com.jostin.launcher;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Grabar la pantalla, como el botón del panel de Android. Android pide permiso cada vez con su
 * propia ventana; el video se guarda en Películas/Jostin. Sin audio, para no pedir el micrófono.
 */
public class Recorder {
    static volatile boolean recording = false;
    private static final String CHANNEL = "grabar";
    private static final String STOP = "com.jostin.launcher.STOP_RECORDING";

    private Recorder() {}

    /** Empieza (Android pregunta antes) o, si ya está grabando, termina y guarda. */
    static void toggle(Context c) {
        if (recording) {
            c.startService(new Intent(c, Service.class).setAction(STOP));
            return;
        }
        Intent i = new Intent(c, Ask.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Pantalla invisible que solo muestra la ventana de permiso de Android. */
    public static class Ask extends Activity {
        @Override
        protected void onCreate(Bundle saved) {
            super.onCreate(saved);
            MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
            if (mpm == null) {
                finish();
                return;
            }
            try {
                startActivityForResult(mpm.createScreenCaptureIntent(), 1);
            } catch (Exception e) {
                finish();
            }
        }

        @Override
        protected void onActivityResult(int request, int result, Intent data) {
            super.onActivityResult(request, result, data);
            if (result == RESULT_OK && data != null) {
                startForegroundService(new Intent(this, Service.class).putExtra("code", result).putExtra("data", data));
            }
            finish();
            overridePendingTransition(0, 0);
        }
    }

    public static class Service extends android.app.Service {
        private MediaProjection projection;
        private VirtualDisplay display;
        private MediaRecorder recorder;
        private ParcelFileDescriptor fd;
        private Uri item;
        private final Handler main = new Handler(Looper.getMainLooper());

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent == null || STOP.equals(intent.getAction())) {
                finishRecording(true);
                return START_NOT_STICKY;
            }
            if (recording) return START_NOT_STICKY;
            // Android 14 exige estar en primer plano (tipo mediaProjection) antes de pedir la grabación
            Notification n = notification();
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(7, n);
            try {
                MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
                Intent data = intent.getParcelableExtra("data");
                projection = mpm.getMediaProjection(intent.getIntExtra("code", 0), data);
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        // Android la cortó (por ejemplo, desde su aviso de "Compartiendo pantalla")
                        finishRecording(true);
                    }
                }, main);
                // Un momento para que se cierre el panel y no salga en el video
                main.postDelayed(this::begin, 500);
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo empezar a grabar", Toast.LENGTH_SHORT).show();
                finishRecording(false);
            }
            return START_NOT_STICKY;
        }

        private void begin() {
            try {
                DisplayMetrics dm = new DisplayMetrics();
                ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
                // Hasta 1080 de ancho y en números pares, que es lo que aceptan los codificadores
                float scale = Math.min(1f, 1080f / dm.widthPixels);
                int w = Math.round(dm.widthPixels * scale) & ~1, h = Math.round(dm.heightPixels * scale) & ~1;

                recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                recorder.setVideoSize(w, h);
                recorder.setVideoFrameRate(30);
                recorder.setVideoEncodingBitRate(8_000_000);
                openOutput(recorder);
                recorder.prepare();
                display = projection.createVirtualDisplay("Jostin", w, h, dm.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.getSurface(), null, null);
                recorder.start();
                recording = true;
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo empezar a grabar", Toast.LENGTH_SHORT).show();
                finishRecording(false);
            }
        }

        /** Android 10+: en Películas/Jostin de la galería. Antes, en la carpeta de Jostin. */
        private void openOutput(MediaRecorder r) throws Exception {
            String name = "Jostin_" + new SimpleDateFormat("yyyyMMdd_HHmmss", MainActivity.ES).format(new Date()) + ".mp4";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Jostin");
                v.put(MediaStore.Video.Media.IS_PENDING, 1);
                ContentResolver cr = getContentResolver();
                item = cr.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                fd = cr.openFileDescriptor(item, "w");
                r.setOutputFile(fd.getFileDescriptor());
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
                r.setOutputFile(new File(dir, name).getAbsolutePath());
            }
        }

        private void finishRecording(boolean save) {
            boolean had = recording;
            recording = false;
            try {
                if (recorder != null && had) recorder.stop();
            } catch (Exception ignored) {
                // muy corto: no alcanzó a grabar nada
                save = false;
            }
            if (recorder != null) recorder.release();
            recorder = null;
            if (display != null) display.release();
            display = null;
            if (projection != null) {
                MediaProjection p = projection;
                projection = null;
                p.stop();
            }
            try {
                if (fd != null) fd.close();
            } catch (Exception ignored) {
                // nada
            }
            fd = null;
            if (item != null && Build.VERSION.SDK_INT >= 29) {
                if (save && had) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Video.Media.IS_PENDING, 0);
                    getContentResolver().update(item, v, null, null);
                } else {
                    getContentResolver().delete(item, null, null);
                }
            }
            item = null;
            if (save && had) Toast.makeText(this, "Grabación guardada en Películas/Jostin", Toast.LENGTH_LONG).show();
            stopForeground(true);
            stopSelf();
        }

        private Notification notification() {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Grabar pantalla", NotificationManager.IMPORTANCE_LOW));
            }
            PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, Service.class).setAction(STOP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.presence_video_online)
                    .setContentTitle("Grabando la pantalla")
                    .setContentText("Toca para detener y guardar")
                    .setContentIntent(stop)
                    .addAction(new Notification.Action.Builder(null, "Detener", stop).build())
                    .setOngoing(true)
                    .build();
        }
    }
}
