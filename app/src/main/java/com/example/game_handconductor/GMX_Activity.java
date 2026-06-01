package com.example.game_handconductor;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class GMX_Activity extends AppCompatActivity {

    private ProgressBar pbSongProgress;
    private ValueAnimator progressAnimator;
    private FrameLayout noteContainer;
    private TextureView cameraPreview;

    // 弃用的 Camera API，但对于快速原型前置摄像头背景最有效且无需复杂配置
    @SuppressWarnings("deprecation")
    private Camera mCamera;

    private String songName;
    private int songCoverId;
    private int currentMaxCombo = 21;
    private Handler sequenceHandler = new Handler(Looper.getMainLooper());
    private static final int CAMERA_REQ_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gmx);

        Intent intent = getIntent();
        songName = intent.getStringExtra("SONG_NAME");
        songCoverId = intent.getIntExtra("SONG_COVER_ID", R.mipmap.ic_launcher);

        pbSongProgress = findViewById(R.id.pb_song_progress);
        TextView tvMaxCombo = findViewById(R.id.tv_max_combo);
        Button btnReturn = findViewById(R.id.btn_return_g0);
        Button btnSettings = findViewById(R.id.btn_settings_s1);
        noteContainer = findViewById(R.id.note_container);
        cameraPreview = findViewById(R.id.camera_preview);

        tvMaxCombo.setText("最大连击数 " + currentMaxCombo);

        btnReturn.setOnClickListener(v -> {
            cancelSequence();
            Intent g0Intent = new Intent(GMX_Activity.this, G0_Activity.class);
            g0Intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(g0Intent);
            finish();
        });

        btnSettings.setOnClickListener(v -> startActivity(new Intent(GMX_Activity.this, S1_Activity.class)));

        checkCameraPermissionAndInit();
    }

    private void checkCameraPermissionAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQ_CODE);
        } else {
            initCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQ_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initCamera();
        }
    }

    private void initCamera() {
        cameraPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openFrontCamera(surface);
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                releaseCamera();
                return true;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
    }

    @SuppressWarnings("deprecation")
    private void openFrontCamera(SurfaceTexture surface) {
        try {
            int cameraId = -1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    cameraId = i;
                    break;
                }
            }
            if (cameraId != -1) {
                mCamera = Camera.open(cameraId);
                mCamera.setPreviewTexture(surface);
                mCamera.setDisplayOrientation(0); // 竖屏修正
                mCamera.startPreview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    // ================= 核心：音符序列发生器 =================
    private void startNoteSequence() {
        long t = 1000; // 初始延迟1秒

        // 1. 蓝键
        sequenceHandler.postDelayed(() -> noteContainer.addView(new BlueNoteView(this)), t);
        t += 2000;

        // 2. 独立的上紫键
        sequenceHandler.postDelayed(() -> noteContainer.addView(new PurpleNoteView(this, true)), t);
        t += 2000;

        // 3. 【修改点】紫链接线组 (上至下)：必须包含一个作为起点的上紫键，和一条链接线
        sequenceHandler.postDelayed(() -> {
            noteContainer.addView(new PurpleLinkView(this, true)); // 先添加线（画在底层）
            noteContainer.addView(new PurpleNoteView(this, true)); // 再添加紫键（盖在线上）
        }, t);
        t += 2500; // 链接线交互时间更长，这里多加 0.5s 给玩家"滑动"的余地

        // 4. 独立的下紫键
        sequenceHandler.postDelayed(() -> noteContainer.addView(new PurpleNoteView(this, false)), t);
        t += 2000;

        // 左橙键
        sequenceHandler.postDelayed(() -> noteContainer.addView(new OrangeNoteView(this, true)), t);
        t += 2000;

        // 右橙键
        sequenceHandler.postDelayed(() -> noteContainer.addView(new OrangeNoteView(this, false)), t);
        t += 2000;

        // 左黄键 * 3
        sequenceHandler.postDelayed(() -> noteContainer.addView(new YellowNoteView(this, true)), t); t += 500;
        sequenceHandler.postDelayed(() -> noteContainer.addView(new YellowNoteView(this, true)), t); t += 500;
        sequenceHandler.postDelayed(() -> noteContainer.addView(new YellowNoteView(this, true)), t); t += 1000;

        // 右黄键 * 3
        sequenceHandler.postDelayed(() -> noteContainer.addView(new YellowNoteView(this, false)), t); t += 500;
        sequenceHandler.postDelayed(() -> noteContainer.addView(new YellowNoteView(this, false)), t); t += 500;
        sequenceHandler.postDelayed(() -> noteContainer.addView(new YellowNoteView(this, false)), t); t += 2000;

        // 结束进入结算 (动态总时长)
        int totalDuration = (int) t;
        setupProgressAnimator(totalDuration+18000);
    }

    private void setupProgressAnimator(int durationMs) {
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }
        progressAnimator = ValueAnimator.ofInt(0, 100);
        // 这里使用的是动态计算的总时长 (17500ms)，而不是定死的 10000ms
        progressAnimator.setDuration(durationMs);
        progressAnimator.addUpdateListener(animation -> pbSongProgress.setProgress((int) animation.getAnimatedValue()));
        progressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                goToFXActivity();
            }
        });
        progressAnimator.start();
    }

    private void cancelSequence() {
        sequenceHandler.removeCallbacksAndMessages(null);
        if (progressAnimator != null) progressAnimator.cancel();
    }

    private void goToFXActivity() {
        Intent fxIntent = new Intent(GMX_Activity.this, FX_Activity.class);
        fxIntent.putExtra("SONG_NAME", songName);
        fxIntent.putExtra("SONG_COVER_ID", songCoverId);
        fxIntent.putExtra("RANK", "S");
        fxIntent.putExtra("COMPLETION", "100%");
        fxIntent.putExtra("MAX_COMBO", currentMaxCombo + 12); // 加12个生成的note
        startActivity(fxIntent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraPreview.isAvailable()) openFrontCamera(cameraPreview.getSurfaceTexture());
        if (progressAnimator != null && progressAnimator.isPaused()) progressAnimator.resume();
        else if (progressAnimator == null) startNoteSequence(); // 首次进入开始序列
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
        if (progressAnimator != null && progressAnimator.isRunning()) progressAnimator.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelSequence();
        releaseCamera();
    }

    // ================= 自定义音符视图类 =================

    // 1. 蓝色音符 (屏幕中央，出现后0.5s开始填满外圈，填满后消失)
    class BlueNoteView extends View {
        private Paint innerPaint, strokePaint;
        private float sweepAngle = 0;

        public BlueNoteView(android.content.Context context) {
            super(context);
            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#00BFFF")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#00008B")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(30); strokePaint.setAntiAlias(true);

            // 0.5s后自动发生判定(模拟按压)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 360);
                anim.setDuration(1000); // 转一圈耗时1s
                anim.addUpdateListener(a -> { sweepAngle = (float) a.getAnimatedValue(); invalidate(); });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) { ((FrameLayout) getParent()).removeView(BlueNoteView.this); }
                });
                anim.start();
            }, 500);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = 150f;
            canvas.drawCircle(cx, cy, radius, innerPaint);
            RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(rect, -90, sweepAngle, false, strokePaint);
        }
    }

    // 2. 紫色音符 (上下半区，机制类似蓝键)
    class PurpleNoteView extends View {
        private Paint innerPaint, strokePaint;
        private float sweepAngle = 0;
        private boolean isTop;

        public PurpleNoteView(android.content.Context context, boolean isTop) {
            super(context);
            this.isTop = isTop;
            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#9932CC")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#4B0082")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(30); strokePaint.setAntiAlias(true);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 360);
                anim.setDuration(1000);
                anim.addUpdateListener(a -> { sweepAngle = (float) a.getAnimatedValue(); invalidate(); });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) { ((FrameLayout) getParent()).removeView(PurpleNoteView.this); }
                });
                anim.start();
            }, 500);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = isTop ? getHeight() * 0.25f : getHeight() * 0.75f;
            float radius = 150f;
            canvas.drawCircle(cx, cy, radius, innerPaint);
            RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(rect, -90, sweepAngle, false, strokePaint);
        }
    }

    // 2.5 紫色连接线 (上至下箭头)
    class PurpleLinkView extends View {
        private Paint paint;
        private boolean isTopToBottom; // 增加方向控制

        public PurpleLinkView(android.content.Context context, boolean isTopToBottom) {
            super(context);
            this.isTopToBottom = isTopToBottom;
            paint = new Paint();
            paint.setColor(Color.parseColor("#4B0082"));
            paint.setStrokeWidth(25);
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);

            // 紫键的寿命是 1.5s。链接线要在紫键被“击打”后，再给 0.5s 的滑动时间，所以寿命设为 2.0s
            post(() -> {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (getParent() != null) {
                        ((android.view.ViewGroup) getParent()).removeView(PurpleLinkView.this);
                    }
                }, 2000);
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            Path path = new Path();

            if (isTopToBottom) {
                // 上至下 (从上 1/4 处指向下)
                path.moveTo(cx, getHeight() * 0.25f);
                path.lineTo(cx, getHeight() * 0.65f); // 垂直线
                path.lineTo(cx - 50, getHeight() * 0.60f); // 箭头左翼
                path.moveTo(cx, getHeight() * 0.65f);
                path.lineTo(cx + 50, getHeight() * 0.60f); // 箭头右翼
            } else {
                // 下至上 (从下 3/4 处指向上)
                path.moveTo(cx, getHeight() * 0.75f);
                path.lineTo(cx, getHeight() * 0.35f); // 垂直线
                path.lineTo(cx - 50, getHeight() * 0.40f); // 箭头左翼
                path.moveTo(cx, getHeight() * 0.35f);
                path.lineTo(cx + 50, getHeight() * 0.40f); // 箭头右翼
            }
            canvas.drawPath(path, paint);
        }
    }

    // 3. 橙色长条音符 (中线刷出，向左/右移动，完全移出边缘判定成功)
    class OrangeNoteView extends View {
        private Paint paint;
        private float currentX;
        private boolean isLeft;

        public OrangeNoteView(android.content.Context context, boolean isLeft) {
            super(context);
            this.isLeft = isLeft;
            paint = new Paint(); paint.setColor(Color.parseColor("#FFA500"));

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                float startX = getWidth() / 2f;
                // 长条宽度固定假设为 400
                float endX = isLeft ? -400f : getWidth();
                ValueAnimator anim = ValueAnimator.ofFloat(startX, endX);
                anim.setDuration(1500); // 移动耗时1.5s
                anim.addUpdateListener(a -> { currentX = (float) a.getAnimatedValue(); invalidate(); });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) { ((FrameLayout) getParent()).removeView(OrangeNoteView.this); }
                });
                anim.start();
            }, 500); // 0.5s后开始运动
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentX == 0) currentX = getWidth() / 2f; // 初始位置
            float cy = getHeight() * 0.5f; // 固定高度
            float rectWidth = 400f, rectHeight = 120f;
            float left = isLeft ? currentX - rectWidth : currentX;
            canvas.drawRect(left, cy - rectHeight/2, left + rectWidth, cy + rectHeight/2, paint);
        }
    }

    // 4. 黄色短音符 (3/4横线下方生成，向底部移动)
    // 4. 黄色短音符 (修改后：从下往上3/4处立刻生成并直接下落)
    class YellowNoteView extends View {
        private Paint paint;
        private float currentY = -1; // 使用 -1 作为未初始化的标记
        private boolean isLeft;

        public YellowNoteView(android.content.Context context, boolean isLeft) {
            super(context);
            this.isLeft = isLeft;
            paint = new Paint();
            paint.setColor(Color.YELLOW);
            paint.setStrokeWidth(40);
            paint.setStrokeCap(Paint.Cap.ROUND);

            // 使用 post 确保在 View 完成尺寸测量后，立刻获取真实高度并开始动画，彻底实现 0 延迟
            post(() -> {
                // 修改点1：从下往上 3/4 处，也就是距离顶部的 1/4 (0.25f)
                float startY = getHeight() * 0.25f;
                float endY = getHeight() + 100f; // 移出屏幕下边缘

                ValueAnimator anim = ValueAnimator.ofFloat(startY, endY);
                // 现在的下落耗时是 1秒 (1000ms)，如果觉得太慢或太快可以修改这个值
                anim.setDuration(1000);
                anim.addUpdateListener(a -> {
                    currentY = (float) a.getAnimatedValue();
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // 安全移除音符，防止闪退
                        if (getParent() != null) {
                            ((android.view.ViewGroup) getParent()).removeView(YellowNoteView.this);
                        }
                    }
                });
                anim.start(); // 修改点2：立刻 start()，不再使用 postDelayed
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // 如果动画还没跑起来的极短瞬间（第一帧），先把它画在初始位置
            if (currentY == -1) {
                currentY = getHeight() * 0.25f; // 同步修改为 0.25f
            }

            // 竖直中线区分左右，左侧占 1/4 处，右侧占 3/4 处
            float cx = isLeft ? getWidth() * 0.25f : getWidth() * 0.75f;
            float lineWidth = 150f;
            canvas.drawLine(cx - lineWidth/2, currentY, cx + lineWidth/2, currentY, paint);
        }
    }
}