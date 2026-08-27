package com.ledgerhub.domain.quote

/** Cycle de vie réglementaire d'un devis. Seul [DRAFT] autorise modification/suppression. */
enum class QuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED,
}
