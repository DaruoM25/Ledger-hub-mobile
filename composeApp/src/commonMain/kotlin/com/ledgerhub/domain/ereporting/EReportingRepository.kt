package com.ledgerhub.domain.ereporting

/**
 * Dépôt des déclarations e-Reporting.
 *
 * Contrat volontairement minimal : lecture de la liste et de l'unité, création, et transition
 * unique DRAFT → ACKNOWLEDGED. Aucune méthode de mise à jour libre — le cycle de vie ne s'y
 * prête pas.
 */
interface EReportingRepository {

    /** Toutes les déclarations, de la plus récente à la plus ancienne. */
    suspend fun getAll(): List<EReportingReport>

    /** La déclaration d'identifiant [id], ou `null` si elle n'existe pas. */
    suspend fun getById(id: String): EReportingReport?

    /**
     * Persiste [report] et retourne l'instance effectivement stockée (identifiant et horodatage
     * éventuellement complétés).
     */
    suspend fun create(report: EReportingReport): EReportingReport

    /**
     * Passe la déclaration [id] au statut [EReportingStatus.ACKNOWLEDGED] et y inscrit
     * [ackNumber]. Aucune vérification de cohérence ici : l'appelant (le cas d'usage) garantit
     * que la déclaration était en [EReportingStatus.DRAFT].
     */
    suspend fun markAsAcknowledged(id: String, ackNumber: String)
}
