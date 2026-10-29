package com.example.tvexplorer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class ServerService extends Service {
    private static final String CHANNEL_ID = "TV_File_Server_Channel";
    private FileServer server;
    public static boolean isRunning = false;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            try {
                // 1. BLINDAJE DE ANTENA: Forzar máxima potencia y cero ahorro de energía
                try {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TVExplorer:HighPerfWifi");
                        wifiLock.setReferenceCounted(false);
                        wifiLock.acquire();
                    }
                } catch (Exception ignored) {}

                // 2. Carpeta temporal interna segura
                System.setProperty("java.io.tmpdir", getCacheDir().getAbsolutePath());

                server = new FileServer(8080);
                server.start(0, false); // Timeout infinito (sin cortes)
                isRunning = true;

                String ip = NetworkUtils.getDeviceIpAddress(this);
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Servidor de archivos activo")
                        .setContentText("Disponible en: http://" + ip + ":8080")
                        .setSmallIcon(android.R.drawable.stat_sys_upload)
                        .setOngoing(true)
                        .build();

                startForeground(1, notification);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
            server = null;
        }
        // Liberar la antena cuando el usuario presiona "DETENER"
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            wifiLock = null;
        }
        isRunning = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Servidor de Transferencia",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
