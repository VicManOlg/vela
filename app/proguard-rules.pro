# Vela shrinker rules. Room, Hilt, Coil, OkHttp and Paging ship their own consumer rules.

# Readable stack traces from optimised builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepnames class ** extends java.lang.Throwable

# kotlinx.serialization: type-safe navigation routes and the settings/theme/catalog JSON look up
# serializers through the generated Companion.serializer() and $$serializer classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { static <1>$Companion Companion; }
-if @kotlinx.serialization.Serializable class ** { static **$* *; }
-keepclassmembers class <2>$<3> { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class ** { public static ** INSTANCE; }
-keepclassmembers class <1> { public static <1> INSTANCE; kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.vela.**$$serializer { *; }
-keepclassmembers class io.vela.** { *** Companion; }
-keepclasseswithmembers class io.vela.** { kotlinx.serialization.KSerializer serializer(...); }
