package com.example.aerorush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View {

    private Paint paint;

    private enum GameState {
        START, PLAYING, PAUSED, RANKING, GAME_OVER
    }
    private GameState gameState = GameState.START;

    private float playerX = 400f;
    private float playerY = 1200f;
    private float playerSize = 100f;
    private int score = 0;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            update();
            invalidate();
            postDelayed(this, 16);
        }
    };

    public GameView(Context context) {
        super(context);
        paint = new Paint();
    }

    public void startGame() {
        post(gameLoop);
    }

    private void update() {
        if (gameState == GameState.PLAYING) {
            score++;

            if (getWidth() > 0 && getHeight() > 0) {
                if (playerX < 0 || playerX + playerSize > getWidth() ||
                        playerY < 0 || playerY + playerSize > getHeight()) {
                    gameState = GameState.GAME_OVER;
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        switch (gameState) {
            case START:
                drawStartScreen(canvas);
                break;
            case PLAYING:
                drawGame(canvas);
                break;
            case PAUSED:
                drawPauseScreen(canvas);
                break;
            case RANKING:
                drawRankingScreen(canvas);
                break;
            case GAME_OVER:
                drawGameOverScreen(canvas);
                break;
        }
    }

    private void drawStartScreen(Canvas canvas) {
        canvas.drawColor(Color.parseColor("#2C3E50"));
        paint.setColor(Color.WHITE);
        paint.setTextSize(80f);
        canvas.drawText("AeroRush", 100, 400, paint);

        paint.setTextSize(40f);
        paint.setColor(Color.parseColor("#FF8C00"));
        canvas.drawText("Touchez pour décoller", 100, 600, paint);
    }

    private void drawGame(Canvas canvas) {
        canvas.drawColor(Color.parseColor("#87CEEB")); // Ciel

        paint.setColor(Color.parseColor("#FF8C00"));
        canvas.drawRect(playerX, playerY, playerX + playerSize, playerY + playerSize, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(50f);
        canvas.drawText("Distance: " + score + " m", 50, 100, paint);
    }

    private void drawPauseScreen(Canvas canvas) {

    }

    private void drawRankingScreen(Canvas canvas) {

    }

    private void drawGameOverScreen(Canvas canvas) {
        canvas.drawColor(Color.parseColor("#8B0000"));
        paint.setColor(Color.WHITE);
        paint.setTextSize(80f);
        canvas.drawText("CRASH !", 100, 400, paint);

        paint.setTextSize(50f);
        canvas.drawText("Distance parcourue : " + score + " m", 100, 500, paint);
        canvas.drawText("Touchez pour rejouer", 100, 700, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {

            if (gameState == GameState.START && action == MotionEvent.ACTION_DOWN) {
                gameState = GameState.PLAYING;
            }
            else if (gameState == GameState.PLAYING) {
                playerX = event.getX() - (playerSize / 2);
                playerY = event.getY() - (playerSize / 2);
            }
            else if (gameState == GameState.GAME_OVER && action == MotionEvent.ACTION_DOWN) {
                playerX = 400f;
                playerY = 1200f;
                score = 0;
                gameState = GameState.PLAYING;
            }

            return true;
        }
        return super.onTouchEvent(event);
    }
}