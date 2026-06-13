package com.example.game_handconductor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast; // 增量导入：用于清理完毕后的屏幕弹窗反馈

import androidx.appcompat.app.AppCompatActivity;

import java.io.File; // 增量导入：用于定位内部存储沙盒空间的文件对象

public class S1_Activity extends AppCompatActivity {

    private SeekBar seekBarVolume;
    private EditText etVolumeValue;
    private SharedPreferences sharedPreferences;

    private boolean isUpdating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_s1);

        Button btnBack = findViewById(R.id.btn_back);
        Button btnClearCache = findViewById(R.id.btn_clear_cache);
        seekBarVolume = findViewById(R.id.seekbar_volume);
        etVolumeValue = findViewById(R.id.et_volume_value);

        sharedPreferences = getSharedPreferences("GameSettings", Context.MODE_PRIVATE);

        int savedVolume = sharedPreferences.getInt("global_volume", 50);
        seekBarVolume.setProgress(savedVolume);
        etVolumeValue.setText(String.valueOf(savedVolume));

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                executeClearCacheLogic();
            }
        });

        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    isUpdating = true;
                    etVolumeValue.setText(String.valueOf(progress));
                    saveVolumeGlobally(progress);
                    isUpdating = false;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        etVolumeValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {

                String input = s.toString();
                if (!input.isEmpty()) {
                    try {
                        int volume = Integer.parseInt(input);

                        if (volume > 100) volume = 100;
                        if (volume < 0) volume = 0;

                        isUpdating = true;
                        seekBarVolume.setProgress(volume);
                        saveVolumeGlobally(volume);

                        if (Integer.parseInt(input) > 100 || Integer.parseInt(input) < 0) {
                            etVolumeValue.setText(String.valueOf(volume));
                            etVolumeValue.setSelection(etVolumeValue.getText().length());
                        }
                        isUpdating = false;

                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void saveVolumeGlobally(int volume) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("global_volume", volume);
        editor.apply();
    }


    private void executeClearCacheLogic() {
        try {
            File internalStorageDir = getFilesDir();
            if (internalStorageDir == null || !internalStorageDir.exists()) {
                Toast.makeText(this, "清除失败：未检索到有效的应用本地沙盒空间", Toast.LENGTH_SHORT).show();
                return;
            }

            File[] filesInSandbox = internalStorageDir.listFiles();
            int deletedPhotosCount = 0;
            boolean isCaptureCsvDeleted = false;
            boolean isMusicCsvDeleted = false;

            if (filesInSandbox != null) {
                for (File file : filesInSandbox) {
                    String fileName = file.getName();

                    if (fileName.startsWith("snap_") && fileName.endsWith(".jpg")) {
                        if (file.delete()) {
                            deletedPhotosCount++;
                        }
                    }
                    else if ("CaptureConfig.csv".equalsIgnoreCase(fileName)) {
                        if (file.delete()) {
                            isCaptureCsvDeleted = true;
                        }
                    }
                    else if ("MusicConfig.csv".equalsIgnoreCase(fileName)) {
                        if (file.delete()) {
                            isMusicCsvDeleted = true;
                        }
                    }
                }
            }

            StringBuilder resultReport = new StringBuilder("【本地清理结果报告】\n");
            resultReport.append("已彻底销毁对局精彩截图: ").append(deletedPhotosCount).append(" 张\n");
            if (isCaptureCsvDeleted) resultReport.append("已重置本地游戏相册配置账本\n");
            if (isMusicCsvDeleted) resultReport.append("已重置所有曲目最高分记录为0\n");

            Toast.makeText(this, resultReport.toString(), Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "清理中途发生非预期的I/O系统级阻断异常", Toast.LENGTH_SHORT).show();
        }
    }
}