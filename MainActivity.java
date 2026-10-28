package com.example.tvexplorer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvIpAddress, tvCurrentPath;
    private TextView tvPreviewName, tvPreviewDetails;
    private ImageView ivPreview;
    private Button btnToggleServer, btnSettings;
    private ListView lvFiles;

    private File currentDir;
    private List<File> currentFiles = new ArrayList<>();
    private ArrayAdapter<String> fileAdapter;
    private List<String> fileNames = new ArrayList<>();

    private SharedPreferences prefs;
    private Bitmap currentPreviewBitmap = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("tv_explorer_prefs", Context.MODE_PRIVATE);

        tvStatus = findViewById(R.id.tvStatus);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvCurrentPath = findViewById(R.id.tvCurrentPath);
        tvPreviewName = findViewById(R.id.tvPreviewName);
        tvPreviewDetails = findViewById(R.id.tvPreviewDetails);
        ivPreview = findViewById(R.id.ivPreview);
        btnToggleServer = findViewById(R.id.btnToggleServer);
        btnSettings = findViewById(R.id.btnSettings);
        lvFiles = findViewById(R.id.lvFiles);

        checkStoragePermission();

        btnToggleServer.setOnClickListener(v -> toggleServer());
        btnSettings.setOnClickListener(v -> showSettingsDialog());

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
                tv.setTextSize(15);
                tv.setPadding(12, 10, 12, 10);
                return view;
            }
        };
        lvFiles.setAdapter(fileAdapter);

        // Al moverte con las flechas del control (Focus): genera preview único
        lvFiles.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < currentFiles.size()) {
                    updatePreviewPanel(currentFiles.get(position));
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Pulsar OK para abrir
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

        // Mantener presionado OK para borrar
        lvFiles.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < currentFiles.size()) {
                File selected = currentFiles.get(position);
                new AlertDialog.Builder(this)
                        .setTitle("¿Eliminar archivo?")
                        .setMessage("¿Deseas borrar permanentemente:\n" + selected.getName() + "?")
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

    // Actualiza la miniatura en tiempo real para el archivo enfocado (0 MB acumulación de RAM)
    private void updatePreviewPanel(File file) {
        tvPreviewName.setText(file.getName());
        String dateStr = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(file.lastModified()));

        if (file.isDirectory()) {
            tvPreviewDetails.setText("Carpeta\n" + dateStr);
            ivPreview.setImageResource(android.R.drawable.ic_menu_agenda);
            return;
        }

        long sizeKB = file.length() / 1024;
        String sizeText = (sizeKB > 1024) ? String.format(Locale.getDefault(), "%.2f MB", sizeKB / 1024.0) : sizeKB + " KB";
        tvPreviewDetails.setText(sizeText + "\n" + dateStr);

        String name = file.getName().toLowerCase();

        // Liberar la memoria RAM de la miniatura anterior antes de cargar la nueva
        if (currentPreviewBitmap != null && !currentPreviewBitmap.isRecycled()) {
            currentPreviewBitmap.recycle();
            currentPreviewBitmap = null;
        }

        if (isImage(name)) {
            executor.execute(() -> {
                Bitmap bmp = decodeSampledBitmap(file, 300, 300);
                mainHandler.post(() -> {
                    if (bmp != null) {
                        currentPreviewBitmap = bmp;
                        ivPreview.setImageBitmap(bmp);
                    } else {
                        ivPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                });
            });
        } else if (isVideo(name)) {
            executor.execute(() -> {
                Bitmap bmp = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
                mainHandler.post(() -> {
                    if (bmp != null) {
                        currentPreviewBitmap = bmp;
                        ivPreview.setImageBitmap(bmp);
                    } else {
                        ivPreview.setImageResource(android.R.drawable.ic_media_play);
                    }
                });
            });
        } else if (isApk(name)) {
            ivPreview.setImageResource(android.R.drawable.ic_menu_set_as);
        } else {
            ivPreview.setImageResource(android.R.drawable.ic_menu_save);
        }
    }

    private Bitmap decodeSampledBitmap(File file, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void showSettingsDialog() {
        boolean fullThumbs = prefs.getBoolean("full_thumbnails", false);
        String[] options = {"Modo Rendimiento: Ultra Ahorro (Recomendado)", "Modo Visual: Miniaturas simultáneas en lista"};
        int checkedItem = fullThumbs ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Ajustes de Exploración")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    prefs.edit().putBoolean("full_thumbnails", which == 1).apply();
                    Toast.makeText(this, which == 1 ? "Modo visual activado" : "Ultra Ahorro activado", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadFiles(currentDir);
                })
                .setNegativeButton("Cerrar", null)
                .show();
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
        if (!currentFiles.isEmpty()) {
            updatePreviewPanel(currentFiles.get(0));
        }
    }

    private void openFile(File file) {
        String name = file.getName().toLowerCase();
        if (isApk(name)) {
            installApk(file);
        } else if (isVideo(name) || isAudio(name) || isImage(name)) {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("FILE_PATH", file.getAbsolutePath());
            intent.putExtra("FILE_TYPE", isImage(name) ? "image" : (isVideo(name) ? "video" : "audio"));
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            File parent = currentDir.getParentFile();
            if (parent != null && parent.canRead() && !currentDir.getAbsolutePath().equals("/storage/emulated")) {
                loadFiles(parent);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // SOPORTE COMPLETO .jfif AGREGADO AQUÍ
    private boolean isImage(String n) { 
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".jfif"); 
    }
    private boolean isVideo(String n) { return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".avi"); }
    private boolean isAudio(String n) { return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".aac"); }
    private boolean isApk(String n) { return n.endsWith(".apk"); }

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
            tvIpAddress.setText("Presiona INICIAR para recibir archivos");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentPreviewBitmap != null && !currentPreviewBitmap.isRecycled()) {
            currentPreviewBitmap.recycle();
        }
        executor.shutdown();
    }
}
