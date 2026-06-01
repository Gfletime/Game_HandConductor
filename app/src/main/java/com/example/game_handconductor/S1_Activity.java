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

import androidx.appcompat.app.AppCompatActivity;

public class S1_Activity extends AppCompatActivity {

    private SeekBar seekBarVolume;
    private EditText etVolumeValue;
    private SharedPreferences sharedPreferences;

    // 用于防止 SeekBar 和 EditText 互相触发改变造成的死循环
    private boolean isUpdating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_s1);

        Button btnBack = findViewById(R.id.btn_back);
        seekBarVolume = findViewById(R.id.seekbar_volume);
        etVolumeValue = findViewById(R.id.et_volume_value);

        // 初始化 SharedPreferences，用于全局保存数据 ("GameSettings" 是保存的文件名)
        sharedPreferences = getSharedPreferences("GameSettings", Context.MODE_PRIVATE);

        // 读取全局保存的音量，如果没有保存过则默认值为 50
        int savedVolume = sharedPreferences.getInt("global_volume", 50);
        seekBarVolume.setProgress(savedVolume);
        etVolumeValue.setText(String.valueOf(savedVolume));

        // 1. 返回按钮逻辑：调用 finish() 关闭当前界面，自动返回上一个界面
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 2. 监听 SeekBar 的滑动
        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) { // 只有玩家手动拖动时才更新
                    isUpdating = true;
                    etVolumeValue.setText(String.valueOf(progress)); // 更新输入框显示
                    saveVolumeGlobally(progress); // 全局保存
                    isUpdating = false;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 3. 监听 EditText 的手动输入
        etVolumeValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating) return; // 如果是代码触发的更新，直接跳过

                String input = s.toString();
                if (!input.isEmpty()) {
                    try {
                        int volume = Integer.parseInt(input);

                        // 边界限制：音量不能超过 100，也不能小于 0
                        if (volume > 100) volume = 100;
                        if (volume < 0) volume = 0;

                        isUpdating = true;
                        seekBarVolume.setProgress(volume); // 同步更新滑动条
                        saveVolumeGlobally(volume); // 全局保存

                        // 可选：如果用户输入了大于 100 的数字，自动帮其修正为 100 并重新显示
                        if (Integer.parseInt(input) > 100 || Integer.parseInt(input) < 0) {
                            etVolumeValue.setText(String.valueOf(volume));
                            etVolumeValue.setSelection(etVolumeValue.getText().length()); // 将光标移到末尾
                        }
                        isUpdating = false;

                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    // 全局保存音量的方法
    private void saveVolumeGlobally(int volume) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("global_volume", volume);
        editor.apply(); // 使用 apply() 异步保存，性能更好
    }
}