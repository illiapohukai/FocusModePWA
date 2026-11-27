# Type-safe navigation routes and other @Serializable classes of the app.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class app.focusmodeplus.** {
    *** Companion;
}
-keepclasseswithmembers class app.focusmodeplus.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.focusmodeplus.**$$serializer { *; }

# v21 Credential Manager: keep the Play services provider that CredentialManager loads reflectively.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
