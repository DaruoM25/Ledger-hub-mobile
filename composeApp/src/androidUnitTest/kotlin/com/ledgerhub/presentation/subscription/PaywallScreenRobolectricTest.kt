package com.ledgerhub.presentation.subscription

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.data.subscription.MockSubscriptionRepository
import com.ledgerhub.domain.subscription.PremiumFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PaywallScreenRobolectricTest {

    @Test
    fun paywallScreen_rendersHeaderPlansAndCta() = runComposeUiTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val viewModel = PaywallViewModel(
            subscriptionRepository = MockSubscriptionRepository(),
            reasonFeature = PremiumFeature.UNLIMITED_INVOICES,
            dispatcher = testDispatcher,
        )

        setContent {
            PaywallScreen(viewModel = viewModel)
        }

        onNodeWithTag(PaywallTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(PaywallTags.CLOSE_BUTTON).assertIsDisplayed()
        onNodeWithTag(PaywallTags.TITLE).assertIsDisplayed()
        onNodeWithTag(PaywallTags.PLAN_MONTHLY).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaywallTags.PLAN_ANNUAL).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaywallTags.CTA_BUTTON).performScrollTo().assertIsDisplayed().assertIsEnabled()
        onNodeWithTag(PaywallTags.RESTORE_BUTTON).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun enteringDevpostPromoCode_unlocksProStatusImmediately() = runComposeUiTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val repository = MockSubscriptionRepository()
        val viewModel = PaywallViewModel(
            subscriptionRepository = repository,
            dispatcher = testDispatcher,
        )

        setContent {
            PaywallScreen(viewModel = viewModel)
        }

        onNodeWithTag(PaywallTags.PROMO_CODE_INPUT).performScrollTo().performTextInput("DEVPOST2026")
        onNodeWithTag(PaywallTags.PROMO_CODE_SUBMIT).performScrollTo().performClick()

        onNodeWithTag(PaywallTags.PROMO_SUCCESS_MESSAGE).performScrollTo().assertIsDisplayed()
        assertTrue(repository.currentStatus.isPro)
    }

    @Test
    fun clickingCloseButton_triggersDismiss() = runComposeUiTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val viewModel = PaywallViewModel(
            subscriptionRepository = MockSubscriptionRepository(),
            dispatcher = testDispatcher,
        )
        var dismissed = false

        setContent {
            PaywallScreen(
                viewModel = viewModel,
                onDismiss = { dismissed = true },
            )
        }

        onNodeWithTag(PaywallTags.CLOSE_BUTTON).performClick()
        assertTrue(dismissed || viewModel.uiState.value.isDismissed)
    }
}
