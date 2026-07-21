# Preserve generic signatures and runtime annotations used by Retrofit and Gson.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Gson accesses these fields by reflection. Their serialized names remain stable
# while the containing classes can still be optimized and obfuscated.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# These Retrofit services are instantiated exclusively through java.lang.Proxy.
# Keep their interface shape so R8 full mode cannot conclude that the values
# returned by Retrofit.create() are impossible and replace them during startup.
-keep interface com.quata.core.network.wordpress.WordpressApi { *; }
-keep interface com.quata.core.network.supabase.SupabaseApi { *; }

# Vosk's JNI bridge initializes JNA by resolving several JNA classes, fields
# and methods by their original names. Obfuscating them breaks transcription
# only in minified release builds (for example Pointer.peer or Native.dispose).
-keep class com.sun.jna.** { *; }

# JNA also instantiates Vosk handle types (such as Model) through reflection;
# their no-arg constructors are otherwise removed as unused by R8.
-keep class org.vosk.** { *; }

# JNA also exposes optional desktop helpers. They are unreachable on Android,
# whose runtime intentionally does not provide java.awt.
-dontwarn java.awt.**
