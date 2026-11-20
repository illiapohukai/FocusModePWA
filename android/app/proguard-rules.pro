# Type-safe navigation routes and other @Serializable classes of the app.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class io.github.illiapohukai.focusmode.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.illiapohukai.focusmode.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.illiapohukai.focusmode.**$$serializer { *; }
