package com.ledgerhub.domain.export

import com.ledgerhub.domain.i18n.StringKey

/**
 * Motif de rejet d'une période d'export (US-22).
 *
 * Chaque motif porte sa clé de traduction, comme `ValidationErrorKey` : l'écran affiche un
 * message, il n'en choisit pas la formulation. Les deux bornes mal formées réutilisent le message
 * de format de date déjà écrit pour le formulaire de facture — une seconde formulation du même
 * reproche finirait par diverger de la première.
 */
enum class ExportPeriodError(val stringKey: StringKey) {
    FROM_INVALID(StringKey.VALIDATION_DATE_FORMAT_INVALID),
    TO_INVALID(StringKey.VALIDATION_DATE_FORMAT_INVALID),
    RANGE_INVERTED(StringKey.EXPORT_PERIOD_INVALID),
}

/**
 * Période couverte par un export comptable — deux bornes ISO `AAAA-MM-JJ`, **incluses** toutes
 * les deux.
 *
 * ## Pourquoi une comparaison de chaînes, et pourquoi c'est correct
 *
 * Le format ISO 8601 est conçu pour que l'ordre lexicographique coïncide avec l'ordre
 * chronologique : les champs vont du plus significatif au moins significatif et sont tous à
 * largeur fixe. Comparer `"2026-01-31" <= "2026-02-01"` donne donc le bon résultat sans parser
 * quoi que ce soit. C'est la même économie que celle faite par `DashboardAnalytics` et
 * `formatIsoDate` : le projet ne convoque `kotlinx-datetime` que là où il calcule des durées.
 *
 * Le format est vérifié **avant** toute comparaison ([validate]) : deux chaînes arbitraires se
 * compareraient sans erreur mais sans signification.
 */
data class ExportPeriod(val from: String, val to: String) {

    /** `null` si la période est exploitable ; le premier motif de rejet sinon. */
    fun validate(): ExportPeriodError? = when {
        !isIsoDate(from) -> ExportPeriodError.FROM_INVALID
        !isIsoDate(to) -> ExportPeriodError.TO_INVALID
        from > to -> ExportPeriodError.RANGE_INVERTED
        else -> null
    }

    val isValid: Boolean get() = validate() == null

    /**
     * Bornes incluses : un exercice « du 01/01 au 31/12 » qui exclurait le 31 décembre perdrait
     * silencieusement les factures du dernier jour — l'erreur la plus coûteuse qu'un export
     * comptable puisse commettre.
     *
     * [isoDate] est tronquée à ses dix premiers caractères : le domaine stocke des dates nues,
     * mais un horodatage complet (`2026-03-04T09:12:00Z`) ne doit pas échouer à se situer.
     */
    fun contains(isoDate: String): Boolean {
        val day = isoDate.trim().take(ISO_DATE_LENGTH)
        if (!isIsoDate(day)) return false
        return day >= from && day <= to
    }

    companion object {
        const val ISO_DATE_LENGTH = 10

        /** Même expression que celle du formulaire de facture — un seul format de date dans l'app. */
        private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

        fun isIsoDate(value: String): Boolean {
            if (!ISO_DATE_REGEX.matches(value)) return false
            val month = value.substring(5, 7).toInt()
            val day = value.substring(8, 10).toInt()
            return month in 1..12 && day in 1..31
        }

        /**
         * Période proposée à l'ouverture : du 1er janvier de l'exercice en cours à aujourd'hui.
         *
         * C'est la demande la plus fréquente d'un cabinet — et surtout, une période pré-remplie
         * juste évite à l'utilisateur de saisir deux dates pour obtenir ce qu'il voulait de toute
         * façon. [todayIso] vient de l'horloge injectée : jamais de l'heure système lue ici.
         */
        fun yearToDate(todayIso: String): ExportPeriod {
            val today = todayIso.take(ISO_DATE_LENGTH)
            if (!isIsoDate(today)) return ExportPeriod(from = "", to = "")
            return ExportPeriod(from = "${today.substring(0, 4)}-01-01", to = today)
        }
    }
}
