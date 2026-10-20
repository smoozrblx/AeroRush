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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends View implements SensorEventListener{

    private final Paint paint;

    public enum GameState {
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
    private final List<Obstacle> obstacles = new ArrayList<>();
    private int spawnCounter = 0;
    private final Random random = new Random();
    private long gameOverTime = 0;
    private float spawnFrequency = 60f;                  // frames entre deux obstacles (valeur actuelle)
    private static final float BASE_SPAWN_FRAMES = 60f;  // valeur à vitesse normale
    private static final float MIN_SPAWN_FRAMES = 12f;   // plancher pour éviter un écran saturé
    private static final float MAX_SPAWN_FRAMES = 120f;  // plafond quand tout est très lent
    private static final float BASE_OBSTACLE_SPEED = 10f;
    private static final float MAX_OBSTACLE_SPEED = 40f;  // vitesse maximale des obstacles

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

            int screenWidth = getWidth();
            int screenHeight = getHeight();

            if (screenWidth > 0 && screenHeight > 0) {
                if (playerX < 0 || playerX + playerSize > screenWidth ||
                        playerY < 0 || playerY + playerSize > screenHeight) {
                    gameState = GameState.GAME_OVER;
                    return;
                }

                // Génération des obstacles
                updateSpawnFrequency();
                spawnCounter++;
                if (spawnCounter >= spawnFrequency) {
                    spawnCounter = 0;
                    float obsWidth = 150f + random.nextFloat() * 200f;
                    float obsHeight = 80f;
                    float obsX = random.nextFloat() * (screenWidth - obsWidth);
                    float obsY = -obsHeight;
                    float obsSpeed = getObstacleSpeed();

                    obstacles.add(new Obstacle(obsX, obsY, obsWidth, obsHeight, obsSpeed));
                }

                // Mise à jour et détection des collisions
                Iterator<Obstacle> iterator = obstacles.iterator();
                while (iterator.hasNext()) {
                    Obstacle obstacle = iterator.next();
                    obstacle.update();
                    obstacle.setSpeed(getObstacleSpeed());

                    if (obstacle.collidesWith(playerX, playerY, playerSize)) {
                        gameState = GameState.GAME_OVER;
                        break;
                    }

                    if (obstacle.isOffScreen(screenHeight)) {
                        iterator.remove();
                    }
                }
                if (Math.abs(tilt) > DEAD_ZONE) {
                    playerX += tilt * SENSITIVITY;
                }

                if (getWidth() > 0) {
                    playerX = Math.max(0f, Math.min(getWidth() - playerSize, playerX));
                }
            }
        }
    }

    public float getObstacleSpeed() {
        float s = (BASE_OBSTACLE_SPEED + (score / 150f)) * speed;
        return Math.min(s, MAX_OBSTACLE_SPEED);
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

        for (Obstacle obstacle : obstacles) {
            obstacle.draw(canvas, paint);
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
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                gameOverTime = SystemClock.uptimeMillis();
                if (gameState == GameState.START || gameState == GameState.GAME_OVER) {
                    if (gameState == GameState.GAME_OVER
                            && SystemClock.uptimeMillis() - gameOverTime < 700) {
                        return true;   // appui direct apres le game over, on l'ignore
                    }
                    resetGame();
                    gameState = GameState.PLAYING;
                    performClick();
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

    private void resetGame() {
        resetPlayer();
        obstacles.clear();   // supprime tous les anciens obstacles
        spawnCounter = 0;    // repart de zéro pour l'apparition
        spawnFrequency = BASE_SPAWN_FRAMES;
        score = 0;
        distance = 0f;
        speed = 1f;
        isSwiping = false;
        moved = false;
        resetNitro();
    }

    private void updateSpawnFrequency() {
        float f = BASE_SPAWN_FRAMES * BASE_OBSTACLE_SPEED / getObstacleSpeed();
        spawnFrequency = Math.max(MIN_SPAWN_FRAMES, Math.min(MAX_SPAWN_FRAMES, f));
    }
}