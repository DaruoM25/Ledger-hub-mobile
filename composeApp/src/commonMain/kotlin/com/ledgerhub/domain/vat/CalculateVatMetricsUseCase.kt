package com.ledgerhub.domain.vat

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.invoice.computeVatBreakdown
import com.ledgerhub.domain.invoice.vatFor

/**
 * Cas d'utilisation central de calcul des métriques de TVA et de l'état 3310-CA3 selon la réforme DGFiP 2026.
 *
 * Règles d'exigibilité appliquées :
 * 1. Prestation de services ([NatureOperation.PRESTATION_SERVICES] ou [NatureOperation.MIXTE]) :
 *    - Exigible uniquement si [InvoiceStatus.PAID].
 *    - En attente d'encaissement si [InvoiceStatus.DEPOSITED], [InvoiceStatus.APPROVED], ou [InvoiceStatus.PENDING_REGULARIZATION].
 * 2. Livraison de biens ([NatureOperation.LIVRAISON_BIENS]) ou Option sur les débits ([Invoice.optionTvaDebit] == true) :
 *    - Exigible dès l'émission / dépôt ([InvoiceStatus.DEPOSITED], [InvoiceStatus.APPROVED], [InvoiceStatus.PAID], [InvoiceStatus.PENDING_REGULARIZATION]).
 * 3. Avoirs ([CreditNote]) :
 *    - Viennent en déduction de la TVA collectée exigible.
 */
class CalculateVatMetricsUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val creditNoteRepository: CreditNoteRepository? = null,
) {
    suspend operator fun invoke(
        period: VatPeriodFilter = VatPeriodFilter.ALL,
        simulatedDeductibleVat: Money = Money.ZERO,
        currentDateIso: String = "2026-09-14",
    ): Result<VatMetrics> = runCatching {
        val invoices = invoiceRepository.fetchInvoices().getOrThrow()
        val creditNotes = creditNoteRepository?.fetchCreditNotes()?.getOrNull().orEmpty()

        val filteredInvoices = invoices.filter { invoice ->
            isDateInPeriod(invoice.issueDate, period, currentDateIso)
        }

        val filteredCreditNotes = creditNotes.filter { creditNote ->
            isDateInPeriod(creditNote.issueDate, period, currentDateIso)
        }

        var totalExigibleVatCents = 0L
        var totalPendingVatCents = 0L

        // Accumulateurs par taux pour les factures exigibles
        val exigibleLinesByRate = mutableMapOf<VatRate, MutableList<InvoiceLine>>()
        VatRate.entries.forEach { exigibleLinesByRate[it] = mutableListOf() }

        for (invoice in filteredInvoices) {
            when (invoice.status) {
                InvoiceStatus.DRAFT,
                InvoiceStatus.REJECTED,
                InvoiceStatus.REFUSED,
                InvoiceStatus.CANCELLED -> {
                    // Hors champ de collecte
                    continue
                }

                InvoiceStatus.PAID -> {
                    // Toujours exigible (prestation payée ou bien vendu)
                    val breakdown = invoice.vatBreakdown
                    val invoiceVat = breakdown.fold(Money.ZERO) { acc, b -> acc + b.vatAmount }
                    totalExigibleVatCents += invoiceVat.cents

                    invoice.lines.forEach { line ->
                        exigibleLinesByRate[line.vatRate]?.add(line)
                    }
                }

                InvoiceStatus.DEPOSITED,
                InvoiceStatus.APPROVED,
                InvoiceStatus.PENDING_REGULARIZATION -> {
                    val isExigibleAtIssue = invoice.optionTvaDebit ||
                        invoice.natureOperation == NatureOperation.LIVRAISON_BIENS

                    val breakdown = invoice.vatBreakdown
                    val invoiceVat = breakdown.fold(Money.ZERO) { acc, b -> acc + b.vatAmount }

                    if (isExigibleAtIssue) {
                        totalExigibleVatCents += invoiceVat.cents
                        invoice.lines.forEach { line ->
                            exigibleLinesByRate[line.vatRate]?.add(line)
                        }
                    } else {
                        // Prestation de services en attente d'encaissement
                        totalPendingVatCents += invoiceVat.cents
                    }
                }
            }
        }

        // Déduction des avoirs émis sur la TVA exigible (totalVat est négatif sur un avoir)
        for (creditNote in filteredCreditNotes) {
            totalExigibleVatCents += creditNote.totalVat.cents
        }

        // Construction des 4 lignes de déclaration 3310-CA3 officielles
        val ca3Lines = listOf(
            buildCa3Line("01", VatRate.TAUX_NORMAL, exigibleLinesByRate[VatRate.TAUX_NORMAL].orEmpty()),
            buildCa3Line("02", VatRate.TAUX_INTERMEDIAIRE, exigibleLinesByRate[VatRate.TAUX_INTERMEDIAIRE].orEmpty()),
            buildCa3Line("03", VatRate.TAUX_REDUIT, exigibleLinesByRate[VatRate.TAUX_REDUIT].orEmpty()),
            buildCa3Line("04", VatRate.TAUX_PARTICULIER, exigibleLinesByRate[VatRate.TAUX_PARTICULIER].orEmpty()),
        )

        VatMetrics(
            totalVatCollectedExigible = Money(maxOf(0L, totalExigibleVatCents)),
            totalVatPendingCollection = Money(maxOf(0L, totalPendingVatCents)),
            totalVatDeductible = simulatedDeductibleVat,
            ca3Lines = ca3Lines,
        )
    }

    private fun buildCa3Line(
        lineCode: String,
        vatRate: VatRate,
        lines: List<InvoiceLine>,
    ): VatCa3Line {
        val baseHt = lines.fold(Money.ZERO) { acc, line -> acc + line.totalHt }
        val taxDue = baseHt.vatFor(vatRate.basisPoints)
        return VatCa3Line(
            lineCode = lineCode,
            vatRate = vatRate,
            baseHt = baseHt,
            taxDue = taxDue,
        )
    }

    private fun isDateInPeriod(dateIso: String, period: VatPeriodFilter, currentDateIso: String): Boolean {
        if (period == VatPeriodFilter.ALL || dateIso.isBlank()) return true
        if (dateIso.length < 10 || currentDateIso.length < 10) return true

        val year = dateIso.substring(0, 4)
        val month = dateIso.substring(5, 7).toIntOrNull() ?: return true

        val currentYear = currentDateIso.substring(0, 4)
        val currentMonth = currentDateIso.substring(5, 7).toIntOrNull() ?: return true

        return when (period) {
            VatPeriodFilter.ALL -> true
            VatPeriodFilter.CURRENT_YEAR -> year == currentYear
            VatPeriodFilter.CURRENT_MONTH -> year == currentYear && month == currentMonth
            VatPeriodFilter.CURRENT_QUARTER -> {
                if (year != currentYear) return false
                val quarter = (month - 1) / 3
                val currentQuarter = (currentMonth - 1) / 3
                quarter == currentQuarter
            }
        }
    }
}
