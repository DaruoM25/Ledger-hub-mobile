package com.ledgerhub.domain.quote

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.invoice.vatFor

/** Ligne de devis. [unitPriceHt] est en centimes — structure identique à InvoiceLine (v1). */
data class QuoteLine(
    val label: String,
    val quantity: Int,
    val unitPriceHt: Money,
    val vatRate: VatRate,
) {
    val totalHt: Money get() = Money(unitPriceHt.cents * quantity)
    val totalVat: Money get() = totalHt.vatFor(vatRate.basisPoints)
    val totalTtc: Money get() = totalHt + totalVat
}
