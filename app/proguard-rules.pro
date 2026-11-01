# kotlinx.serialization ships its own consumer rules. Ktor server (CIO + WebSockets) is plain
# Kotlin on coroutines and reaches its engine through ServiceLoader-free code, but it
# names a few JVM-only classes that Android does not have; they are never reached at runtime.
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.slf4j.**
-dontwarn io.netty.**
-dontwarn reactor.blockhound.**
-dontwarn kotlinx.coroutines.debug.**

# Ktor looks its engine factory and some plugins up by class name.
-keep class io.ktor.server.cio.** { *; }
-keep class io.ktor.server.engine.** { *; }
-keep class io.ktor.server.websocket.** { *; }
-keep class io.ktor.websocket.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }

# The wire protocol is serialized by class name in the "t" field.
-keep,includedescriptorclasses class com.fabriquefutur.qupa.protocol.**$$serializer { *; }
-keepclassmembers class com.fabriquefutur.qupa.protocol.** {
    *** Companion;
}
-keepclasseswithmembers class com.fabriquefutur.qupa.protocol.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.fabriquefutur.qupa.protocol.** { *; }
