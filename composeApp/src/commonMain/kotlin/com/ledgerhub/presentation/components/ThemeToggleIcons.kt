package com.ledgerhub.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Soleil et Lune, construits en [ImageVector.Builder].
 *
 * `material-icons-extended` est **absent des dépendances** du projet — même constat que pour le
 * glyphe des modules d'intégration (US-20) et celui de la palette de commandes (US-19). Plutôt
 * qu'un caractère Unicode, dont le rendu dépend de la police de l'appareil et qui ne se teinte pas
 * proprement, ces deux icônes sont décrites en chemins : elles héritent alors de `LocalContentColor`
 * comme n'importe quelle icône Material, et suivent donc la bascule de thème sans traitement
 * particulier.
 *
 * Grille 24×24, le standard Material — un `Icon` les dimensionne comme les icônes de la librairie.
 */
private const val ViewportSize = 24f

/** Soleil : un disque et huit rayons. Affiché quand l'appui ramènera au thème clair. */
val LedgerHubSunIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LedgerHubSun",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = ViewportSize,
        viewportHeight = ViewportSize,
    ).apply {
        // Disque central — tracé en contour, comme les icônes Material "outlined".
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 7.4f)
            arcToRelative(4.6f, 4.6f, 0f, true, true, -0.01f, 0f)
            close()
        }
        // Huit rayons : quatre cardinaux, quatre diagonaux. Décrits un à un plutôt que calculés —
        // une icône est une donnée, et un `for` en ferait un comportement à relire.
        val rays = listOf(
            listOf(12f, 1.4f, 12f, 3.6f),
            listOf(12f, 20.4f, 12f, 22.6f),
            listOf(1.4f, 12f, 3.6f, 12f),
            listOf(20.4f, 12f, 22.6f, 12f),
            listOf(4.5f, 4.5f, 6.1f, 6.1f),
            listOf(17.9f, 17.9f, 19.5f, 19.5f),
            listOf(4.5f, 19.5f, 6.1f, 17.9f),
            listOf(17.9f, 6.1f, 19.5f, 4.5f),
        )
        rays.forEach { (x1, y1, x2, y2) ->
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
        }
    }.build()
}

/**
 * Lune : un croissant obtenu en **soustrayant** un disque décalé d'un disque plein, décrit d'un
 * seul chemin fermé (arc extérieur, puis arc intérieur en sens inverse). Affichée quand l'appui
 * amènera au thème sombre.
 */
val LedgerHubMoonIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LedgerHubMoon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = ViewportSize,
        viewportHeight = ViewportSize,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // Départ en haut du croissant, grand arc par la gauche jusqu'à sa pointe basse…
            moveTo(20.2f, 15.4f)
            arcTo(8.6f, 8.6f, 0f, true, true, 8.6f, 3.8f)
            // …puis retour par l'arc intérieur, qui creuse l'échancrure.
            arcTo(6.8f, 6.8f, 0f, false, false, 20.2f, 15.4f)
            close()
        }
    }.build()
}
