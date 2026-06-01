package com.example.game_handconductor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class GMX_Activity extends AppCompatActivity {

    private ProgressBar pbSongProgress;
    private ValueAnimator progressAnimator;

    private String songName;
    private int songCoverId;
    private int currentMaxCombo = 21;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gmx);

        // 获取上一层传递的歌曲数据
        Intent intent = getIntent();
        songName = intent.getStringExtra("SONG_NAME");
        songCoverId = intent.getIntExtra("SONG_COVER_ID", R.mipmap.ic_launcher);

        pbSongProgress = findViewById(R.id.pb_song_progress);
        TextView tvMaxCombo = findViewById(R.id.tv_max_combo);
        Button btnReturn = findViewById(R.id.btn_return_g0);
        Button btnSettings = findViewById(R.id.btn_settings_s1);

        tvMaxCombo.setText("最大连击数 " + currentMaxCombo);

        // 返回按钮逻辑：彻底退出当前游戏进度，返回选歌界面
        btnReturn.setOnClickListener(v -> {
            if (progressAnimator != null) progressAnimator.cancel();
            Intent g0Intent = new Intent(GMX_Activity.this, G0_Activity.class);
            g0Intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(g0Intent);
            finish();
        });

        // 设置按钮逻辑：跳转设置界面。不销毁当前Activity。
        btnSettings.setOnClickListener(v -> {
            Intent s1Intent = new Intent(GMX_Activity.this, S1_Activity.class);
            startActivity(s1Intent);
        });

        setupProgressAnimator();
    }

    private void setupProgressAnimator() {
        // 使用 ValueAnimator 模拟进度条填充，此处设定测试时长为10秒
        progressAnimator = ValueAnimator.ofInt(0, 100);
        progressAnimator.setDuration(10000);
        progressAnimator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            pbSongProgress.setProgress(progress);
        });

        // 监听进度条结束
        progressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                goToFXActivity();
            }
        });
    }

    private void goToFXActivity() {
        Intent fxIntent = new Intent(GMX_Activity.this, FX_Activity.class);
        fxIntent.putExtra("SONG_NAME", songName);
        fxIntent.putExtra("SONG_COVER_ID", songCoverId);
        fxIntent.putExtra("RANK", "S");
        fxIntent.putExtra("COMPLETION", "100%");
        fxIntent.putExtra("HITS", 150);
        fxIntent.putExtra("MAX_COMBO", currentMaxCombo);
        fxIntent.putExtra("MISSES", 0);
        startActivity(fxIntent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 生命周期：当从设置(S-1)返回时，进度条自动继续
        if (progressAnimator != null) {
            if (progressAnimator.isPaused()) {
                progressAnimator.resume();
            } else if (!progressAnimator.isRunning()) {
                progressAnimator.start();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 生命周期：当跳转设置(S-1)或切换后台时，进度条暂停
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 防止内存泄漏
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
    }
}