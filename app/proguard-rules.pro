# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class com.oc.catemoji.catoc.data.model.api.** { *; }
-keep class *.R

-keepclasseswithmembers class **.R$* {
    public static <fields>;
}
# Giữ lại các lớp của Gson
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.annotations.** { *; }

# Gson giữ lại thông tin kiểu và annotation
-keepattributes Signature
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations

# Giữ lại LiveData và Retrofit
-keep class androidx.lifecycle.LiveData { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Cảnh báo liên quan đến các thư viện bên ngoài
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.io.**
-dontwarn android.content.res.**
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Giữ lại lớp và thành viên của org.xmlpull
-keep class org.xmlpull.** { *; }
-keepclassmembers class org.xmlpull.** { *; }

# Giữ lại các lớp với TypeAdapterFactory
-keepclassmembers class **$TypeAdapterFactory { <fields>; }
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener
-dontwarn android.media.LoudnessCodecController

# Glide library
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * implements com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.request.BaseRequestOptions
-keep public enum com.bumptech.glide.load.DataSource { *; }
-keep public enum com.bumptech.glide.load.EncodeStrategy { *; }

-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }

-keep class com.pfp.ocmaker.create.maker.ui.splash.SplashActivity.** {*; }
-keep class com.pfp.ocmaker.create.maker.data.** {*; }
-keep public class com.google.android.gms.** { public protected *; }
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class **.*Fragment { *; }
-keep class androidx.navigation.** { *; }
-keep class com.oc.catemoji.catoc.ui.** { *; }
-keep class dagger.hilt.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-dontwarn dagger.hilt.**

