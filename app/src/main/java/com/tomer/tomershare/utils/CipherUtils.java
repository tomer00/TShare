package com.tomer.tomershare.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class CipherUtils {
    private static ByteBuffer saltBytes;
    private static int a, size = 32;

    //region INITIALIZATION----->>>

    private static void init() {
        String ps = "pbo1emrbv8nbr6yqeurbmco4iune5gr9";

        byte b = (byte) ps.charAt(0);
        b = (byte) (Math.abs(b) % size);

        a = (size - b) >> 1;
        byte[] sby = new byte[size];

        // initializing the salt-bytes wth rotation...
        int t = size - a;
        for (int i = 0; i < t; i++) {
            sby[i] = (byte) ps.charAt(i + a);
            sby[i + a] = (byte) ps.charAt(i);
        }

        // this shield be perfect multiple of 4...
        saltBytes = ByteBuffer.wrap(sby);

        a = a >> 2;
        size = size >> 2;
    }

    //endregion INITIALIZATION----->>>

    //region STRING FUNCTIONS---------->>>

    public static String performString(String data) {

        if (saltBytes == null)
            init();

        ByteArrayInputStream bins = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream bois = new ByteArrayOutputStream();

        try {
            int i = a;
            byte[] b = new byte[4];
            int n;
            while ((n = bins.read(b)) != -1) {
                formInt(fromBytes(b) ^ saltBytes.getInt(i++), b);
                bois.write(b, 0, n);

                if (i == size) i = 0;
            }
            return new String(bois.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                bois.flush();
                bois.close();
                bins.close();
            } catch (IOException ignored) {
            }
        }

    }


    //endregion STRING FUNCTIONS---------->>>

    //region HELPER FUNCTIONS-----

    private static int fromBytes(byte[] bytes) {
        int pos = 0;
        return ((0xFF & bytes[pos++]) << 24) | ((0xFF & bytes[pos++]) << 16) |
                ((0xFF & bytes[pos++]) << 8) | (0xFF & bytes[pos]);
    }

    private static void formInt(int value, byte[] _bytes) {
        _bytes[0] = (byte) (value >> 24);
        _bytes[1] = (byte) (value >> 16);
        _bytes[2] = (byte) (value >> 8);
        _bytes[3] = (byte) value;
    }

    //endregion HELPER FUNCTIONS-----
}