package com.ledgerhub.data.remote.dto

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.serialization.Serializable

/**
 * DTOs de transport de l'API Ledger (Factur-X 2026) — jamais exposés à la présentation, qui ne
 * connaît que [com.ledgerhub.domain.invoice.Invoice] (voir décision Étape 1 : un seul modèle
 * métier, pas de `domain/models/Invoice.kt` séparé). [toDomain] est le seul point de passage
 * DTO -> domaine, appelé une fois par [com.ledgerhub.data.repository.LedgerRepositoryImpl].
 */

@Serializable
data class InvoicePartyDto(
    val name: String,
    val siren: String,
    val siret: String,
)

/** Ligne de facturation distante — nommage API "item", mappée vers [InvoiceLine] côté domaine. */
@Serializable
data class InvoiceItemDto(
    val label: String,
    val quantity: Int,
    val unitPriceHtCents: Long,
    /** Nom de la constante [VatRate] (ex: "TAUX_NORMAL") — jamais un taux numérique brut, pour
     * ne pas dupliquer la correspondance taux/points de base déjà définie côté domaine. */
    val vatRate: String,
)

/**
 * Ventilation de TVA telle que renvoyée par le serveur — purement informatif. Le domaine
 * recalcule toujours les totaux lui-même à partir des lignes (voir [Invoice.vatBreakdown]) plutôt
 * que de faire confiance à ce champ : un total serveur périmé ou incohérent ne doit jamais
 * afficher un TTC qui ne correspond à aucune ligne réellement facturée. Conservé sur le DTO
 * uniquement pour diagnostic/logs, jamais lu par [toDomain].
 */
@Serializable
data class TaxSummaryDto(
    val vatRate: String,
    val baseHtCents: Long,
    val vatAmountCents: Long,
)

@Serializable
data class InvoiceDto(
    val number: String,
    val issueDate: String,
    val status: String,
    val issuer: InvoicePartyDto,
    val recipient: InvoicePartyDto,
    val items: List<InvoiceItemDto>,
    val taxSummary: List<TaxSummaryDto> = emptyList(),
    val sourceQuoteId: String? = null,
)

fun InvoicePartyDto.toDomain(): Party = Party(name = name, siren = siren, siret = siret)

fun InvoiceItemDto.toDomain(): InvoiceLine = InvoiceLine(
    label = label,
    quantity = quantity,
    unitPriceHt = Money(unitPriceHtCents),
    vatRate = VatRate.valueOf(vatRate),
)

fun InvoiceDto.toDomain(): Invoice = Invoice(
    number = number,
    issueDate = issueDate,
    issuer = issuer.toDomain(),
    recipient = recipient.toDomain(),
    lines = items.map { it.toDomain() },
    status = InvoiceStatus.valueOf(status),
    sourceQuoteId = sourceQuoteId,
)
