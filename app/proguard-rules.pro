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
