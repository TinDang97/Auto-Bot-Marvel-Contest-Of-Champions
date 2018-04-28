package com.trtin.autobot;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

/**
 * Created by Trung Tin on 1/30/2018.
 */

class Pixel {
    private Bitmap image;

    Pixel(){
        image = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
    }

    private synchronized Bitmap getImage() {
        return image;
    }

    synchronized void setImage(Bitmap image){
        synchronized (getImage()) {
            this.image.recycle();
            this.image = image;
        }
    }

    synchronized int getRed(int x, int y) {
        return Color.red(getImage().getPixel(x, y));
    }

    synchronized int getGreen(int x, int y) {
        return  Color.green(getImage().getPixel(x, y));
    }

    synchronized int getBlue(int x, int y) {
        return Color.blue(getImage().getPixel(x, y));
    }

}
