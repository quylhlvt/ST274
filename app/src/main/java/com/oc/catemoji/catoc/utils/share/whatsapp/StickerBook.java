package com.oc.catemoji.catoc.utils.share.whatsapp;


import android.content.Context;


import java.util.ArrayList;

public class StickerBook {
    public static Context myContext;
    public static ArrayList<StickerPack> allStickerPacks = checkIfPacksAreNull();

    public static void init(Context context) {
        myContext = context;
    }
    private static ArrayList<StickerPack> checkIfPacksAreNull() {
        if (allStickerPacks == null) {
            return new ArrayList<>();
        }
        return allStickerPacks;
    }
    public static boolean checkIfPackAlreadyAddedById(String id) {
        for (StickerPack stickerPack : allStickerPacks) {
            if (stickerPack.getIdentifier().equals(id)) {
                return true;
            }
        }
        return false;
    }
    public static void addPackIfNotAlreadyAdded(StickerPack sp) {
        if (!checkIfPackAlreadyAddedById(sp.getIdentifier())) {
            allStickerPacks.add(sp);
        }
    }
    public static ArrayList<StickerPack> getAllStickerPacks() {
        return allStickerPacks;
    }
    public static StickerPack getStickerPackByIdWithContext(String stickerPackId, Context context) {
        if (allStickerPacks.isEmpty()) {
            init(context);
        }
        for (StickerPack sp : allStickerPacks) {
            if (sp.getIdentifier().equals(stickerPackId)) {
                return sp;
            }
        }
        return null;
    }

}
