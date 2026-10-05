package com.example.aerorush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class GameView extends View implements SensorEventListener{

    private Paint paint;

    private enum GameState {
        START, PLAYING, PAUSED, RANKING, GAME_OVER
    }
    private GameState gameState = GameState.START;

    private float playerX = 400f;
    private float playerY = 1200f;
    private float playerSize = 100f;
    private int score = 0;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float tilt = 0f;
    private static final float ALPHA = 0.8f;
    private static final float DEAD_ZONE = 0.5f;
    private static final float SENSITIVITY = 3.5f;
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            update();
            invalidate();
            postDelayed(this, 16);
        }
    };

    //Constructeur
    public GameView(Context context) {
        super(context);
        paint = new Paint();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void startGame() {
        removeCallbacks(gameLoop);
        post(gameLoop);
    }

    public void stopGame(){
        removeCallbacks(gameLoop);
    }

    public void startSensor(){
        if (accelerometer != null){
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stopSensor(){
        sensorManager.unregisterListener(this);
    }
    private void update() {
        if (gameState == GameState.PLAYING) {
            score++;
            if (Math.abs(tilt) > DEAD_ZONE) {
                playerX += tilt * SENSITIVITY;
            }
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
        canvas.drawColor(Color.parseColor("#87CEEB"));

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
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (gameState == GameState.START) {
                resetPlayer();
                score = 0;
                gameState = GameState.PLAYING;
            } else if (gameState == GameState.GAME_OVER) {
                resetPlayer();
                score = 0;
                gameState = GameState.PLAYING;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            tilt = ALPHA * tilt + (1 - ALPHA) * (-event.values[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetPlayer();
    }

    private void resetPlayer() {
        playerX = (getWidth() - playerSize) / 2f;
        playerY = getHeight() * 0.8f;
        tilt = 0f;
    }
}