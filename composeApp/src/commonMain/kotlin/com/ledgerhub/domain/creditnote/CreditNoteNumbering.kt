package com.ledgerhub.domain.creditnote

/**
 * Numérotation séquentielle des avoirs, au format `AV-AAAA-NNNN`.
 *
 * La continuité de la numérotation est une obligation fiscale : elle ne peut pas dépendre d'une
 * saisie utilisateur, qui laisse passer trous et doublons. Le suffixe est à largeur fixe sur
 * [SEQUENCE_DIGITS] chiffres, ce qui rend le tri lexicographique équivalent au tri numérique —
 * c'est ce qui permet à `CreditNote.sq:selectLastNumberForPrefix` de se contenter d'un
 * `ORDER BY number DESC LIMIT 1`.
 */
object CreditNoteNumbering {

    const val PREFIX = "AV"
    const val SEQUENCE_DIGITS = 4

    /** Motif SQL `LIKE` couvrant tous les avoirs d'un exercice : `AV-2026-%`. */
    fun likePatternForYear(year: Int): String = "$PREFIX-$year-%"

    /** Numéro complet à partir d'un rang : `next(2026, 1)` → `AV-2026-0001`. */
    fun format(year: Int, sequence: Int): String =
        "$PREFIX-$year-${sequence.toString().padStart(SEQUENCE_DIGITS, '0')}"

    /**
     * Numéro suivant pour [year], déduit du dernier numéro attribué.
     *
     * [lastNumber] nul (aucun avoir sur l'exercice) ou illisible démarre la séquence à 1 : une
     * numérotation qu'on ne sait pas relire ne doit pas bloquer l'émission, elle doit repartir
     * proprement — un doublon serait de toute façon refusé par la clé primaire.
     */
    fun next(year: Int, lastNumber: String?): String =
        format(year, (sequenceOf(lastNumber) ?: 0) + 1)

    /** Rang porté par un numéro, ou `null` si le format n'est pas reconnu. */
    fun sequenceOf(number: String?): Int? {
        val match = NUMBER_REGEX.matchEntire(number?.trim().orEmpty()) ?: return null
        return match.groupValues[2].toIntOrNull()
    }

    /** `true` si [number] respecte le format attendu. */
    fun isValid(number: String): Boolean = NUMBER_REGEX.matches(number.trim())

    private val NUMBER_REGEX = Regex("""^$PREFIX-(\d{4})-(\d{$SEQUENCE_DIGITS})$""")
}
