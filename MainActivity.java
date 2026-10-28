package com.example.tvexplorer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvIpAddress;
    private Button btnToggleServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        btnToggleServer = findViewById(R.id.btnToggleServer);

        // Pedir permiso de almacenamiento para Android 11+ en TV
        checkStoragePermission();

        // Configurar botón para el control remoto
        btnToggleServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleServer();
            }
        });

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void toggleServer() {
        Intent serviceIntent = new Intent(this, ServerService.class);

        if (ServerService.isRunning) {
            stopService(serviceIntent);
            Toast.makeText(this, "Servidor apagado", Toast.LENGTH_SHORT).show();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Servidor iniciado en segundo plano", Toast.LENGTH_SHORT).show();
        }

        // Pequeña pausa para refrescar el estado
        btnToggleServer.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateUI();
            }
        }, 300);
    }

    private void updateUI() {
        if (ServerService.isRunning) {
            String ip = NetworkUtils.getDeviceIpAddress(this);
            tvStatus.setText("ESTADO: ACTIVO (SEGUNDO PLANO)");
            tvStatus.setTextColor(0xFF4CAF50); // Verde
            tvIpAddress.setText("Entra desde tu PC a:\nhttp://" + ip + ":8080");
            btnToggleServer.setText("DETENER SERVIDOR");
        } else {
            tvStatus.setText("ESTADO: APAGADO (0 MB RAM)");
            tvStatus.setTextColor(0xFFF44336); // Rojo
            tvIpAddress.setText("Presiona el botón para activar");
            btnToggleServer.setText("INICIAR SERVIDOR");
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
}
