package com.ledgerhub.domain.subscription

import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.subscription.MockSubscriptionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanAccessFeatureUseCaseTest {

    @Test
    fun proUser_canAccessAllFeatures() = runTest {
        val subscriptionRepo = MockSubscriptionRepository(SubscriptionStatus.ProAnnual)
        val useCase = CanAccessFeatureUseCase(subscriptionRepo)

        assertTrue(useCase(PremiumFeature.FEC_EXPORT))
        assertTrue(useCase(PremiumFeature.B2B_PENALTIES))
        assertTrue(useCase(PremiumFeature.UNLIMITED_INVOICES))
        assertTrue(useCase(PremiumFeature.SMART_RECONCILIATION))
    }

    @Test
    fun freeUser_cannotAccessFecExportAndB2bPenalties() = runTest {
        val subscriptionRepo = MockSubscriptionRepository(SubscriptionStatus.Free)
        val useCase = CanAccessFeatureUseCase(subscriptionRepo)

        assertFalse(useCase(PremiumFeature.FEC_EXPORT))
        assertFalse(useCase(PremiumFeature.B2B_PENALTIES))
        assertFalse(useCase(PremiumFeature.SMART_RECONCILIATION))
    }
}
