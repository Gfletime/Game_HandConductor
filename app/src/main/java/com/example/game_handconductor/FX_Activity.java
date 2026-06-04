package com.example.game_handconductor;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FX_Activity extends AppCompatActivity {

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_THUMBS_UP = 3;
    private static final int GESTURE_THUMBS_DOWN = 4;

    private ImageView vMainCover;
    private ImageView ivBackground;
    private String songName;
    private String songCoverFile;

    // 手势识别控制核心状态机组件
    private ExecutorService cameraExecutor;
    private HandLandmarker handLandmarker;
    private ProcessCameraProvider cameraProvider;

    // 右手计时数据器变量
    private volatile int rightHandGesture = GESTURE_NONE;
    private volatile long rightHandGestureStartTime = 0;

    // ====================================================================
    // 【核心解耦组件】：
    // 用来防御 Android 任务栈高频并发穿透的全局原子级跳转安全门锁。
    // 当其为 true 时，冷酷拦截一切后续相机流帧的跳转请求，死锁单次安全闭环。
    // ====================================================================
    private volatile boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fx);

        // 1. 获取从 GMX 传过来的结算数据
        Intent intent = getIntent();
        songName = intent.getStringExtra("SONG_NAME");

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

        // 2. 绑定 UI 组件
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
            if (!isNavigating) {
                isNavigating = true; // 点击时同步锁死安全防护门
                navigateToG0Selection();
            }
        });

        btnReplay.setOnClickListener(v -> {
            if (!isNavigating) {
                isNavigating = true;
                navigateToGmxReplay();
            }
        });

        // 运行时动态相机隐私验证
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isNavigating = false; // 重新进入页面时释放门锁
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initCameraAndGestureRecognition();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (handLandmarker != null) {
            handLandmarker.close();
            handLandmarker = null;
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
        resetRightHandGestureState();
    }

    private void initCameraAndGestureRecognition() {
        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.GPU)
                    .build();

            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::handleGestureResult)
                    .setErrorListener(e -> e.printStackTrace())
                    .setNumHands(2)
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(this, options);

            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider localCameraProvider = cameraProviderFuture.get();
                    CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                    ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();

                    imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                        long timestamp = System.currentTimeMillis();
                        Bitmap bitmap = toBitmap(image);
                        if (bitmap != null && handLandmarker != null) {
                            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                            handLandmarker.detectAsync(mpImage, timestamp);
                        }
                        image.close();
                    });

                    localCameraProvider.unbindAll();
                    this.cameraProvider = localCameraProvider;
                    localCameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, ContextCompat.getMainExecutor(this));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void handleGestureResult(HandLandmarkerResult result, MPImage image) {
        // 【核心加固机制】：如果安全门锁已经闭合，说明已经成功向系统派发了跳转指令。
        // 直接就地拦截并枪毙后续一切积压帧的计算，杜绝重复 startActivity 引发的任务栈恶性大坍塌。
        if (isNavigating) {
            return;
        }

        if (result == null || result.landmarks().isEmpty()) {
            resetRightHandGestureState();
            return;
        }

        List<NormalizedLandmark> rightHandLandmarks = null;

        for (int i = 0; i < result.landmarks().size(); i++) {
            if (i < result.handedness().size() && !result.handedness().get(i).isEmpty()) {
                String label = result.handedness().get(i).get(0).categoryName();
                if ("Right".equalsIgnoreCase(label)) {
                    rightHandLandmarks = result.landmarks().get(i);
                    break;
                }
            }
        }

        long now = System.currentTimeMillis();

        if (rightHandLandmarks != null) {
            boolean isThumbsUp = checkRightThumbsUpPose(rightHandLandmarks);
            boolean isThumbsDown = checkRightThumbsDownPose(rightHandLandmarks);

            if (isThumbsUp) {
                if (rightHandGesture != GESTURE_THUMBS_UP) {
                    rightHandGesture = GESTURE_THUMBS_UP;
                    rightHandGestureStartTime = now;
                } else {
                    if (now - rightHandGestureStartTime >= 3000) {
                        isNavigating = true; // 极其重要：率先把安全锁死锁！
                        resetRightHandGestureState();
                        navigateToG0Selection();
                    }
                }
            } else if (isThumbsDown) {
                if (rightHandGesture != GESTURE_THUMBS_DOWN) {
                    rightHandGesture = GESTURE_THUMBS_DOWN;
                    rightHandGestureStartTime = now;
                } else {
                    if (now - rightHandGestureStartTime >= 3000) {
                        isNavigating = true; // 极其重要：率先把安全锁死锁！
                        resetRightHandGestureState();
                        navigateToGmxReplay();
                    }
                }
            } else {
                resetRightHandGestureState();
            }
        } else {
            resetRightHandGestureState();
        }
    }

    private boolean checkRightThumbsUpPose(List<NormalizedLandmark> landmarks) {
        float thumbTipY = landmarks.get(4).y();
        float thumbIpY = landmarks.get(3).y();
        boolean isThumbUp = (thumbTipY < thumbIpY);

        NormalizedLandmark wrist = landmarks.get(0);

        boolean indexFolded  = getCalculateDistance(landmarks.get(8), wrist)  < getCalculateDistance(landmarks.get(6), wrist);
        boolean middleFolded = getCalculateDistance(landmarks.get(12), wrist) < getCalculateDistance(landmarks.get(10), wrist);
        boolean ringFolded   = getCalculateDistance(landmarks.get(16), wrist) < getCalculateDistance(landmarks.get(14), wrist);
        boolean pinkyFolded  = getCalculateDistance(landmarks.get(20), wrist) < getCalculateDistance(landmarks.get(18), wrist);

        return isThumbUp && indexFolded && middleFolded && ringFolded && pinkyFolded;
    }

    private boolean checkRightThumbsDownPose(List<NormalizedLandmark> landmarks) {
        float thumbTipY = landmarks.get(4).y();
        float thumbIpY = landmarks.get(3).y();
        boolean isThumbDown = (thumbTipY > thumbIpY);

        NormalizedLandmark wrist = landmarks.get(0);

        boolean indexFolded  = getCalculateDistance(landmarks.get(8), wrist)  < getCalculateDistance(landmarks.get(6), wrist);
        boolean middleFolded = getCalculateDistance(landmarks.get(12), wrist) < getCalculateDistance(landmarks.get(10), wrist);
        boolean ringFolded   = getCalculateDistance(landmarks.get(16), wrist) < getCalculateDistance(landmarks.get(14), wrist);
        boolean pinkyFolded  = getCalculateDistance(landmarks.get(20), wrist) < getCalculateDistance(landmarks.get(18), wrist);

        return isThumbDown && indexFolded && middleFolded && ringFolded && pinkyFolded;
    }

    private double getCalculateDistance(NormalizedLandmark pointA, NormalizedLandmark pointB) {
        return Math.sqrt(
                Math.pow(pointA.x() - pointB.x(), 2) +
                        Math.pow(pointA.y() - pointB.y(), 2) +
                        Math.pow(pointA.z() - pointB.z(), 2)
        );
    }

    private void resetRightHandGestureState() {
        rightHandGesture = GESTURE_NONE;
        rightHandGestureStartTime = 0;
    }

    private void navigateToG0Selection() {
        runOnUiThread(() -> {
            Intent g0Intent = new Intent(FX_Activity.this, G0_Activity.class);
            g0Intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(g0Intent);
            finish();
        });
    }

    private void navigateToGmxReplay() {
        runOnUiThread(() -> {
            Intent gmxIntent = new Intent(FX_Activity.this, GMX_Activity.class);
            gmxIntent.putExtra("SONG_NAME", songName);
            gmxIntent.putExtra("CSV_NAME", songName + ".csv");
            gmxIntent.putExtra("SONG_COVER_FILE", songCoverFile);
            startActivity(gmxIntent);
            finish();
        });
    }

    private Bitmap toBitmap(androidx.camera.core.ImageProxy image) {
        try {
            androidx.camera.core.ImageProxy.PlaneProxy[] planes = image.getPlanes();
            java.nio.ByteBuffer yBuffer = planes[0].getBuffer();
            java.nio.ByteBuffer uBuffer = planes[1].getBuffer();
            java.nio.ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()), 100, os);
            byte[] jpegByteArray = os.toByteArray();

            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(rotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                initCameraAndGestureRecognition();
            }
        } else {
            Toast.makeText(this, "体感计算需要相机验证授权", Toast.LENGTH_SHORT).show();
        }
    }

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

    private void updateSongCover(String fileName) {
        loadAndBindAssetCover(vMainCover, fileName);
        loadAndBindAssetCover(ivBackground, fileName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ivBackground.setRenderEffect(
                    RenderEffect.createBlurEffect(
                            60f,
                            60f,
                            Shader.TileMode.CLAMP
                    )
            );
        }
    }

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