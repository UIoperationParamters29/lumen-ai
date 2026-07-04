# Generic ProGuard rules
-dontwarn org.jetbrains.annotations.**
-keep class com.cortex.app.data.model.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
