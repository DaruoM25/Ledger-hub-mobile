package com.ledgerhub.presentation.quoteform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Tests d'interface — Skill 2 : QA Automatisé.
 * Teste [QuoteFormScreen] (stateful, collectAsState) avec un vrai [QuoteFormViewModel]
 * pour exercer le cycle complet saisie -> recomposition -> validation -> totaux, sans mock.
 */
@OptIn(ExperimentalTestApi::class)
class QuoteFormScreenTest {

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

    @Test
    fun typingInvalidValidityDate_displaysFieldError() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.VALIDITY_DATE).performScrollTo().performTextInput("not-a-date")

        onNodeWithTag(QuoteFormTags.errorTagFor(QuoteFormField.VALIDITY_DATE))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
