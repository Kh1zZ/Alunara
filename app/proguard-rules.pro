# Alunara R8 / ProGuard keep rules for Android TV (API 28+, 1 GB RAM)

# Compose 60 FPS R8 Optimization: Strip sourceInformation debugging traces in release builds
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    void sourceInformation(androidx.compose.runtime.Composer, java.lang.String);
    void sourceInformationMarkerStart(androidx.compose.runtime.Composer, int, java.lang.String);
    void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
}
-allowaccessmodification

# Preserve readable file names and line numbers in crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations

# Kotlinx Serialization (ArchiveTune InnerTube & Lyrics providers)
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class moe.rukamori.archivetune.innertube.models.** { *; }
-keep,includedescriptorclasses class moe.rukamori.archivetune.kugou.models.** { *; }
-keep,includedescriptorclasses class moe.rukamori.archivetune.lrclib.models.** { *; }
-keep,includedescriptorclasses class moe.rukamori.archivetune.simpmusic.models.** { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclasseswithmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    kotlinx.serialization.KSerializer* $serializer;
}
-keep class <1>$$serializer { *; }

# Kuromoji IPADIC morphological analyzer & binary dictionary loaders
-keep class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**

# Android ICU Transliterator reflection bridge (LyricsRomanizer)
-dontwarn android.icu.text.Transliterator
-keep class android.icu.text.Transliterator {
    public static android.icu.text.Transliterator getInstance(java.lang.String);
    public java.lang.String transliterate(java.lang.String);
}

# Ktor Client, OkHttp, DNS-over-HTTPS & Brotli
-keep class io.ktor.client.HttpClientEngineContainer
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.client.engine.cio.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.brotli.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**

# NewPipe Extractor & Mozilla Rhino JS engine
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**
-dontwarn java.awt.**
-dontwarn javax.naming.**
-dontwarn javax.beans.**

# Room Database & Media3 PlaybackService
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.alunara.core.data.db.** { *; }
-keep class com.alunara.app.playback.AlunaraPlaybackService { *; }
