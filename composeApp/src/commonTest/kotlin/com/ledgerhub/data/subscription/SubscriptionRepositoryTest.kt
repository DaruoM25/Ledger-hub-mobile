package com.ledgerhub.data.subscription

import com.ledgerhub.domain.subscription.SubscriptionStatus
import com.ledgerhub.domain.subscription.SubscriptionTier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionRepositoryTest {

    @Test
    fun defaultState_isFreeTier() = runTest {
        val repo = MockSubscriptionRepository()
        val status = repo.getSubscription()

        assertEquals(SubscriptionTier.FREE, status.tier)
        assertFalse(status.isPro)
    }

    @Test
    fun purchasingProMonthly_updatesTierAndFlow() = runTest {
        val repo = MockSubscriptionRepository()
        val result = repo.purchase(SubscriptionTier.PRO_MONTHLY)

        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertEquals(SubscriptionTier.PRO_MONTHLY, status.tier)
        assertTrue(status.isPro)

        val flowStatus = repo.observeSubscription().first()
        assertEquals(SubscriptionTier.PRO_MONTHLY, flowStatus.tier)
    }

    @Test
    fun applyingDevpostPromoCode_unlocksProBypass() = runTest {
        val repo = MockSubscriptionRepository()
        val result = repo.applyPromoCode("DEVPOST2026")

        assertTrue(result.isSuccess)
        val status = repo.getSubscription()
        assertTrue(status.isPro)
        assertTrue(status.isBypassed)
    }

    @Test
    fun applyingInvalidPromoCode_failsAndKeepsCurrentTier() = runTest {
        val repo = MockSubscriptionRepository()
        val result = repo.applyPromoCode("INVALID_CODE_123")

        assertTrue(result.isFailure)
        val status = repo.getSubscription()
        assertFalse(status.isPro)
    }
}
