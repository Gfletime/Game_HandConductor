package com.example.game_handconductor;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GMX_Activity extends AppCompatActivity {

    // ================= 游戏 UI 与状态 =================
    private ProgressBar pbSongProgress;
    private ValueAnimator progressAnimator;
    private FrameLayout noteContainer;
    private String songName;
    private int songCoverId;
    private int currentMaxCombo = 21;

    // ================= 摄像头与识别 =================
    private HandLandmarker handLandmarker;
    private ExecutorService cameraExecutor;
    private PreviewView viewFinder;
    private static final int CAMERA_REQ_CODE = 100;

    // ================= 手势实时交互状态 =================
    public volatile boolean flagPushing = false;
    public volatile boolean flagPointingTop = false;
    public volatile boolean flagPointingBottom = false;
    public volatile boolean flagRaisingLeft = false;
    public volatile boolean flagRaisingRight = false;

    // ================= 谱面时间轴系统 =================
    class NoteEvent {
        long spawnTimeMs;
        Runnable action;
        boolean isSpawned = false;
        NoteEvent(long time, Runnable action) { this.spawnTimeMs = time; this.action = action; }
    }
    private List<NoteEvent> noteTimeline = new ArrayList<>();

    // ================= 手势识别追踪器 =================
    private static class RaiseTrendTracker {
        List<Float> tipHistory = new ArrayList<>();
        boolean isPrepReady = false;
        boolean isMovingUpContinuous = false;
        long lastUpdateTime = 0;

        void update(float avgTipY, long now) {
            if (now - lastUpdateTime > 500) clear();
            lastUpdateTime = now;
            tipHistory.add(avgTipY);
            if (tipHistory.size() > 4) tipHistory.remove(0);
            isMovingUpContinuous = false;
            if (tipHistory.size() == 4) {
                boolean allMovingDown = true;
                boolean allMovingUp = true;
                for (int i = 1; i < 4; i++) {
                    if (tipHistory.get(i) <= tipHistory.get(i - 1)) allMovingDown = false;
                    if (tipHistory.get(i) >= tipHistory.get(i - 1)) allMovingUp = false;
                }
                if (allMovingDown) isPrepReady = true;
                if (allMovingUp) { isMovingUpContinuous = true; isPrepReady = false; }
            }
        }
        void clear() { tipHistory.clear(); isPrepReady = false; isMovingUpContinuous = false; }
    }

    private static class MovementTracker {
        List<Long> times = new ArrayList<>();
        List<Float> positions = new ArrayList<>();
        long windowMs = 400;

        void update(long time, float pos) {
            times.add(time); positions.add(pos);
            while (times.size() > 0 && time - times.get(0) > windowMs) {
                times.remove(0); positions.remove(0);
            }
        }
        float getVelocity() {
            if (times.size() < 2) return 0f;
            long dtMs = times.get(times.size() - 1) - times.get(0);
            if (dtMs < 50) return 0f;
            float dy = positions.get(positions.size() - 1) - positions.get(0);
            return dy / (dtMs / 1000f);
        }
        float getDropDistance() {
            if (positions.size() < 2) return 0f;
            float current = positions.get(positions.size() - 1);
            float minPos = positions.get(0);
            for (float p : positions) if (p < minPos) minPos = p;
            return current - minPos;
        }
    }

    private final RaiseTrendTracker leftRaiseTrend = new RaiseTrendTracker();
    private final RaiseTrendTracker rightRaiseTrend = new RaiseTrendTracker();
    private final MovementTracker leftScreenTracker = new MovementTracker();
    private final MovementTracker rightScreenTracker = new MovementTracker();

    // ================= Activity 生命周期 =================
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
        viewFinder = findViewById(R.id.viewFinder);

        tvMaxCombo.setText("最大连击数 " + currentMaxCombo);

        btnReturn.setOnClickListener(v -> {
            if (progressAnimator != null) progressAnimator.cancel();
            Intent g0Intent = new Intent(GMX_Activity.this, G0_Activity.class);
            g0Intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(g0Intent);
            finish();
        });

        btnSettings.setOnClickListener(v -> startActivity(new Intent(GMX_Activity.this, S1_Activity.class)));

        cameraExecutor = Executors.newSingleThreadExecutor();
        checkCameraPermissionAndInit();
        initNoteTimeline();
    }

    private void checkCameraPermissionAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQ_CODE);
        } else {
            setupMediaPipe();
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQ_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupMediaPipe();
            startCamera();
        }
    }

    private void setupMediaPipe() {
        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.GPU)
                .build();
        HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions).setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setResultListener((result, image) -> processHandResult(result))
                .setErrorListener(error -> Log.e("MediaPipe", "Error: ", error)).build();
        handLandmarker = HandLandmarker.createFromOptions(this, options);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new Size(640, 480)).build();

                imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
                    Bitmap bitmap = imageProxy.toBitmap();
                    MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                    handLandmarker.detectAsync(mpImage, imageProxy.getImageInfo().getTimestamp());
                    imageProxy.close();
                });
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer);
            } catch (Exception e) {
                Log.e("CameraX", "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private float getDistance(NormalizedLandmark p1, NormalizedLandmark p2) {
        float dx = p1.x() - p2.x(); float dy = p1.y() - p2.y(); return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void processHandResult(HandLandmarkerResult result) {
        List<List<NormalizedLandmark>> landmarks = result.landmarks();
        long now = System.currentTimeMillis();

        boolean currentTopPurple = false, currentBottomPurple = false;
        boolean currentLeftRaise = false, currentRightRaise = false;
        boolean currentPush = false, foundLeft = false, foundRight = false;

        if (landmarks != null && !landmarks.isEmpty()) {
            boolean leftInverted = false, rightInverted = false;
            for (List<NormalizedLandmark> hand : landmarks) {
                boolean isLeft = hand.get(0).x() > 0.5f;
                if (isLeft) foundLeft = true; else foundRight = true;
                NormalizedLandmark wrist = hand.get(0);

                float avgTipY = (hand.get(8).y() + hand.get(12).y() + hand.get(16).y() + hand.get(20).y()) / 4f;
                if (isLeft) {
                    leftRaiseTrend.update(avgTipY, now); leftScreenTracker.update(now, hand.get(9).y());
                } else {
                    rightRaiseTrend.update(avgTipY, now); rightScreenTracker.update(now, hand.get(9).y());
                }

                boolean indexExtended = getDistance(wrist, hand.get(8)) > getDistance(wrist, hand.get(6));
                boolean middleCurled = getDistance(wrist, hand.get(12)) < getDistance(wrist, hand.get(10));
                boolean ringCurled = getDistance(wrist, hand.get(16)) < getDistance(wrist, hand.get(14));
                boolean pinkyCurled = getDistance(wrist, hand.get(20)) < getDistance(wrist, hand.get(18));
                if (indexExtended && middleCurled && ringCurled && pinkyCurled) {
                    if (hand.get(8).y() < 0.5f) currentTopPurple = true; else currentBottomPurple = true;
                }

                boolean inverted = hand.get(8).y() > hand.get(6).y() && hand.get(12).y() > hand.get(10).y() &&
                        hand.get(16).y() > hand.get(14).y() && hand.get(20).y() > hand.get(18).y();
                if (inverted) { if (isLeft) leftInverted = true; else rightInverted = true; }
            }

            if (foundLeft && ((leftRaiseTrend.isPrepReady && leftScreenTracker.getVelocity() < -0.05f) || leftRaiseTrend.isMovingUpContinuous)) {
                currentLeftRaise = true;
            }
            if (foundRight && ((rightRaiseTrend.isPrepReady && rightScreenTracker.getVelocity() < -0.05f) || rightRaiseTrend.isMovingUpContinuous)) {
                currentRightRaise = true;
            }
            if (foundLeft && foundRight) {
                float leftDrop = leftScreenTracker.getDropDistance(), rightDrop = rightScreenTracker.getDropDistance();
                if ((leftDrop > 0.08f && rightDrop > 0.08f) || (leftInverted && rightInverted)) currentPush = true;
            }
        }
        flagPushing = currentPush;
        flagPointingTop = currentTopPurple;
        flagPointingBottom = currentBottomPurple;
        flagRaisingLeft = currentLeftRaise;
        flagRaisingRight = currentRightRaise;
    }

    // ================= 游戏进度与音符生成 =================
    private void initNoteTimeline() {
        noteTimeline.clear();
        long t = 1000;

        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new BlueNoteView(this)))); t += 2000;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new PurpleNoteView(this, true, null)))); t += 3000; // 留出5s寿命空间

        noteTimeline.add(new NoteEvent(t, () -> {
            PurpleLinkView link = new PurpleLinkView(this, true);
            noteContainer.addView(link);
            noteContainer.addView(new PurpleNoteView(this, true, link));
        })); t += 3000;

        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new PurpleNoteView(this, false, null)))); t += 3000;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new OrangeNoteView(this, true)))); t += 2500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new OrangeNoteView(this, false)))); t += 2500;

        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, true)))); t += 500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, true)))); t += 500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, true)))); t += 1000;

        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, false)))); t += 500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, false)))); t += 500;
        noteTimeline.add(new NoteEvent(t, () -> noteContainer.addView(new YellowNoteView(this, false)))); t += 3000;

        setupProgressAnimator((int) t);
    }

    private void setupProgressAnimator(int durationMs) {
        if (progressAnimator != null && progressAnimator.isRunning()) progressAnimator.cancel();

        progressAnimator = ValueAnimator.ofInt(0, 100);
        progressAnimator.setDuration(durationMs);
        progressAnimator.addUpdateListener(animation -> {
            pbSongProgress.setProgress((int) animation.getAnimatedValue());
            long currentTime = animation.getCurrentPlayTime();
            for (NoteEvent event : noteTimeline) {
                if (!event.isSpawned && currentTime >= event.spawnTimeMs) {
                    event.isSpawned = true;
                    event.action.run();
                }
            }
        });
        progressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { goToFXActivity(); }
        });
        progressAnimator.start();
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
        if (progressAnimator != null) {
            if (progressAnimator.isPaused()) progressAnimator.resume();
            else if (!progressAnimator.isRunning() && pbSongProgress.getProgress() == 0) progressAnimator.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (progressAnimator != null && progressAnimator.isRunning()) progressAnimator.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressAnimator != null) progressAnimator.cancel();
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) cameraExecutor.shutdown();
        if (handLandmarker != null) handLandmarker.close();
    }

    // ================= 特效类 =================
    class HitEffectView extends View {
        private Paint paint;
        private float cx, cy, currentSize = 50f;

        public HitEffectView(android.content.Context context, int color, float cx, float cy) {
            super(context);
            this.cx = cx; this.cy = cy;
            paint = new Paint(); paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(15); paint.setAntiAlias(true);
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
                    @Override public void onAnimationEnd(Animator animation) {
                        if (getParent() != null) ((ViewGroup) getParent()).removeView(HitEffectView.this);
                    }
                });
                anim.start();
            });
        }
        @Override protected void onDraw(Canvas canvas) {
            float half = currentSize / 2f;
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint);
        }
    }

    // ================= 自定义交互音符视图类 =================

    // 1. 蓝色音符
    class BlueNoteView extends View {
        private Paint innerPaint, strokePaint;
        private float sweepAngle = 0;
        private boolean isHit = false;

        public BlueNoteView(android.content.Context context) {
            super(context);
            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#00BFFF")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#00008B")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(30); strokePaint.setAntiAlias(true);

            post(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 360);
                anim.setDuration(1500);
                anim.addUpdateListener(a -> {
                    if (isHit) return;
                    sweepAngle = (float) a.getAnimatedValue();
                    if (flagPushing) {
                        isHit = true;
                        if (getParent() != null) {
                            ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#00BFFF"), getWidth() / 2f, getHeight() / 2f));
                            ((ViewGroup) getParent()).removeView(BlueNoteView.this);
                        }
                        anim.cancel();
                    }
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (!isHit && getParent() != null) ((ViewGroup) getParent()).removeView(BlueNoteView.this);
                    }
                });
                anim.start();
            });
        }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = 150f;
            canvas.drawCircle(cx, cy, radius, innerPaint);
            RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(rect, -90, sweepAngle, false, strokePaint);
        }
    }

    // 2. 紫色音符 (长按交互，动态时间与严格容错)
    class PurpleNoteView extends View {
        private Paint innerPaint, strokePaint;
        private float sweepAngle = 0;
        private boolean isTop;
        private PurpleLinkView linkView; // 绑定的紫链接键

        private long t = 5000; // 寿命设定为5s
        private long m = 2000; // 容错设定为2s
        private long hitStartTime = -1;
        private long dropStartTime = -1;
        private boolean isMissed = false;
        private boolean isSuccessCompleted = false;
        private long lastEffectTime = 0;

        public PurpleNoteView(android.content.Context context, boolean isTop, PurpleLinkView link) {
            super(context);
            this.isTop = isTop;
            this.linkView = link;
            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#9932CC")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#4B0082")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(25); strokePaint.setAntiAlias(true);

            post(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 1f);
                anim.setDuration(t); // 按设定寿命进行
                anim.addUpdateListener(a -> {
                    if (isMissed || isSuccessCompleted) return;
                    long currentTime = a.getCurrentPlayTime();
                    boolean isCorrectPointing = isTop ? flagPointingTop : flagPointingBottom;

                    if (hitStartTime == -1) {
                        // 还没有开始交互
                        if (isCorrectPointing) {
                            hitStartTime = currentTime; // 玩家开始长按！
                        } else if (currentTime > m) {
                            // 超过初始容错m没交互，直接MISS
                            triggerMiss();
                        }
                    } else {
                        // 正在交互中
                        if (!isCorrectPointing) {
                            if (dropStartTime == -1) dropStartTime = currentTime;
                            else if (currentTime - dropStartTime > 300) {
                                triggerMiss(); // 断开超过 300ms 容错，MISS
                            }
                        } else {
                            dropStartTime = -1; // 恢复识别，清空断开计时
                        }

                        // 动态计算进度圈：剩余时间内走完360度
                        if (hitStartTime != -1 && !isMissed) {
                            float progress = (float)(currentTime - hitStartTime) / (t - hitStartTime);
                            sweepAngle = progress * 360f;

                            if (currentTime - lastEffectTime >= 100) {
                                lastEffectTime = currentTime;
                                if (getParent() != null) {
                                    float cx = getWidth() / 2f, cy = isTop ? getHeight() * 0.25f : getHeight() * 0.75f;
                                    ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#9932CC"), cx, cy));
                                }
                            }
                        }
                    }
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (!isMissed && hitStartTime != -1) {
                            isSuccessCompleted = true;
                            if (linkView != null) linkView.activate(); // 【核心】：成功结束时，唤醒后续的链接键
                            if (getParent() != null) ((ViewGroup) getParent()).removeView(PurpleNoteView.this);
                        } else {
                            if (!isMissed) triggerMiss();
                        }
                    }
                });
                anim.start();
            });
        }

        private void triggerMiss() {
            isMissed = true;
            setAlpha(0.3f);
            invalidate();
            if (linkView != null) linkView.triggerMiss(); // 连带惩罚
            // Miss后透明度降低，在存活时间结束(t)时会被自然移除
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f; float cy = isTop ? getHeight() * 0.25f : getHeight() * 0.75f;
            float radius = 90f;
            canvas.drawCircle(cx, cy, radius, innerPaint);
            RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(rect, -90, sweepAngle, false, strokePaint);
        }
    }

    // 3. 紫色连接线 (前置唤醒逻辑)
    class PurpleLinkView extends View {
        private Paint paint;
        private boolean isTopToBottom;
        private boolean isHit = false;
        private boolean isMissed = false;

        public PurpleLinkView(android.content.Context context, boolean isTopToBottom) {
            super(context);
            this.isTopToBottom = isTopToBottom;
            paint = new Paint(); paint.setColor(Color.parseColor("#4B0082")); paint.setStrokeWidth(20);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND); paint.setAntiAlias(true);
        }

        // 被父节点紫键 Miss 时调用
        public void triggerMiss() {
            if (isHit) return;
            isMissed = true;
            setAlpha(0.3f);
            invalidate();
            // 在惩罚结束后自然消失
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
            }, 500);
        }

        // 只有父节点紫键成功走完 t 时间，才会激活此滑动判定
        public void activate() {
            if (isMissed) return;

            ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
            anim.setDuration(800); // 给予玩家非常宽裕的 800ms 去换区滑动
            anim.addUpdateListener(a -> {
                if (isHit || isMissed) return;
                boolean isZoneChanged = isTopToBottom ? flagPointingBottom : flagPointingTop;
                if (isZoneChanged) {
                    isHit = true;
                    if (getParent() != null) {
                        float cx = getWidth() / 2f, cy = getHeight() * 0.5f;
                        ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#4B0082"), cx, cy));
                        ((ViewGroup) getParent()).removeView(PurpleLinkView.this);
                    }
                    anim.cancel();
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (!isHit && !isMissed) triggerMiss(); // 换区超时，算作Miss
                }
            });
            anim.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f; Path path = new Path();
            float startY = isTopToBottom ? getHeight() * 0.35f : getHeight() * 0.65f;
            float endY = isTopToBottom ? getHeight() * 0.65f : getHeight() * 0.35f;
            canvas.drawLine(cx, startY, cx, endY, paint);
            for (int i = 1; i <= 3; i++) {
                float y = startY + (endY - startY) * (i / 4f);
                if (isTopToBottom) {
                    path.moveTo(cx - 30, y - 30); path.lineTo(cx, y); path.lineTo(cx + 30, y - 30);
                } else {
                    path.moveTo(cx - 30, y + 30); path.lineTo(cx, y); path.lineTo(cx + 30, y + 30);
                }
            }
            canvas.drawPath(path, paint);
        }
    }

    // 4. 橙色长条音符 (严格全程覆盖判定)
    class OrangeNoteView extends View {
        private Paint paint;
        private float currentX = -1;
        private boolean isLeft;

        private long lastTime = -1;
        private long dropTimer = 0;
        private boolean hasStartedTouching = false;
        private boolean wasTouchingEdge = false;
        private boolean isMissed = false;
        private boolean isSuccessCompleted = false;
        private long lastEffectTime = 0;

        public OrangeNoteView(android.content.Context context, boolean isLeft) {
            super(context);
            this.isLeft = isLeft;
            paint = new Paint(); paint.setColor(Color.parseColor("#FFA500"));

            post(() -> {
                float startX = getWidth() / 2f, rectWidth = 400f;
                float endX = isLeft ? -rectWidth : getWidth() + rectWidth;

                ValueAnimator anim = ValueAnimator.ofFloat(startX, endX);
                anim.setDuration(2500);
                anim.addUpdateListener(a -> {
                    if (isMissed || isSuccessCompleted) return;

                    currentX = (float) a.getAnimatedValue();
                    long currentTime = a.getCurrentPlayTime();
                    long dt = (lastTime == -1) ? 0 : (currentTime - lastTime);
                    lastTime = currentTime;

                    float leftEdge = isLeft ? currentX - rectWidth : currentX;
                    float rightEdge = isLeft ? currentX : currentX + rectWidth;

                    boolean isTouchingEdge = (isLeft) ? (leftEdge <= 0 && rightEdge >= 0) : (rightEdge >= getWidth() && leftEdge <= getWidth());
                    boolean isCorrectRaising = isLeft ? flagRaisingLeft : flagRaisingRight;

                    if (isTouchingEdge) {
                        hasStartedTouching = true;

                        // 交互断开或一直未按
                        if (!isCorrectRaising) {
                            dropTimer += dt;
                            if (dropTimer > 300) { // 300ms 容错，超过立刻变成半透明(Miss)
                                triggerMiss();
                            }
                        } else {
                            dropTimer = 0; // 恢复交互，重置容错
                            if (currentTime - lastEffectTime >= 100) {
                                lastEffectTime = currentTime;
                                if (getParent() != null) {
                                    float cy = getHeight() * 0.5f;
                                    float effectX = isLeft ? 0f : getWidth();
                                    ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#FFA500"), effectX, cy));
                                }
                            }
                        }
                    } else if (hasStartedTouching) {
                        // 刚才在碰边缘，现在离开了屏幕，且中途没有被 Miss，视为成功击打
                        triggerSuccess();
                    }

                    wasTouchingEdge = isTouchingEdge;
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

        private void triggerMiss() {
            isMissed = true;
            setAlpha(0.3f);
            invalidate(); // 继续维持在屏幕上，保持半透明移出屏幕
        }

        private void triggerSuccess() {
            isSuccessCompleted = true;
            if (getParent() != null) {
                float effectX = isLeft ? 0f : getWidth();
                ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#FFA500"), effectX, getHeight() * 0.5f));
                ((ViewGroup) getParent()).removeView(this);
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            if (currentX == -1) currentX = getWidth() / 2f;
            float cy = getHeight() * 0.5f, rectWidth = 400f, rectHeight = 120f;
            float left = isLeft ? currentX - rectWidth : currentX;
            canvas.drawRect(left, cy - rectHeight/2, left + rectWidth, cy + rectHeight/2, paint);
        }
    }

    // 5. 黄色短音符
    class YellowNoteView extends View {
        private Paint paint;
        private float currentY = -1;
        private boolean isLeft;
        private boolean isHit = false;

        public YellowNoteView(android.content.Context context, boolean isLeft) {
            super(context);
            this.isLeft = isLeft;
            paint = new Paint(); paint.setColor(Color.YELLOW); paint.setStrokeWidth(40); paint.setStrokeCap(Paint.Cap.ROUND);

            post(() -> {
                float startY = getHeight() * 0.25f;
                float endY = getHeight() + 100f;

                ValueAnimator anim = ValueAnimator.ofFloat(startY, endY);
                anim.setDuration(1200);
                anim.addUpdateListener(a -> {
                    if (isHit) return;
                    currentY = (float) a.getAnimatedValue();

                    boolean inHitZone = currentY > getHeight() - 300f;
                    boolean isCorrectRaising = isLeft ? flagRaisingLeft : flagRaisingRight;

                    if (inHitZone && isCorrectRaising) {
                        isHit = true;
                        if (getParent() != null) {
                            float cx = isLeft ? getWidth() * 0.25f : getWidth() * 0.75f;
                            ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.YELLOW, cx, getHeight()));
                            ((ViewGroup) getParent()).removeView(YellowNoteView.this);
                        }
                        anim.cancel();
                    }
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (!isHit && getParent() != null) ((ViewGroup) getParent()).removeView(YellowNoteView.this);
                    }
                });
                anim.start();
            });
        }
        @Override protected void onDraw(Canvas canvas) {
            if (currentY == -1) currentY = getHeight() * 0.25f;
            float cx = isLeft ? getWidth() * 0.25f : getWidth() * 0.75f;
            float lineWidth = 150f;
            canvas.drawLine(cx - lineWidth/2, currentY, cx + lineWidth/2, currentY, paint);
        }
    }
}