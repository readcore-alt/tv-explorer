package com.example.tvexplorer;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvIpAddress, tvCurrentPath;
    private Button btnToggleServer;
    private ListView lvFiles;

    private File currentDir;
    private List<File> currentFiles = new ArrayList<>();
    private ArrayAdapter<String> fileAdapter;
    private List<String> fileNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvCurrentPath = findViewById(R.id.tvCurrentPath);
        btnToggleServer = findViewById(R.id.btnToggleServer);
        lvFiles = findViewById(R.id.lvFiles);

        checkStoragePermission();

        btnToggleServer.setOnClickListener(v -> toggleServer());

        // Iniciar en la carpeta Downloads o en la raíz
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        currentDir = (downloads != null && downloads.exists()) ? downloads : Environment.getExternalStorageDirectory();

        setupExplorer();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
        loadFiles(currentDir);
    }

    private void setupExplorer() {
        fileAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, fileNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(0xFFEEEEEE);
                tv.setTextSize(16);
                tv.setPadding(16, 12, 16, 12);
                return view;
            }
        };
        lvFiles.setAdapter(fileAdapter);

        // Clic con el botón OK del control remoto
        lvFiles.setOnItemClickListener((parent, view, position, id) -> {
            if (position < currentFiles.size()) {
                File selected = currentFiles.get(position);
                if (selected.isDirectory()) {
                    loadFiles(selected);
                } else {
                    openFile(selected);
                }
            }
        });

        // Mantener presionado OK para borrar un archivo
        lvFiles.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < currentFiles.size()) {
                File selected = currentFiles.get(position);
                new AlertDialog.Builder(this)
                        .setTitle("¿Eliminar archivo?")
                        .setMessage("¿Deseas borrar: " + selected.getName() + "?")
                        .setPositiveButton("Eliminar", (dialog, which) -> {
                            if (selected.delete()) {
                                Toast.makeText(this, "Eliminado", Toast.LENGTH_SHORT).show();
                                loadFiles(currentDir);
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
            return true;
        });
    }

    private void loadFiles(File dir) {
        if (dir == null || !dir.exists()) return;
        currentDir = dir;
        tvCurrentPath.setText("📁 " + currentDir.getAbsolutePath());

        File[] files = currentDir.listFiles();
        currentFiles.clear();
        fileNames.clear();

        if (files != null) {
            List<File> dirList = new ArrayList<>();
            List<File> fileList = new ArrayList<>();

            for (File f : files) {
                if (f.isHidden()) continue;
                if (f.isDirectory()) dirList.add(f);
                else fileList.add(f);
            }

            Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            Collections.sort(fileList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            currentFiles.addAll(dirList);
            currentFiles.addAll(fileList);

            for (File f : currentFiles) {
                String name = f.getName();
                if (f.isDirectory()) {
                    fileNames.add("📁  " + name);
                } else if (isApk(name)) {
                    fileNames.add("📦  " + name);
                } else if (isVideo(name)) {
                    fileNames.add("🎬  " + name);
                } else if (isImage(name)) {
                    fileNames.add("🖼️  " + name);
                } else if (isAudio(name)) {
                    fileNames.add("🎵  " + name);
                } else {
                    fileNames.add("📄  " + name);
                }
            }
        }
        fileAdapter.notifyDataSetChanged();
    }

    private void openFile(File file) {
        String name = file.getName().toLowerCase();

        if (isApk(name)) {
            installApk(file);
        } else if (isVideo(name) || isAudio(name)) {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("FILE_PATH", file.getAbsolutePath());
            intent.putExtra("FILE_TYPE", isVideo(name) ? "video" : "audio");
            startActivity(intent);
        } else if (isImage(name)) {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("FILE_PATH", file.getAbsolutePath());
            intent.putExtra("FILE_TYPE", "image");
            startActivity(intent);
        } else {
            Toast.makeText(this, "Formato no compatible", Toast.LENGTH_SHORT).show();
        }
    }

    private void installApk(File file) {
        try {
            Uri apkUri;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(file);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Al pulsar ATRÁS en el control remoto: subir un nivel de carpeta
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            File parent = currentDir.getParentFile();
            if (parent != null && parent.canRead() && !currentDir.getAbsolutePath().equals("/storage/emulated")) {
                loadFiles(parent);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isApk(String n) { return n.endsWith(".apk"); }
    private boolean isVideo(String n) { return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".avi"); }
    private boolean isImage(String n) { return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"); }
    private boolean isAudio(String n) { return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".aac"); }

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
            Toast.makeText(this, "Servidor iniciado", Toast.LENGTH_SHORT).show();
        }
        btnToggleServer.postDelayed(this::updateUI, 300);
    }

    private void updateUI() {
        if (ServerService.isRunning) {
            String ip = NetworkUtils.getDeviceIpAddress(this);
            tvStatus.setText("SERVIDOR: ACTIVO");
            tvStatus.setTextColor(0xFF4CAF50);
            tvIpAddress.setText("Entra desde tu PC a: http://" + ip + ":8080");
            btnToggleServer.setText("DETENER");
        } else {
            tvStatus.setText("SERVIDOR: APAGADO");
            tvStatus.setTextColor(0xFFF44336);
            tvIpAddress.setText("Presiona el botón para recibir archivos");
            btnToggleServer.setText("INICIAR");
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
