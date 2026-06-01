package com.example.game_handconductor;

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

import java.util.ArrayList;
import java.util.List;

public class L1_Activity extends AppCompatActivity {

    // 图片数据模型
    class PhotoRecord {
        int imageResId;
        String shootTime;
        String songName;

        public PhotoRecord(int imageResId, String shootTime, String songName) {
            this.imageResId = imageResId;
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

        // 2. 准备测试数据
        photoList = new ArrayList<>();
        // 替换为你真实的截图资源
        photoList.add(new PhotoRecord(R.mipmap.ic_launcher, "2026/6/1 13:20", "Track 1 - 启航"));
        photoList.add(new PhotoRecord(R.mipmap.ic_launcher, "2026/6/2 09:15", "Track 2 - 激流"));
        photoList.add(new PhotoRecord(R.mipmap.ic_launcher, "2026/6/3 21:40", "Track 3 - 宁静"));
        photoList.add(new PhotoRecord(R.mipmap.ic_launcher, "2026/6/5 18:05", "Track 4 - 终焉"));

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
            holder.ivPhoto.setImageResource(list.get(position).imageResId);
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