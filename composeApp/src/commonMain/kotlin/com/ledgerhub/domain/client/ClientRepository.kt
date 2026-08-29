package com.ledgerhub.domain.client

import com.ledgerhub.domain.invoice.Party

/**
 * Fiches clients réutilisables entre devis, factures et avoirs. Le modèle est [Party] : la table
 * `Customer` en a exactement la forme, dupliquer un type `Client` n'apporterait rien.
 *
 * Le SIRET est l'identité métier — clé primaire côté base, et donc identifiant de mise à jour
 * comme de suppression.
 */
interface ClientRepository {

    suspend fun fetchClients(): Result<List<Party>>

    /**
     * Crée une fiche. Échoue si le SIRET est déjà connu : la mise à jour d'une fiche existante
     * passe par [updateClient], jamais par un écrasement silencieux (règle héritée de D-03).
     */
    suspend fun createClient(client: Party): Result<Unit>

    /** Met à jour la fiche portant [Party.siret]. Sans effet sur les factures déjà émises. */
    suspend fun updateClient(client: Party): Result<Unit>

    /**
     * Supprime la fiche. Échoue avec [ClientInUseException] si des factures la référencent :
     * on ne retire pas un destinataire du dossier fiscal.
     */
    suspend fun deleteClient(siret: String): Result<Unit>

    /** Nombre de factures émises pour ce SIRET — alimente le garde-fou de suppression. */
    suspend fun countInvoicesFor(siret: String): Result<Long>
}

/** Suppression refusée : la fiche est référencée par [invoiceCount] facture(s) déjà émise(s). */
class ClientInUseException(val siret: String, val invoiceCount: Long) : Exception(
    "Le client $siret figure sur $invoiceCount facture(s) émise(s) et ne peut pas être supprimé",
)

/** Création refusée : une fiche porte déjà ce SIRET. */
class DuplicateClientException(val siret: String) : Exception(
    "Un client portant le SIRET $siret existe déjà",
)
