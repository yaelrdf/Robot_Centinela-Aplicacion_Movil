# Reglas de R8 para los builds release y demo.
#
# kotlinx.serialization: el plugin del compilador genera los serializadores de los DTO. Se conservan
# los companion y los `serializer()` de las clases propias para que la reflexión interna los encuentre.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-keepclassmembers class com.robotcentinela.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.robotcentinela.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.robotcentinela.app.**$$serializer { *; }

# Ktor y supabase-kt referencian SLF4J y APIs de JVM de escritorio que no existen en Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
