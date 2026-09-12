package com.ledgerhub.data.subscription

import com.ledgerhub.domain.subscription.SubscriptionRepository
import com.ledgerhub.domain.subscription.SubscriptionStatus
import com.ledgerhub.domain.subscription.SubscriptionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implémentation en mémoire et réactive du [SubscriptionRepository].
 *
 * Gère les achats simulés, la restauration et les codes promo jury Devpost
 * (DEVPOST2026, SHIPATON2026, PRO2026) débloquant instantanément le statut Pro.
 */
class MockSubscriptionRepository(
    initialStatus: SubscriptionStatus = SubscriptionStatus.Free,
) : SubscriptionRepository {

    private val _statusFlow = MutableStateFlow(initialStatus)
    val currentStatus: SubscriptionStatus get() = _statusFlow.value

    override fun observeSubscription(): Flow<SubscriptionStatus> = _statusFlow.asStateFlow()

    override suspend fun getSubscription(): SubscriptionStatus = _statusFlow.value

    override suspend fun purchase(tier: SubscriptionTier): Result<SubscriptionStatus> {
        val newStatus = SubscriptionStatus(
            tier = tier,
            isActive = true,
            expiresAt = "2027-12-31",
            isBypassed = false,
        )
        _statusFlow.value = newStatus
        return Result.success(newStatus)
    }

    override suspend fun restorePurchases(): Result<SubscriptionStatus> {
        // En mock/démo, restore restaure le statut courant
        return Result.success(_statusFlow.value)
    }

    override suspend fun applyPromoCode(code: String): Result<Boolean> {
        val cleanCode = code.trim().uppercase()
        val isValid = cleanCode in VALID_PROMO_CODES
        if (isValid) {
            _statusFlow.value = SubscriptionStatus.DevpostBypass
            return Result.success(true)
        }
        return Result.failure(IllegalArgumentException("Code d'accès invalide ou expiré"))
    }

    fun setStatus(status: SubscriptionStatus) {
        _statusFlow.value = status
    }

    companion object {
        val VALID_PROMO_CODES = setOf(
            "DEVPOST2026",
            "SHIPATON2026",
            "PRO2026",
            "JURY2026",
            "LEDGERPRO",
        )
    }
}
