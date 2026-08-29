package com.ledgerhub.data.client

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.client.ClientInUseException
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.Party

/**
 * Persistance des fiches clients via SQLDelight (table `Customer`).
 *
 * Deux règles y sont tenues, héritées de l'immutabilité fiscale :
 * - une création ne peut pas écraser une fiche existante (voir [DuplicateClientException]) ;
 * - une suppression est refusée dès qu'une facture référence le SIRET ([ClientInUseException]).
 */
class SqlDelightClientRepository(
    private val database: LedgerHubDatabase,
) : ClientRepository {

    override suspend fun fetchClients(): Result<List<Party>> = runCatching {
        database.customerQueries.selectAll().executeAsList()
            .map { Party(name = it.name, siren = it.siren, siret = it.siret, email = it.email) }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun createClient(client: Party): Result<Unit> = runCatching {
        database.transaction {
            val existing = database.customerQueries.selectBySiret(client.siret).executeAsOneOrNull()
            if (existing != null) throw DuplicateClientException(client.siret)
            database.customerQueries.insertIfAbsent(
                siret = client.siret,
                siren = client.siren,
                name = client.name,
                email = client.email,
            )
        }
    }

    override suspend fun updateClient(client: Party): Result<Unit> = runCatching {
        database.customerQueries.updateIdentity(
            siren = client.siren,
            name = client.name,
            email = client.email,
            siret = client.siret,
        )
    }

    override suspend fun deleteClient(siret: String): Result<Unit> = runCatching {
        database.transaction {
            val invoiceCount = database.invoiceQueries.countByRecipientSiret(siret).executeAsOne()
            if (invoiceCount > 0) throw ClientInUseException(siret, invoiceCount)
            database.customerQueries.deleteBySiret(siret)
        }
    }

    override suspend fun countInvoicesFor(siret: String): Result<Long> = runCatching {
        database.invoiceQueries.countByRecipientSiret(siret).executeAsOne()
    }
}
