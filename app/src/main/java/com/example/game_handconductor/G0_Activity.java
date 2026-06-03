package com.example.game_handconductor;

import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class G0_Activity extends AppCompatActivity {

    class Song {
        String name;
        int coverResId;
        int bestCompletion;
        int maxCombo;

        public Song(String name, int coverResId, int bestCompletion, int maxCombo) {
            this.name = name;
            this.coverResId = coverResId;
            this.bestCompletion = bestCompletion;
            this.maxCombo = maxCombo;
        }
    }

    private ImageView ivMainCover;
    private TextView tvSongName, tvBestCompletion, tvMaxCombo;
    private List<Song> songList;
    private Song currentSelectedSong;

    private ImageView ivBackground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_g0);

        ivMainCover = findViewById(R.id.iv_main_cover);
        tvSongName = findViewById(R.id.tv_song_name);
        tvBestCompletion = findViewById(R.id.tv_best_completion);
        tvMaxCombo = findViewById(R.id.tv_max_combo);
        Button btnBack = findViewById(R.id.btn_back_title);
        Button btnSettings = findViewById(R.id.btn_goto_settings);

        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(G0_Activity.this, S1_Activity.class)));

        songList = new ArrayList<>();
        // 请替换为你的真实图片资源
        songList.add(new Song("Track 1 - 启航", R.mipmap.ic_launcher, 99, 30));
        songList.add(new Song("Track 2 - 激流", R.mipmap.ic_launcher, 85, 120));
        songList.add(new Song("Track 3 - 宁静", R.mipmap.ic_launcher, 100, 55));
        songList.add(new Song("Track 4 - 终焉", R.mipmap.ic_launcher, 45, 15));
        songList.add(new Song("Track 5 - 轮回", R.mipmap.ic_launcher, 0, 0));

        RecyclerView rvSongList = findViewById(R.id.rv_song_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvSongList.setLayoutManager(layoutManager);

        // 自动吸附到中心
        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvSongList);

        SongAdapter adapter = new SongAdapter(songList);
        rvSongList.setAdapter(adapter);

        if (!songList.isEmpty()) {
            updateLeftPanel(songList.get(0));
        }

        // ================= 旋转盘滚动与自动更新监听 =================
        rvSongList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // 滑动过程中实时计算弧度形变
                applyCarouselEffect(recyclerView);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                // 修改点3：当列表停止滑动时 (SCROLL_STATE_IDLE)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // 找到当前被吸附在正中间的 View
                    View snapView = snapHelper.findSnapView(layoutManager);
                    if (snapView != null) {
                        // 获取该 View 对应的数据位置
                        int position = layoutManager.getPosition(snapView);
                        if (position >= 0 && position < songList.size()) {
                            // 自动更新左侧面板
                            updateLeftPanel(songList.get(position));
                        }
                    }
                }
            }
        });

        // 首次渲染视图时的弧度初始化
        rvSongList.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                applyCarouselEffect(rvSongList);
                rvSongList.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        // ==============================================================

        // 修改点2：左侧大图点击事件，跳转通用场景 GMX
        ivMainCover.setOnClickListener(v -> {
            if (currentSelectedSong != null) {
                Intent intent = new Intent(G0_Activity.this, GMX_Activity.class);
                // 将歌曲名字和封面等信息传递给通用场景
                intent.putExtra("SONG_NAME", currentSelectedSong.name);
                intent.putExtra("SONG_COVER_ID", currentSelectedSong.coverResId);
                startActivity(intent);
            }
        });
    }

    /**
     * 计算每个 Item 距离屏幕中心的偏移量，实现 3D 旋转盘效果
     */
    private void applyCarouselEffect(RecyclerView rv) {
        float rvCenterY = rv.getHeight() / 2f;

        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            float childCenterY = child.getTop() + child.getHeight() / 2f;
            float distance = Math.abs(rvCenterY - childCenterY);
            float ratio = Math.min(1f, distance / rvCenterY);

            float scale = 1f - (0.4f * ratio);
            child.setScaleX(scale);
            child.setScaleY(scale);

            float translationX = (ratio * ratio) * 150f;
            child.setTranslationX(translationX);

            child.setAlpha(1f - (0.4f * ratio));
        }
    }

    private void updateLeftPanel(Song song) {
        currentSelectedSong = song;
        ivMainCover.setImageResource(song.coverResId);
        tvSongName.setText("当前选择: " + song.name);
        tvBestCompletion.setText("最佳完成度: " + song.bestCompletion + "%");
        tvMaxCombo.setText("最大combo: " + song.maxCombo);
        updateBlurBackground(song.coverResId);
    }
    private void updateBlurBackground(int coverResId) {

        ivBackground.setImageResource(coverResId);

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

    class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {
        private List<Song> list;

        public SongAdapter(List<Song> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
            return new SongViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
            Song song = list.get(position);
            holder.ivItemCover.setImageResource(song.coverResId);

            holder.itemView.setOnClickListener(v -> {
                // 点击右侧小图时，不仅更新左侧，还让 RecyclerView 平滑滚动将该项居中
                updateLeftPanel(song);
                ((RecyclerView) holder.itemView.getParent()).smoothScrollToPosition(position);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class SongViewHolder extends RecyclerView.ViewHolder {
            ImageView ivItemCover;
            public SongViewHolder(@NonNull View itemView) {
                super(itemView);
                ivItemCover = itemView.findViewById(R.id.iv_item_cover);
            }
        }
    }
}