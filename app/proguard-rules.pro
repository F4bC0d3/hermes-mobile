# Keep Compose
-keep class androidx.compose.runtime.** { *; }
# Keep kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.hermes.mobile.**$$serializer { *; }
-keepclassmembers class com.hermes.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class com.hermes.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# WebView JS bridge
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
