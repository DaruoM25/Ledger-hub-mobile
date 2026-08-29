val sqliteTmp = java.io.File(rootDir, "build/tmp/sqlite").apply { mkdirs() }
System.setProperty("org.sqlite.tmpdir", sqliteTmp.absolutePath)
System.setProperty("java.io.tmpdir", sqliteTmp.absolutePath)

// Auto-détection et génération résiliente de local.properties si absent (worktrees Copilot, CI locale/isolée)
val localPropertiesFile = file("local.properties")
if (!localPropertiesFile.exists()) {
    val sdkDirFromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val userHome = System.getProperty("user.home") ?: ""
    val osName = System.getProperty("os.name").lowercase()

    val detectedSdkDir: String? = when {
        !sdkDirFromEnv.isNullOrBlank() -> sdkDirFromEnv
        osName.contains("win") -> {
            val candidatePaths = listOf(
                "C:\\Android\\Sdk",
                "$userHome\\AppData\\Local\\Android\\Sdk"
            )
            candidatePaths.firstOrNull { java.io.File(it).exists() } ?: candidatePaths.first()
        }
        osName.contains("mac") -> "$userHome/Library/Android/sdk"
        else -> "$userHome/Android/Sdk"
    }

    if (detectedSdkDir != null) {
        val escapedPath = detectedSdkDir.replace("\\", "\\\\")
        localPropertiesFile.writeText("sdk.dir=$escapedPath\n")
        logger.lifecycle("Fichier 'local.properties' généré automatiquement avec sdk.dir=$detectedSdkDir")
    }
}

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "LedgerHub"
include(":composeApp")
