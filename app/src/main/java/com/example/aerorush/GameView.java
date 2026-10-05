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
import android.os.SystemClock;
import android.view.ViewConfiguration;

public class GameView extends View implements SensorEventListener{

    private Paint paint;

    private enum GameState {
        START, PLAYING, PAUSED, RANKING, GAME_OVER
    }
    private GameState gameState = GameState.START;
    private float distance = 0f;
    private float playerX = 400f;
    private float playerY = 1200f;
    private float playerSize = 100f;
    private float speed = 1f;
    private int score = 0;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float tilt = 0f;
    private float lastTouchY;
    private static final float ALPHA = 0.8f;
    private static final float DEAD_ZONE = 0.5f;
    private static final float SENSITIVITY = 3.5f;
    private static final float MIN_SPEED = 0.5f;
    private static final float MAX_SPEED = 3f;
    private static final float TOUCH_FACTOR = 0.004f;

    private static final long DOUBLE_TAP_MS = 300;
    private static final long NITRO_DURATION_MS = 2000;
    private static final long NITRO_COOLDOWN_MS = 5000;
    private static final float NITRO_MULTIPLIER = 2f;
    private long lastTapTime = 0;
    private long nitroEndTime = 0;
    private long nitroReadyTime = 0;
    private float downX, downY;
    private boolean moved = false;
    private int touchSlop;
    private boolean isSwiping = false;
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
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        paint = new Paint();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private boolean isNitroActive() {
        return SystemClock.uptimeMillis() < nitroEndTime;
    }

    public float getEffectiveSpeed() {
        return isNitroActive() ? speed * NITRO_MULTIPLIER : speed;
    }

    private void activateNitro() {
        long now = SystemClock.uptimeMillis();
        if (now >= nitroReadyTime) {
            nitroEndTime = now + NITRO_DURATION_MS;
            nitroReadyTime = now + NITRO_COOLDOWN_MS;
        }
    }

    private void resetNitro() {
        nitroEndTime = 0;
        nitroReadyTime = 0;
        lastTapTime = 0;
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
            distance += getEffectiveSpeed();
            score = (int) distance;

            if (Math.abs(tilt) > DEAD_ZONE) {
                playerX += tilt * SENSITIVITY;
            }

            if (getWidth() > 0) {
                playerX = Math.max(0f, Math.min(getWidth() - playerSize, playerX));
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

        paint.setColor(isNitroActive() ? Color.parseColor("#FFD700") : Color.parseColor("#FF8C00"));
        canvas.drawRect(playerX, playerY, playerX + playerSize, playerY + playerSize, paint);
        if (isNitroActive()) {
            paint.setColor(Color.RED);
            paint.setTextSize(70f);
            canvas.drawText("NITRO !", 50, 260, paint);
        }
        paint.setColor(Color.BLACK);
        paint.setTextSize(50f);
        canvas.drawText("Vitesse: x" + String.format("%.1f", getEffectiveSpeed()), 50, 170, paint);
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
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (gameState == GameState.START || gameState == GameState.GAME_OVER) {
                    resetPlayer();
                    score = 0;
                    distance = 0f;
                    speed = 1f;
                    resetNitro();
                    gameState = GameState.PLAYING;
                } else if (gameState == GameState.PLAYING) {
                    downX = event.getX();
                    downY = event.getY();
                    lastTouchY = downY;
                    moved = false;
                    isSwiping = true;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (gameState == GameState.PLAYING && isSwiping) {
                    if (Math.abs(event.getX() - downX) > touchSlop
                            || Math.abs(event.getY() - downY) > touchSlop) {
                        moved = true;
                    }
                    float dy = lastTouchY - event.getY();
                    speed += dy * TOUCH_FACTOR;
                    speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
                    lastTouchY = event.getY();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (gameState == GameState.PLAYING && isSwiping && !moved) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTapTime <= DOUBLE_TAP_MS) {
                        activateNitro();
                        lastTapTime = 0;
                    } else {
                        lastTapTime = now;
                    }
                }
                isSwiping = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                isSwiping = false;
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