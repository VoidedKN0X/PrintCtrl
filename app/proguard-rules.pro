# kotlinx.serialization models (library ships its own rules; these keep our DTOs and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.printctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.printctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.printctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Release builds drop verbose, debug and info logging (from the app and its libraries), so nothing
# from them can end up in the phone's log.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
