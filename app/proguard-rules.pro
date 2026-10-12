# Methods called from page JavaScript (SiteBridge) must keep their names.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Optional dependencies of OkHttp / Jsoup that don't exist on Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
