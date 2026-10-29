package com.example.tvexplorer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;
import java.io.File;

public class PlayerActivity extends Activity {

    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(0xFF000000);

        String filePath = getIntent().getStringExtra("FILE_PATH");
        String fileType = getIntent().getStringExtra("FILE_TYPE");

        if (filePath == null) {
            finish();
            return;
        }

        File file = new File(filePath);
        String name = file.getName().toLowerCase();

        // 1. VIDEOS (CENTRADO PERFECTO PARA FORMATO TIKTOK / VERTICAL)
        if ("video".equals(fileType) || "audio".equals(fileType)) {
            videoView = new VideoView(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER // Centrado exacto horizontal y vertical
            );
            videoView.setLayoutParams(params);
            layout.addView(videoView);

            MediaController mediaController = new MediaController(this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
            videoView.setVideoURI(Uri.fromFile(file));
            videoView.setOnPreparedListener(mp -> videoView.start());
            videoView.setOnCompletionListener(mp -> finish());

        // 2. FOTOS Y GIFS ANIMADOS
        } else if ("image".equals(fileType)) {
            if (name.endsWith(".gif")) {
                reproducirGif(layout, file);
            } else {
                ImageView imageView = new ImageView(this);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                );
                imageView.setLayoutParams(params);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                layout.addView(imageView);

                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }

        setContentView(layout);
    }

    private void reproducirGif(FrameLayout layout, File file) {
        // En Android 9+ (API 28+) usa ImageDecoder nativo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(file);
                Drawable drawable = ImageDecoder.decodeDrawable(source);

                ImageView iv = new ImageView(this);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                );
                iv.setLayoutParams(params);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(drawable);

                if (drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) drawable).start();
                }
                layout.addView(iv);
                return;
            } catch (Exception ignored) {}
        }

        // Para Android 5 a 8 usa la clase Movie nativa (cero librerías)
        GifMovieView gifView = new GifMovieView(this, file);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
        gifView.setLayoutParams(params);
        layout.addView(gifView);
    }

    // Vista ultra ligera para reproducir GIFs en bucle en TVs más antiguas
    private static class GifMovieView extends View {
        private final Movie movie;
        private long movieStart;

        public GifMovieView(Context context, File file) {
            super(context);
            movie = Movie.decodeFile(file.getAbsolutePath());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (movie == null) return;
            long now = SystemClock.uptimeMillis();
            if (movieStart == 0) movieStart = now;

            int duration = movie.duration();
            if (duration == 0) duration = 1000;
            int relTime = (int) ((now - movieStart) % duration);
            movie.setTime(relTime);

            float scaleX = (float) getWidth() / movie.width();
            float scaleY = (float) getHeight() / movie.height();
            float scale = Math.min(scaleX, scaleY);

            float x = (getWidth() - (movie.width() * scale)) / 2f;
            float y = (getHeight() - (movie.height() * scale)) / 2f;

            canvas.save();
            canvas.translate(x, y);
            canvas.scale(scale, scale);
            movie.draw(canvas, 0, 0);
            canvas.restore();

            invalidate(); // Siguiente frame
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }
}
