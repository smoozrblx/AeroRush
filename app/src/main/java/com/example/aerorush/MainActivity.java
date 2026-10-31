package com.example.aerorush;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.media.AudioManager;

public class MainActivity extends AppCompatActivity {

    private GameView gameView;
    private OnBackPressedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        gameView = new GameView(this);
        setContentView(gameView);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), gameView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!gameView.handleBack()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        backCallback.setEnabled(true);
        gameView.startSensor();
        gameView.startGame();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pauseGame();
        gameView.stopSensor();
        gameView.stopGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        gameView.release();
    }
}