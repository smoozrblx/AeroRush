package com.example.aerorush;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.HashSet;
import java.util.Set;
import android.util.Log;
public class FeedbackManager {

    private final Vibrator vibrator;
    private final SoundPool soundPool;
    private final Set<Integer> loaded = new HashSet<>();
    private final int sCrash, sNitro, sClick, sDenied;

    public FeedbackManager(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build();
        // Le chargement est asynchrone : on ne joue un son qu'une fois prêt
        soundPool.setOnLoadCompleteListener((sp, id, status) -> {
            if (status == 0) loaded.add(id);
        });
        sCrash = soundPool.load(context, R.raw.crash, 1);
        sNitro = soundPool.load(context, R.raw.nitro, 1);
        sClick = soundPool.load(context, R.raw.click, 1);
        sDenied = soundPool.load(context, R.raw.denied, 1);
    }

    /** Crash : vibration longue et forte + explosion. */
    public void onCrash() {
        vibratePattern(new long[]{0, 120, 60, 300});
        play(sCrash, 1f);
    }

    /** Nitro accepté : vibration brève + montée en régime. */
    public void onNitro() {
        vibrate(80);
        play(sNitro, 0.9f);
    }

    /** Nitro refusé (en recharge) : petit « non » sonore et haptique. */
    public void onNitroDenied() {
        vibrate(25);
        play(sDenied, 0.6f);
    }

    /** Validation d'un bouton / changement d'écran. */
    public void onClick() {
        play(sClick, 0.5f);
    }

    public void release() {
        soundPool.release();
    }

    private void play(int id, float volume) {
        if (loaded.contains(id)) soundPool.play(id, volume, volume, 1, 0, 1f);
    }

    private boolean canVibrate() {
        return vibrator != null && vibrator.hasVibrator();   // la plupart des tablettes n'en ont pas
    }

    private void vibrate(long ms) {
        if (!canVibrate()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }

    private void vibratePattern(long[] pattern) {
        if (!canVibrate()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }
}
