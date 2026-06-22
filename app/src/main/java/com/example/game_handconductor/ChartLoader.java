package com.example.game_handconductor;
import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ChartLoader {
    public static ChartData loadChart(android.content.Context context, String fileName) {
        ChartData chartData = new ChartData();
        try {
            // 【修改点3】：将读取路径强制指向 assets/MusicCSV/ 文件夹
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(context.getAssets().open("MusicCSV/" + fileName))
            );
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                if (line.startsWith("@")) {
                    if (parts.length >= 2) {
                        chartData.songFileName = parts[1].trim();
                    }
                    continue;
                }
                if (line.startsWith("#")) {
                    if (parts.length >= 2) {
                        chartData.songLengthSeconds = Integer.parseInt(parts[1].trim());
                    }
                    continue;
                }
                if (line.startsWith("!")) {
                    if (parts.length >= 2) {
                        chartData.totalNotes = Integer.parseInt(parts[1].trim());
                    }
                    continue;
                }
                if (line.startsWith("Order")) {
                    continue;
                }
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