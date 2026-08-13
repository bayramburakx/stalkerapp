# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.stalkerapp.**$$serializer { *; }
-keepclassmembers class com.stalkerapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.stalkerapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
