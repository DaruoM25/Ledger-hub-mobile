package com.ledgerhub.domain.subscription

import kotlinx.coroutines.flow.Flow

/**
 * Contrat du dépôt d'abonnement et de gestion du statut Pro (Clean Architecture).
 */
interface SubscriptionRepository {

    /** Flux réactif de l'état d'abonnement de l'utilisateur. */
    fun observeSubscription(): Flow<SubscriptionStatus>

    /** Récupération synchrone/instantanée de l'état courant. */
    suspend fun getSubscription(): SubscriptionStatus

    /** Déclenche l'achat d'un forfait (mensuel ou annuel). */
    suspend fun purchase(tier: SubscriptionTier): Result<SubscriptionStatus>

    /** Restaure les achats précédents auprès du store. */
    suspend fun restorePurchases(): Result<SubscriptionStatus>

    /** Applique un code d'accès spécial (ex. jury Devpost). */
    suspend fun applyPromoCode(code: String): Result<Boolean>
}
