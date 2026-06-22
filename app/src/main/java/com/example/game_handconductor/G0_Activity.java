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
import androidx.recyclerview.widget.LinearSmoothScroller;
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
    private int currentSelectedPosition = RecyclerView.NO_POSITION;
    private SongAdapter adapter;

    private ImageView ivBackground;

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

        ivBackground = findViewById(R.id.iv_background);

        Button btnBack = findViewById(R.id.btn_back_title);
        Button btnSettings = findViewById(R.id.btn_goto_settings);

        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(G0_Activity.this, S1_Activity.class)));

        songList = new ArrayList<>();
        adapter = new SongAdapter(songList);

        rvSongList = findViewById(R.id.rv_song_list);
        layoutManager = new CenterLinearLayoutManager();
        rvSongList.setLayoutManager(layoutManager);

        snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvSongList);
        rvSongList.setAdapter(adapter);

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
                            updateLeftPanel(songList.get(position), position);
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

    @Override
    protected void onResume() {
        super.onResume();
        songList.clear();
        songList.addAll(loadMusicConfig());
        adapter.notifyDataSetChanged();

        if (!songList.isEmpty()) {
            int targetPosition = 0;
            boolean foundSelectedSong = false;

            if (currentSelectedSong != null) {
                if (currentSelectedPosition >= 0 && currentSelectedPosition < songList.size()) {
                    Song indexedSong = songList.get(currentSelectedPosition);
                    if (indexedSong.name.equalsIgnoreCase(currentSelectedSong.name)
                            && indexedSong.coverFileName.equalsIgnoreCase(currentSelectedSong.coverFileName)) {
                        updateLeftPanel(indexedSong, currentSelectedPosition);
                        centerSongAtPosition(currentSelectedPosition, false);
                        return;
                    }
                }

                for (int i = 0; i < songList.size(); i++) {
                    Song s = songList.get(i);
                    if (s.name.equalsIgnoreCase(currentSelectedSong.name)) {
                        updateLeftPanel(s, i);
                        targetPosition = i;
                        foundSelectedSong = true;
                        break;
                    }
                }
                if (!foundSelectedSong) {
                    updateLeftPanel(songList.get(0), 0);
                }
            } else {
                updateLeftPanel(songList.get(0), 0);
            }

            final int finalPos = targetPosition;
            if (rvSongList != null) {
                centerSongAtPosition(finalPos, false);
            }
        }
    }

    private void centerSongAtPosition(int position, boolean smooth) {
        if (rvSongList == null || position < 0 || position >= songList.size()) {
            return;
        }

        rvSongList.post(() -> {
            if (smooth) {
                rvSongList.smoothScrollToPosition(position);
            } else {
                layoutManager.scrollToPositionWithOffset(position, 0);
                rvSongList.post(() -> snapPositionToCenter(position));
            }
        });
    }

    private void snapPositionToCenter(int position) {
        View targetView = layoutManager.findViewByPosition(position);
        if (targetView == null) {
            return;
        }

        int[] distance = snapHelper.calculateDistanceToFinalSnap(layoutManager, targetView);
        if (distance != null) {
            rvSongList.scrollBy(distance[0], distance[1]);
        }
        applyCarouselEffect(rvSongList);
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
        updateLeftPanel(song, RecyclerView.NO_POSITION);
    }

    private void updateLeftPanel(Song song, int position) {
        currentSelectedSong = song;
        if (position != RecyclerView.NO_POSITION) {
            currentSelectedPosition = position;
        }
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
            InputStream is = getAssets().open("Image/MusicPageFace/" + fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(is);

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
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                updateLeftPanel(list.get(adapterPosition), adapterPosition);
                centerSongAtPosition(adapterPosition, true);
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

    class CenterLinearLayoutManager extends LinearLayoutManager {
        public CenterLinearLayoutManager() {
            super(G0_Activity.this);
        }

        @Override
        public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
            LinearSmoothScroller smoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    int viewCenter = viewStart + (viewEnd - viewStart) / 2;
                    int boxCenter = boxStart + (boxEnd - boxStart) / 2;
                    return boxCenter - viewCenter;
                }
            };
            smoothScroller.setTargetPosition(position);
            startSmoothScroll(smoothScroller);
        }
    }
}
