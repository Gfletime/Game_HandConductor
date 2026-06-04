package com.example.game_handconductor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class FX_Activity extends AppCompatActivity {

    private ImageView vMainCover;
    private ImageView ivBackground;
    private String songName;
    private String songCoverFile; // 重构修复：将 int ID 升级为 String 资产文件名

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fx);

        // 1. 获取从 GMX 传过来的结算数据
        Intent intent = getIntent();
        songName = intent.getStringExtra("SONG_NAME");

        // 核心修复点：全面对接新架构，接收字符串性质的资产封面图片名称
        songCoverFile = intent.getStringExtra("SONG_COVER_FILE");
        if (songCoverFile == null || songCoverFile.isEmpty()) {
            songCoverFile = "default";
        }

        String rank = intent.getStringExtra("RANK");
        int hits = intent.getIntExtra("HITS", 0);
        int maxCombo = intent.getIntExtra("MAX_COMBO", 0);
        int misses = intent.getIntExtra("MISSES", 0);

        vMainCover = findViewById(R.id.iv_fx_cover);
        ivBackground = findViewById(R.id.iv_backgroundfx);

        // 启动高能资产图像渲染与高斯模糊引擎
        updateSongCover(songCoverFile);

        // 兼容性数据提取：完美支持 Integer 与带有 % 符号的 String
        int completion = 0;
        if (intent.hasExtra("COMPLETION")) {
            Object completionExtra = intent.getExtras().get("COMPLETION");
            if (completionExtra instanceof Integer) {
                completion = (Integer) completionExtra;
            } else if (completionExtra instanceof String) {
                try {
                    completion = Integer.parseInt(((String) completionExtra).replace("%", "").trim());
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }

        // 智能执行高分纪录本地安全覆写
        saveStatsToLocalCsv(songName, rank, completion, maxCombo);

        // 2. 绑定 UI 组件 (彻底剔除了原本此处对 iv_fx_cover 的冗余重复绑定与二次设置)
        TextView tvRank = findViewById(R.id.tv_fx_rank);
        TextView labelCompletion = (TextView) ((LinearLayout) findViewById(R.id.tv_fx_completion).getParent()).getChildAt(0);
        TextView labelHits = (TextView) ((LinearLayout) findViewById(R.id.tv_fx_hits).getParent()).getChildAt(0);
        TextView labelCombo = (TextView) ((LinearLayout) findViewById(R.id.tv_fx_combo).getParent()).getChildAt(0);
        TextView labelMisses = (TextView) ((LinearLayout) findViewById(R.id.tv_fx_misses).getParent()).getChildAt(0);

        TextView tvCompletion = findViewById(R.id.tv_fx_completion);
        TextView tvHits = findViewById(R.id.tv_fx_hits);
        TextView tvCombo = findViewById(R.id.tv_fx_combo);
        TextView tvMisses = findViewById(R.id.tv_fx_misses);

        Button btnToG0 = findViewById(R.id.btn_fx_to_g0);
        Button btnReplay = findViewById(R.id.btn_fx_replay);

        if (rank != null) {
            tvRank.setText(rank);
            applyRankColoring(tvRank, rank);
        }

        // ================= 3. 开始执行入场动画 =================
        hideViewsInitially(tvRank, labelCompletion, tvCompletion, labelHits, tvHits,
                labelCombo, tvCombo, labelMisses, tvMisses, btnToG0, btnReplay);
        long delay = 300;

        // 评级弹性弹出
        tvRank.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(new OvershootInterpolator(2.0f))
                .start();
        delay += 400;

        // 数据行依次跑字滚动渐显
        animateRow(labelCompletion, tvCompletion, completion, delay, true);
        delay += 250;
        animateRow(labelHits, tvHits, hits, delay, false);
        delay += 250;
        animateRow(labelCombo, tvCombo, maxCombo, delay, false);
        delay += 250;
        animateRow(labelMisses, tvMisses, misses, delay, false);
        delay += 400;

        // 底部按钮渐显
        fadeInView(btnToG0, delay);
        fadeInView(btnReplay, delay + 100);

        // =========================================================

        // 4. 处理跳转按钮逻辑
        btnToG0.setOnClickListener(v -> {
            Intent g0Intent = new Intent(FX_Activity.this, G0_Activity.class);
            g0Intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(g0Intent);
            finish();
        });

        btnReplay.setOnClickListener(v -> {
            Intent gmxIntent = new Intent(FX_Activity.this, GMX_Activity.class);
            gmxIntent.putExtra("SONG_NAME", songName);
            gmxIntent.putExtra("CSV_NAME", songName + ".csv");
            gmxIntent.putExtra("SONG_COVER_FILE", songCoverFile); // 将最新正确的文件名传递回对局页
            startActivity(gmxIntent);
            finish();
        });
    }

    /**
     * 重构打通：解耦并还原的 Assets 沙盒图片高能动态解码加载引擎
     */
    private void loadAndBindAssetCover(ImageView imageView, String fileName) {
        if (fileName == null || fileName.isEmpty() || "default".equalsIgnoreCase(fileName)) {
            imageView.setImageResource(R.mipmap.ic_launcher);
        } else {
            try {
                InputStream is = getAssets().open("Image/MusicPageFace/" + fileName);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                imageView.setImageBitmap(bitmap);
                is.close();
            } catch (Exception e) {
                e.printStackTrace();
                imageView.setImageResource(R.mipmap.ic_launcher);
            }
        }
    }

    /**
     * 重构修复：让封面卡片和背景大图全面通过流加载，彻底解决图片无法替换的 Bug
     */
    private void updateSongCover(String fileName) {
        loadAndBindAssetCover(vMainCover, fileName);
        loadAndBindAssetCover(ivBackground, fileName);

        // Android 12+ 强力硬件级背景磨砂模糊特效
        ivBackground.setRenderEffect(
                RenderEffect.createBlurEffect(
                        60f,
                        60f,
                        Shader.TileMode.CLAMP
                )
        );
    }

    // --- 动画辅助方法封装 ---
    private void hideViewsInitially(View... views) {
        for (int i = 0; i < views.length; i++) {
            views[i].setAlpha(0f);
            if (i == 0) {
                views[i].setScaleX(0f);
                views[i].setScaleY(0f);
            }
        }
    }

    private void animateRow(View labelView, TextView valueView, int targetValue, long delay, boolean isPercentage) {
        labelView.animate().alpha(1f).setDuration(300).setStartDelay(delay).start();
        valueView.animate().alpha(1f).setDuration(300).setStartDelay(delay).start();

        ValueAnimator animator = ValueAnimator.ofInt(0, targetValue);
        animator.setDuration(1000);
        animator.setStartDelay(delay);
        animator.addUpdateListener(animation -> {
            int currentValue = (int) animation.getAnimatedValue();
            if (isPercentage) {
                valueView.setText(currentValue + "%");
            } else {
                valueView.setText(String.valueOf(currentValue));
            }
        });
        animator.start();
    }

    private void fadeInView(View view, long delay) {
        view.animate()
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(delay)
                .start();
    }

    // ==================== 评级色彩高级分级渲染矩阵 ====================
    private void applyRankColoring(TextView tvRank, String rankStr) {
        String cleanRank = rankStr.toUpperCase().trim();
        tvRank.getPaint().setShader(null);

        if ("SSS".equals(cleanRank)) {
            float textWidth = tvRank.getPaint().measureText("SSS");
            Shader rainbowShader = new LinearGradient(
                    0, 0, textWidth, 0,
                    new int[]{
                            Color.parseColor("#FF1493"),
                            Color.parseColor("#FF4500"),
                            Color.parseColor("#FFD700"),
                            Color.parseColor("#00FF00"),
                            Color.parseColor("#00FFFF"),
                            Color.parseColor("#0000FF"),
                            Color.parseColor("#8A2BE2")
                    },
                    null, Shader.TileMode.CLAMP
            );
            tvRank.getPaint().setShader(rainbowShader);
            tvRank.setTextColor(Color.RED);
        } else if ("S".equals(cleanRank)) {
            tvRank.setTextColor(Color.parseColor("#FFD700"));
        } else if ("A".equals(cleanRank)) {
            tvRank.setTextColor(Color.parseColor("#9932CC"));
        } else if ("B".equals(cleanRank)) {
            tvRank.setTextColor(Color.parseColor("#1E90FF"));
        } else if ("C".equals(cleanRank)) {
            tvRank.setTextColor(Color.parseColor("#32CD32"));
        } else {
            tvRank.setTextColor(Color.parseColor("#808080"));
        }
        tvRank.invalidate();
    }

    // ==================== 本地私有沙盒 CSV 持久化写回机制 ====================
    private void saveStatsToLocalCsv(String currentSongName, String newRank, int newCompletion, int newMaxCombo) {
        if (currentSongName == null || currentSongName.isEmpty()) {
            return;
        }

        List<String> updatedFileLines = new ArrayList<>();
        try {
            File localCachedFile = new File(getFilesDir(), "MusicConfig.csv");
            InputStream inputStream;
            if (localCachedFile.exists()) {
                inputStream = openFileInput("MusicConfig.csv");
            } else {
                inputStream = getAssets().open("MusicCSV/MusicConfig.csv");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Order") || line.trim().isEmpty()) {
                    updatedFileLines.add(line);
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 6 && parts[1].trim().equalsIgnoreCase(currentSongName.trim())) {
                    int oldCompletion = Integer.parseInt(parts[4].trim());
                    int oldMaxCombo = Integer.parseInt(parts[5].trim());

                    if (newCompletion > oldCompletion) {
                        parts[3] = newRank;
                        parts[4] = String.valueOf(newCompletion);
                    }
                    if (newMaxCombo > oldMaxCombo) {
                        parts[5] = String.valueOf(newMaxCombo);
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        sb.append(parts[i]);
                        if (i < parts.length - 1) sb.append(",");
                    }
                    line = sb.toString();
                }
                updatedFileLines.add(line);
            }
            reader.close();
            inputStream.close();

            FileOutputStream fos = openFileOutput("MusicConfig.csv", Context.MODE_PRIVATE);
            PrintWriter writer = new PrintWriter(fos);
            for (String savedLine : updatedFileLines) {
                writer.println(savedLine);
            }
            writer.flush();
            writer.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}