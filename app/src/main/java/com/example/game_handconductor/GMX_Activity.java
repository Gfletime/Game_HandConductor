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

    private class ScoreManager {
        int currentCombo = 0, maxCombo = 0, hits = 0,miss=0;
        //public int totalNote=0;
        int totalNotes = 15;

        void addHit() {
            hits++;
            currentCombo++;
            if (currentCombo > maxCombo) maxCombo = currentCombo;
            updateComboUI();
        }

        void resetCombo() {
            miss++;
            currentCombo = 0;
            updateComboUI();
        }
    }
    private ScoreManager scoreManager = new ScoreManager();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView tvCombo;

    // ================= 游戏 UI 与状态 =================
    private ProgressBar pbSongProgress;
    private ValueAnimator progressAnimator;
    private FrameLayout noteContainer;
    private String songName;
    private int songCoverId;
    //private int currentMaxCombo = 0;

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

        tvCombo = findViewById(R.id.tv_max_combo); // 新增绑定
        pbSongProgress = findViewById(R.id.pb_song_progress);
        noteContainer = findViewById(R.id.note_container);
        viewFinder = findViewById(R.id.viewFinder);

        Intent intent = getIntent();
        songName = intent.getStringExtra("SONG_NAME");
        songCoverId = intent.getIntExtra("SONG_COVER_ID", R.mipmap.ic_launcher);

        pbSongProgress = findViewById(R.id.pb_song_progress);
        TextView tvMaxCombo = findViewById(R.id.tv_max_combo);
        Button btnReturn = findViewById(R.id.btn_return_g0);
        Button btnSettings = findViewById(R.id.btn_settings_s1);
        noteContainer = findViewById(R.id.note_container);
        viewFinder = findViewById(R.id.viewFinder);

        tvMaxCombo.setText("连击数: " + scoreManager.currentCombo);

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

    private void updateComboUI() {
        mainHandler.post(() -> {
            if (tvCombo != null) tvCombo.setText("连击数: " + scoreManager.currentCombo);
        });
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

    // === 游戏媒体与核心状态控制变量 ===
    private android.media.MediaPlayer mediaPlayer = null;
    private PurpleNoteView lastPurpleNote = null;

    // === 【新增逻辑点1】：多媒体播放控制引擎全量实现 ===
    private void initMediaPlayer(String songFileName) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        try {
            mediaPlayer = new android.media.MediaPlayer();
            // 【修改点3】：将音频源路径强制指向 assets/Music/ 文件夹
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("Music/" + songFileName);
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mediaPlayer.prepare();
        } catch (Exception e) {
            android.util.Log.e("AudioError", "MediaPlayer 装载音频失败: " + songFileName);
            e.printStackTrace();
        }
    }

    private void startMusic() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }
    // ================= 游戏进度与音符生成 =================
    private void initNoteTimeline() {
        noteTimeline.clear();

        // 动态接收从选关界面传过来的 CSV 谱面文件名（例如 "song1.csv"），若为空则兜底默认读取 "song1.csv"
        String csvName = getIntent().getStringExtra("CSV_NAME");
        if (csvName == null || csvName.isEmpty()) {
            csvName = "song1.csv";
        }

        // 加载 MusicCSV 目录下的配置文件
        ChartData chart = ChartLoader.loadChart(this, csvName);

        // 【修改点2】：文件名双向绝对对齐检验逻辑
        String cleanCsvName = csvName.replace(".csv", "").trim();
        String cleanSongName = chart.songFileName.replace(".mp3", "").trim();
        if (!cleanCsvName.equalsIgnoreCase(cleanSongName)) {
            android.util.Log.e("AssetValidationError", "【警告】谱面资产配置产生错位！当前加载的谱面为: " + csvName + "，但其内部声明的音频文件却为: " + chart.songFileName);
        }

        // 装载对应名称的音乐文件
        initMediaPlayer(chart.songFileName);

        scoreManager.totalNotes = chart.totalNotes;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float centerX = screenWidth / 2f;

        float orangeSpeed = 0.6f;
        long preTouchDuration = (long) (centerX / orangeSpeed);

        for (String[] row : chart.noteRows) {
            int type = Integer.parseInt(row[1]);
            long start = Long.parseLong(row[2]);
            long life = Long.parseLong(row[3]);

            switch (type) {
                case 0: // 蓝键
                    noteTimeline.add(new NoteEvent(start, () -> noteContainer.addView(new BlueNoteView(this))));
                    break;
                case 1: // 上紫键位
                    final PurpleNoteView topPurple = new PurpleNoteView(this, true, null, life);
                    lastPurpleNote = topPurple;
                    noteTimeline.add(new NoteEvent(start, () -> noteContainer.addView(topPurple)));
                    break;
                case 2: // 下紫键位
                    final PurpleNoteView bottomPurple = new PurpleNoteView(this, false, null, life);
                    lastPurpleNote = bottomPurple;
                    noteTimeline.add(new NoteEvent(start, () -> noteContainer.addView(bottomPurple)));
                    break;
                case 3: // 紫连接键
                    final PurpleNoteView targetNote = lastPurpleNote;

                    boolean calculatedDirection = true;
                    if (targetNote != null) {
                        calculatedDirection = targetNote.isTop();
                    }

                    final boolean finalDirection = calculatedDirection;
                    PurpleLinkView link = new PurpleLinkView(this, finalDirection);

                    if (targetNote != null) {
                        targetNote.setLinkView(link);
                    }
                    noteTimeline.add(new NoteEvent(start, () -> noteContainer.addView(link)));
                    break;
                case 4: // 左侧橙键
                    noteTimeline.add(new NoteEvent(start - preTouchDuration, () -> noteContainer.addView(new OrangeNoteView(this, true, life, screenWidth))));
                    break;
                case 5: // 右侧橙键
                    noteTimeline.add(new NoteEvent(start - preTouchDuration, () -> noteContainer.addView(new OrangeNoteView(this, false, life, screenWidth))));
                    break;
                case 6: // 左侧黄键
                    noteTimeline.add(new NoteEvent(start - life, () -> noteContainer.addView(new YellowNoteView(this, true, life))));
                    break;
                case 7: // 右侧黄键
                    noteTimeline.add(new NoteEvent(start - life, () -> noteContainer.addView(new YellowNoteView(this, false, life))));
                    break;
            }
        }

        setupProgressAnimator(chart.songLengthSeconds * 1000);

        // 整个关卡时间轴及音频装载就绪，开启音乐播放
        startMusic();
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
        float score = ((float) scoreManager.hits*0.8f+scoreManager.maxCombo*0.2f) / (float) scoreManager.totalNotes;
        String rank = (score >= 1.0f) ? "SSS" : (score >= 0.9f) ? "S" : (score >= 0.8f) ? "A" : (score >= 0.6f) ? "C" : "D";

        Intent intent = new Intent(this, FX_Activity.class);
        intent.putExtra("RANK", rank);
        intent.putExtra("HITS", scoreManager.hits);
        intent.putExtra("MAX_COMBO", scoreManager.maxCombo);
        intent.putExtra("COMPLETION", (int)((float)scoreManager.hits / scoreManager.totalNotes * 100) + "%");
        intent.putExtra("MISSES",scoreManager.miss);
        startActivity(intent);
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

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
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
                        scoreManager.addHit(); // 【击打成功】
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
                        if (!isHit) scoreManager.resetCombo(); // 【漏键中断】
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
    // 2. 紫色音符 (修复：确保无论是击打成功还是 Miss，在 t 时间结束时必然移除)
    // 2. 紫色音符 (长按交互，动态时间与严格容错)
    class PurpleNoteView extends View {
        private Paint innerPaint, strokePaint;
        private float sweepAngle = 0;
        private boolean isTop;
        private PurpleLinkView linkView;

        private long t;
        private long m;
        private long hitStartTime = -1;
        private long dropStartTime = -1;
        private boolean isMissed = false;
        private boolean isSuccessCompleted = false;
        private long lastEffectTime = 0;

        public PurpleNoteView(android.content.Context context, boolean isTop, PurpleLinkView link, long lifeTime) {
            super(context);
            this.isTop = isTop;
            this.linkView = link;
            this.t = lifeTime;
            this.m = (long) (lifeTime * 0.8f);

            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#9932CC")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#4B0082")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(25); strokePaint.setAntiAlias(true);

            post(() -> {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 1f);
                anim.setDuration(t);

                anim.addUpdateListener(a -> {
                    if (isMissed || isSuccessCompleted) return;
                    long currentTime = a.getCurrentPlayTime();
                    boolean isCorrectPointing = isTop ? flagPointingTop : flagPointingBottom;

                    if (hitStartTime == -1) {
                        if (isCorrectPointing) {
                            hitStartTime = currentTime;
                        } else if (currentTime > m) {
                            triggerMiss();
                        }
                    } else {
                        if (!isCorrectPointing) {
                            if (dropStartTime == -1) dropStartTime = currentTime;
                            else if (currentTime - dropStartTime > 300) {
                                triggerMiss();
                            }
                        } else {
                            dropStartTime = -1;
                        }

                        if (hitStartTime != -1 && !isMissed) {
                            float progress = (float)(currentTime - hitStartTime) / (t - hitStartTime);
                            sweepAngle = Math.max(0, Math.min(360f, progress * 360f));

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
                        if (getParent() != null) {
                            if (!isMissed && hitStartTime != -1) {
                                isSuccessCompleted = true;
                                scoreManager.addHit();
                                if (linkView != null) linkView.activate();
                            } else if (!isMissed && hitStartTime == -1) {
                                triggerMiss();
                            }
                            ((ViewGroup) getParent()).removeView(PurpleNoteView.this);
                        }
                    }
                });
                anim.start();
            });
        }

        private void triggerMiss() {
            if (isMissed) return;
            isMissed = true;
            scoreManager.resetCombo();
            setAlpha(0.3f);
            invalidate();
            if (linkView != null) linkView.triggerMiss();
        }

        // 【核心排查点】：此方法必须存在且为 public 权限，参数类型必须为 PurpleLinkView
        public void setLinkView(PurpleLinkView link) {
            this.linkView = link;
        }

        public boolean isTop() {
            return this.isTop;
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f; float cy = isTop ? getHeight() * 0.25f : getHeight() * 0.75f;
            float radius = 90f;

            canvas.drawCircle(cx, cy, radius, innerPaint);

            if (!isMissed) {
                RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
                canvas.drawArc(rect, -90, sweepAngle, false, strokePaint);
            }
        }
    }

    // 3. 紫色连接线 (前置唤醒逻辑)
    // 3. 紫色连接线 (修复未挂载时提前 Miss 残留屏幕的 BUG 类)
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

        // 【核心新增检测修复点】：当视图被系统真正挂载到窗口时触发
        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            // 如果在被真正 addView 进屏幕前，前导键就已经触发了 triggerMiss 并把当前变量改为了 true
            // 说明错过了前面的延迟销毁，在这里刚一冒头就必须立刻执行安全自毁，绝不留在屏幕中央卡死
            if (isMissed) {
                post(() -> {
                    if (getParent() != null) {
                        ((ViewGroup) getParent()).removeView(PurpleLinkView.this);
                    }
                });
            }
        }

        public void triggerMiss() {
            if (isHit) return;
            isMissed = true;
            scoreManager.resetCombo();
            setAlpha(0.3f);
            invalidate();

            // 只有在已经挂载到容器里的情况下，延时自毁才生效
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
            }, 500);
        }

        public void activate() {
            if (isMissed) return;

            ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
            anim.setDuration(800);
            anim.addUpdateListener(a -> {
                if (isHit || isMissed) return;
                boolean isZoneChanged = isTopToBottom ? flagPointingBottom : flagPointingTop;
                if (isZoneChanged) {
                    isHit = true;
                    scoreManager.addHit();
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
                    if (!isHit && !isMissed) triggerMiss();
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
    // 4. 橙色长条音符 (严格时间与速度矩阵控制)
    // 4. 橙色长条音符 (逐渐从中线冒头机制)
    // 4. 橙色长条音符 (精准两阶段时间控制，支持 Miss 后继续移动)
    // 4. 橙色长条音符 (全期绝对匀速控制类)
    // 4. 橙色长条音符 (统一高速匀速，长度受 LivingLife 制约类)
    // 4. 橙色长条音符 (完全不省略补全类)
    class OrangeNoteView extends View {
        private Paint paint;
        private float currentX = -1;
        private boolean isLeft;

        private long lastTime = -1;
        private long dropTimer = 0;
        private boolean isMissed = false;
        private boolean isSuccessStarted = false;
        private boolean isSuccessCompleted = false;
        private long lastEffectTime = 0;
        private long lifeTime;
        private int screenWidth;

        // 构造函数重构：强行接收外界统一的 screenWidth 像素值
        public OrangeNoteView(android.content.Context context, boolean isLeft, long lifeTime, int screenWidth) {
            super(context);
            this.isLeft = isLeft;
            this.lifeTime = lifeTime;
            this.screenWidth = screenWidth;
            paint = new Paint(); paint.setColor(Color.parseColor("#FFA500"));

            post(() -> {
                float centerX = screenWidth / 2f;
                float orangeSpeed = 0.6f;
                float rectWidth = lifeTime * orangeSpeed;
                long preTouchDuration = (long) (centerX / orangeSpeed);

                // 完全基于外部强对齐的绝对坐标系
                float startX = isLeft ? (centerX + rectWidth) : (centerX - rectWidth);
                float endX = isLeft ? 0f : (float) screenWidth;

                ValueAnimator anim = ValueAnimator.ofFloat(startX, endX);
                anim.setDuration(preTouchDuration + lifeTime);
                anim.setInterpolator(new android.view.animation.LinearInterpolator());

                anim.addUpdateListener(a -> {
                    currentX = (float) a.getAnimatedValue();
                    long currentTime = a.getCurrentPlayTime();

                    if (!isMissed && !isSuccessCompleted) {
                        long dt = (lastTime == -1) ? 0 : (currentTime - lastTime);
                        lastTime = currentTime;

                        float leftEdge = isLeft ? currentX - rectWidth : currentX;
                        float rightEdge = isLeft ? currentX : currentX + rectWidth;

                        boolean isTouchingEdge = (isLeft) ? (leftEdge <= 0 && rightEdge >= 0) : (rightEdge >= screenWidth && leftEdge <= screenWidth);
                        boolean isCorrectRaising = isLeft ? flagRaisingLeft : flagRaisingRight;

                        if (isTouchingEdge) {
                            if (!isSuccessStarted) {
                                if (isCorrectRaising) {
                                    isSuccessStarted = true;
                                    scoreManager.addHit();
                                } else {
                                    dropTimer += dt;
                                    if (dropTimer > 300) triggerMiss();
                                }
                            } else {
                                if (!isCorrectRaising) {
                                    dropTimer += dt;
                                    if (dropTimer > 300) triggerMiss();
                                } else {
                                    dropTimer = 0;
                                    if (currentTime - lastEffectTime >= 100) {
                                        lastEffectTime = currentTime;
                                        if (getParent() != null) {
                                            float cy = getHeight() * 0.5f;
                                            float effectX = isLeft ? 0f : (float) screenWidth;
                                            ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#FFA500"), effectX, cy));
                                        }
                                    }
                                }
                            }
                        }
                    }
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (!isMissed && !isSuccessCompleted) {
                            if (isSuccessStarted) {
                                triggerSuccessEnd();
                            } else {
                                scoreManager.resetCombo();
                            }
                        }
                        if (getParent() != null) ((ViewGroup) getParent()).removeView(OrangeNoteView.this);
                    }
                });
                anim.start();
            });
        }

        private void triggerMiss() {
            if (isMissed) return;
            isMissed = true;
            scoreManager.resetCombo();
            setAlpha(0.3f);
            invalidate();
        }

        private void triggerSuccessEnd() {
            if (isSuccessCompleted) return;
            isSuccessCompleted = true;
            scoreManager.addHit();
            if (getParent() != null) {
                float effectX = isLeft ? 0f : (float) screenWidth;
                ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.parseColor("#FFA500"), effectX, getHeight() * 0.5f));
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            float centerX = screenWidth / 2f;
            float orangeSpeed = 0.6f;
            float rectWidth = lifeTime * orangeSpeed;

            if (currentX == -1) {
                currentX = isLeft ? (centerX + rectWidth) : (centerX - rectWidth);
            }

            canvas.save();
            if (isLeft) {
                canvas.clipRect(0f, 0f, centerX, (float) getHeight());
            } else {
                canvas.clipRect(centerX, 0f, (float) screenWidth, (float) getHeight());
            }

            float cy = getHeight() * 0.5f, rectHeight = 120f;
            float left = isLeft ? currentX - rectWidth : currentX;
            canvas.drawRect(left, cy - rectHeight/2, left + rectWidth, cy + rectHeight/2, paint);
            canvas.restore();
        }
    }

    // 5. 黄色短音符
    // 5. 黄色短音符 (准时触线打击机制)
    class YellowNoteView extends View {
        private Paint paint;
        private float currentY = -1;
        private boolean isLeft;
        private boolean isHit = false;
        private long lifeTime;

        public YellowNoteView(android.content.Context context, boolean isLeft, long lifeTime) {
            super(context);
            this.isLeft = isLeft;
            this.lifeTime = lifeTime;
            paint = new Paint(); paint.setColor(Color.YELLOW); paint.setStrokeWidth(40); paint.setStrokeCap(Paint.Cap.ROUND);

            post(() -> {
                // 起点为顶部0，触线边缘设为屏幕底线 getHeight()
                float startY = 0f;
                float targetHitLine = getHeight();

                ValueAnimator anim = ValueAnimator.ofFloat(startY, targetHitLine * 1.2f);
                // 触线时间为 lifeTime，总运行距离为 1.2倍，因此总时间按等比线性扩充
                anim.setDuration((long) (lifeTime * 1.2f));
                anim.setInterpolator(new android.view.animation.LinearInterpolator()); // 必须线性匀速

                anim.addUpdateListener(a -> {
                    if (isHit) return;
                    currentY = (float) a.getAnimatedValue();

                    // 判定窗口：在恰好触及边缘线的前后 200 像素区间内开放交互
                    boolean inHitZone = currentY > getHeight() - 200f && currentY < getHeight() + 100f;
                    boolean isCorrectRaising = isLeft ? flagRaisingLeft : flagRaisingRight;

                    if (inHitZone && isCorrectRaising) {
                        isHit = true;
                        scoreManager.addHit();
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
                        if (!isHit) {
                            scoreManager.resetCombo(); // 动画结束仍未击中，断连
                        }
                        if (getParent() != null) ((ViewGroup) getParent()).removeView(YellowNoteView.this);
                    }
                });
                anim.start();
            });
        }
        @Override protected void onDraw(Canvas canvas) {
            if (currentY == -1) currentY = 0f;
            float cx = isLeft ? getWidth() * 0.25f : getWidth() * 0.75f;
            float lineWidth = 150f;
            canvas.drawLine(cx - lineWidth/2, currentY, cx + lineWidth/2, currentY, paint);
        }
    }
}