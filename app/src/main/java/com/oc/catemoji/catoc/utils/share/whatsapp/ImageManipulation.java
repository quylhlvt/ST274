package com.oc.catemoji.catoc.utils.share.whatsapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageManipulation {
    private static void makeSmallestTrayBitmapCompatible(String path, Bitmap bitmap) {
        int quality = 100;
        FileOutputStream outs = null;
        try {
            outs = new FileOutputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        bitmap = Bitmap.createScaledBitmap(bitmap, 96, 96, true);

        int byteArrayLength = 100000;
        ByteArrayOutputStream bos = null;

        while ((byteArrayLength / 1000) >= 50) {
            bos = new ByteArrayOutputStream();

            bitmap.compress(Bitmap.CompressFormat.WEBP,
                    quality,
                    bos);

            byteArrayLength = bos.toByteArray().length;
            quality -= 10;

        }
        try {
            outs.write(bos.toByteArray());
            outs.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private static void makeSmallestBitmapCompatible(String path, Bitmap bitmap) {
        int quality = 100;
        FileOutputStream outs = null;
        try {
            outs = new FileOutputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        bitmap = Bitmap.createScaledBitmap(bitmap, 512, 512, true);

        int byteArrayLength = 100000;
        ByteArrayOutputStream bos = null;

        while ((byteArrayLength / 1000) >= 100) {
            bos = new ByteArrayOutputStream();

            bitmap.compress(Bitmap.CompressFormat.WEBP,
                    quality,
                    bos);

            byteArrayLength = bos.toByteArray().length;
            quality -= 10;
        }
        try {
            outs.write(bos.toByteArray());
            outs.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void dirChecker(String dir) {
        File f = new File(dir);
        if (!f.isDirectory()) {
            f.mkdirs();
        }
    }
    public static Uri convertIconTrayToWebP(Uri uri, String StickerBookId, String StickerId, Context context) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);

            dirChecker(context.getFilesDir() + "/" + StickerBookId);

            String path = context.getFilesDir() + "/" + StickerBookId + "/" + StickerBookId + "-" + StickerId + ".webp";

            makeSmallestTrayBitmapCompatible(path, bitmap);

            return Uri.fromFile(new File(path));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }
    public static Uri convertImageToWebP(Uri uri, String StickerBookId, String StickerId, Context context) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);

            dirChecker(context.getFilesDir() + "/" + StickerBookId);

            String path = context.getFilesDir() + "/" + StickerBookId + "/" + StickerBookId + "-" + StickerId + ".webp";

            makeSmallestBitmapCompatible(path, bitmap);

            return Uri.fromFile(new File(path));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }
}
