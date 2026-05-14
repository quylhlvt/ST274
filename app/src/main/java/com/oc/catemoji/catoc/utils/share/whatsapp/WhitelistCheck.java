package com.oc.catemoji.catoc.utils.share.whatsapp;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;


@SuppressWarnings("FieldCanBeLocal")
public class WhitelistCheck {
    public static final String CONTENT_PROVIDER_AUTHORITY = "com.oc.catemoji.catoc.utils.share.whatsapp.StickerContentProvider";
    private static final String AUTHORITY_QUERY_PARAM = "authority";
    private static final String IDENTIFIER_QUERY_PARAM = "identifier";
    private static String STICKER_APP_AUTHORITY = CONTENT_PROVIDER_AUTHORITY;
    private static String CONSUMER_WHATSAPP_PACKAGE_NAME = "com.whatsapp";
    private static String SMB_WHATSAPP_PACKAGE_NAME = "com.whatsapp.w4b";
    private static String CONTENT_PROVIDER = ".provider.sticker_whitelist_check";
    private static String QUERY_PATH = "is_whitelisted";
    private static String QUERY_RESULT_COLUMN_NAME = "result";

    static boolean isWhitelisted(@NonNull Context context, @NonNull String identifier) {
        try {
            boolean consumerResult = isWhitelistedFromProvider(context, identifier, CONSUMER_WHATSAPP_PACKAGE_NAME);
            boolean smbResult = isWhitelistedFromProvider(context, identifier, SMB_WHATSAPP_PACKAGE_NAME);
            return consumerResult || smbResult;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWhitelistedFromProvider(@NonNull Context context, @NonNull String identifier, String whatsappPackageName) {
        final PackageManager packageManager = context.getPackageManager();
        if (isPackageInstalled(whatsappPackageName, packageManager)) {
            final String whatsappProviderAuthority = whatsappPackageName + CONTENT_PROVIDER;
            final ProviderInfo providerInfo = packageManager.resolveContentProvider(whatsappProviderAuthority, PackageManager.GET_META_DATA);
            // provider is not there.
            if (providerInfo == null) {
                return false;
            }
            final Uri queryUri = new Uri.Builder().scheme(StickerContentProvider.CONTENT_SCHEME).authority(whatsappProviderAuthority).appendPath(QUERY_PATH).appendQueryParameter(AUTHORITY_QUERY_PARAM, STICKER_APP_AUTHORITY).appendQueryParameter(IDENTIFIER_QUERY_PARAM, identifier).build();
            try (final Cursor cursor = context.getContentResolver().query(queryUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    final int whiteListResult = cursor.getInt(cursor.getColumnIndexOrThrow(QUERY_RESULT_COLUMN_NAME));
                    return whiteListResult == 1;
                }
            }
        } else {
            return true;
        }
        return false;
    }

    private static boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            final ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            return applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
