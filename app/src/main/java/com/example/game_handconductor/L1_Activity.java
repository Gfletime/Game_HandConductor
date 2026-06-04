package com.example.game_handconductor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class L1_Activity extends AppCompatActivity {

    // 【数据模型升级】：将固定的整型图片 ID 更改为指向本地沙盒的文件名路径字符串
    class PhotoRecord {
        String fileName;
        String shootTime;
        String songName;

        public PhotoRecord(String fileName, String shootTime, String songName) {
            this.fileName = fileName;
            this.shootTime = shootTime;
            this.songName = songName;
        }
    }

    private RecyclerView rvPhotos;
    private RelativeLayout layoutInfoOverlay;
    private TextView tvShootTime, tvSongName;
    private List<PhotoRecord> photoList;
    private LinearLayoutManager layoutManager;
    private LinearSnapHelper snapHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_l1);

        // 1. 绑定组件
        rvPhotos = findViewById(R.id.rv_photos);
        layoutInfoOverlay = findViewById(R.id.layout_info_overlay);
        tvShootTime = findViewById(R.id.tv_shoot_time);
        tvSongName = findViewById(R.id.tv_song_name);
        Button btnBack = findViewById(R.id.btn_back_title);

        btnBack.setOnClickListener(v -> finish()); // 返回主界面

        // 2. 【核心打通】：调用本地沙盒解析器，替换原本写死的 photoList.add() 测试数据
        photoList = loadCaptureConfig();

        // 3. 配置 RecyclerView
        layoutManager = new LinearLayoutManager(this);
        rvPhotos.setLayoutManager(layoutManager);

        PhotoAdapter adapter = new PhotoAdapter(photoList);
        rvPhotos.setAdapter(adapter);

        // 使用 LinearSnapHelper 实现滚动停止时自动吸附到中央
        snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvPhotos);

        // 4. 添加滚动与状态监听
        rvPhotos.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // 滑动时实时计算缩放比例
                applyZoomEffect(recyclerView);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // 当玩家手指开始拖拽时，隐藏底部信息框
                    hideInfoOverlay();

                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // 当滑动完全停止时，找到当前位于正中央的卡片
                    View snapView = snapHelper.findSnapView(layoutManager);
                    if (snapView != null) {
                        int position = layoutManager.getPosition(snapView);
                        if (position >= 0 && position < photoList.size()) {
                            // 更新并显示信息框
                            showInfoOverlay(photoList.get(position));
                        }
                    }
                }
            }
        });

        // 5. 首次渲染时触发一次动画和信息框显示
        rvPhotos.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                applyZoomEffect(rvPhotos);
                if (!photoList.isEmpty()) {
                    showInfoOverlay(photoList.get(0));
                }
                rvPhotos.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    /**
     * 【新增增量引擎】：解析应用内部存储空间的 CaptureConfig.csv 数据
     */
    private List<PhotoRecord> loadCaptureConfig() {
        List<PhotoRecord> records = new ArrayList<>();
        try {
            File csvFile = new File(getFilesDir(), "CaptureConfig.csv");
            // 如果文件还不存在（证明玩家从未触发过截图），直接返回空列表安全兜底
            if (!csvFile.exists()) {
                return records;
            }

            InputStream is = openFileInput("CaptureConfig.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;

            while ((line = reader.readLine()) != null) {
                // 自动跳过 CSV 的头部表头或空白空行
                if (line.startsWith("FileName") || line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String fileName = parts[0].trim();
                    String songName = parts[1].trim();
                    String shootTime = parts[2].trim();

                    // 核心技术点：利用 records.add(0, ...) 在头部逆向插入
                    // 这样可以让玩家最新截好的精彩瞬间永远排在相册最上方，更符合现代手游相册体验
                    records.add(0, new PhotoRecord(fileName, shootTime, songName));
                }
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return records;
    }

    /**
     * 核心形变算法：计算图片距离中心的垂直距离，实现缩放效果
     */
    private void applyZoomEffect(RecyclerView rv) {
        float rvCenterY = rv.getHeight() / 2f;

        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            float childCenterY = child.getTop() + child.getHeight() / 2f;

            // 计算距离中心的绝对值
            float distance = Math.abs(rvCenterY - childCenterY);

            // 归一化比例：距离中心越远，ratio 越接近 1
            float ratio = Math.min(1f, distance / rvCenterY);

            // 缩放计算：中心最大(1.0)，边缘最小(0.75)
            float scale = 1f - (0.25f * ratio);
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }

    /**
     * 更新文字并渐显底部信息框
     */
    private void showInfoOverlay(PhotoRecord record) {
        tvShootTime.setText("拍摄时间: " + record.shootTime);
        tvSongName.setText("游玩歌曲: " + record.songName);

        layoutInfoOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
    }

    /**
     * 渐隐底部信息框
     */
    private void hideInfoOverlay() {
        layoutInfoOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .start();
    }

    // --- RecyclerView 适配器 ---
    class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
        private List<PhotoRecord> list;

        public PhotoAdapter(List<PhotoRecord> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            PhotoRecord record = list.get(position);

            // 顺着记录的文件名，去应用内部私有沙盒空间检索对应的物理图像
            File imgFile = new File(getFilesDir(), record.fileName);

            if (imgFile.exists()) {
                // 【性能防崩溃优化点】：由于真机全屏音符+摄像头叠图分辨率极高，
                // 如果不做处理直接用原始比例狂刷列表极易引发堆内存溢出(OOM)。
                // 这里采用 inSampleSize=2 进行高能硬件级二次降采样解码（宽高减半，内存缩减至 1/4），大幅度提升滑动帧率。
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;

                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath(), options);
                holder.ivPhoto.setImageBitmap(bitmap);
            } else {
                // 容错安全伞：如果图片文件不幸被误删，展示小机器人默认图标兜底
                holder.ivPhoto.setImageResource(R.mipmap.ic_launcher);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            public PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                ivPhoto = itemView.findViewById(R.id.iv_photo);
            }
        }
    }
}