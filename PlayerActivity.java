package com.example.tvexplorer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;
import java.io.File;

public class PlayerActivity extends Activity {

    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.widget.FrameLayout layout = new android.widget.FrameLayout(this);
        layout.setBackgroundColor(0xFF000000);

        String filePath = getIntent().getStringExtra("FILE_PATH");
        String fileType = getIntent().getStringExtra("FILE_TYPE");

        if (filePath == null) {
            finish();
            return;
        }

        File file = new File(filePath);

        if ("video".equals(fileType) || "audio".equals(fileType)) {
            videoView = new VideoView(this);
            videoView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ));
            layout.addView(videoView);

            MediaController mediaController = new MediaController(this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
            videoView.setVideoURI(Uri.fromFile(file));
            videoView.setOnPreparedListener(mp -> videoView.start());
            videoView.setOnCompletionListener(mp -> finish());

        } else if ("image".equals(fileType)) {
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            layout.addView(imageView);

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }

        setContentView(layout);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Evitar que el evento rebote y cierre la pantalla principal
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
