package com.ledgerhub.domain.command

/**
 * Reconnaissance du raccourci clavier d'ouverture de la palette (US-19).
 *
 * ## Pourquoi une fonction pure, hors de la composition
 *
 * Un test d'interface ne peut pas produire d'appui clavier matériel réaliste : Robolectric n'a pas
 * de clavier, et l'émulateur Pixel 5 non plus. Câblée directement dans un `onPreviewKeyEvent`, la
 * règle ne serait donc couverte par aucun niveau de la pyramide. Isolée ici, elle se teste
 * exhaustivement — combinaisons attendues comme combinaisons voisines à rejeter.
 *
 * ## La règle
 *
 * `Ctrl+K` **ou** `Cmd+K`, pour couvrir d'un même geste un clavier externe branché sur Android et
 * un clavier Apple sur iOS, sans forcer l'utilisateur à connaître la convention de sa plateforme.
 *
 * `K` seul est refusé : la touche doit rester saisissable dans n'importe quel champ de texte de
 * l'application. Un modificateur est donc exigé, toujours.
 */
object CommandPaletteShortcut {

    /**
     * @param keyLabel étiquette de la touche pressée, telle que l'expose l'événement clavier
     *   (`Key.K.toString()` donne « Key: K »). Comparée sans casse et sur la seule présence du
     *   caractère, pour ne pas dépendre du format exact d'une plateforme à l'autre.
     */
    fun isTriggeredBy(keyLabel: String, isCtrlPressed: Boolean, isMetaPressed: Boolean): Boolean {
        if (!isCtrlPressed && !isMetaPressed) return false
        return keyLabel.matchesLetterK()
    }

    /**
     * L'étiquette d'une touche n'a pas de format normalisé entre plateformes : Android rend
     * « Key: K », Skiko peut rendre « K ». On ne retient donc que la dernière lettre significative
     * plutôt que d'égaler une chaîne complète, qui casserait au premier changement de format.
     */
    private fun String.matchesLetterK(): Boolean =
        trim().lastOrNull()?.uppercaseChar() == 'K'
}
