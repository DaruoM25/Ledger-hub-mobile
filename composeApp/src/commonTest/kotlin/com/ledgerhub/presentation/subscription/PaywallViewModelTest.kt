package com.ledgerhub.presentation.subscription

import com.ledgerhub.data.subscription.MockSubscriptionRepository
import com.ledgerhub.domain.subscription.PremiumFeature
import com.ledgerhub.domain.subscription.SubscriptionTier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @Test
    fun initialState_hasProAnnualSelectedByDefault() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = PaywallViewModel(
            subscriptionRepository = MockSubscriptionRepository(),
            reasonFeature = PremiumFeature.UNLIMITED_INVOICES,
            dispatcher = testDispatcher,
        )

        val state = viewModel.uiState.value
        assertEquals(SubscriptionTier.PRO_ANNUAL, state.selectedTier)
        assertEquals(PremiumFeature.UNLIMITED_INVOICES, state.reasonFeature)
        assertFalse(state.isPro)
        assertFalse(state.isDismissed)
    }

    @Test
    fun selectTier_updatesSelectedTier() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = PaywallViewModel(
            subscriptionRepository = MockSubscriptionRepository(),
            dispatcher = testDispatcher,
        )

        viewModel.processIntent(PaywallIntent.SelectTier(SubscriptionTier.PRO_MONTHLY))
        assertEquals(SubscriptionTier.PRO_MONTHLY, viewModel.uiState.value.selectedTier)
    }

    @Test
    fun purchaseSelected_activatesProStatus() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = MockSubscriptionRepository()
        val viewModel = PaywallViewModel(
            subscriptionRepository = repo,
            dispatcher = testDispatcher,
        )

        viewModel.processIntent(PaywallIntent.PurchaseSelected)

        val state = viewModel.uiState.value
        assertTrue(state.isPro)
        assertNotNull(state.promoSuccessMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun applyValidDevpostPromoCode_unlocksProWithSuccessMessage() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = MockSubscriptionRepository()
        val viewModel = PaywallViewModel(
            subscriptionRepository = repo,
            dispatcher = testDispatcher,
        )

        viewModel.processIntent(PaywallIntent.PromoCodeChanged("DEVPOST2026"))
        viewModel.processIntent(PaywallIntent.ApplyPromoCode)

        val state = viewModel.uiState.value
        assertTrue(state.isPro)
        assertNotNull(state.promoSuccessMessage)
        assertNull(state.promoErrorMessage)
    }

    @Test
    fun applyInvalidPromoCode_showsErrorMessage() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = MockSubscriptionRepository()
        val viewModel = PaywallViewModel(
            subscriptionRepository = repo,
            dispatcher = testDispatcher,
        )

        viewModel.processIntent(PaywallIntent.PromoCodeChanged("WRONG_CODE"))
        viewModel.processIntent(PaywallIntent.ApplyPromoCode)

        val state = viewModel.uiState.value
        assertFalse(state.isPro)
        assertNotNull(state.promoErrorMessage)
    }

    @Test
    fun dismissIntent_setsIsDismissedTrue() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = PaywallViewModel(dispatcher = testDispatcher)

        viewModel.processIntent(PaywallIntent.Dismiss)
        assertTrue(viewModel.uiState.value.isDismissed)
    }
}
