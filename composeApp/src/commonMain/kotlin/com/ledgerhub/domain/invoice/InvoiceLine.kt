package com.ledgerhub.domain.invoice

/** Ligne de facturation unique (v1). [unitPriceHt] est en centimes. */
data class InvoiceLine(
    val label: String,
    val quantity: Int,
    val unitPriceHt: Money,
    val vatRate: VatRate,
) {
    val totalHt: Money get() = Money(unitPriceHt.cents * quantity)
    val totalVat: Money get() = totalHt.vatFor(vatRate.basisPoints)
    val totalTtc: Money get() = totalHt + totalVat
}
