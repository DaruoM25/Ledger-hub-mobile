package com.ledgerhub.presentation.i18n

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.components.InvoiceCard
import com.ledgerhub.presentation.invoices.components.InvoiceCardTags
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Réactivité du formatage monétaire et temporel à la bascule de langue **en mémoire**.
 *
 * L'arbre de composition n'est monté qu'une fois : seule la valeur de [LocalAppLanguage] change.
 * Le test échoue donc si un montant ou une date est figé à la première composition (valeur
 * capturée dans un `remember` sans clé, formatage réalisé hors composition, cache statique…).
 *
 * Complète [com.ledgerhub.LanguageUiTest] (androidInstrumentedTest), qui couvre le même scénario
 * mais exige un émulateur via `connectedDebugAndroidTest` et ne tourne donc ni en local ni en CI.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class LocalizationReactivityTest {

    private val invoice = Invoice(
        number = "FAC-2026-0142",
        issueDate = "2026-06-24",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        // 21 053,00 € HT à 20 % → 25 263,60 € TTC, le montant relevé en recette sur le dashboard.
        lines = listOf(
            InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(2_105_300), vatRate = VatRate.TAUX_NORMAL),
        ),
        status = InvoiceStatus.PAID,
    )

    /** Monte la carte une seule fois et expose de quoi changer la langue à chaud. */
    private fun runWithLanguageSwitch(
        block: ComposeUiTest.(switchTo: (AppLanguage) -> Unit) -> Unit,
    ) = runComposeUiTest {
        var language by mutableStateOf(AppLanguage.FR)
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides language) {
                InvoiceCard(invoice = invoice, onClick = {})
            }
        }
        block { target -> language = target }
    }

    @Test
    fun switchingToEnglish_reformatsTheAmount_withoutRemountingTheTree() = runWithLanguageSwitch { switchTo ->
        // FR : espace insécable pour les milliers, virgule décimale, symbole € suffixé.
        onNodeWithText("25 263,60 €").assertIsDisplayed()

        switchTo(AppLanguage.EN)

        // EN : symbole € préfixé, virgule pour les milliers, point décimal, aucune espace.
        onNodeWithText("€25,263.60").assertIsDisplayed()
    }

    @Test
    fun switchingToEnglish_reformatsTheDate_withoutRemountingTheTree() = runWithLanguageSwitch { switchTo ->
        onNodeWithText("Émise le 24/06/2026").assertIsDisplayed()

        switchTo(AppLanguage.EN)

        onNodeWithText("Issued on Jun 24, 2026").assertIsDisplayed()
    }

    @Test
    fun switchingBackToFrench_restoresTheFrenchFormatting() = runWithLanguageSwitch { switchTo ->
        switchTo(AppLanguage.EN)
        onNodeWithText("€25,263.60").assertIsDisplayed()

        switchTo(AppLanguage.FR)

        onNodeWithText("25 263,60 €").assertIsDisplayed()
        onNodeWithText("Émise le 24/06/2026").assertIsDisplayed()
    }

    @Test
    fun switchingLanguage_alsoRetranslatesTheStatusTag() = runWithLanguageSwitch { switchTo ->
        // La carte fusionne ses descendants sémantiques : la pastille se cible en arbre non fusionné.
        onNodeWithTag(InvoiceCardTags.statusTag(invoice.number), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText("Payée").assertIsDisplayed()

        switchTo(AppLanguage.EN)

        onNodeWithText("Paid").assertIsDisplayed()
    }
}
