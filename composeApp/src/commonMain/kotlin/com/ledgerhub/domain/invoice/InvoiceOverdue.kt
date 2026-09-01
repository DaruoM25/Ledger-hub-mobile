package com.ledgerhub.domain.invoice

/**
 * Retard de paiement d'une facture (US-19).
 *
 * ## Une seule définition, deux consommateurs
 *
 * Le KPI « En retard » du tableau de bord et le filtre « En retard » de la liste répondent à la
 * même question. Deux implémentations finiraient par diverger — l'écran annoncerait un montant que
 * la liste ne saurait pas justifier. La règle vit donc ici, et nulle part ailleurs.
 *
 * ## La règle
 *
 * Une facture est en retard lorsque son échéance est **dépassée** et qu'elle n'est pas soldée.
 * Trois précisions qui ne vont pas de soi :
 *
 * - **L'échéance du jour n'est pas un retard.** Le débiteur a jusqu'au soir pour payer : la
 *   comparaison est stricte (`dueDate < today`), jamais `<=`.
 * - **Une facture sans échéance n'est jamais en retard.** [Invoice.dueDate] est vide sur le parc
 *   antérieur à l'US-16 ; sans date, aucun retard n'est mesurable, et en inventer un exposerait
 *   l'utilisateur à relancer un client qui ne doit rien.
 * - **Seules les factures encore dues comptent.** Une facture payée ou annulée par avoir est
 *   éteinte : son échéance passée ne fait plus d'elle une créance.
 *
 * La comparaison lexicographique est valide parce que les deux dates sont en ISO `AAAA-MM-JJ`,
 * format déjà imposé au formulaire de saisie.
 */
object InvoiceOverdue {

    /** Statuts d'une créance encore due. */
    private val UNSETTLED = setOf(
        InvoiceStatus.DEPOSITED,
        InvoiceStatus.APPROVED,
        // Rejetée ou refusée, la facture reste à recouvrer : elle devra être corrigée et
        // redéposée, ou remplacée. L'échéance contractuelle, elle, court toujours.
        InvoiceStatus.REJECTED,
        InvoiceStatus.REFUSED,
    )

    /**
     * @param today date du jour en ISO `AAAA-MM-JJ`. Vide = pas d'horloge : aucune facture n'est
     *   déclarée en retard, plutôt que de retomber sur une date implicite.
     */
    fun isOverdue(invoice: Invoice, today: String): Boolean {
        if (today.isBlank() || invoice.dueDate.isBlank()) return false
        if (invoice.status !in UNSETTLED) return false
        return invoice.dueDate < today
    }

    /** Factures en retard, de la plus ancienne échéance à la plus récente — l'ordre de relance. */
    fun filter(invoices: List<Invoice>, today: String): List<Invoice> =
        invoices.filter { isOverdue(it, today) }.sortedBy { it.dueDate }

    /** Montant TTC cumulé des créances en retard — KPI « En retard » du tableau de bord. */
    fun totalTtc(invoices: List<Invoice>, today: String): Money =
        filter(invoices, today).fold(Money.ZERO) { acc, invoice -> acc + invoice.totalTtc }
}
