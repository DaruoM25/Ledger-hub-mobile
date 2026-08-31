plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

/**
 * Isole l'extraction de la bibliothèque native de sqlite-jdbc dans le projet (US-16).
 *
 * Fixé à la configuration, donc **avant** que la moindre tâche n'ouvre une base : cela vaut pour
 * les tâches SQLDelight (génération et vérification de migration) qui s'exécutent dans la JVM du
 * build. Un chemin absolu calculé ici, plutôt que codé en dur dans gradle.properties, garde le
 * dépôt portable d'un poste à l'autre — et prend le pas sur un `-Dorg.sqlite.tmpdir` hérité de
 * l'environnement (JAVA_TOOL_OPTIONS), qui rabattrait sinon l'extraction sur le Temp partagé.
 */
val sqliteNativeTmpDir: java.io.File = layout.buildDirectory.dir("tmp/sqlite").get().asFile
sqliteNativeTmpDir.mkdirs()
System.setProperty("org.sqlite.tmpdir", sqliteNativeTmpDir.absolutePath)
