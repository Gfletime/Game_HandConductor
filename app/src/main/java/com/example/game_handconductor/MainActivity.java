package com.example.game_handconductor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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