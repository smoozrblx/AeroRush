package com.example.aerorush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class GameView extends View implements SensorEventListener {

    public enum GameState { START, PLAYING, PAUSED, RANKING, GAME_OVER }

    private static final int SKY_TOP = Color.parseColor("#2F6BFF");
    private static final int SKY = Color.parseColor("#5B8DFF");
    private static final int ORANGE = Color.parseColor("#FF8C00");
    private static final int NAVY = Color.parseColor("#1B2A4A");
    private static final int GOLD = Color.parseColor("#FFD700");

    private static final float ALPHA = 0.8f;
    private static final float DEAD_ZONE = 0.6f;
    private static final float MAX_TILT = 7f;

    private static final float MIN_SPEED = 0.6f;
    private static final float MAX_SPEED = 2.2f;

    private static final long DOUBLE_TAP_MS = 300;
    private static final long NITRO_DURATION_MS = 2000;
    private static final long NITRO_COOLDOWN_MS = 5000;
    private static final float NITRO_MULTIPLIER = 2f;

    private static final long GAME_OVER_GUARD_MS = 800;
    private static final int TUTORIAL_FRAMES = 3 * 180;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float dp;
    private final int touchSlop;

    private final FeedbackManager feedback;
    private final ScoreManager scoreManager;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int displayRotation = Surface.ROTATION_0;

    private GameState gameState = GameState.START;

    private float playerX, playerY, playerSize;
    private final RectF playerHitbox = new RectF();
    private float tilt = 0f;

    private float speed = 1f;
    private float distance = 0f;
    private int score = 0;
    private int playFrames = 0;
    private float spawnAccumulator = 0f;

    private long lastTapTime = 0, nitroEndTime = 0, nitroReadyTime = 0;

    private float downX, downY, lastTouchY;
    private boolean moved = false, isSwiping = false;

    private final List<Obstacle> obstacles = new ArrayList<>();
    private final float[][] clouds = new float[6][3];
    private final float[][] streaks = new float[14][2];

    private long pauseStartTime = 0, gameOverTime = 0;
    private float crashX, crashY;
    private List<Integer> topScores = new ArrayList<>();
    private int bestScore = 0;
    private boolean newRecord = false;
    private boolean showTutorial;

    private final RectF pauseButton = new RectF();
    private final RectF resumeButton = new RectF();
    private final RectF menuButton = new RectF();
    private final RectF bottomButton = new RectF();
    private final RectF helpButton = new RectF();

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
        feedback = new FeedbackManager(context);
        scoreManager = new ScoreManager(context);
        showTutorial = !scoreManager.isTutorialDone();
        refreshScores();

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        setKeepScreenOn(true);
    }

    public void startGame() {
        removeCallbacks(gameLoop);
        post(gameLoop);
    }

    public void stopGame() {
        removeCallbacks(gameLoop);
    }

    public void startSensor() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stopSensor() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    public void release() {
        feedback.release();
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

    private void newGame() {
        playerX = getWidth() / 2f;
        playerY = getHeight() * 0.78f;
        tilt = 0f;
        speed = 1f;
        distance = 0f;
        score = 0;
        playFrames = 0;
        spawnAccumulator = 0f;
        newRecord = false;
        obstacles.clear();
        nitroEndTime = nitroReadyTime = lastTapTime = 0;
        gameState = GameState.PLAYING;
    }

    public void pauseGame() {
        if (gameState != GameState.PLAYING) return;
        gameState = GameState.PAUSED;
        pauseStartTime = SystemClock.uptimeMillis();
        isSwiping = false;
    }

    private void resumeGame() {
        long pausedFor = SystemClock.uptimeMillis() - pauseStartTime;
        if (nitroEndTime > 0) nitroEndTime += pausedFor;
        if (nitroReadyTime > 0) nitroReadyTime += pausedFor;
        tilt = 0f;
        lastTapTime = 0;
        gameState = GameState.PLAYING;
    }

    private void triggerGameOver() {
        if (gameState != GameState.PLAYING) return;
        gameState = GameState.GAME_OVER;
        gameOverTime = SystemClock.uptimeMillis();
        crashX = playerX;
        crashY = playerY;
        newRecord = scoreManager.addScore(score);
        refreshScores();
        if (showTutorial) {
            showTutorial = false;
            scoreManager.setTutorialDone(true);
        }
        feedback.onCrash();
    }

    private void refreshScores() {
        topScores = scoreManager.getTopScores();
        bestScore = topScores.isEmpty() ? 0 : topScores.get(0);
    }

    private boolean isNitroActive() {
        return SystemClock.uptimeMillis() < nitroEndTime;
    }

    public float getEffectiveSpeed() {
        return isNitroActive() ? speed * NITRO_MULTIPLIER : speed;
    }

    private float nitroCharge() {
        if (nitroReadyTime == 0) return 1f;
        float elapsed = SystemClock.uptimeMillis() - (nitroReadyTime - NITRO_COOLDOWN_MS);
        return Math.max(0f, Math.min(1f, elapsed / NITRO_COOLDOWN_MS));
    }

    private void tryNitro() {
        long now = SystemClock.uptimeMillis();
        if (now >= nitroReadyTime) {
            nitroEndTime = now + NITRO_DURATION_MS;
            nitroReadyTime = now + NITRO_COOLDOWN_MS;
            feedback.onNitro();
        } else {
            feedback.onNitroDenied();
        }
    }

    private boolean isTutorialActive() {
        return showTutorial && playFrames < TUTORIAL_FRAMES;
    }

    private void update() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        if (gameState != GameState.PLAYING) {
            if (gameState == GameState.START) scrollScenery(h * 0.004f, w, h);
            return;
        }

        playFrames++;
        float difficulty = 1f + Math.min(score / 4000f, 0.8f);
        float dy = h * 0.006f * difficulty * getEffectiveSpeed();

        distance += getEffectiveSpeed() * difficulty;
        score = (int) (distance / 2f);

        float t = Math.abs(tilt) - DEAD_ZONE;
        if (t > 0) {
            t = Math.min(t, MAX_TILT);
            playerX += Math.signum(tilt) * t * w * 0.0045f;
        }
        float half = playerSize / 2f;
        playerX = Math.max(half, Math.min(w - half, playerX));
        playerHitbox.set(playerX - playerSize * 0.30f, playerY - playerSize * 0.40f,
                playerX + playerSize * 0.30f, playerY + playerSize * 0.35f);

        scrollScenery(dy, w, h);

        if (!isTutorialActive()) {
            spawnAccumulator += dy;
            float gap = h * (0.40f - 0.14f * Math.min(score / 2500f, 1f));
            if (spawnAccumulator >= gap) {
                spawnAccumulator = 0f;
                spawnObstacle(w);
            }
        }

        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle o = it.next();
            o.update(dy, w);
            if (o.collidesWith(playerHitbox)) {
                triggerGameOver();
                return;
            }
            if (o.isOffScreen(h)) it.remove();
        }
    }

    private void spawnObstacle(int w) {
        int roll = random.nextInt(100);
        Obstacle.Type type;
        if (score > 300 && roll < 25) type = Obstacle.Type.PLANE;
        else if (score > 120 && roll < 50) type = Obstacle.Type.BIRD;
        else type = Obstacle.Type.BUILDING;

        float ow, oh, vx = 0f;
        switch (type) {
            case PLANE:
                ow = oh = playerSize * 0.95f;
                vx = (random.nextFloat() - 0.5f) * 2f * dp;
                break;
            case BIRD:
                ow = playerSize * 0.55f;
                oh = ow * 0.5f;
                vx = (random.nextBoolean() ? 1 : -1) * (1.5f + random.nextFloat() * 1.5f) * dp;
                break;
            default:
                ow = w * (0.25f + random.nextFloat() * 0.27f);
                oh = ow * (0.55f + random.nextFloat() * 0.3f);
        }
        float x = random.nextFloat() * (w - ow);
        obstacles.add(new Obstacle(type, x, -oh - 10 * dp, ow, oh, vx));
    }

    private void scrollScenery(float dy, int w, int h) {
        for (float[] c : clouds) {
            c[1] += dy * 0.5f;
            if (c[1] - c[2] > h) resetCloud(c, w, false);
        }
        for (float[] s : streaks) {
            s[1] += dy * 2.2f;
            if (s[1] > h) {
                s[0] = random.nextFloat() * w;
                s[1] = -random.nextFloat() * h * 0.3f;
            }
        }
    }

    private void resetCloud(float[] c, int w, boolean anywhere) {
        c[2] = w * (0.18f + random.nextFloat() * 0.18f);
        c[0] = random.nextFloat() * w;
        c[1] = anywhere ? random.nextFloat() * getHeight() : -c[2] - random.nextFloat() * getHeight() * 0.5f;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float raw;
        switch (displayRotation) {
            case Surface.ROTATION_90:  raw = event.values[1];  break;
            case Surface.ROTATION_180: raw = event.values[0];  break;
            case Surface.ROTATION_270: raw = -event.values[1]; break;
            default:                   raw = -event.values[0];
        }
        tilt = ALPHA * tilt + (1 - ALPHA) * raw;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Display d = getDisplay();
        displayRotation = d != null ? d.getRotation() : Surface.ROTATION_0;

        playerSize = Math.max(56 * dp, Math.min(w * 0.17f, 110 * dp));
        playerX = w / 2f;
        playerY = h * 0.78f;

        for (float[] c : clouds) resetCloud(c, w, true);
        for (float[] s : streaks) {
            s[0] = random.nextFloat() * w;
            s[1] = random.nextFloat() * h;
        }

        float size = 64 * dp, margin = 16 * dp;
        pauseButton.set(w - margin - size, h - margin - size, w - margin, h - margin);
        helpButton.set(w - margin - 52 * dp, margin + 8 * dp, w - margin, margin + 60 * dp);

        float bw = w * 0.7f, bh = 68 * dp, left = (w - bw) / 2f;
        resumeButton.set(left, h * 0.55f, left + bw, h * 0.55f + bh);
        menuButton.set(left, resumeButton.bottom + 20 * dp, left + bw, resumeButton.bottom + 20 * dp + bh);
        bottomButton.set(left, h - 40 * dp - bh, left + bw, h - 40 * dp);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (gameState) {
            case START:     drawStartScreen(canvas); break;
            case PLAYING:   drawGame(canvas); break;
            case PAUSED:    drawPauseScreen(canvas); break;
            case RANKING:   drawRankingScreen(canvas); break;
            case GAME_OVER: drawGameOverScreen(canvas); break;
        }
    }

    private void drawBackground(Canvas c) {
        c.drawColor(SKY);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SKY_TOP);
        c.drawRect(0, 0, getWidth(), getHeight() * 0.12f, paint);
        for (float[] cl : clouds) Sprites.drawCloud(c, paint, cl[0], cl[1], cl[2]);
    }

    private void drawWorld(Canvas c, boolean animate) {
        drawBackground(c);

        float eff = getEffectiveSpeed();
        int alpha = (int) Math.min(170, Math.max(0, (eff - 0.8f) * 110));
        if (alpha > 0) {
            paint.setColor(Color.argb(alpha, 255, 255, 255));
            paint.setStrokeWidth(2 * dp);
            float len = 30 * dp * eff;
            for (float[] s : streaks) c.drawLine(s[0], s[1], s[0], s[1] + len, paint);
        }

        for (Obstacle o : obstacles) o.draw(c, paint, dp);

        float bank = Math.max(-14f, Math.min(14f, tilt * 2f));
        c.save();
        c.rotate(bank, playerX, playerY);
        if (isNitroActive()) {
            float flicker = animate ? random.nextFloat() : 0.5f;
            float tailY = playerY + playerSize * 0.44f;
            Sprites.drawFlame(c, paint, playerX - playerSize * 0.12f, tailY, playerSize, flicker);
            Sprites.drawFlame(c, paint, playerX + playerSize * 0.12f, tailY, playerSize, 1f - flicker);
        }
        Sprites.drawPlane(c, paint, playerX, playerY, playerSize, true,
                Color.WHITE, isNitroActive() ? ORANGE : SKY_TOP);
        c.restore();
    }

    private void drawGame(Canvas c) {
        drawWorld(c, true);
        drawHud(c);
        if (isTutorialActive()) drawTutorial(c);
        drawPauseButton(c);
    }

    private void drawHud(Canvas c) {
        float m = 20 * dp, top = 44 * dp;

        text(c, score + " m", m, top, 32, Color.WHITE, Paint.Align.LEFT, true);
        text(c, "Record " + Math.max(bestScore, score) + " m", m, top + 24 * dp, 14,
                Color.argb(220, 255, 255, 255), Paint.Align.LEFT, false);

        text(c, String.format(Locale.FRANCE, "x%.1f", getEffectiveSpeed()), getWidth() - m, top, 26,
                isNitroActive() ? GOLD : Color.WHITE, Paint.Align.RIGHT, true);
        text(c, "vitesse", getWidth() - m, top + 20 * dp, 13,
                Color.argb(220, 255, 255, 255), Paint.Align.RIGHT, false);

        float bx = m, by = top + 40 * dp, bw = 120 * dp, bh = 10 * dp;
        float charge = isNitroActive() ? 1f : nitroCharge();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(90, 0, 0, 0));
        c.drawRoundRect(bx, by, bx + bw, by + bh, bh / 2, bh / 2, paint);
        paint.setColor(isNitroActive() ? GOLD : (charge >= 1f ? ORANGE : Color.argb(200, 255, 255, 255)));
        c.drawRoundRect(bx, by, bx + bw * charge, by + bh, bh / 2, bh / 2, paint);
        String label = isNitroActive() ? "NITRO !" : (charge >= 1f ? "Nitro prêt" : "Recharge…");
        text(c, label, bx + bw + 10 * dp, by + bh, 13, Color.WHITE, Paint.Align.LEFT, isNitroActive());
    }

    private void drawPauseButton(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(170, 27, 42, 74));
        c.drawRoundRect(pauseButton, 18 * dp, 18 * dp, paint);
        paint.setColor(Color.WHITE);
        float cx = pauseButton.centerX(), cy = pauseButton.centerY();
        float bar = 6 * dp, h = 13 * dp;
        c.drawRoundRect(cx - 9 * dp, cy - h, cx - 9 * dp + bar, cy + h, 2 * dp, 2 * dp, paint);
        c.drawRoundRect(cx + 3 * dp, cy - h, cx + 3 * dp + bar, cy + h, 2 * dp, 2 * dp, paint);
    }

    private void drawTutorial(Canvas c) {
        int step = playFrames / 180;
        float t = (playFrames % 180) / 60f;
        float w = getWidth(), cx = w / 2f, cy = getHeight() * 0.40f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 27, 42, 74));
        c.drawRoundRect(w * 0.08f, cy - 110 * dp, w * 0.92f, cy + 90 * dp, 24 * dp, 24 * dp, paint);

        String title;
        if (step == 0) {
            title = "Inclinez pour diriger l'avion";
            float angle = (float) Math.sin(t * 2.5f) * 22f;
            c.save();
            c.rotate(angle, cx, cy);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4 * dp);
            paint.setColor(Color.WHITE);
            c.drawRoundRect(cx - 28 * dp, cy - 50 * dp, cx + 28 * dp, cy + 50 * dp, 10 * dp, 10 * dp, paint);
            paint.setStyle(Paint.Style.FILL);
            c.restore();
            text(c, "◀", cx - 70 * dp, cy + 10 * dp, 28, ORANGE, Paint.Align.CENTER, true);
            text(c, "▶", cx + 70 * dp, cy + 10 * dp, 28, ORANGE, Paint.Align.CENTER, true);
        } else if (step == 1) {
            title = "Glissez ↑ accélérer · ↓ ralentir";
            paint.setColor(Color.argb(120, 255, 255, 255));
            paint.setStrokeWidth(6 * dp);
            c.drawLine(cx, cy - 55 * dp, cx, cy + 55 * dp, paint);
            float fy = cy + (float) Math.sin(t * 2.2f) * 50 * dp;
            paint.setColor(ORANGE);
            c.drawCircle(cx, fy, 16 * dp, paint);
        } else {
            title = "Double-tap n'importe où : NITRO";
            float cycle = t % 1f;
            boolean second = cycle > 0.35f && cycle < 0.6f;
            float r = 18 * dp + (cycle < 0.6f ? (cycle % 0.35f) * 60 * dp : 0);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4 * dp);
            paint.setColor(second ? GOLD : ORANGE);
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, 12 * dp, paint);
        }
        text(c, title, cx, cy - 75 * dp, 18, Color.WHITE, Paint.Align.CENTER, true);

        for (int i = 0; i < 3; i++) {
            paint.setColor(i == step ? ORANGE : Color.argb(120, 255, 255, 255));
            c.drawCircle(cx + (i - 1) * 18 * dp, cy + 72 * dp, 5 * dp, paint);
        }
    }

    private void drawStartScreen(Canvas c) {
        drawBackground(c);
        float cx = getWidth() / 2f, h = getHeight();

        paint.setTextSize(52 * dp);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        float wAero = paint.measureText("Aero"), wRush = paint.measureText("Rush");
        float x0 = cx - (wAero + wRush) / 2f;
        text(c, "Aero", x0, h * 0.22f, 52, Color.WHITE, Paint.Align.LEFT, true);
        text(c, "Rush", x0 + wAero, h * 0.22f, 52, ORANGE, Paint.Align.LEFT, true);

        float bob = (float) Math.sin(SystemClock.uptimeMillis() / 400.0) * 8 * dp;
        Sprites.drawPlane(c, paint, cx, h * 0.42f + bob, Math.max(playerSize * 1.6f, 120 * dp), true, Color.WHITE, SKY_TOP);

        int a = (int) (170 + 85 * Math.sin(SystemClock.uptimeMillis() / 300.0));
        text(c, "Touchez pour décoller", cx, h * 0.64f, 22, Color.argb(a, 255, 255, 255), Paint.Align.CENTER, true);
        text(c, "Meilleure distance : " + bestScore + " m", cx, h * 0.70f, 16, Color.WHITE, Paint.Align.CENTER, false);

        drawButton(c, bottomButton, "Classements", NAVY);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 27, 42, 74));
        c.drawRoundRect(helpButton, 14 * dp, 14 * dp, paint);
        text(c, "?", helpButton.centerX(), helpButton.centerY() + 9 * dp, 26,
                showTutorial ? ORANGE : Color.WHITE, Paint.Align.CENTER, true);
    }

    private void drawPauseScreen(Canvas c) {
        drawWorld(c, false);
        drawHud(c);
        c.drawColor(Color.argb(170, 0, 0, 0));
        text(c, "PAUSE", getWidth() / 2f, getHeight() * 0.40f, 44, Color.WHITE, Paint.Align.CENTER, true);
        drawButton(c, resumeButton, "Reprendre", ORANGE);
        drawButton(c, menuButton, "Menu", NAVY);
    }

    private void drawGameOverScreen(Canvas c) {
        drawWorld(c, false);

        float t = Math.min(1f, (SystemClock.uptimeMillis() - gameOverTime) / 450f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (220 * (1 - t)), 255, 140, 0));
        c.drawCircle(crashX, crashY, playerSize * (0.4f + 1.4f * t), paint);
        paint.setColor(Color.argb((int) (255 * (1 - t)), 255, 230, 100));
        c.drawCircle(crashX, crashY, playerSize * (0.2f + 0.8f * t), paint);

        c.drawColor(Color.argb((int) (190 * t), 90, 0, 0));
        if (t < 1f) return;

        float cx = getWidth() / 2f, h = getHeight();
        text(c, "CRASH !", cx, h * 0.24f, 46, Color.WHITE, Paint.Align.CENTER, true);
        text(c, score + " m", cx, h * 0.36f, 56, Color.WHITE, Paint.Align.CENTER, true);
        if (newRecord) {
            text(c, "NOUVEAU RECORD !", cx, h * 0.44f, 24, GOLD, Paint.Align.CENTER, true);
        } else {
            text(c, "Record : " + bestScore + " m", cx, h * 0.44f, 18, Color.WHITE, Paint.Align.CENTER, false);
        }
        boolean ready = SystemClock.uptimeMillis() - gameOverTime >= GAME_OVER_GUARD_MS;
        text(c, "Touchez pour rejouer", cx, h * 0.60f, 22,
                ready ? Color.WHITE : Color.argb(80, 255, 255, 255), Paint.Align.CENTER, true);
        drawButton(c, bottomButton, "Classements", NAVY);
    }

    private void drawRankingScreen(Canvas c) {
        drawBackground(c);
        c.drawColor(Color.argb(120, 27, 42, 74));
        float cx = getWidth() / 2f, h = getHeight();
        text(c, "CLASSEMENT", cx, h * 0.15f, 38, Color.WHITE, Paint.Align.CENTER, true);

        if (topScores.isEmpty()) {
            text(c, "Aucune partie jouée", cx, h * 0.35f, 20, Color.WHITE, Paint.Align.CENTER, false);
        } else {
            for (int i = 0; i < topScores.size(); i++) {
                float y = h * 0.28f + i * 64 * dp;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(i == 0 ? 200 : 110, 27, 42, 74));
                c.drawRoundRect(getWidth() * 0.15f, y - 38 * dp, getWidth() * 0.85f, y + 16 * dp, 16 * dp, 16 * dp, paint);
                text(c, (i + 1) + ".", getWidth() * 0.22f, y, 24, i == 0 ? GOLD : Color.WHITE, Paint.Align.LEFT, true);
                text(c, topScores.get(i) + " m", getWidth() * 0.78f, y, 24, i == 0 ? GOLD : Color.WHITE, Paint.Align.RIGHT, true);
            }
        }
        drawButton(c, bottomButton, "Retour", ORANGE);
    }

    private void drawButton(Canvas c, RectF r, String label, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(r, 22 * dp, 22 * dp, paint);
        text(c, label, r.centerX(), r.centerY() + 8 * dp, 22, Color.WHITE, Paint.Align.CENTER, true);
    }

    private void text(Canvas c, String s, float x, float y, float sizeDp, int color, Paint.Align align, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(sizeDp * dp);
        paint.setColor(color);
        paint.setTextAlign(align);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        c.drawText(s, x, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                switch (gameState) {
                    case START:
                        if (bottomButton.contains(x, y)) {
                            gameState = GameState.RANKING;
                        } else if (helpButton.contains(x, y)) {
                            showTutorial = !showTutorial;
                            scoreManager.setTutorialDone(!showTutorial);
                        } else {
                            newGame();
                            performClick();
                        }
                        feedback.onClick();
                        break;

                    case GAME_OVER:
                        if (SystemClock.uptimeMillis() - gameOverTime < GAME_OVER_GUARD_MS) break;
                        if (bottomButton.contains(x, y)) {
                            gameState = GameState.RANKING;
                        } else {
                            newGame();
                        }
                        feedback.onClick();
                        break;

                    case RANKING:
                        if (bottomButton.contains(x, y)) {
                            gameState = GameState.START;
                            feedback.onClick();
                        }
                        break;

                    case PAUSED:
                        if (resumeButton.contains(x, y)) {
                            resumeGame();
                            feedback.onClick();
                        } else if (menuButton.contains(x, y)) {
                            gameState = GameState.START;
                            feedback.onClick();
                        }
                        break;

                    case PLAYING:
                        if (pauseButton.contains(x, y)) {
                            pauseGame();
                            feedback.onClick();
                        } else {
                            downX = x;
                            downY = y;
                            lastTouchY = y;
                            moved = false;
                            isSwiping = true;
                        }
                        break;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (gameState == GameState.PLAYING && isSwiping) {
                    if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) moved = true;
                    if (moved) {
                        float dyRatio = (lastTouchY - y) / (getHeight() * 0.6f);
                        speed += dyRatio * (MAX_SPEED - MIN_SPEED);
                        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
                    }
                    lastTouchY = y;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (gameState == GameState.PLAYING && isSwiping && !moved) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTapTime <= DOUBLE_TAP_MS) {
                        tryNitro();
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
}