package com.ledgerhub.presentation.quoteform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Équivalent Android de [QuoteFormScreenTest] (commonTest, qui sert iosTest sans modification).
 * Voir HelloScreenRobolectricTest pour le détail de cette duplication ciblée, imposée par
 * l'impossibilité d'ajouter @RunWith(RobolectricTestRunner) à une classe partagée commonTest.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class QuoteFormScreenRobolectricTest {

    @Test
    fun initialState_submitButtonIsDisabled() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsDisplayed()
        onNodeWithTag(QuoteFormTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun typingInvalidSiren_displaysFieldError() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.ISSUER_SIREN).performScrollTo().performTextInput("123")

        onNodeWithTag(QuoteFormTags.errorTagFor(QuoteFormField.ISSUER_SIREN))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun typingQuantityAndPrice_updatesTotalTtc() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.lineQuantityTag(0)).performScrollTo().performTextInput("2")
        onNodeWithTag(QuoteFormTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("50.00")

        onNodeWithTag(QuoteFormTags.TOTAL_TTC).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun addLineButton_appendsSecondLine_withOwnRemoveButton() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.lineRemoveButtonTag(0)).performScrollTo().assertIsNotEnabled()

        onNodeWithTag(QuoteFormTags.ADD_LINE_BUTTON).performScrollTo().performClick()

        onNodeWithTag(QuoteFormTags.lineLabelTag(1)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun removeLineButton_removesLine() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.ADD_LINE_BUTTON).performScrollTo().performClick()
        onNodeWithTag(QuoteFormTags.lineLabelTag(1)).performScrollTo().assertIsDisplayed()

        onNodeWithTag(QuoteFormTags.lineRemoveButtonTag(1)).performScrollTo().performClick()

        onNodeWithTag(QuoteFormTags.lineLabelTag(1)).assertDoesNotExist()
        onNodeWithTag(QuoteFormTags.lineLabelTag(0)).performScrollTo().assertIsDisplayed()
    }
}
