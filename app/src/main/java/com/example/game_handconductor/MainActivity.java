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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                bgImage.setRenderEffect(
                        RenderEffect.createBlurEffect(
                                30f,
                                30f,
                                Shader.TileMode.CLAMP
                        )
                );
            }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bgImage.setRenderEffect(
                    RenderEffect.createBlurEffect(
                            30f,
                            30f,
                            Shader.TileMode.CLAMP
                    )
            );
        }

        // 1. 绑定UI组件
        Button btnStartGame = findViewById(R.id.btn_start_game);
        Button btnHighlights = findViewById(R.id.btn_highlights);
        Button btnSettings = findViewById(R.id.btn_settings);

        // 2. 设置“开始游戏”点击事件 -> 跳转 G-0 界面
        btnStartGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 假设目标 Activity 名为 G0_Activity
                Intent intent = new Intent(MainActivity.this, G0_Activity.class);
                startActivity(intent);
            }
        });

        // 3. 设置“精彩瞬间”点击事件 -> 跳转 L-1 界面
        btnHighlights.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 假设目标 Activity 名为 L1_Activity
                Intent intent = new Intent(MainActivity.this, L1_Activity.class);
                startActivity(intent);
            }
        });

        // 4. 设置“设置”点击事件 -> 跳转 S-1 界面
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 假设目标 Activity 名为 S1_Activity
                Intent intent = new Intent(MainActivity.this, S1_Activity.class);
                startActivity(intent);
            }
        });
    }
}