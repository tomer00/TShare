package com.tomer.tomershare.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

public class QRProvider {

    public static Bitmap getQRBMP(String data,int eyeCol) {
        boolean[][] mat = getMATRIX(CipherUtils.performString(data));
        int size = mat.length * 20 + 80;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas g = new Canvas(bmp);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.rgb(254,251,234));
        p.setStyle(Paint.Style.FILL);

        g.drawRoundRect(0f, 0f, size, size, 32f, 32f, p);

//        p.setColor(Color.rgb(189, 4, 4));
        p.setColor(Color.parseColor("#FFFF1744"));

        int unit = 20, x = 40, y = 40;


        int w = mat.length;
        Rect rect = new Rect(0, 0, 0, 0);
        for (int i = 0; i < w; i++)
            for (int j = 0; j < w; j++)
                if (mat[i][j]) {

                    boolean top;
                    boolean bottom;

                    if (j == 0) top = false;
                    else top = mat[i][j - 1];

                    if (j == w - 1) bottom = false;
                    else bottom = mat[i][j + 1];

                    if (top) {
                        if (bottom) {
                            //both are true... // centre asset
                            g.drawRect(x + i * unit + 2, y + j * unit, x + i * unit + 18, y + j * unit + unit, p);
                        } else {
                            //only upper is true and lower is false  BottomCentre asset
                            rect.set(x + i * unit + 2, y + j * unit + 4, x + i * unit + 18, y + j * unit + 20);
                            g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), 8, p);
                            g.drawRect(x + i * unit + 2, y + j * unit, x + i * unit + 18, y + j * unit + 12, p);
                        }
                    } else {
                        if (bottom) {
                            //only upper is false but lower is true  TopCentre asset
                            rect.set(x + i * unit + 2, y + j * unit, x + i * unit + 18, y + j * unit + 16);
                            g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), 8, p);
                            g.drawRect(x + i * unit + 2, y + j * unit + 8, x + i * unit + 18, y + j * unit + 20, p);

                        } else {
                            //both are false single asset
                            rect.set(x + i * unit + 2, y + j * unit + 2, x + i * unit + 18, y + j * unit + 18);
                            g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), 8, p);
                        }
                    }
                }

        p.setColor(eyeCol);

        // draw eyes....
        int w7 = (6 * unit);

        p.setStrokeWidth(unit);

        p.setStyle(Paint.Style.STROKE);
        rect.set(x + 10, y + 10, x + 10 + w7, y + 10 + w7);
        g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), 3 * unit, p);
        p.setStyle(Paint.Style.FILL);
        g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), (3 * unit) >> 1, p);

        x = x + (mat.length - 7) * unit;
        p.setStyle(Paint.Style.STROKE);
        rect.set(x + 10, y + 10, x + 10 + w7, y + 10 + w7);
        g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), 3 * unit, p);
        p.setStyle(Paint.Style.FILL);
        g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), (3 * unit) >> 1, p);

        x = 40;
        y = y + (mat.length - 7) * unit;
        p.setStyle(Paint.Style.STROKE);
        rect.set(x + 10, y + 10, x + 10 + w7, y + 10 + w7);
        g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), 3 * unit, p);
        p.setStyle(Paint.Style.FILL);
        g.drawCircle(rect.exactCenterX(), rect.exactCenterY(), (3 * unit) >> 1, p);
        return bmp;
    }

    private static BitMatrix genQr(String data) {
        QRCodeWriter r = new QRCodeWriter();
        BitMatrix b;
        Map<EncodeHintType, ErrorCorrectionLevel> err = new HashMap<>(1);
        err.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        try {
            b = r.encode(data, BarcodeFormat.QR_CODE, 120, 120, err);
        } catch (WriterException e) {
            b = new BitMatrix(4);
        }
        return b;
    }

    private static boolean[][] getMATRIX(String data) {
        int i;
        BitMatrix bmp = genQr(data);
        int bmpDimen = bmp.getWidth();
        int unit;
        int topPoint;


        // sideways finding the first eye pixel
        for (i = 0; i < bmpDimen; i++)
            if (bmp.get(i, i)) break;
        topPoint = i;

        for (; i < bmpDimen; i++)
            if (!bmp.get(i, i)) break;
        unit = i - topPoint;


        int x;

        x = bmpDimen - (topPoint << 1);
        x /= unit;

        boolean[][] ret = new boolean[x][x];

        int r = 0, c = 0;

        int ru = bmpDimen - topPoint - unit + 1;

        for (i = topPoint; i < ru; i += unit) {
            for (int j = topPoint; j < ru; j += unit) {
                if (bmp.get(i, j)) ret[c][r] = true;
                c++;
            }
            r++;
            c = 0;
        }

        //remove eyeTopRight
        for (int k = 0; k < 7; k++)
            for (int l = x - 7; l < x; l++)
                ret[l][k] = false;


        //remove  eyeTopLeft
        for (int k = 0; k < 7; k++)
            for (int l = 0; l < 7; l++)
                ret[l][k] = false;


        //remove eyeBottomLeft
        for (int k = x - 7; k < x; k++)
            for (int l = 0; l < 7; l++)
                ret[l][k] = false;

        return ret;
    }
}
