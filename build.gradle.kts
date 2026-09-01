plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

/**
 * Isole l'extraction de la bibliothèque native de sqlite-jdbc dans le projet (US-16, durci US-17).
 *
 * Fixé à la configuration, donc **avant** que la moindre tâche n'ouvre une base : cela vaut pour
 * les tâches SQLDelight (génération et vérification de migration) qui s'exécutent dans la JVM du
 * build. Un chemin absolu calculé ici, plutôt que codé en dur dans gradle.properties, garde le
 * dépôt portable d'un poste à l'autre — et prend le pas sur un `-Dorg.sqlite.tmpdir` hérité de
 * l'environnement (JAVA_TOOL_OPTIONS), qui rabattrait sinon l'extraction sur le Temp partagé.
 *
 * `java.io.tmpdir` est posé en plus de `org.sqlite.tmpdir` : c'est le repli de sqlite-jdbc quand
 * sa propre propriété n'est pas lue (versions, chemins d'extraction annexes). Sur un poste où la
 * JVM Gradle hérite d'un `java.io.tmpdir` non inscriptible — `C:\WINDOWS` lorsque le démon a été
 * lancé depuis un contexte service — ce repli produisait
 * `AccessDeniedException: C:\WINDOWS\sqlite-...-sqlitejdbc.dll.lck` sur
 * `generateCommonMainLedgerHubDatabaseInterface`. Les deux propriétés doivent donc pointer sur le
 * dossier projet, sans exception.
 */
val sqliteNativeTmpDir: java.io.File = layout.buildDirectory.dir("tmp/sqlite").get().asFile
sqliteNativeTmpDir.mkdirs()
System.setProperty("org.sqlite.tmpdir", sqliteNativeTmpDir.absolutePath)
System.setProperty("java.io.tmpdir", sqliteNativeTmpDir.absolutePath)

/**
 * Les JVM **forkées** par le build (tests, workers en isolation processus, outils lancés en
 * JavaExec) ne voient rien des `System.setProperty` ci-dessus : elles repartent de l'environnement,
 * donc du `JAVA_TOOL_OPTIONS` du poste. On leur repasse explicitement les deux propriétés, dans
 * tous les sous-projets, pour qu'aucune JVM du build ne puisse retomber sur un Temp partagé.
 */
allprojects {
    val forkedTmpDir = sqliteNativeTmpDir.invariantSeparatorsPath
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        systemProperty("org.sqlite.tmpdir", forkedTmpDir)
        systemProperty("java.io.tmpdir", forkedTmpDir)
    }
    tasks.withType<JavaExec>().configureEach {
        systemProperty("org.sqlite.tmpdir", forkedTmpDir)
        systemProperty("java.io.tmpdir", forkedTmpDir)
    }
}
