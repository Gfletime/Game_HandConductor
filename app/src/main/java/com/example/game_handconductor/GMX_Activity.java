package com.example.game_handconductor;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GMX_Activity extends AppCompatActivity {

    public void ShotScreen() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault());
        String currentTimeStr = sdf.format(new Date());

        String fileTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "snap_" + fileTimestamp + ".jpg";

        PreviewView previewView = findViewById(R.id.viewFinder);
        if (previewView == null) return;

        Bitmap cameraBitmap = previewView.getBitmap();
        if (cameraBitmap == null) {
            runOnUiThread(() -> Toast.makeText(GMX_Activity.this, "截图失败：相机未就绪，请确保 PreviewView 开启了 compatible 模式", Toast.LENGTH_LONG).show());
            return;
        }

        Bitmap finalBitmap = cameraBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(finalBitmap);

        if (previewView.getParent() instanceof ViewGroup) {
            ViewGroup rootLayout = (ViewGroup) previewView.getParent();
            for (int i = 0; i < rootLayout.getChildCount(); i++) {
                View child = rootLayout.getChildAt(i);

                if (child != previewView && child.getVisibility() == View.VISIBLE) {
                    canvas.save();
                    canvas.translate(child.getLeft(), child.getTop());
                    child.draw(canvas);
                    canvas.restore();
                }
            }
        }

        final Bitmap bitmapToSave = finalBitmap;
        new Thread(() -> {
            try {
                File imageFile = new File(getFilesDir(), imageFileName);
                FileOutputStream fos = new FileOutputStream(imageFile);
                bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.flush();
                fos.close();

                bitmapToSave.recycle();

                File csvFile = new File(getFilesDir(), "CaptureConfig.csv");
                boolean isNewFile = !csvFile.exists();

                FileOutputStream csvFos = new FileOutputStream(csvFile, true);
                PrintWriter writer = new PrintWriter(csvFos);

                if (isNewFile) {
                    writer.println("FileName,MusicName,CaptureTime");
                }

                String safeSongName = (songName != null) ? songName.replace(",", " ") : "UnknownSong";
                writer.println(imageFileName + "," + safeSongName + "," + currentTimeStr);

                writer.flush();
                writer.close();
                csvFos.close();


            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(GMX_Activity.this, "存储失败：沙盒空间无写入权限", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveSnapshotData(Bitmap bitmap, String fileName, String timeRecord) {
        try {
            File imageFile = new File(getFilesDir(), fileName);
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

            bitmap.recycle();

            File csvFile = new File(getFilesDir(), "CaptureConfig.csv");
            boolean isNewFile = !csvFile.exists();

            FileOutputStream csvFos = new FileOutputStream(csvFile, true);
            PrintWriter writer = new PrintWriter(csvFos);

            if (isNewFile) {
                writer.println("FileName,MusicName,CaptureTime");
            }

            String safeSongName = (songName != null) ? songName.replace(",", " ") : "UnknownSong";
            writer.println(fileName + "," + safeSongName + "," + timeRecord);

            writer.flush();
            writer.close();
            csvFos.close();


        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                Toast.makeText(GMX_Activity.this, "保存失败: 内部存储空间由于IO异常被拒绝", Toast.LENGTH_SHORT).show();
            });
        }
    }
    private class ScoreManager {
        int currentCombo = 0, maxCombo = 0, hits = 0,miss=0;
        int fevernote=0;
        int totalNotes = 0;

        void addHit() {
            hits++;
            currentCombo++;
            fevernote++;
            if (currentCombo > maxCombo) maxCombo = currentCombo;
            if(fevernote>1) {
                ShotScreen();
                fevernote=0;
            }
            updateComboUI();
        }

        void resetCombo() {
            miss++;
            currentCombo = 0;
            fevernote=0;
            updateComboUI();
        }
    }
    private ScoreManager scoreManager = new ScoreManager();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView tvCombo;

    private ProgressBar pbSongProgress;
    private ValueAnimator progressAnimator;
    private FrameLayout noteContainer;
    private String songName;

    private String SongFaceFile;
    private int songCoverId;

    private HandLandmarker handLandmarker;
    private ExecutorService cameraExecutor;
    private PreviewView viewFinder;
    private static final int CAMERA_REQ_CODE = 100;

    public volatile boolean flagPushing = false;
    public volatile boolean flagPointingTop = false;
    public volatile boolean flagPointingBottom = false;
    public volatile boolean flagRaisingLeft = false;
    public volatile boolean flagRaisingRight = false;

    class NoteEvent {
        long spawnTimeMs;
        Runnable action;
        public boolean isTriggered = false;
        NoteEvent(long time, Runnable action) { this.spawnTimeMs = time; this.action = action;this.isTriggered = false; }
    }
    private List<NoteEvent> noteTimeline = new ArrayList<>();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gmx);

        tvCombo = findViewById(R.id.tv_max_combo);
        pbSongProgress = findViewById(R.id.pb_song_progress);
        noteContainer = findViewById(R.id.note_container);
        viewFinder = findViewById(R.id.viewFinder);

        Intent intent = getIntent();
        songName = intent.getStringExtra("SONG_NAME");
        SongFaceFile = intent.getStringExtra("SONG_COVER_FILE");

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

        btnSettings.setOnClickListener(v ->
        {
            togglePauseGame();
            startActivity(new Intent(GMX_Activity.this, S1_Activity.class));
        });

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

    private android.media.MediaPlayer mediaPlayer = null;
    private PurpleNoteView lastPurpleNote = null;

    private boolean isGamePaused = false;

    private void togglePauseGame() {
        if (isGamePaused) {
            resumeGame();
        } else {
            pauseGame();
        }
    }

    private void pauseGame() {
        if (isGamePaused) return;
        isGamePaused = true;

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.pause();
        }

        if (noteContainer != null) {
            for (int i = 0; i < noteContainer.getChildCount(); i++) {
                android.view.View child = noteContainer.getChildAt(i);
                if (child instanceof BlueNoteView) ((BlueNoteView) child).pauseAnimation();
                if (child instanceof PurpleNoteView) ((PurpleNoteView) child).pauseAnimation();
                if (child instanceof PurpleLinkView) ((PurpleLinkView) child).pauseAnimation();
                if (child instanceof OrangeNoteView) ((OrangeNoteView) child).pauseAnimation();
                if (child instanceof YellowNoteView) ((YellowNoteView) child).pauseAnimation();
            }
        }
    }

    private void resumeGame() {
        if (!isGamePaused) return;
        isGamePaused = false;

        if (mediaPlayer != null) {
            adjustVolumeOnTheFly();
            mediaPlayer.start();
        }
        if (progressAnimator != null && progressAnimator.isPaused()) {
            progressAnimator.resume();
        }

        if (noteContainer != null) {
            for (int i = 0; i < noteContainer.getChildCount(); i++) {
                android.view.View child = noteContainer.getChildAt(i);
                if (child instanceof BlueNoteView) ((BlueNoteView) child).resumeAnimation();
                if (child instanceof PurpleNoteView) ((PurpleNoteView) child).resumeAnimation();
                if (child instanceof PurpleLinkView) ((PurpleLinkView) child).resumeAnimation();
                if (child instanceof OrangeNoteView) ((OrangeNoteView) child).resumeAnimation();
                if (child instanceof YellowNoteView) ((YellowNoteView) child).resumeAnimation();
            }
        }
    }
    private void initMediaPlayer(String songFileName) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        try {
            mediaPlayer = new android.media.MediaPlayer();
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("Music/" + songFileName);
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mediaPlayer.prepare();

            SharedPreferences sharedPreferences = getSharedPreferences("GameSettings", MODE_PRIVATE);

            int globalVolume = sharedPreferences.getInt("global_volume", 100);

            float volumeRatio = globalVolume / 100f;

            if (mediaPlayer != null) {
                mediaPlayer.setVolume(volumeRatio, volumeRatio);
            }

        } catch (Exception e) {
            android.util.Log.e("AudioError", "MediaPlayer 装载音频失败: " + songFileName);
            e.printStackTrace();
        }
    }

    private void adjustVolumeOnTheFly() {
        SharedPreferences sharedPreferences = getSharedPreferences("GameSettings", MODE_PRIVATE);

        int globalVolume = sharedPreferences.getInt("global_volume", 100);

        float volumeRatio = globalVolume / 100f;

        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volumeRatio, volumeRatio);
        }
    }
    private void startMusic() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }
    private void initNoteTimeline() {
        noteTimeline.clear();

        String csvName = getIntent().getStringExtra("CSV_NAME");
        if (csvName == null || csvName.isEmpty()) {
            csvName = "song1.csv";
        }

        ChartData chart = ChartLoader.loadChart(this, csvName);

        String cleanCsvName = csvName.replace(".csv", "").trim();
        String cleanSongName = chart.songFileName.replace(".mp3", "").trim();
        if (!cleanCsvName.equalsIgnoreCase(cleanSongName)) {
            android.util.Log.e("AssetValidationError", "【警告】谱面资产配置产生错位！当前加载的谱面为: " + csvName + "，但其内部声明的音频文件却为: " + chart.songFileName);
        }

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
                case 0:
                    noteTimeline.add(new NoteEvent(start, () -> noteContainer.addView(new BlueNoteView(this))));
                    break;
                case 1:
                    final PurpleNoteView topPurple = new PurpleNoteView(this, true, null, life);
                    lastPurpleNote = topPurple;
                    noteTimeline.add(new NoteEvent(start, () -> noteContainer.addView(topPurple)));
                    break;
                case 2:
                    final PurpleNoteView bottomPurple = new PurpleNoteView(this, false, null, life);
                    lastPurpleNote = bottomPurple;
                    noteTimeline.add(new NoteEvent(start, () -> noteContainer.addView(bottomPurple)));
                    break;
                case 3:
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
                case 4:
                    noteTimeline.add(new NoteEvent(start - preTouchDuration, () -> noteContainer.addView(new OrangeNoteView(this, true, life, screenWidth))));
                    break;
                case 5:
                    noteTimeline.add(new NoteEvent(start - preTouchDuration, () -> noteContainer.addView(new OrangeNoteView(this, false, life, screenWidth))));
                    break;
                case 6:
                    noteTimeline.add(new NoteEvent(start - life, () -> noteContainer.addView(new YellowNoteView(this, true, life))));
                    break;
                case 7:
                    noteTimeline.add(new NoteEvent(start - life, () -> noteContainer.addView(new YellowNoteView(this, false, life))));
                    break;
            }
        }

        setupProgressAnimator(chart.songLengthSeconds * 1000);

        startMusic();
    }

    private void setupProgressAnimator(int durationMs) {
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }

        progressAnimator = android.animation.ValueAnimator.ofFloat(0, 1f);
        progressAnimator.setDuration(durationMs);
        progressAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        progressAnimator.addUpdateListener(animation -> {
            long currentTime = animation.getCurrentPlayTime();

            android.widget.ProgressBar pb = findViewById(R.id.pb_song_progress);
            if (pb != null) {
                pb.setProgress((int) ((float) currentTime / durationMs * pb.getMax()));
            }

            for (NoteEvent event : noteTimeline) {
                if (!event.isTriggered && currentTime >= event.spawnTimeMs) {
                    event.isTriggered = true;
                    event.action.run();
                }
            }
        });
        progressAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!isGamePaused) {
                    goToFXActivity();
                }
            }
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
        intent.putExtra("SONG_NAME",songName);
        intent.putExtra("SONG_COVER_FILE",SongFaceFile);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isGamePaused) {
            resumeGame();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isGamePaused && mediaPlayer != null && mediaPlayer.isPlaying()) {
            pauseGame();
        }
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

    class BlueNoteView extends View {
        private Paint innerPaint, strokePaint;
        private float sweepAngle = 0;
        private boolean isHit = false;
        private ValueAnimator anim = null;

        public BlueNoteView(android.content.Context context) {
            super(context);
            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#00BFFF")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#00008B")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(30); strokePaint.setAntiAlias(true);

            anim = ValueAnimator.ofFloat(0, 360);
            anim.setDuration(1500);
            anim.addUpdateListener(a -> {
                if (isHit) return;
                sweepAngle = (float) a.getAnimatedValue();
                if (flagPushing) {
                    isHit = true;
                    scoreManager.addHit();
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
                    if (!isHit) scoreManager.resetCombo();
                    if (!isHit && getParent() != null) ((ViewGroup) getParent()).removeView(BlueNoteView.this);
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (anim != null) {
                anim.start();
                if (isGamePaused) {
                    anim.pause();
                }
            }
        }

        public void pauseAnimation() {
            if (anim != null && anim.isRunning()) anim.pause();
        }

        public void resumeAnimation() {
            if (anim != null && anim.isPaused()) anim.resume();
        }

        @Override protected void onDraw(Canvas canvas) {
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
        private PurpleLinkView linkView;

        private long t;
        private long m;
        private long hitStartTime = -1;
        private long dropStartTime = -1;
        private boolean isMissed = false;
        private boolean isSuccessCompleted = false;
        private long lastEffectTime = 0;
        private ValueAnimator anim = null;

        public PurpleNoteView(android.content.Context context, boolean isTop, PurpleLinkView link, long lifeTime) {
            super(context);
            this.isTop = isTop;
            this.linkView = link;
            this.t = lifeTime;
            this.m = (long) (lifeTime * 0.8f);

            innerPaint = new Paint(); innerPaint.setColor(Color.parseColor("#9932CC")); innerPaint.setAntiAlias(true);
            strokePaint = new Paint(); strokePaint.setColor(Color.parseColor("#4B0082")); strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeWidth(25); strokePaint.setAntiAlias(true);

            anim = ValueAnimator.ofFloat(0, 1f);
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
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (anim != null) {
                anim.start();
                if (isGamePaused) {
                    anim.pause();
                }
            }
        }

        public void pauseAnimation() {
            if (anim != null && anim.isRunning()) anim.pause();
        }

        public void resumeAnimation() {
            if (anim != null && anim.isPaused()) anim.resume();
        }

        private void triggerMiss() {
            if (isMissed) return;
            isMissed = true;
            scoreManager.resetCombo();
            setAlpha(0.3f);
            invalidate();
            if (linkView != null) linkView.triggerMiss();
        }

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

    class PurpleLinkView extends View {
        private Paint paint;
        private boolean isTopToBottom;
        private boolean isHit = false;
        private boolean isMissed = false;
        private ValueAnimator anim = null;

        public PurpleLinkView(android.content.Context context, boolean isTopToBottom) {
            super(context);
            this.isTopToBottom = isTopToBottom;
            paint = new Paint(); paint.setColor(Color.parseColor("#4B0082")); paint.setStrokeWidth(20);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND); paint.setAntiAlias(true);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (isMissed) {
                post(() -> {
                    if (getParent() != null) {
                        ((ViewGroup) getParent()).removeView(PurpleLinkView.this);
                    }
                });
            }
        }

        public void pauseAnimation() {
            if (anim != null && anim.isRunning()) anim.pause();
        }

        public void resumeAnimation() {
            if (anim != null && anim.isPaused()) anim.resume();
        }

        public void triggerMiss() {
            if (isHit) return;
            isMissed = true;
            scoreManager.resetCombo();
            setAlpha(0.3f);
            invalidate();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
            }, 500);
        }

        public void activate() {
            if (isMissed) return;

            anim = ValueAnimator.ofFloat(0, 1);
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
            if (isGamePaused) {
                anim.pause();
            }
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
        private ValueAnimator anim = null;

        public OrangeNoteView(android.content.Context context, boolean isLeft, long lifeTime, int screenWidth) {
            super(context);
            this.isLeft = isLeft;
            this.lifeTime = lifeTime;
            this.screenWidth = screenWidth;
            paint = new Paint(); paint.setColor(Color.parseColor("#FFA500"));

            float centerX = screenWidth / 2f;
            float orangeSpeed = 0.6f;
            float rectWidth = lifeTime * orangeSpeed;
            long preTouchDuration = (long) (centerX / orangeSpeed);

            float startX = isLeft ? (centerX + rectWidth) : (centerX - rectWidth);
            float endX = isLeft ? 0f : (float) screenWidth;

            anim = ValueAnimator.ofFloat(startX, endX);
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
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (anim != null) {
                anim.start();
                if (isGamePaused) {
                    anim.pause();
                }
            }
        }

        public void pauseAnimation() {
            if (anim != null && anim.isRunning()) anim.pause();
        }

        public void resumeAnimation() {
            if (anim != null && anim.isPaused()) anim.resume();
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

    class YellowNoteView extends View {
        private Paint paint;
        private float currentY = -1;
        private boolean isLeft;
        private boolean isHit = false;
        private long lifeTime;
        private ValueAnimator anim = null;

        public YellowNoteView(android.content.Context context, boolean isLeft, long lifeTime) {
            super(context);
            this.isLeft = isLeft;
            this.lifeTime = lifeTime;
            paint = new Paint(); paint.setColor(Color.YELLOW); paint.setStrokeWidth(40); paint.setStrokeCap(Paint.Cap.ROUND);

            anim = ValueAnimator.ofFloat(0f, 1.2f);
            anim.setDuration((long) (lifeTime * 1.2f));
            anim.setInterpolator(new android.view.animation.LinearInterpolator());

            anim.addUpdateListener(a -> {
                if (isHit) return;
                float factor = (float) a.getAnimatedValue();

                float viewHeight = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
                currentY = factor * viewHeight;

                boolean inHitZone = currentY > viewHeight - 200f && currentY < viewHeight + 100f;
                boolean isCorrectRaising = isLeft ? flagRaisingLeft : flagRaisingRight;

                if (inHitZone && isCorrectRaising) {
                    isHit = true;
                    scoreManager.addHit();
                    if (getParent() != null) {
                        float cx = isLeft ? getWidth() * 0.25f : getWidth() * 0.75f;
                        ((FrameLayout) getParent()).addView(new HitEffectView(getContext(), Color.YELLOW, cx, viewHeight));
                        ((ViewGroup) getParent()).removeView(YellowNoteView.this);
                    }
                    anim.cancel();
                }
                invalidate();
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (!isHit) {
                        scoreManager.resetCombo();
                    }
                    if (getParent() != null) ((ViewGroup) getParent()).removeView(YellowNoteView.this);
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (anim != null) {
                anim.start();
                if (isGamePaused) {
                    anim.pause();
                }
            }
        }

        public void pauseAnimation() {
            if (anim != null && anim.isRunning()) anim.pause();
        }

        public void resumeAnimation() {
            if (anim != null && anim.isPaused()) anim.resume();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (currentY == -1) currentY = 0f;
            float cx = isLeft ? getWidth() * 0.25f : getWidth() * 0.75f;
            float lineWidth = 150f;
            canvas.drawLine(cx - lineWidth/2, currentY, cx + lineWidth/2, currentY, paint);
        }
    }
}