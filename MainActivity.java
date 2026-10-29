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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private FileAdapter fileAdapter;

    private SharedPreferences prefs;
    private Bitmap currentPreviewBitmap = null;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
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
        fileAdapter = new FileAdapter();
        lvFiles.setAdapter(fileAdapter);

        // Al mover el cursor del control remoto: actualiza el panel derecho
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

        // Pulsar OK para abrir carpeta o archivo
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

    // Adaptador personalizado: crea un icono/miniatura a la izquierda + texto
    private class FileAdapter extends BaseAdapter {

        @Override
        public int getCount() { return currentFiles.size(); }
        @Override
        public Object getItem(int position) { return currentFiles.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ImageView ivIcon;
            TextView tvName;

            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(14, 10, 14, 10);

                ivIcon = new ImageView(MainActivity.this);
                int size = dpToPx(38);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
                iconParams.setMarginEnd(dpToPx(12));
                ivIcon.setLayoutParams(iconParams);
                ivIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(ivIcon);

                tvName = new TextView(MainActivity.this);
                tvName.setTextColor(0xFFEEEEEE);
                tvName.setTextSize(15);
                tvName.setSingleLine(true);
                tvName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                row.addView(tvName);
            } else {
                row = (LinearLayout) convertView;
                ivIcon = (ImageView) row.getChildAt(0);
                tvName = (TextView) row.getChildAt(1);
            }

            File file = currentFiles.get(position);
            String name = file.getName();
            tvName.setText(name);

            boolean fullThumbs = prefs.getBoolean("full_thumbnails", false);
            String path = file.getAbsolutePath();
            ivIcon.setTag(path);

            if (file.isDirectory()) {
                ivIcon.setImageResource(android.R.drawable.ic_menu_agenda);
            } else if (isApk(name.toLowerCase())) {
                ivIcon.setImageResource(android.R.drawable.ic_menu_set_as);
            } else if (isAudio(name.toLowerCase())) {
                ivIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            } else if (isImage(name.toLowerCase()) || isVideo(name.toLowerCase())) {
                if (fullThumbs) {
                    // MODO VISUAL: Carga miniaturas reales al lado del nombre (Estilo Cx)
                    ivIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                    executor.execute(() -> {
                        Bitmap thumb;
                        if (isImage(name.toLowerCase())) {
                            thumb = decodeSampledBitmap(file, 80, 80);
                        } else {
                            thumb = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Images.Thumbnails.MICRO_KIND);
                        }
                        mainHandler.post(() -> {
                            if (path.equals(ivIcon.getTag()) && thumb != null) {
                                ivIcon.setImageBitmap(thumb);
                            }
                        });
                    });
                } else {
                    // MODO ULTRA AHORRO: Icono estático veloz (0 MB RAM)
                    ivIcon.setImageResource(isVideo(name.toLowerCase()) ? android.R.drawable.ic_media_play : android.R.drawable.ic_menu_gallery);
                }
            } else {
                ivIcon.setImageResource(android.R.drawable.ic_menu_save);
            }

            return row;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // Panel lateral derecho: Vista previa individual en tamaño grande
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

        // Liberar la memoria RAM de la miniatura previa
        if (currentPreviewBitmap != null && !currentPreviewBitmap.isRecycled()) {
            currentPreviewBitmap.recycle();
            currentPreviewBitmap = null;
        }

        if (isImage(name)) {
            executor.execute(() -> {
                Bitmap bmp = decodeSampledBitmap(file, 350, 350);
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
        String[] options = {
            "⚡ Modo Rendimiento (Lista instantánea + Vista previa derecha)",
            "🖼️ Modo Visual (Miniaturas directas en la lista al estilo Cx)"
        };
        int checkedItem = fullThumbs ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Ajustes de Exploración")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    boolean enableVisual = (which == 1);
                    prefs.edit().putBoolean("full_thumbnails", enableVisual).apply();
                    Toast.makeText(this, enableVisual ? "Modo Visual activado" : "Modo Rendimiento activado", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    fileAdapter.notifyDataSetChanged();
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

    // Compatible con .jfif
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
