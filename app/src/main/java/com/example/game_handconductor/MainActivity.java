package com.example.game_handconductor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;

import java.io.IOException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.InputStream;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private ImageView bgImage;

    private void loadRandomBackground() {

        try {

            String[] files =
                    getAssets().list("background");

            if (files == null || files.length == 0) {
                return;
            }

            Random random = new Random();

            String randomFile =
                    files[random.nextInt(files.length)];

            InputStream is =
                    getAssets().open(
                            "background/" + randomFile
                    );

            Bitmap bitmap =
                    BitmapFactory.decodeStream(is);

            bgImage.setImageBitmap(bitmap);

            is.close();

            bgImage.setRenderEffect(
                    RenderEffect.createBlurEffect(
                            30f,
                            30f,
                            Shader.TileMode.CLAMP
                    )
            );

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bgImage = findViewById(R.id.bgImage);

        loadRandomBackground();

        Button btnStartGame = findViewById(R.id.btn_start_game);
        Button btnHighlights = findViewById(R.id.btn_highlights);
        Button btnSettings = findViewById(R.id.btn_settings);

        btnStartGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, G0_Activity.class);
                startActivity(intent);
            }
        });

        btnHighlights.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, L1_Activity.class);
                startActivity(intent);
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, S1_Activity.class);
                startActivity(intent);
            }
        });
    }
}