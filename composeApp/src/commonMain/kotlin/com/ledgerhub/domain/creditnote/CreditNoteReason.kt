package com.ledgerhub.domain.creditnote

/**
 * Motif légal d'un avoir — US-10.
 *
 * Trois motifs types couvrant l'essentiel des annulations commerciales, plus [OTHER] qui laisse
 * l'utilisateur préciser un motif libre. Le stockage reste une chaîne ([CreditNote.reason]) : un
 * preset y est enregistré sous son [label], un motif libre sous son texte brut — d'où
 * [fromStoredReason] pour le chemin inverse à l'affichage.
 */
enum class CreditNoteReason(val label: String) {
    BILLING_ERROR("Erreur de facturation"),
    COMMERCIAL_DISCOUNT("Remise commerciale"),
    GOODS_RETURN("Retour de marchandise"),
    OTHER("Autre motif"),
    ;

    companion object {
        /** Les trois motifs types proposés en premier, dans l'ordre d'affichage. */
        val PRESETS: List<CreditNoteReason> = listOf(BILLING_ERROR, COMMERCIAL_DISCOUNT, GOODS_RETURN)

        /**
         * Résout la raison légale effective à enregistrer.
         *
         * @return le [label] du preset ; pour [OTHER], le texte libre nettoyé — ou
         *   `Result.failure` si ce texte est vide (la raison légale est un champ bloquant).
         */
        fun resolveReason(kind: CreditNoteReason, freeText: String): Result<String> =
            if (kind == OTHER) {
                val trimmed = freeText.trim()
                if (trimmed.isEmpty()) {
                    Result.failure(IllegalArgumentException("La raison légale de l'avoir est obligatoire"))
                } else {
                    Result.success(trimmed)
                }
            } else {
                Result.success(kind.label)
            }

        /** Preset dont le [label] correspond à [stored], sinon [OTHER] (motif libre historique). */
        fun fromStoredReason(stored: String): CreditNoteReason =
            entries.firstOrNull { it != OTHER && it.label == stored.trim() } ?: OTHER
    }
}
