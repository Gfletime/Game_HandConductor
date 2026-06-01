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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class GMX_Activity extends AppCompatActivity {

    private ProgressBar pbSongProgress;
    private ValueAnimator progressAnimator;
    private FrameLayout noteContainer;
    private TextureView cameraPreview;

    @SuppressWarnings("deprecation")
    private Camera mCamera;

    private String songName;
    private int songCoverId;
    private int currentMaxCombo = 21;
    private static final int CAMERA_REQ_CODE = 100;

    // ================= 架构升级：基于时间轴的谱面事件队列 =================
    class NoteEvent {
        long spawnTimeMs;
        Runnable action;
        boolean isSpawned = false;

        NoteEvent(long time, Runnable action) {
            this.spawnTimeMs = time;
            this.action = action;
        }
    }
    private List<NoteEvent> noteTimeline = new ArrayList<>();
    // ==============================================================

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
            if (progressAnimator != null) progressAnimator.cancel();
            Intent g0Intent = new Intent(GMX_Activity.this, G0_Activity.class);
            g0Intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(g0Intent);
            finish();
        });

        btnSettings.setOnClickListener(v -> startActivity(new Intent(GMX_Activity.this, S1_Activity.class)));

        checkCameraPermissionAndInit();
        initNoteTimeline(); // 初始化谱面，但先不跑
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
                mCamera.setDisplayOrientation(0);
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

    // ================= 初始化时间轴谱面 =================
    private void initNoteTimeline() {
        noteTimeline.clear();
        long t = 1000;

        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new BlueNoteView(this)))); t += 2000;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new PurpleNoteView(this, true)))); t += 2000;

        noteTimeline.add(new NoteEvent(t, () -> {
            noteContainer.addView(new PurpleLinkView(this, true));
            noteContainer.addView(new PurpleNoteView(this, true));
        })); t += 2000;

        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new PurpleNoteView(this, false)))); t += 2000;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new OrangeNoteView(this, true)))); t += 2000;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new OrangeNoteView(this, false)))); t += 2000;

        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, true)))); t += 500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, true)))); t += 500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, true)))); t += 1000;

        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, false)))); t += 500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, false)))); t += 500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, false)))); t += 2000;

        int totalDuration = (int) t;
        setupProgressAnimator(totalDuration);
    }

    private void setupProgressAnimator(int durationMs) {
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }
        progressAnimator = ValueAnimator.ofInt(0, 100);
        progressAnimator.setDuration(durationMs);

        progressAnimator.addUpdateListener(animation -> {
            pbSongProgress.setProgress((int) animation.getAnimatedValue());

            // 【核心修复】：基于动画进度时间，动态解锁事件。
            // 这样就算按了暂停再去设置界面，回来后时间轴依然严丝合缝！
            long currentTime = animation.getCurrentPlayTime();
            for (NoteEvent event : noteTimeline) {
                if (!event.isSpawned && currentTime >= event.spawnTimeMs) {
                    event.isSpawned = true;
                    event.action.run();
                }
            }
        });

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
        fxIntent.putExtra("MAX_COMBO", currentMaxCombo + 12);
        startActivity(fxIntent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraPreview.isAvailable()) openFrontCamera(cameraPreview.getSurfaceTexture());

        // 恢复播放
        if (progressAnimator != null) {
            if (progressAnimator.isPaused()) progressAnimator.resume();
            else if (!progressAnimator.isRunning() && pbSongProgress.getProgress() == 0) progressAnimator.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressAnimator != null) progressAnimator.cancel();
        releaseCamera();
    }

    // ================= 特效类：空心正方形打击反馈 =================
    class HitEffectView extends View {
        private Paint paint;
        private float cx, cy;
        private float currentSize = 50f;

        public HitEffectView(android.content.Context context, int color, float cx, float cy) {
            super(context);
            this.cx = cx;
            this.cy = cy;
            paint = new Paint();
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(15);
            paint.setAntiAlias(true);

            post(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(400);
                anim.addUpdateListener(a -> {
                    float fraction = a.getAnimatedFraction();
                    currentSize = 50f + fraction * 250f;
                    paint.setAlpha((int) (255 * (1 - fraction)));
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (getParent() != null) ((ViewGroup) getParent()).removeView(HitEffectView.this);
                    }
                });
                anim.start();
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float half = currentSize / 2f;
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint);
        }
    }

    // ================= 自定义音符视图类 =================

    class BlueNoteView extends View {
        private Paint innerPaint, strokePaint;
        private float sweepAngle = 0;

        public BlueNoteView(android.content.Context context) {
            super(context);
            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#00BFFF")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#00008B")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(30); strokePaint.setAntiAlias(true);

            post(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 360);
                anim.setStartDelay(500); // 使用系统的 StartDelay 替代 Handler
                anim.setDuration(1000);
                anim.addUpdateListener(a -> { sweepAngle = (float) a.getAnimatedValue(); invalidate(); });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (getParent() != null) {
                            ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#00BFFF"), getWidth() / 2f, getHeight() / 2f));
                            ((ViewGroup) getParent()).removeView(BlueNoteView.this);
                        }
                    }
                });
                anim.start();
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = 150f;
            canvas.drawCircle(cx, cy, radius, innerPaint);
            RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(rect, -90, sweepAngle, false, strokePaint);
        }
    }

    class PurpleNoteView extends View {
        private Paint innerPaint, strokePaint;
        private float sweepAngle = 0;
        private boolean isTop;
        private long lastEffectTime = 0;

        public PurpleNoteView(android.content.Context context, boolean isTop) {
            super(context);
            this.isTop = isTop;
            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#9932CC")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#4B0082")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(25); strokePaint.setAntiAlias(true);

            post(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 360);
                anim.setStartDelay(500);
                anim.setDuration(1000);
                anim.addUpdateListener(a -> {
                    sweepAngle = (float) a.getAnimatedValue();
                    long currentTime = a.getCurrentPlayTime(); // 获取去除 delay 后的纯播放时间

                    if (currentTime - lastEffectTime >= 100) {
                        lastEffectTime = currentTime;
                        if (getParent() != null) {
                            float cx = getWidth() / 2f;
                            float cy = isTop ? getHeight() * 0.25f : getHeight() * 0.75f;
                            ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#9932CC"), cx, cy));
                        }
                    }
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (getParent() != null) ((ViewGroup) getParent()).removeView(PurpleNoteView.this);
                    }
                });
                anim.start();
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = isTop ? getHeight() * 0.25f : getHeight() * 0.75f;
            float radius = 90f;
            canvas.drawCircle(cx, cy, radius, innerPaint);
            RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(rect, -90, sweepAngle, false, strokePaint);
        }
    }

    class PurpleLinkView extends View {
        private Paint paint;
        private boolean isTopToBottom;

        public PurpleLinkView(android.content.Context context, boolean isTopToBottom) {
            super(context);
            this.isTopToBottom = isTopToBottom;
            paint = new Paint();
            paint.setColor(Color.parseColor("#4B0082"));
            paint.setStrokeWidth(20);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setAntiAlias(true);

            post(() -> {
                ValueAnimator timerAnim = ValueAnimator.ofFloat(0, 1);
                // 【修改点1】：紫键寿命(1500ms) + 0.1s(100ms) = 1600ms
                timerAnim.setDuration(1600);
                timerAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (getParent() != null) {
                            float cx = getWidth() / 2f;
                            float cy = getHeight() * 0.5f;
                            ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#4B0082"), cx, cy));
                            ((ViewGroup) getParent()).removeView(PurpleLinkView.this);
                        }
                    }
                });
                timerAnim.start();
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            Path path = new Path();

            float startY = isTopToBottom ? getHeight() * 0.35f : getHeight() * 0.65f;
            float endY = isTopToBottom ? getHeight() * 0.65f : getHeight() * 0.35f;

            canvas.drawLine(cx, startY, cx, endY, paint);

            for (int i = 1; i <= 3; i++) {
                float y = startY + (endY - startY) * (i / 4f);
                if (isTopToBottom) {
                    path.moveTo(cx - 30, y - 30);
                    path.lineTo(cx, y);
                    path.lineTo(cx + 30, y - 30);
                } else {
                    path.moveTo(cx - 30, y + 30);
                    path.lineTo(cx, y);
                    path.lineTo(cx + 30, y + 30);
                }
            }
            canvas.drawPath(path, paint);
        }
    }

    // 4. 橙色长条音符 (【核心修复】：纯净的碰撞检测逻辑)
    // 4. 橙色长条音符
    class OrangeNoteView extends View {
        private Paint paint;
        private float currentX = -1;
        private boolean isLeft;
        private long lastEffectTime = 0;

        public OrangeNoteView(android.content.Context context, boolean isLeft) {
            super(context);
            this.isLeft = isLeft;
            paint = new Paint(); paint.setColor(Color.parseColor("#FFA500"));

            post(() -> {
                float startX = getWidth() / 2f;
                float rectWidth = 400f;
                float endX = isLeft ? -rectWidth : getWidth() + rectWidth;

                ValueAnimator anim = ValueAnimator.ofFloat(startX, endX);
                anim.setStartDelay(500);
                anim.setDuration(1500);
                anim.addUpdateListener(a -> {
                    currentX = (float) a.getAnimatedValue();
                    long currentTime = a.getCurrentPlayTime();

                    float leftEdge = isLeft ? currentX - rectWidth : currentX;
                    float rightEdge = isLeft ? currentX : currentX + rectWidth;

                    boolean isTouchingEdge = false;
                    if (isLeft) {
                        if (leftEdge <= 0 && rightEdge >= 0) isTouchingEdge = true;
                    } else {
                        if (rightEdge >= getWidth() && leftEdge <= getWidth()) isTouchingEdge = true;
                    }

                    if (isTouchingEdge) {
                        if (currentTime - lastEffectTime >= 100) {
                            lastEffectTime = currentTime;
                            if (getParent() != null) {
                                float cy = getHeight() * 0.5f;
                                // 【修改点2】：将原来的 100f 和 getWidth() - 100f 替换为严丝合缝的 0f 和 getWidth()
                                float effectX = isLeft ? 0f : getWidth();
                                ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#FFA500"), effectX, cy));
                            }
                        }
                    }

                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (getParent() != null) ((ViewGroup) getParent()).removeView(OrangeNoteView.this);
                    }
                });
                anim.start();
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentX == -1) currentX = getWidth() / 2f;
            float cy = getHeight() * 0.5f;
            float rectWidth = 400f, rectHeight = 120f;
            float left = isLeft ? currentX - rectWidth : currentX;
            canvas.drawRect(left, cy - rectHeight/2, left + rectWidth, cy + rectHeight/2, paint);
        }
    }

    class YellowNoteView extends View {
        private Paint paint;
        private float currentY = -1;
        private boolean isLeft;

        public YellowNoteView(android.content.Context context, boolean isLeft) {
            super(context);
            this.isLeft = isLeft;
            paint = new Paint();
            paint.setColor(Color.YELLOW);
            paint.setStrokeWidth(40);
            paint.setStrokeCap(Paint.Cap.ROUND);

            post(() -> {
                float startY = getHeight() * 0.25f;
                float endY = getHeight() + 100f;

                ValueAnimator anim = ValueAnimator.ofFloat(startY, endY);
                anim.setDuration(1000);
                anim.addUpdateListener(a -> {
                    currentY = (float) a.getAnimatedValue();
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (getParent() != null) {
                            float cx = isLeft ? getWidth() * 0.25f : getWidth() * 0.75f;
                            ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.YELLOW, cx, getHeight()));
                            ((ViewGroup) getParent()).removeView(YellowNoteView.this);
                        }
                    }
                });
                anim.start();
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentY == -1) currentY = getHeight() * 0.25f;
            float cx = isLeft ? getWidth() * 0.25f : getWidth() * 0.75f;
            float lineWidth = 150f;
            canvas.drawLine(cx - lineWidth/2, currentY, cx + lineWidth/2, currentY, paint);
        }
    }
}