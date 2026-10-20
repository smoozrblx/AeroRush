package com.example.aerorush;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.startGame();
        gameView.startSensor();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.stopSensor();
        gameView.stopGame();
    }
}