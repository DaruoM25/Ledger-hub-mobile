package com.ledgerhub.domain.command

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.StringKey

/**
 * Actions rapides offertes par la palette de commandes (US-19).
 *
 * Chaque action porte son libellé traduit **et** ses mots-clés de recherche, dans les deux langues.
 * Les mots-clés ne sont pas de la décoration : personne ne tape le libellé exact d'une commande.
 * On tape « client », « impayé », « csv » — des termes qui n'apparaissent pas tous dans le libellé
 * affiché. Sans eux, la palette ne trouve que ce qu'on savait déjà nommer, ce qui lui retire tout
 * intérêt.
 *
 * L'action est un **identifiant de domaine**, pas une lambda de navigation : la palette dit ce que
 * l'utilisateur veut, le shell décide comment y aller. C'est ce découplage qui permet de tester la
 * résolution sans composition ni navigation.
 */
enum class CommandAction(
    val labelKey: StringKey,
    private val keywordsFr: Set<String>,
    private val keywordsEn: Set<String>,
) {
    /** Ouvre le formulaire de création de facture. */
    CREATE_INVOICE(
        labelKey = StringKey.COMMAND_ACTION_CREATE_INVOICE,
        keywordsFr = setOf("facture", "creer", "nouveau", "nouvelle", "client", "emettre", "devis"),
        keywordsEn = setOf("invoice", "create", "new", "client", "customer", "issue", "bill"),
    ),

    /** Bascule la liste des factures sur le filtre « En retard ». */
    REMIND_OVERDUE(
        labelKey = StringKey.COMMAND_ACTION_REMIND_OVERDUE,
        keywordsFr = setOf("relance", "relancer", "retard", "impaye", "echeance", "recouvrement", "facture"),
        keywordsEn = setOf("remind", "reminder", "overdue", "unpaid", "late", "due", "collection", "invoice"),
    ),

    /** Génère l'export comptable CSV du grand livre. */
    EXPORT_ACCOUNTING(
        labelKey = StringKey.COMMAND_ACTION_EXPORT_ACCOUNTING,
        keywordsFr = setOf("export", "exporter", "comptable", "comptabilite", "csv", "grand livre", "expert"),
        keywordsEn = setOf("export", "accounting", "csv", "ledger", "bookkeeping", "accountant"),
    );

    fun keywords(language: AppLanguage): Set<String> = when (language) {
        AppLanguage.FR -> keywordsFr
        AppLanguage.EN -> keywordsEn
    }
}
