package com.example.aerorush;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        GameView gameView = new GameView(this);
        setContentView(gameView);

        gameView.startGame();
    }
}