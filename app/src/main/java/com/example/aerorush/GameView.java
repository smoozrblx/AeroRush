package com.example.aerorush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends View implements SensorEventListener {

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
    private FeedbackManager feedback;
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

    // --- Pause ---
    private float dp;
    private final RectF pauseButton = new RectF();
    private final RectF resumeButton = new RectF();
    private final RectF menuButton = new RectF();
    private long pauseStartTime = 0;

    // --- Scores ---
    private ScoreManager scoreManager;
    private List<Integer> topScores = new ArrayList<>();
    private int bestScore = 0;
    private boolean newRecord = false;
    private long gameOverTime = 0;
    private final RectF rankingButton = new RectF();
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
        dp = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        paint = new Paint();
        feedback = new FeedbackManager(context);
        scoreManager = new ScoreManager(context);
        refreshScores();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
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
            feedback.vibrateNitro();
        }
    }

    public void triggerGameOver() {
        if (gameState != GameState.PLAYING) return;
        gameState = GameState.GAME_OVER;
        gameOverTime = SystemClock.uptimeMillis();
        newRecord = scoreManager.addScore(score);
        refreshScores();
        feedback.vibrateCrash();
    }

    private void resetNitro() {
        nitroEndTime = 0;
        nitroReadyTime = 0;
        lastTapTime = 0;
    }
    private void refreshScores() {
        topScores = scoreManager.getTopScores();
        bestScore = topScores.isEmpty() ? 0 : topScores.get(0);
    }
    public void startGame() {
        removeCallbacks(gameLoop);
        post(gameLoop);
    }

    public void stopGame() {
        removeCallbacks(gameLoop);
    }

    // --- Pause / reprise ---
    public void pauseGame() {
        if (gameState != GameState.PLAYING) return;
        gameState = GameState.PAUSED;
        pauseStartTime = SystemClock.uptimeMillis();
        isSwiping = false;
    }

    private void resumeGame() {
        // Le nitro se base sur l'horloge : on décale ses échéances de la durée de la pause
        long pausedFor = SystemClock.uptimeMillis() - pauseStartTime;
        if (nitroEndTime > 0) nitroEndTime += pausedFor;
        if (nitroReadyTime > 0) nitroReadyTime += pausedFor;
        tilt = 0f;
        lastTapTime = 0;
        gameState = GameState.PLAYING;
    }

    public void startSensor() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stopSensor() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void update() {
        if (gameState == GameState.PLAYING) {
            distance += getEffectiveSpeed();
            score = (int) distance;

            int screenWidth = getWidth();
            int screenHeight = getHeight();

            if (screenWidth > 0 && screenHeight > 0) {

                // Déplacement latéral (accéléromètre), avion bloqué contre les bords
                if (Math.abs(tilt) > DEAD_ZONE) {
                    playerX += tilt * SENSITIVITY;
                }
                playerX = Math.max(0f, Math.min(screenWidth - playerSize, playerX));

                spawnCounter++;
                if (spawnCounter >= 45) {
                    spawnCounter = 0;
                    float obsWidth = 150f + random.nextFloat() * 200f;
                    float obsHeight = 80f;
                    float obsX = random.nextFloat() * (screenWidth - obsWidth);
                    float obsY = -obsHeight;
                    float obsSpeed = 10f + (score / 150f);

                    obstacles.add(new Obstacle(obsX, obsY, obsWidth, obsHeight, obsSpeed));
                }

                Iterator<Obstacle> iterator = obstacles.iterator();
                while (iterator.hasNext()) {
                    Obstacle obstacle = iterator.next();
                    obstacle.update();

                    if (obstacle.collidesWith(playerX, playerY, playerSize)) {
                        triggerGameOver();
                        break;
                    }

                    if (obstacle.isOffScreen(screenHeight)) {
                        iterator.remove();
                    }
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
        paint.setColor(Color.WHITE);
        paint.setTextSize(40f);
        canvas.drawText("Meilleur score : " + bestScore + " m", 100, 700, paint);
        drawButton(canvas, rankingButton, "Classements", Color.parseColor("#FF8C00"));
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
        canvas.drawText("Distance: " + score + " m", 50, 100, paint);
        canvas.drawText("Vitesse: x" + String.format("%.1f", getEffectiveSpeed()), 50, 170, paint);

        // Bouton pause (en bas à droite, accessible au pouce)
        paint.setColor(Color.argb(200, 44, 62, 80));
        canvas.drawRoundRect(pauseButton, 16 * dp, 16 * dp, paint);
        paint.setColor(Color.WHITE);
        float cx = pauseButton.centerX(), cy = pauseButton.centerY();
        float bar = 6 * dp, h = 14 * dp;
        canvas.drawRect(cx - 9 * dp, cy - h, cx - 9 * dp + bar, cy + h, paint);
        canvas.drawRect(cx + 3 * dp, cy - h, cx + 3 * dp + bar, cy + h, paint);
    }

    private void drawPauseScreen(Canvas canvas) {
        drawGame(canvas);                              // jeu figé en arrière-plan
        canvas.drawColor(Color.argb(170, 0, 0, 0));    // voile sombre

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(80f);
        canvas.drawText("PAUSE", getWidth() / 2f, getHeight() * 0.28f, paint);

        drawButton(canvas, resumeButton, "Reprendre", Color.parseColor("#FF8C00"));
        drawButton(canvas, menuButton, "Menu", Color.parseColor("#2C3E50"));
        paint.setTextAlign(Paint.Align.LEFT);          // valeur par défaut
    }

    private void drawButton(Canvas canvas, RectF r, String label, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(r, 24 * dp, 24 * dp, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(50f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, r.centerX(), r.centerY() + 18f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawRankingScreen(Canvas canvas) {
        canvas.drawColor(Color.parseColor("#2C3E50"));
        float cx = getWidth() / 2f;

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(80f);
        canvas.drawText("CLASSEMENT", cx, getHeight() * 0.15f, paint);

        paint.setTextSize(55f);
        if (topScores.isEmpty()) {
            canvas.drawText("Aucune partie jouée", cx, getHeight() * 0.35f, paint);
        } else {
            for (int i = 0; i < topScores.size(); i++) {
                paint.setColor(i == 0 ? Color.parseColor("#FFD700") : Color.WHITE);
                canvas.drawText((i + 1) + ".  " + topScores.get(i) + " m",
                        cx, getHeight() * 0.28f + i * 90f, paint);
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);

        drawButton(canvas, rankingButton, "Retour", Color.parseColor("#FF8C00"));
    }

    private void drawGameOverScreen(Canvas canvas) {
        canvas.drawColor(Color.parseColor("#8B0000"));
        paint.setColor(Color.WHITE);
        paint.setTextSize(80f);
        canvas.drawText("CRASH !", 100, 400, paint);

        paint.setTextSize(50f);
        canvas.drawText("Distance parcourue : " + score + " m", 100, 500, paint);
        canvas.drawText("Touchez pour rejouer", 100, 700, paint);
        if (newRecord) {
            paint.setColor(Color.parseColor("#FFD700"));
            paint.setTextSize(60f);
            canvas.drawText("NOUVEAU RECORD !", 100, 600, paint);
        } else {
            paint.setColor(Color.WHITE);
            paint.setTextSize(40f);
            canvas.drawText("Record : " + bestScore + " m", 100, 600, paint);
        }
        drawButton(canvas, rankingButton, "Classements", Color.parseColor("#FF8C00"));
    }

    public boolean handleBack() {
        switch (gameState) {
            case PLAYING:
                pauseGame();
                return true;
            case PAUSED:
                resumeGame();
                return true;
            case RANKING:
                gameState = GameState.START;
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (gameState == GameState.START || gameState == GameState.GAME_OVER) {
                    boolean tooSoon = gameState == GameState.GAME_OVER
                            && SystemClock.uptimeMillis() - gameOverTime < 800;
                    if (tooSoon) {
                    } else if (rankingButton.contains(event.getX(), event.getY())) {
                        refreshScores();
                        gameState = GameState.RANKING;
                    } else {
                        resetPlayer();
                        obstacles.clear();
                        spawnCounter = 0;
                        score = 0;
                        distance = 0f;
                        speed = 1f;
                        newRecord = false;
                        resetNitro();
                        gameState = GameState.PLAYING;
                        performClick();
                    }
                } else if (gameState == GameState.RANKING) {
                    if (rankingButton.contains(event.getX(), event.getY())) {
                        gameState = GameState.START;
                    }
                } else if (gameState == GameState.PLAYING) {
                    if (pauseButton.contains(event.getX(), event.getY())) {
                        pauseGame();
                    } else {
                        downX = event.getX();
                        downY = event.getY();
                        lastTouchY = downY;
                        moved = false;
                        isSwiping = true;
                    }
                } else if (gameState == GameState.PAUSED) {
                    if (resumeButton.contains(event.getX(), event.getY())) {
                        resumeGame();
                    } else if (menuButton.contains(event.getX(), event.getY())) {
                        gameState = GameState.START;
                    }
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
        if (event != null && event.sensor != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
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
        layoutButtons();
    }

    private void layoutButtons() {
        float size = 64 * dp, margin = 16 * dp;
        pauseButton.set(getWidth() - margin - size, getHeight() - margin - size,
                getWidth() - margin, getHeight() - margin);

        float bw = getWidth() * 0.6f, bh = 72 * dp;
        float left = (getWidth() - bw) / 2f;
        resumeButton.set(left, getHeight() * 0.40f, left + bw, getHeight() * 0.40f + bh);
        menuButton.set(left, getHeight() * 0.40f + bh + 24 * dp,
                left + bw, getHeight() * 0.40f + 2 * bh + 24 * dp);
        rankingButton.set(left, getHeight() - 56 * dp - bh, left + bw, getHeight() - 56 * dp);
    }

    private void resetPlayer() {
        playerX = (getWidth() - playerSize) / 2f;
        playerY = getHeight() * 0.8f;
        tilt = 0f;
    }
}