package com.ledgerhub.domain.ereporting

import kotlin.random.Random

/**
 * Transmission d'une déclaration e-Reporting au Portail Public de Facturation.
 *
 * Le geste est idempotent par refus : une déclaration déjà acquittée lève
 * [AlreadyAcknowledgedException] (parité HTTP 409), sans nouvel accusé ni écrasement. Sur une
 * déclaration en [EReportingStatus.DRAFT], un numéro d'accusé `ACK-2026-XXXX` est généré puis
 * persisté via [EReportingRepository.markAsAcknowledged].
 *
 * @param ackNumberGenerator source du numéro d'accusé — injectable pour rendre les tests
 *   déterministes ; par défaut, quatre chiffres aléatoires préfixés.
 */
class TransmitEReportUseCase(
    private val repository: EReportingRepository,
    private val ackNumberGenerator: () -> String = { defaultAckNumber() },
) {

    /**
     * @param reportId identifiant de la déclaration à transmettre.
     * @return la déclaration mise à jour (statut acquitté, accusé renseigné).
     * @throws AlreadyAcknowledgedException si la déclaration est déjà acquittée.
     * @throws NoSuchElementException si aucune déclaration ne porte cet identifiant.
     */
    suspend operator fun invoke(reportId: String): EReportingReport {
        val report = repository.getById(reportId)
            ?: throw NoSuchElementException("Déclaration e-Reporting introuvable : $reportId")

        if (report.status == EReportingStatus.ACKNOWLEDGED) {
            throw AlreadyAcknowledgedException(reportId)
        }

        val ackNumber = ackNumberGenerator()
        repository.markAsAcknowledged(reportId, ackNumber)

        return report.copy(status = EReportingStatus.ACKNOWLEDGED, ackNumber = ackNumber)
    }

    companion object {
        /** Préfixe imposé par le millésime de la réforme. */
        const val ACK_PREFIX = "ACK-2026-"

        /** `ACK-2026-` suivi de quatre chiffres (0000–9999), complétés à gauche par des zéros. */
        fun defaultAckNumber(): String {
            val suffix = Random.nextInt(0, 10_000).toString().padStart(4, '0')
            return "$ACK_PREFIX$suffix"
        }
    }
}
