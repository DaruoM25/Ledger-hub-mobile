# ==============================================================================
# Règles ProGuard / R8 — LedgerHub Mobile (Protection Anti-Reverse & Robustesse)
# ==============================================================================

# ── 1. Attributs généraux & Réflexion ─────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, SourceFile, LineNumberTable

# Dé-anonymisation sécurisée des stacktraces dans les rapports d'erreur
-renamesourcefileattribute SourceFile

# ── 2. kotlinx.serialization ──────────────────────────────────────────────────
# Préservation des classes sérialisables, de leurs Companion et des serializers générés
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    *** INSTANCE;
}
-keep @kotlinx.serialization.Serializable class * {
    *;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-dontwarn kotlinx.serialization.**

# ── 3. SQLDelight — Base locale SQLite ────────────────────────────────────────
# Préservation des interfaces de base de données, des requêtes générées et des adaptateurs
-keep class com.ledgerhub.db.** { *; }
-keep interface com.ledgerhub.db.** { *; }
-keep class com.squareup.sqldelight.** { *; }
-keep interface com.squareup.sqldelight.** { *; }
-keep class app.cash.sqldelight.** { *; }
-keep interface app.cash.sqldelight.** { *; }

# ── 4. Ktor & Réseau ─────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── 5. Compose Multiplatform ─────────────────────────────────────────────────
# Préservation des points d'entrée Compose requis pour la recomposition
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── 6. Éradication des logs en production (Anti-fuite d'informations) ─────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

-assumenosideeffects class kotlin.io.ConsoleKt {
    public static void print(java.lang.Object);
    public static void println(java.lang.Object);
    public static void println();
}

-assumenosideeffects class java.io.PrintStream {
    public static void print(...);
    public static void println(...);
}
