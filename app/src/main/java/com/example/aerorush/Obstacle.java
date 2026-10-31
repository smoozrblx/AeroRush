package com.example.aerorush;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Un obstacle : immeuble, avion ennemi ou oiseau.
 * Sa chute dépend du défilement fourni par GameView (vitesse du joueur, nitro, difficulté).
 */
public class Obstacle {

    public enum Type { BUILDING, PLANE, BIRD }

    public final Type type;
    private final RectF bounds = new RectF();
    private final RectF hitbox = new RectF();
    private final float fallFactor;   // un avion ennemi vient vers nous : il descend plus vite
    private float vx;                 // dérive latérale (oiseaux, avions)
    private float phase;              // animation (battement d'ailes)

    public Obstacle(Type type, float x, float y, float w, float h, float vx) {
        this.type = type;
        this.bounds.set(x, y, x + w, y + h);
        this.vx = vx;
        this.fallFactor = type == Type.PLANE ? 1.45f : 1f;
        this.phase = (float) (Math.random() * Math.PI * 2);
    }

    /** dy = défilement du décor pendant cette image, en pixels. */
    public void update(float dy, int screenWidth) {
        bounds.offset(vx, dy * fallFactor);
        if (bounds.left < 0 || bounds.right > screenWidth) {
            vx = -vx;   // rebond sur les bords
            bounds.offset(vx * 2, 0);
        }
        phase += type == Type.BIRD ? 0.35f : 0f;
    }

    /**
     * Collision avec une hitbox réduite : on ne compte que le « cœur » de l'obstacle.
     * Un crash ressenti comme injuste (frôlement d'un pixel) frustre davantage qu'un léger
     * manque de rigueur.
     */
    public boolean collidesWith(RectF player) {
        float insetX = bounds.width() * (type == Type.BUILDING ? 0.04f : 0.22f);
        float insetY = bounds.height() * (type == Type.BUILDING ? 0.04f : 0.22f);
        hitbox.set(bounds.left + insetX, bounds.top + insetY,
                bounds.right - insetX, bounds.bottom - insetY);
        return RectF.intersects(hitbox, player);
    }

    public boolean isOffScreen(int screenHeight) {
        return bounds.top > screenHeight;
    }

    public float centerX() {
        return bounds.centerX();
    }

    public float centerY() {
        return bounds.centerY();
    }

    public void draw(Canvas canvas, Paint paint, float dp) {
        switch (type) {
            case BUILDING:
                Sprites.drawBuilding(canvas, paint, bounds, dp);
                break;
            case PLANE:
                Sprites.drawPlane(canvas, paint, bounds.centerX(), bounds.centerY(), bounds.width(),
                        false, Color.parseColor("#C0392B"), Color.parseColor("#2C3E50"));
                break;
            case BIRD:
                Sprites.drawBird(canvas, paint, bounds.centerX(), bounds.centerY(), bounds.width(), phase);
                break;
        }
    }
}
