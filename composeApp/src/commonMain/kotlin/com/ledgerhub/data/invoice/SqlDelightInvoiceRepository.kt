package com.ledgerhub.data.invoice

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.db.Invoice as InvoiceRow
import com.ledgerhub.db.InvoiceLine as InvoiceLineRow
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.InvoiceStatusRepository
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate

/**
 * Persistance réelle des factures via SQLDelight (voir Invoice.sq / InvoiceLine.sq /
 * Customer.sq, commonMain/sqldelight). Le destinataire est normalisé vers la table Customer
 * (SIRET unique, réutilisable d'une facture à l'autre) ; l'émetteur reste embarqué sur la
 * ligne Invoice (toujours la même entreprise, celle de [userEmail]).
 *
 * @param userEmail compte propriétaire des factures lues/écrites par ce repository —
 *   scoping multi-utilisateurs (voir Invoice.sq:selectByUserEmail).
 */
class SqlDelightInvoiceRepository(
    private val database: LedgerHubDatabase,
    private val userEmail: String,
    /** Horodatage des traces d'audit — injecté pour rester déterministe en test. */
    private val clock: Clock = SystemClock,
) : InvoiceRepository, InvoiceStatusRepository {

    /**
     * Change le statut **et** écrit la trace d'audit, dans une seule transaction.
     *
     * L'atomicité est la raison d'être de cette méthode : un statut modifié sans trace rendrait
     * la piste d'audit mensongère, une trace sans changement la rendrait fausse. La validation de
     * la transition, elle, appartient à
     * [ChangeInvoiceStatusUseCase][com.ledgerhub.domain.invoice.ChangeInvoiceStatusUseCase] —
     * elle a lieu avant d'arriver ici.
     */
    override suspend fun changeStatus(
        invoiceNumber: String,
        from: InvoiceStatus,
        to: InvoiceStatus,
        reason: String?,
    ): Result<Unit> = runCatching {
        database.transaction {
            database.invoiceQueries.updateStatus(to.name, invoiceNumber)
            database.auditLogQueries.insert(
                id = newAuditId(invoiceNumber, to),
                invoiceNumber = invoiceNumber,
                userId = userEmail,
                fromStatus = from.name,
                toStatus = to.name,
                reason = reason,
                createdAt = clock.nowIso(),
            )
        }
    }

    /**
     * Identifiant d'entrée d'audit. `Uuid.random()` est multiplateforme depuis Kotlin 2.0 :
     * ni dépendance ni `expect`/`actual` pour un besoin aussi élémentaire.
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun newAuditId(invoiceNumber: String, to: InvoiceStatus): String = Uuid.random().toString()

    override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = runCatching {
        database.transaction {
            // Ne crée la fiche client que si le SIRET est inconnu : émettre une facture ne doit
            // jamais réécrire l'identité d'un client déjà enregistré (voir Customer.sq).
            database.customerQueries.insertIfAbsent(
                siret = invoice.recipient.siret,
                siren = invoice.recipient.siren,
                name = invoice.recipient.name,
                email = invoice.recipient.email,
            )
            database.invoiceQueries.insertOrReplace(
                number = invoice.number,
                issueDate = invoice.issueDate,
                status = invoice.status.name,
                sourceQuoteId = invoice.sourceQuoteId,
                userEmail = userEmail,
                issuerName = invoice.issuer.name,
                issuerSiren = invoice.issuer.siren,
                issuerSiret = invoice.issuer.siret,
                recipientSiret = invoice.recipient.siret,
                // Copie gelée du destinataire — immutabilité de la facture émise.
                recipientName = invoice.recipient.name,
                recipientEmail = invoice.recipient.email,
                dueDate = invoice.dueDate,
                // SQLite n'a pas de type booléen — 1/0 en INTEGER, reconverti dans toDomain().
                facturX = if (invoice.facturX) 1L else 0L,
                applyB2bPenalties = if (invoice.applyB2bPenalties) 1L else 0L,
            )
            // Remplacement intégral des lignes — plus simple et moins sujet aux bugs qu'un diff
            // ligne à ligne, pour un volume de lignes par facture qui reste faible en pratique.
            database.invoiceLineQueries.deleteByInvoiceNumber(invoice.number)
            invoice.lines.forEach { line ->
                database.invoiceLineQueries.insert(
                    invoiceNumber = invoice.number,
                    label = line.label,
                    quantity = line.quantity.toLong(),
                    unitPriceHtCents = line.unitPriceHt.cents,
                    vatRate = line.vatRate.name,
                )
            }
        }
    }

    /**
     * Nombre de factures du compte. Ne construit **aucun** objet de domaine, donc ne peut pas
     * échouer sur une ligne indésérialisable — c'est précisément ce qui rend ce comptage sûr
     * pour décider s'il faut semer les données de démonstration (voir `seedDemoDataIfEmpty`).
     */
    suspend fun countInvoices(): Result<Long> = runCatching {
        database.invoiceQueries.countByUserEmail(userEmail).executeAsOne()
    }

    /**
     * Une ligne indésérialisable est **écartée**, pas propagée : auparavant, une seule facture
     * corrompue faisait échouer la lecture entière et vidait l'écran de toutes les autres.
     * Perdre une ligne à l'affichage est regrettable ; perdre les cent autres est inacceptable.
     *
     * L'anomalie est signalée sur la sortie standard — le projet n'a pas de journalisation
     * partagée, et `println` est la seule sortie disponible en commonMain. À remplacer par un
     * vrai logger le jour où il en existe un.
     */
    override suspend fun fetchInvoices(): Result<List<Invoice>> = runCatching {
        database.invoiceQueries.selectByUserEmail(userEmail).executeAsList().mapNotNull { row ->
            runCatching { row.toDomain() }.getOrElse { throwable ->
                println("[LedgerHub] Facture ${row.number} ignorée — enregistrement illisible : ${throwable.message}")
                null
            }
        }
    }

    private fun InvoiceRow.toDomain(): Invoice {
        // Priorité à la copie gelée portée par la facture : c'est elle qui fait foi une fois la
        // facture émise. La fiche Customer ne sert plus qu'aux factures héritées (colonne vide),
        // écrites avant l'introduction du gel du destinataire.
        val recipient = if (recipientName.isNotEmpty()) {
            Party(
                name = recipientName,
                // Règle INSEE : le SIREN est le préfixe à 9 chiffres du SIRET.
                siren = recipientSiret.take(9),
                siret = recipientSiret,
                email = recipientEmail,
            )
        } else {
            database.customerQueries.selectBySiret(recipientSiret).executeAsOneOrNull()
                ?.let { Party(name = it.name, siren = it.siren, siret = it.siret, email = it.email) }
                ?: Party(name = "", siren = "", siret = recipientSiret)
        }
        val lines = database.invoiceLineQueries.selectByInvoiceNumber(number).executeAsList().map { it.toDomain() }
        return Invoice(
            number = number,
            issueDate = issueDate,
            issuer = Party(name = issuerName, siren = issuerSiren, siret = issuerSiret),
            recipient = recipient,
            lines = lines,
            status = InvoiceStatus.valueOf(status),
            sourceQuoteId = sourceQuoteId,
            dueDate = dueDate,
            facturX = facturX == 1L,
            applyB2bPenalties = applyB2bPenalties == 1L,
        )
    }

    private fun InvoiceLineRow.toDomain(): InvoiceLine = InvoiceLine(
        label = label,
        quantity = quantity.toInt(),
        unitPriceHt = Money(unitPriceHtCents),
        vatRate = VatRate.valueOf(vatRate),
    )
}
