package com.ledgerhub.presentation.adaptive

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Paliers de largeur d'écran (Window Size Classes) inspirés des spécifications Material 3
 * et Android Adaptive Guidelines, 100% compatibles Kotlin Multiplatform (sans dépendance Android).
 */
enum class WindowWidthSizeClass {
    /**
     * Smartphones en mode portrait, écrans externes de téléphones pliables (Z Fold fermé, Z Flip).
     * Largeur < 600 dp.
     */
    COMPACT,

    /**
     * Écrans pliables ouverts en mode portrait (Galaxy Z Fold), petites tablettes, fenêtres en split-screen.
     * 600 dp <= Largeur < 840 dp.
     */
    MEDIUM,

    /**
     * Tablettes en paysage, smartphones larges en paysage, écrans desktop.
     * Largeur >= 840 dp.
     */
    EXPANDED;

    val isCompact: Boolean get() = this == COMPACT
    val isMedium: Boolean get() = this == MEDIUM
    val isExpanded: Boolean get() = this == EXPANDED
}

/**
 * État de taille de fenêtre encapsulant les dimensions réelles et la classe de largeur calculée.
 */
data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val maxWidth: Dp,
    val maxHeight: Dp,
)

/**
 * Calcule la [WindowSizeClass] correspondante selon les breakpoints officiels.
 */
fun calculateWindowSizeClass(maxWidth: Dp, maxHeight: Dp): WindowSizeClass {
    val widthClass = when {
        maxWidth < 600.dp -> WindowWidthSizeClass.COMPACT
        maxWidth < 840.dp -> WindowWidthSizeClass.MEDIUM
        else -> WindowWidthSizeClass.EXPANDED
    }
    return WindowSizeClass(
        widthSizeClass = widthClass,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )
}

/**
 * CompositionLocal permettant d'accéder à la taille de fenêtre courante depuis n'importe quel Composable.
 */
val LocalWindowSizeClass = compositionLocalOf {
    WindowSizeClass(
        widthSizeClass = WindowWidthSizeClass.COMPACT,
        maxWidth = 360.dp,
        maxHeight = 640.dp,
    )
}
