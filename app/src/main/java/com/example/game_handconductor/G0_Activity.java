package com.example.game_handconductor;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class G0_Activity extends AppCompatActivity {

    class Song {
        String name;
        String coverFileName;
        String betterScore;
        int bestCompletion;
        int maxCombo;

        public Song(String name, String coverFileName, String betterScore, int bestCompletion, int maxCombo) {
            this.name = name;
            this.coverFileName = coverFileName;
            this.betterScore = betterScore;
            this.bestCompletion = bestCompletion;
            this.maxCombo = maxCombo;
        }
    }

    private ImageView ivMainCover;
    private TextView tvSongName, tvBestCompletion, tvMaxCombo;
    private TextView tvMainRank;
    private List<Song> songList;
    private Song currentSelectedSong;
    private SongAdapter adapter;

    private ImageView ivBackground;

    // 【重构核心点一】：提升为全局变量，用于在 onResume 生命周期中执行位置强行校准与对齐
    private RecyclerView rvSongList;
    private LinearLayoutManager layoutManager;
    private LinearSnapHelper snapHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_g0);

        ivMainCover = findViewById(R.id.iv_main_cover);
        tvSongName = findViewById(R.id.tv_song_name);
        tvBestCompletion = findViewById(R.id.tv_best_completion);
        tvMaxCombo = findViewById(R.id.tv_max_combo);
        tvMainRank = findViewById(R.id.tv_main_rank);

        // 【核心修复点二】：补齐原代码缺失的背景绑定，彻底消灭点击图片引发的 NullPointerException 闪退
        ivBackground = findViewById(R.id.iv_background);

        Button btnBack = findViewById(R.id.btn_back_title);
        Button btnSettings = findViewById(R.id.btn_goto_settings);

        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(G0_Activity.this, S1_Activity.class)));

        songList = new ArrayList<>();
        adapter = new SongAdapter(songList);

        rvSongList = findViewById(R.id.rv_song_list);
        layoutManager = new LinearLayoutManager(this);
        rvSongList.setLayoutManager(layoutManager);

        snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvSongList);
        rvSongList.setAdapter(adapter);

        // ================= 旋转盘滚动与自动更新监听 =================
        rvSongList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                applyCarouselEffect(recyclerView);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snapView = snapHelper.findSnapView(layoutManager);
                    if (snapView != null) {
                        int position = layoutManager.getPosition(snapView);
                        if (position >= 0 && position < songList.size()) {
                            updateLeftPanel(songList.get(position));
                        }
                    }
                }
            }
        });

        rvSongList.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                applyCarouselEffect(rvSongList);
                rvSongList.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        ivMainCover.setOnClickListener(v -> {
            if (currentSelectedSong != null) {
                Intent intent = new Intent(G0_Activity.this, GMX_Activity.class);
                intent.putExtra("SONG_NAME", currentSelectedSong.name);
                intent.putExtra("CSV_NAME", currentSelectedSong.name + ".csv");
                intent.putExtra("SONG_COVER_FILE", currentSelectedSong.coverFileName);
                startActivity(intent);
            }
        });
    }

    // 【生命周期核心同步】：每次重新回到选歌页面，不仅重载数据，还强制轮盘物理对齐正确档位
    @Override
    protected void onResume() {
        super.onResume();
        songList.clear();
        songList.addAll(loadMusicConfig());
        adapter.notifyDataSetChanged();

        if (!songList.isEmpty()) {
            int targetPosition = 0; // 记录需要对齐的档位索引

            if (currentSelectedSong != null) {
                for (int i = 0; i < songList.size(); i++) {
                    Song s = songList.get(i);
                    if (s.name.equalsIgnoreCase(currentSelectedSong.name)) {
                        updateLeftPanel(s);
                        targetPosition = i; // 锁死目标歌曲索引位置
                        break;
                    }
                }
            } else {
                updateLeftPanel(songList.get(0));
            }

            // 【核心修复点三】：利用主线程空闲队列，强行命令右侧轮盘物理滚动到当前选中的歌曲位置，斩断错位现象
            final int finalPos = targetPosition;
            if (rvSongList != null) {
                rvSongList.post(() -> rvSongList.scrollToPosition(finalPos));
            }
        }
    }

    private void loadAndBindAssetCover(ImageView imageView, String fileName) {
        if (fileName == null || fileName.isEmpty() || "default".equalsIgnoreCase(fileName)) {
            imageView.setImageResource(R.mipmap.xnn);
        } else {
            try {
                InputStream is = getAssets().open("Image/MusicPageFace/" + fileName);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                imageView.setImageBitmap(bitmap);
                is.close();
            } catch (Exception e) {
                e.printStackTrace();
                imageView.setImageResource(R.mipmap.xnn);
            }
        }
    }

    private List<Song> loadMusicConfig() {
        List<Song> parsedList = new ArrayList<>();
        try {
            java.io.File localCachedFile = new java.io.File(getFilesDir(), "MusicConfig.csv");
            InputStream is;
            if (localCachedFile.exists()) {
                is = openFileInput("MusicConfig.csv");
            } else {
                is = getAssets().open("MusicCSV/MusicConfig.csv");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Order") || line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String musicName = parts[1].trim();
                    String faceFile = parts[2].trim();
                    String scoreRank = parts[3].trim();
                    int completion = Integer.parseInt(parts[4].trim());
                    int maxCombo = Integer.parseInt(parts[5].trim());

                    parsedList.add(new Song(musicName, faceFile, scoreRank, completion, maxCombo));
                }
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return parsedList;
    }

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
        loadAndBindAssetCover(ivMainCover, song.coverFileName);

        tvSongName.setText("当前选择: " + song.name);
        tvBestCompletion.setText("最佳完成度: " + song.bestCompletion + "%");
        tvMaxCombo.setText("最大combo: " + song.maxCombo);
        updateBlurBackground(song.coverFileName);

        String rankStr = song.betterScore.toUpperCase().trim();
        tvMainRank.setText(rankStr);
        tvMainRank.getPaint().setShader(null);

        switch (rankStr) {
            case "SSS":
                float textWidth = tvMainRank.getPaint().measureText("SSS");
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
                tvMainRank.getPaint().setShader(rainbowShader);
                tvMainRank.setTextColor(Color.RED);
                break;
            case "S":
                tvMainRank.setTextColor(Color.parseColor("#FFD700"));
                break;
            case "A":
                tvMainRank.setTextColor(Color.parseColor("#9932CC"));
                break;
            case "B":
                tvMainRank.setTextColor(Color.parseColor("#1E90FF"));
                break;
            case "C":
                tvMainRank.setTextColor(Color.parseColor("#32CD32"));
                break;
            case "D":
            default:
                tvMainRank.setTextColor(Color.parseColor("#808080"));
                break;
        }
        tvMainRank.invalidate();
    }

    private void updateBlurBackground(String fileName) {
        try {
            // 【核心修复点四】：将模糊图的 Assets 统一归入绝对正确的 "Image/MusicPageFace/" 路径下
            InputStream is = getAssets().open("Image/MusicPageFace/" + fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(is);

            // 注入防御性空安全校验，双重锁死绝不抛出异常，绝不闪退
            if (ivBackground != null) {
                ivBackground.setImageBitmap(bitmap);
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
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
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
            loadAndBindAssetCover(holder.ivItemCover, song.coverFileName);

            holder.itemView.setOnClickListener(v -> {
                updateLeftPanel(song);
                if (holder.itemView.getParent() instanceof RecyclerView) {
                    ((RecyclerView) holder.itemView.getParent()).smoothScrollToPosition(position);
                }
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