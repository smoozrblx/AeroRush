package com.example.aerorush;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreManager {

    private static final String PREFS = "aerorush_prefs";
    private static final String KEY_TOP = "top_scores";
    private static final int MAX_SCORES = 5;

    private final SharedPreferences prefs;

    public ScoreManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Integer> getTopScores() {
        List<Integer> list = new ArrayList<>();
        String saved = prefs.getString(KEY_TOP, "");
        if (!saved.isEmpty()) {
            for (String part : saved.split(",")) {
                try {
                    list.add(Integer.parseInt(part));
                } catch (NumberFormatException ignored) { }
            }
        }
        return list;
    }

    public boolean addScore(int score) {
        List<Integer> list = getTopScores();
        int oldBest = list.isEmpty() ? 0 : list.get(0);

        if (score > 0) list.add(score);
        Collections.sort(list, Collections.reverseOrder());
        while (list.size() > MAX_SCORES) list.remove(list.size() - 1);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i));
        }
        prefs.edit().putString(KEY_TOP, sb.toString()).apply();

        return score > oldBest;
    }
}