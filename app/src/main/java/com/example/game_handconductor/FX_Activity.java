package com.example.game_handconductor;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class FX_Activity extends AppCompatActivity {

    private String songName;
    private int songCoverId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fx);

        // 1. 获取从 GMX 传过来的结算数据
        Intent intent = getIntent();
        songName = intent.getStringExtra("SONG_NAME");
        songCoverId = intent.getIntExtra("SONG_COVER_ID", R.mipmap.ic_launcher);

        String rank = intent.getStringExtra("RANK");
        String completionStr = intent.getStringExtra("COMPLETION");
        int hits = intent.getIntExtra("HITS", 0);
        int maxCombo = intent.getIntExtra("MAX_COMBO", 0);
        int misses = intent.getIntExtra("MISSES", 0);

        // 解析完成度数字 (去掉 "%" 符号以便进行数字滚动)
        int completion = 0;
        if (completionStr != null) {
            try {
                completion = Integer.parseInt(completionStr.replace("%", "").trim());
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        // 2. 绑定 UI 组件
        ImageView ivCover = findViewById(R.id.iv_fx_cover);
        TextView tvRank = findViewById(R.id.tv_fx_rank);

        // 左侧的标签文字 (需要依次渐显)
        TextView labelCompletion = (TextView) ((LinearLayout) findViewById(R.id.tv_fx_completion).getParent()).getChildAt(0);
        TextView labelHits = (TextView) ((LinearLayout) findViewById(R.id.tv_fx_hits).getParent()).getChildAt(0);
        TextView labelCombo = (TextView) ((LinearLayout) findViewById(R.id.tv_fx_combo).getParent()).getChildAt(0);
        TextView labelMisses = (TextView) ((LinearLayout) findViewById(R.id.tv_fx_misses).getParent()).getChildAt(0);

        // 右侧的数值文字 (需要数字滚动)
        TextView tvCompletion = findViewById(R.id.tv_fx_completion);
        TextView tvHits = findViewById(R.id.tv_fx_hits);
        TextView tvCombo = findViewById(R.id.tv_fx_combo);
        TextView tvMisses = findViewById(R.id.tv_fx_misses);

        Button btnToG0 = findViewById(R.id.btn_fx_to_g0);
        Button btnReplay = findViewById(R.id.btn_fx_replay);

        // 设置封面和评级文字
        ivCover.setImageResource(songCoverId);
        if (rank != null) tvRank.setText(rank);

        // ================= 3. 开始执行入场动画 =================

        // 隐藏所有需要动画的元素初始状态
        hideViewsInitially(tvRank, labelCompletion, tvCompletion, labelHits, tvHits,
                labelCombo, tvCombo, labelMisses, tvMisses, btnToG0, btnReplay);

        // 设定基础延迟时间 (单位: 毫秒)
        long delay = 300;

        // A. 评级(S) 弹性弹出动画
        tvRank.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(new OvershootInterpolator(2.0f)) // 弹性阻尼效果
                .start();

        // B. 数据列表依次渐显 + 数字滚动
        delay += 400; // 等待评级弹出后，开始显示数据

        animateRow(labelCompletion, tvCompletion, completion, delay, true);
        delay += 250; // 每行间隔 250ms 显示下一行

        animateRow(labelHits, tvHits, hits, delay, false);
        delay += 250;

        animateRow(labelCombo, tvCombo, maxCombo, delay, false);
        delay += 250;

        animateRow(labelMisses, tvMisses, misses, delay, false);
        delay += 400;

        // C. 底部按钮最后渐显
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
            gmxIntent.putExtra("SONG_COVER_ID", songCoverId);
            startActivity(gmxIntent);
            finish();
        });
    }

    // --- 动画辅助方法 ---

    /**
     * 将所有视图初始设为全透明且缩放为0（针对评级），为入场动画做准备
     */
    private void hideViewsInitially(View... views) {
        for (int i = 0; i < views.length; i++) {
            views[i].setAlpha(0f);
            if (i == 0) { // 第一个元素是 tvRank
                views[i].setScaleX(0f);
                views[i].setScaleY(0f);
            }
        }
    }

    /**
     * 渐显整行数据：标签渐显 + 数值从0开始滚动
     */
    private void animateRow(View labelView, TextView valueView, int targetValue, long delay, boolean isPercentage) {
        // 1. 标签和数值框渐显
        labelView.animate().alpha(1f).setDuration(300).setStartDelay(delay).start();
        valueView.animate().alpha(1f).setDuration(300).setStartDelay(delay).start();

        // 2. 数值滚动动画
        ValueAnimator animator = ValueAnimator.ofInt(0, targetValue);
        animator.setDuration(1000); // 滚动持续时间 1秒
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

    /**
     * 普通元素的渐显效果 (如底部按钮)
     */
    private void fadeInView(View view, long delay) {
        view.animate()
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(delay)
                .start();
    }
}