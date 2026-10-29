package com.example.tvexplorer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.widget.ProgressBar;
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

public class MainActivity extends AppCompatActivity implements FileServer.TransferListener {

    private TextView tvStatus, tvIpAddress, tvCurrentPath;
    private TextView tvPreviewName, tvPreviewDetails;
    private TextView tvTransferDetail;
    private ProgressBar pbTransfer;
    private ImageView ivPreview;
    private LinearLayout panelPreview;
    private Button btnToggleServer, btnSettings;
    private ListView lvFiles;

    private File currentDir;
    private List<File> currentFiles = new ArrayList<>();
    private FileAdapter fileAdapter;

    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private long backPressedTime = 0;

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
        tvTransferDetail = findViewById(R.id.tvTransferDetail);
        pbTransfer = findViewById(R.id.pbTransfer);
        ivPreview = findViewById(R.id.ivPreview);
        panelPreview = findViewById(R.id.panelPreview);
        btnToggleServer = findViewById(R.id.btnToggleServer);
        btnSettings = findViewById(R.id.btnSettings);
        lvFiles = findViewById(R.id.lvFiles);

        FileServer.listener = this;

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
        FileServer.listener = this;
        updateUI();
        applyPanelVisibility();
        if (currentDir != null) {
            loadFiles(currentDir);
        }
    }

    // AVISO EN PANTALLA EN TIEMPO REAL AL RECIBIR UN ARCHIVO
    @Override
    public void onFileReceived(String fileName, int index, int total) {
        mainHandler.post(() -> {
            tvStatus.setText("📥 RECIBIDO (" + index + "/" + total + ")");
            tvStatus.setTextColor(0xFF4CAF50);
            tvTransferDetail.setVisibility(View.VISIBLE);
            tvTransferDetail.setText("Guardado en /Download: " + fileName);
            pbTransfer.setVisibility(View.VISIBLE);
            pbTransfer.setProgress(Math.round(((float) index / total) * 100));

            // Refrescar lista de inmediato
            if (currentDir != null) {
                loadFiles(currentDir);
            }

            // Ocultar barra 3 segundos después del último archivo
            if (index == total) {
                mainHandler.postDelayed(() -> {
                    pbTransfer.setVisibility(View.GONE);
                    tvTransferDetail.setVisibility(View.GONE);
                    updateUI();
                }, 3500);
            }
        });
    }

    private void applyPanelVisibility() {
        boolean showPanel = prefs.getBoolean("show_preview_panel", true);
        panelPreview.setVisibility(showPanel ? View.VISIBLE : View.GONE);
    }

    private void setupExplorer() {
        fileAdapter = new FileAdapter();
        lvFiles.setAdapter(fileAdapter);

        lvFiles.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < currentFiles.size() && panelPreview.getVisibility() == View.VISIBLE) {
                    updatePreviewPanel(currentFiles.get(position));
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        lvFiles.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < currentFiles.size()) {
                File selected = currentFiles.get(position);
                if (selected.isDirectory()) {
                    loadFiles(selected);
                } else {
                    openFile(selected);
                }
            }
        });

        lvFiles.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < currentFiles.size()) {
                File selected = currentFiles.get(position);
                new AlertDialog.Builder(this)
                        .setTitle("Eliminar")
                        .setMessage("¿Deseas eliminar:\n" + selected.getName() + "?")
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
            TextView tvName, tvSubtext;

            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(14, 8, 14, 8);

                ivIcon = new ImageView(MainActivity.this);
                int size = dpToPx(38);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
                iconParams.setMarginEnd(dpToPx(12));
                ivIcon.setLayoutParams(iconParams);
                ivIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(ivIcon);

                LinearLayout textCol = new LinearLayout(MainActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                tvName = new TextView(MainActivity.this);
                tvName.setTextColor(0xFFEEEEEE);
                tvName.setTextSize(15);
                tvName.setSingleLine(true);
                textCol.addView(tvName);

                tvSubtext = new TextView(MainActivity.this);
                tvSubtext.setTextColor(0xFF888888);
                tvSubtext.setTextSize(12);
                tvSubtext.setSingleLine(true);
                textCol.addView(tvSubtext);

                row.addView(textCol);
            } else {
                row = (LinearLayout) convertView;
                ivIcon = (ImageView) row.getChildAt(0);
                LinearLayout textCol = (LinearLayout) row.getChildAt(1);
                tvName = (TextView) textCol.getChildAt(0);
                tvSubtext = (TextView) textCol.getChildAt(1);
            }

            if (position < 0 || position >= currentFiles.size()) return row;

            File file = currentFiles.get(position);
            String name = file.getName();
            tvName.setText(name);

            String dateStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(file.lastModified()));
            if (file.isDirectory()) {
                String[] ch = file.list();
                int c = (ch != null) ? ch.length : 0;
                tvSubtext.setText((c == 1 ? "1 elemento" : c + " elementos") + "  •  " + dateStr);
            } else {
                long sizeKB = file.length() / 1024;
                String sizeText = (sizeKB > 1024) ? String.format(Locale.getDefault(), "%.2f MB", sizeKB / 1024.0) : sizeKB + " KB";
                tvSubtext.setText(sizeText + "  •  " + dateStr);
            }

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

    private void updatePreviewPanel(File file) {
        if (file == null) return;
        tvPreviewName.setText(file.getName());
        String dateStr = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(file.lastModified()));

        if (file.isDirectory()) {
            String[] children = file.list();
            int count = (children != null) ? children.length : 0;
            tvPreviewDetails.setText((count == 1 ? "1 elemento" : count + " elementos") + "\n" + dateStr);
            ivPreview.setImageResource(android.R.drawable.ic_menu_agenda);
            return;
        }

        long sizeKB = file.length() / 1024;
        String sizeText = (sizeKB > 1024) ? String.format(Locale.getDefault(), "%.2f MB", sizeKB / 1024.0) : sizeKB + " KB";
        tvPreviewDetails.setText(sizeText + "\n" + dateStr);

        String name = file.getName().toLowerCase();
        String path = file.getAbsolutePath();
        ivPreview.setTag(path);

        if (isImage(name)) {
            executor.execute(() -> {
                Bitmap bmp = decodeSampledBitmap(file, 350, 350);
                mainHandler.post(() -> {
                    if (path.equals(ivPreview.getTag())) {
                        if (bmp != null) ivPreview.setImageBitmap(bmp);
                        else ivPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                });
            });
        } else if (isVideo(name)) {
            executor.execute(() -> {
                Bitmap bmp = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
                mainHandler.post(() -> {
                    if (path.equals(ivPreview.getTag())) {
                        if (bmp != null) ivPreview.setImageBitmap(bmp);
                        else ivPreview.setImageResource(android.R.drawable.ic_media_play);
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
        String[] menuItems = {
            "Mostrar miniaturas en la lista",
            "Panel lateral de vista previa",
            "Ordenar archivos"
        };

        new AlertDialog.Builder(this)
                .setTitle("Configuración")
                .setItems(menuItems, (dialog, which) -> {
                    if (which == 0) toggleThumbnailsOption();
                    else if (which == 1) togglePanelOption();
                    else if (which == 2) showSortDialog();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void toggleThumbnailsOption() {
        boolean fullThumbs = prefs.getBoolean("full_thumbnails", false);
        String[] opts = {"Desactivado (Recomendado)", "Activado"};
        new AlertDialog.Builder(this)
                .setTitle("Mostrar miniaturas en la lista")
                .setSingleChoiceItems(opts, fullThumbs ? 1 : 0, (d, w) -> {
                    prefs.edit().putBoolean("full_thumbnails", w == 1).apply();
                    d.dismiss();
                    fileAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private void togglePanelOption() {
        boolean showPanel = prefs.getBoolean("show_preview_panel", true);
        String[] opts = {"Visible", "Oculto (Lista pantalla completa)"};
        new AlertDialog.Builder(this)
                .setTitle("Panel lateral de vista previa")
                .setSingleChoiceItems(opts, showPanel ? 0 : 1, (d, w) -> {
                    prefs.edit().putBoolean("show_preview_panel", w == 0).apply();
                    d.dismiss();
                    applyPanelVisibility();
                })
                .show();
    }

    private void showSortDialog() {
        String[] sortOptions = {
            "Nombre (A - Z)",
            "Fecha (Más recientes primero)",
            "Tamaño (Más grandes primero)",
            "Tipo de archivo"
        };
        int currentSort = prefs.getInt("sort_mode", 0);

        new AlertDialog.Builder(this)
                .setTitle("Ordenar archivos por:")
                .setSingleChoiceItems(sortOptions, currentSort, (d, w) -> {
                    prefs.edit().putInt("sort_mode", w).apply();
                    d.dismiss();
                    loadFiles(currentDir);
                })
                .show();
    }

    private void loadFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.canRead()) return;
        currentDir = dir;
        tvCurrentPath.setText("📁 " + currentDir.getAbsolutePath());

        File[] files = currentDir.listFiles();
        List<File> combined = new ArrayList<>();

        if (files != null) {
            List<File> dirList = new ArrayList<>();
            List<File> fileList = new ArrayList<>();

            for (File f : files) {
                if (f.isHidden()) continue;
                if (f.isDirectory()) dirList.add(f);
                else fileList.add(f);
            }

            int sortMode = prefs.getInt("sort_mode", 0);
            if (sortMode == 0) {
                Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(fileList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            } else if (sortMode == 1) {
                Collections.sort(dirList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                Collections.sort(fileList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            } else if (sortMode == 2) {
                Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(fileList, (a, b) -> Long.compare(b.length(), a.length()));
            } else if (sortMode == 3) {
                Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(fileList, (a, b) -> getFileExtension(a).compareToIgnoreCase(getFileExtension(b)));
            }

            combined.addAll(dirList);
            combined.addAll(fileList);
        }

        currentFiles = combined;
        fileAdapter.notifyDataSetChanged();

        if (!currentFiles.isEmpty() && panelPreview.getVisibility() == View.VISIBLE) {
            updatePreviewPanel(currentFiles.get(0));
        }
    }

    private String getFileExtension(File f) {
        String n = f.getName();
        int idx = n.lastIndexOf('.');
        return (idx > 0) ? n.substring(idx) : "";
    }

    private void openFile(File file) {
        if (file == null) return;
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
            if (currentDir != null) {
                File parent = currentDir.getParentFile();
                String path = currentDir.getAbsolutePath();
                if (parent != null && parent.canRead() && !path.equals("/storage/emulated/0") && !path.equals("/storage/emulated") && !path.equals("/")) {
                    loadFiles(parent);
                    return true;
                }
            }

            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                finish();
                return true;
            } else {
                Toast.makeText(this, "Presiona Atrás otra vez para salir", Toast.LENGTH_SHORT).show();
                backPressedTime = System.currentTimeMillis();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isImage(String n) { 
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".jfif") || n.endsWith(".gif"); 
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e1) {
                        try {
                            Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(fallback);
                        } catch (Exception ignored) {}
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 100);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FileServer.listener = null;
        executor.shutdown();
    }
}
