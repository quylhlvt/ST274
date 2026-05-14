/*
 * Copyright (c) WhatsApp Inc. and its affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.oc.catemoji.catoc.utils.share.whatsapp;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class StickerPack implements Parcelable {
    Uri trayImageUri;
    String identifier;
    String name;
    String publisher;
    String trayImageFile;
    final String publisherEmail;
    final String publisherWebsite;
    final String privacyPolicyWebsite;
    final String licenseAgreementWebsite;
    String iosAppStoreLink;
    private List<Sticker> stickers;
    private long totalSize;
    String androidPlayStoreLink;
    private boolean isWhitelisted;
    private int stickersAddedIndex = 0;

    public StickerPack(String identifier, String name, List<Uri> stickerUris, Context context) {
        this.identifier = identifier;
        this.name = name;
        this.publisher = "_global";
        this.trayImageFile = "trayimage";
        this.trayImageUri = ImageManipulation.convertIconTrayToWebP(stickerUris.get(0), this.identifier, "trayImage", context);
        this.publisherEmail = "";
        this.publisherWebsite = "";
        this.privacyPolicyWebsite = "";
        this.licenseAgreementWebsite = "";
        this.stickers = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stickerUris.forEach(uri -> addSticker(uri, context));
        }
    }

    protected StickerPack(Parcel in) {
        identifier = in.readString();
        name = in.readString();
        publisher = in.readString();
        trayImageFile = in.readString();
        publisherEmail = in.readString();
        publisherWebsite = in.readString();
        privacyPolicyWebsite = in.readString();
        licenseAgreementWebsite = in.readString();
        iosAppStoreLink = in.readString();
        stickers = in.createTypedArrayList(Sticker.CREATOR);
        totalSize = in.readLong();
        androidPlayStoreLink = in.readString();
        isWhitelisted = in.readByte() != 0;
    }

    public static final Creator<StickerPack> CREATOR = new Creator<StickerPack>() {
        @Override
        public StickerPack createFromParcel(Parcel in) {
            return new StickerPack(in);
        }

        @Override
        public StickerPack[] newArray(int size) {
            return new StickerPack[size];
        }
    };

    public void addSticker(Uri uri, Context context) {
        String index = String.valueOf(stickersAddedIndex);
        this.stickers.add(new Sticker(
                index,
                ImageManipulation.convertImageToWebP(uri, this.identifier, index, context),
                new ArrayList<>()));
        stickersAddedIndex++;
    }

    public Sticker getStickerById(int index) {
        for (Sticker s : this.stickers) {
            if (s.getImageFileName().equals(String.valueOf(index))) {
                return s;
            }
        }
        return null;
    }

    public List<Sticker> getStickers() {
        return stickers;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(identifier);
        dest.writeString(name);
        dest.writeString(publisher);
        dest.writeString(trayImageFile);
        dest.writeString(publisherEmail);
        dest.writeString(publisherWebsite);
        dest.writeString(privacyPolicyWebsite);
        dest.writeString(licenseAgreementWebsite);
        dest.writeString(iosAppStoreLink);
        dest.writeTypedList(stickers);
        dest.writeLong(totalSize);
        dest.writeString(androidPlayStoreLink);
        dest.writeByte((byte) (isWhitelisted ? 1 : 0));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    public Uri getTrayImageUri() {
        return trayImageUri;
    }
}
