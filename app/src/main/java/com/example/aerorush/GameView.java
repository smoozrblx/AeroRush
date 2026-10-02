package com.example.aerorush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class GameView extends View {

    private Paint paint;

    private float playerX = 400f;
    private float playerY = 1200f;
    private float playerSize = 100f;


    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run(){
            update();
            invalidate();
            postDelayed(this, 16);
        }
    };

    public GameView(Context context) {
        super(context);
        paint = new Paint();
    }

    private void update(){
        }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.parseColor("#87CEEB"));

        paint.setColor(Color.parseColor("#FF8C00"));

        canvas.drawRect(playerX, playerY, playerX + playerSize, playerY + playerSize, paint);
    }

    public void startGame() {
        post(gameLoop);
    }
}