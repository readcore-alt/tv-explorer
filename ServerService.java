package com.example.tvexplorer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class ServerService extends Service {
    private static final String CHANNEL_ID = "TV_File_Server_Channel";
    private FileServer server;
    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            try {
                // 1. Redirigir la carpeta temporal a la memoria permitida de la app
                System.setProperty("java.io.tmpdir", getCacheDir().getAbsolutePath());

                server = new FileServer(8080);

                // 2. TIMEOUT 0 = INFINITO: Jamás se cortará la subida de archivos grandes
                server.start(0, false);
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
