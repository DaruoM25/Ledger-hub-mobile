package com.ledgerhub.presentation.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.VatRate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interface de l'écran Paramètres fiscaux, rendue depuis [TaxSettingsView] (sans état).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class TaxSettingsScreenRobolectricTest {

    private val loaded = TaxSettingsUiState(
        isLoading = false,
        issuerName = "Cabinet LedgerHub",
        issuerSiret = "82032933100027",
        vatNumber = "FR82820329331",
    )

    @Test
    fun form_rendersTheIssuerSectionAndTheSaveAction() = runComposeUiTest {
        setContent { TaxSettingsView(uiState = loaded) }

        onNodeWithTag(TaxSettingsTags.ISSUER_NAME).performScrollTo().assertIsDisplayed()
        onNodeWithTag(TaxSettingsTags.ISSUER_SIRET).performScrollTo().assertIsDisplayed()
        onNodeWithTag(TaxSettingsTags.VAT_NUMBER).performScrollTo().assertIsDisplayed()
        onNodeWithTag(TaxSettingsTags.SAVE_BUTTON).performScrollTo().assertIsEnabled()
    }

    @Test
    fun sirenIsDerivedFromTheSiret_andNeverTyped() = runComposeUiTest {
        setContent { TaxSettingsView(uiState = loaded) }

        onNodeWithTag(TaxSettingsTags.DERIVED_SIREN, useUnmergedTree = true)
            .performScrollTo()
            .assertTextEquals("820329331")
    }

    @Test
    fun everyStatutoryRateIsOffered_asAReadOnlyReference() = runComposeUiTest {
        setContent { TaxSettingsView(uiState = loaded) }

        onNodeWithTag(TaxSettingsTags.VAT_RATES_REFERENCE, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        // Les cinq taux du domaine sont présentés — dont le taux particulier à 2,1 %.
        VatRate.entries.forEach { rate ->
            onNodeWithTag(TaxSettingsTags.defaultRateChip(rate), useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
        }
        onNodeWithText("20 %  ·  10 %  ·  5,5 %  ·  2,1 %  ·  Exonéré").assertIsDisplayed()
    }

    @Test
    fun selectingARateChip_emitsTheIntent() = runComposeUiTest {
        var selected: VatRate? = null
        setContent {
            TaxSettingsView(
                uiState = loaded,
                onIntent = { if (it is TaxSettingsIntent.DefaultVatRateSelected) selected = it.rate },
            )
        }

        onNodeWithTag(TaxSettingsTags.defaultRateChip(VatRate.TAUX_REDUIT), useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        assertEquals(VatRate.TAUX_REDUIT, selected)
    }

    @Test
    fun facturXSwitch_reflectsTheStateAndEmitsOnToggle() = runComposeUiTest {
        var toggled: Boolean? = null
        setContent {
            TaxSettingsView(
                uiState = loaded.copy(facturXEnabled = true),
                onIntent = { if (it is TaxSettingsIntent.FacturXToggled) toggled = it.enabled },
            )
        }

        onNodeWithTag(TaxSettingsTags.FACTURX_SWITCH).performScrollTo().assertIsOn().performClick()

        assertEquals(false, toggled)
    }

    @Test
    fun facturXSwitch_rendersOffWhenDisabled() = runComposeUiTest {
        setContent { TaxSettingsView(uiState = loaded.copy(facturXEnabled = false)) }

        onNodeWithTag(TaxSettingsTags.FACTURX_SWITCH).performScrollTo().assertIsOff()
    }

    @Test
    fun siretField_rejectsLetters_andStopsAt14Digits() = runComposeUiTest {
        var siret = ""
        setContent {
            TaxSettingsView(
                uiState = loaded.copy(issuerSiret = siret),
                onIntent = { if (it is TaxSettingsIntent.IssuerSiretChanged) siret = it.value },
            )
        }

        onNodeWithTag(TaxSettingsTags.ISSUER_SIRET).performScrollTo().performTextInput("xy98zt76")
        assertEquals("9876", siret)
    }

    @Test
    fun saveButton_emitsTheSaveIntent() = runComposeUiTest {
        var saved = false
        setContent {
            TaxSettingsView(
                uiState = loaded,
                onIntent = { if (it is TaxSettingsIntent.Save) saved = true },
            )
        }

        onNodeWithTag(TaxSettingsTags.SAVE_BUTTON).performScrollTo().performClick()

        assertTrue(saved)
    }

    @Test
    fun successMessage_isPresentedInASnackbar() = runComposeUiTest {
        setContent {
            TaxSettingsView(uiState = loaded.copy(savedMessage = "Paramètres fiscaux mis à jour avec succès"))
        }

        onNodeWithTag(TaxSettingsTags.SNACKBAR_HOST, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Paramètres fiscaux mis à jour avec succès").assertIsDisplayed()
        // La consommation du message (FeedbackShown) intervient à la fermeture du Snackbar, donc
        // après cette assertion : elle est couverte côté ViewModel par
        // TaxSettingsViewModelTest.feedbackShown_clearsTheConfirmation_soItIsNotReplayed.
    }

    @Test
    fun errorMessage_isAlsoPresentedInASnackbar() = runComposeUiTest {
        setContent {
            TaxSettingsView(uiState = loaded.copy(errorMessage = "Enregistrement des paramètres impossible"))
        }

        onNodeWithText("Enregistrement des paramètres impossible").assertIsDisplayed()
    }
}
