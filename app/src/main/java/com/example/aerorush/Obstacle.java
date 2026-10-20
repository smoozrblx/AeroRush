package com.example.aerorush;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Obstacle {

    private final float posX;
    private float posY;
    private final float width;
    private final float height;
    private final float speed;

    public Obstacle(float posX, float posY, float width, float height, float speed) {
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
        this.speed = speed;
    }

    public void update() {
        posY += speed;
    }

    public boolean isOffScreen(float screenHeight) {
        return posY > screenHeight;
    }

    public boolean collidesWith(float pX, float pY, float pSize) {
        return pX < posX + width &&
                pX + pSize > posX &&
                pY < posY + height &&
                pY + pSize > posY;
    }

    public void draw(Canvas canvas, Paint paint) {
        paint.setColor(Color.parseColor("#555555"));
        canvas.drawRect(posX, posY, posX + width, posY + height, paint);
    }
}