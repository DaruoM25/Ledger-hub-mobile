package com.ledgerhub.domain.facturx

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.settings.TaxSettings

/**
 * Projection des pièces du domaine vers le modèle pivot [FacturXDocument].
 *
 * Les assiettes et les totaux sont **repris tels quels** de `Invoice` et `CreditNote`, jamais
 * recalculés ici : le moteur fiscal du domaine reste l'unique autorité sur les montants, et une
 * seconde implémentation finirait tôt ou tard par diverger de la première.
 */

/** Facture → pièce de type 380. */
fun Invoice.toFacturXDocument(settings: TaxSettings): FacturXDocument = FacturXDocument(
    type = FacturXDocumentType.INVOICE,
    documentNumber = number,
    issueDate = issueDate,
    seller = issuer,
    buyer = recipient,
    sellerVatNumber = settings.vatNumber,
    lines = lines,
    taxes = FacturXDocument.taxesFrom(vatBreakdown),
    totalHt = totalHt,
    totalVat = totalVat,
    totalTtc = totalTtc,
)

/**
 * Avoir → pièce de type 381, avec le chaînage obligatoire vers la facture annulée.
 * Les montants sont déjà négatifs côté domaine (invariant de [CreditNote]) : rien à inverser ici.
 */
fun CreditNote.toFacturXDocument(settings: TaxSettings): FacturXDocument = FacturXDocument(
    type = FacturXDocumentType.CREDIT_NOTE,
    documentNumber = number,
    issueDate = issueDate,
    seller = issuer,
    buyer = recipient,
    sellerVatNumber = settings.vatNumber,
    lines = lines,
    taxes = FacturXDocument.taxesFrom(vatBreakdown),
    totalHt = totalHt,
    totalVat = totalVat,
    totalTtc = totalTtc,
    referencedDocument = FacturXReference(
        documentNumber = invoiceId,
        issueDate = originalInvoiceDate,
    ),
)
