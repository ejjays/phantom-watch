# R8 keeps for the release build

# crash reports need file/line info to be retraceable with mapping.txt
-keepattributes SourceFile,LineNumberTable

# the provider bridge is called from JavaScript by name
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# the jlibtorrent .so resolves Swig director callbacks by name
-keep class com.frostwire.jlibtorrent.** { *; }
-dontwarn com.frostwire.jlibtorrent.**

# okhttp probes for optional TLS providers that are not in the APK
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn java.lang.invoke.**
