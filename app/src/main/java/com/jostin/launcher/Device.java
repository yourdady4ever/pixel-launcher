package com.jostin.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;

/** Datos del teléfono (batería, almacenamiento, memoria) y la linterna. */
final class Device {
    private Device() {}

    static final class Stats {
        int battery;
        boolean charging;
        float batteryTemp;
        float storageUsed;      // 0..1
        float storageFreeGb;
        float ramUsed;          // 0..1
    }

    static Stats read(Context c) {
        Stats s = new Stats();
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            s.battery = level < 0 ? 0 : Math.round(level * 100f / Math.max(1, scale));
            int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            s.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            s.batteryTemp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            s.storageUsed = total > 0 ? 1f - free / (float) total : 0;
            s.storageFreeGb = free / 1e9f;
        } catch (Exception ignored) {
            s.storageUsed = 0;
        }
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.ramUsed = mi.totalMem > 0 ? 1f - mi.availMem / (float) mi.totalMem : 0;
        }
        return s;
    }

    // ---------- Linterna ----------

    private static boolean torchOn = false;
    private static String torchId;
    private static CameraManager.TorchCallback torchCb;

    /** Si la linterna está prendida (según lo último que avisó Android). */
    static boolean torchOn() {
        return torchOn;
    }

    static boolean toggleTorch(Context c) {
        CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) return false;
        try {
            if (torchId == null) {
                for (String id : cm.getCameraIdList()) {
                    Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (f != null && f) {
                        torchId = id;
                        break;
                    }
                }
            }
            if (torchId == null) return false;
            if (torchCb == null) {
                torchCb = new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(String id, boolean enabled) {
                        if (id.equals(torchId)) torchOn = enabled;
                    }
                };
                cm.registerTorchCallback(torchCb, null);
            }
            torchOn = !torchOn;
            cm.setTorchMode(torchId, torchOn);
            return torchOn;
        } catch (Exception e) {
            return false;
        }
    }
}
