package com.example.aerorush;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Dessins vectoriels du jeu (avion, oiseau, immeuble, nuage).
 * Tout est dessiné avec Path/Canvas : aucune image à importer, et le rendu
 * reste net quelle que soit la taille de l'écran.
 */
public final class Sprites {

    // Silhouette d'avion vue de dessus, nez vers le haut, centrée en (0,0), envergure 1.
    private static final float[] PLANE = {
            0f, -0.50f,
            0.07f, -0.36f,
            0.08f, -0.10f,
            0.50f, 0.08f,
            0.50f, 0.17f,
            0.08f, 0.08f,
            0.07f, 0.31f,
            0.20f, 0.42f,
            0.20f, 0.49f,
            0f, 0.44f,
            -0.20f, 0.49f,
            -0.20f, 0.42f,
            -0.07f, 0.31f,
            -0.08f, 0.08f,
            -0.50f, 0.17f,
            -0.50f, 0.08f,
            -0.08f, -0.10f,
            -0.07f, -0.36f,
    };

    private static final Path path = new Path();
    private static final RectF oval = new RectF();

    private Sprites() { }

    /** Avion de taille {@code size}, centré en (cx, cy). pointingUp=false pour un avion ennemi. */
    public static void drawPlane(Canvas c, Paint p, float cx, float cy, float size,
                                 boolean pointingUp, int body, int accent) {
        float dir = pointingUp ? 1f : -1f;

        // Ombre portée (donne du relief au-dessus du sol)
        buildPlanePath(cx + size * 0.08f, cy + size * 0.10f, size, dir);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(60, 0, 0, 0));
        c.drawPath(path, p);

        buildPlanePath(cx, cy, size, dir);
        p.setColor(body);
        c.drawPath(path, p);

        // Liseré
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.025f);
        p.setColor(Color.argb(90, 0, 0, 0));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);

        // Cockpit
        p.setColor(accent);
        float ky = cy - dir * size * 0.30f;
        oval.set(cx - size * 0.045f, ky - size * 0.07f, cx + size * 0.045f, ky + size * 0.07f);
        c.drawOval(oval, p);
    }

    private static void buildPlanePath(float cx, float cy, float size, float dir) {
        path.reset();
        for (int i = 0; i < PLANE.length; i += 2) {
            float x = cx + PLANE[i] * size;
            float y = cy + PLANE[i + 1] * size * dir;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    /** Flammes du réacteur derrière l'avion du joueur (nitro). */
    public static void drawFlame(Canvas c, Paint p, float cx, float tailY, float size, float flicker) {
        float len = size * (0.35f + 0.15f * flicker);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#FF8C00"));
        path.reset();
        path.moveTo(cx - size * 0.10f, tailY);
        path.lineTo(cx, tailY + len);
        path.lineTo(cx + size * 0.10f, tailY);
        path.close();
        c.drawPath(path, p);

        p.setColor(Color.parseColor("#FFE066"));
        path.reset();
        path.moveTo(cx - size * 0.05f, tailY);
        path.lineTo(cx, tailY + len * 0.6f);
        path.lineTo(cx + size * 0.05f, tailY);
        path.close();
        c.drawPath(path, p);
    }

    /** Oiseau en « V » qui bat des ailes ; phase sert à l'animation. */
    public static void drawBird(Canvas c, Paint p, float cx, float cy, float size, float phase) {
        float flap = (float) Math.sin(phase) * size * 0.25f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(size * 0.12f);
        p.setColor(Color.parseColor("#2B2B2B"));
        path.reset();
        path.moveTo(cx - size * 0.5f, cy - flap);
        path.quadTo(cx - size * 0.25f, cy - size * 0.2f, cx, cy + size * 0.05f);
        path.quadTo(cx + size * 0.25f, cy - size * 0.2f, cx + size * 0.5f, cy - flap);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    /** Toit d'immeuble vu de dessus. */
    public static void drawBuilding(Canvas c, Paint p, RectF r, float dp) {
        p.setStyle(Paint.Style.FILL);

        // Ombre
        p.setColor(Color.argb(70, 0, 0, 0));
        c.drawRect(r.left + 8 * dp, r.top + 10 * dp, r.right + 8 * dp, r.bottom + 10 * dp, p);

        // Toit
        p.setColor(Color.parseColor("#7F8C8D"));
        c.drawRect(r, p);

        // Rebord
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4 * dp);
        p.setColor(Color.parseColor("#5D6D6E"));
        c.drawRect(r, p);
        p.setStyle(Paint.Style.FILL);

        // Équipements sur le toit (climatiseurs, cage d'ascenseur)
        float w = r.width(), h = r.height();
        p.setColor(Color.parseColor("#95A5A6"));
        c.drawRect(r.left + w * 0.12f, r.top + h * 0.18f, r.left + w * 0.32f, r.top + h * 0.45f, p);
        c.drawRect(r.left + w * 0.62f, r.top + h * 0.55f, r.left + w * 0.86f, r.top + h * 0.82f, p);

        // Hélipad
        p.setColor(Color.parseColor("#FF8C00"));
        float hx = r.left + w * 0.62f, hy = r.top + h * 0.30f, hr = Math.min(w, h) * 0.13f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3 * dp);
        c.drawCircle(hx, hy, hr, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** Nuage simple fait de trois disques. */
    public static void drawCloud(Canvas c, Paint p, float cx, float cy, float size) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(150, 255, 255, 255));
        c.drawCircle(cx, cy, size * 0.35f, p);
        c.drawCircle(cx - size * 0.35f, cy + size * 0.08f, size * 0.25f, p);
        c.drawCircle(cx + size * 0.35f, cy + size * 0.06f, size * 0.28f, p);
    }
}
