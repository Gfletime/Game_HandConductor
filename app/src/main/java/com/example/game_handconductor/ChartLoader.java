package com.example.game_handconductor;
import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

// 这个类负责解析 assets 下的 .csv 文件
// === 【新增数据结构】：谱面数据实体类 ===


// === 【重构工具类】：完全适配新版 CSV 格式 ===
public class ChartLoader {
    public static ChartData loadChart(android.content.Context context, String fileName) {
        ChartData chartData = new ChartData();
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(context.getAssets().open(fileName)));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                // 1. 读取歌曲长度信息：如 #,9999,,,,
                if (line.startsWith("#")) {
                    if (parts.length >= 2) {
                        chartData.songLengthSeconds = Integer.parseInt(parts[1].trim());
                    }
                    continue;
                }

                // 2. 读取音符总数信息：如 !,666,,,,
                if (line.startsWith("!")) {
                    if (parts.length >= 2) {
                        chartData.totalNotes = Integer.parseInt(parts[1].trim());
                    }
                    continue;
                }

                // 跳过表头行
                if (line.startsWith("Order")) {
                    continue;
                }

                // 3. 读取正文音符数据
                if (parts.length >= 4) {
                    chartData.noteRows.add(parts);
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return chartData;
    }
}