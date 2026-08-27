package com.ledgerhub.domain.invoice

/** Cycle de vie d'une facture. Seul [DRAFT] autorise modification/suppression. */
enum class InvoiceStatus {
    DRAFT,
    VALIDATED,
    SENT,
    PAID,
    CANCELLED,
}
