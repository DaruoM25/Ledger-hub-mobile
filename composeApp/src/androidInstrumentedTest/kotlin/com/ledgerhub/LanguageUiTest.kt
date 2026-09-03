package com.ledgerhub

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.components.LangToggleTags
import com.ledgerhub.presentation.dashboard.DashboardTags
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test d'interface **instrumenté** (émulateur/appareil réel) du support multilingue dynamique (US-02).
 *
 * 1. Lance [App] avec une base SQLDelight **en mémoire** pré-semée d'une facture PAID à 1 234,56 €
 *    (TVA exonérée → HT = TTC, montant déterministe correspondant aux exemples du dictionnaire).
 * 2. Vérifie que l'UI démarre en **français** (défaut) : « Vue d'ensemble » + devise « 1 234,56 € ».
 * 3. Clique le sélecteur de langue (segment EN).
 * 4. Vérifie la bascule **instantanée** en anglais : « Dashboard » (et plus « Vue d'ensemble »),
 *    devise reformatée « €1,234.56 ».
 * 5. Re-clique FR → retour « Vue d'ensemble » (aller-retour).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class LanguageUiTest {

    private val NBSP = ' '

    private fun seededDatabase(): LedgerHubDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val driver = AndroidSqliteDriver(LedgerHubDatabase.Schema, context, name = null) // name=null → base en mémoire
        val database = LedgerHubDatabase(driver)

        // Même userEmail que celui câblé dans App.kt, sinon l'app ne verrait pas la facture.
        val repository = SqlDelightInvoiceRepository(database, userEmail = "demo@ledgerhub.app")
        val invoice = Invoice(
            number = "FAC-2026-0001",
            issueDate = "2026-06-24",
            issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027", "facturation@ledgerhub.app"),
            recipient = Party("Client Démo SARL", "784102336", "78410233600021", "compta@client-demo.fr"),
            // 123 456 centimes, TVA exonérée → HT = TVA(0) = TTC = 1 234,56.
            lines = listOf(
                InvoiceLine("Prestation", quantity = 1, unitPriceHt = Money(123_456), vatRate = VatRate.EXONERE),
            ),
            status = InvoiceStatus.PAID,
            dueDate = "2026-07-24",
        )
        runBlocking { repository.submitInvoice(invoice) }
        return database
    }

    @Test
    fun languageToggle_switchesEveryUiStringAndCurrencyFormat_instantly() = runComposeUiTest {
        val database = seededDatabase()
        setContent { App(database = database, startAuthenticated = true) }

        // Attendre le rendu du tableau de bord et du KPI monétaire (chargement asynchrone).
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("1${NBSP}234,56${NBSP}€", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // ── 2. État initial : FRANÇAIS ─────────────────────────────────────────
        assertTrue(
            onAllNodesWithText("Vue d'ensemble").fetchSemanticsNodes().isNotEmpty(),
            "L'UII devrait démarrer en français (« Vue d'ensemble »).",
        )
        assertTrue(
            onAllNodesWithText("1${NBSP}234,56${NBSP}€", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "La devise devrait être au format français « 1 234,56 € ».",
        )
        assertTrue(
            onAllNodesWithText("Dashboard").fetchSemanticsNodes().isEmpty(),
            "Aucun texte anglais ne devrait être visible avant la bascule.",
        )

        // ── 3. Clic sur le sélecteur de langue (segment EN) ───────────────────
        assertTrue(
            onAllNodesWithTag(LangToggleTags.EN).fetchSemanticsNodes().isNotEmpty(),
            "Le sélecteur de langue (EN) devrait être présent dans le header.",
        )
        onNodeWithTag(LangToggleTags.EN).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Dashboard").fetchSemanticsNodes().isNotEmpty()
        }

        // ── 4. Bascule instantanée : ANGLAIS ─────────────────────────────────
        assertTrue(
            onAllNodesWithText("Dashboard").fetchSemanticsNodes().isNotEmpty(),
            "Après bascule, le titre devrait être « Dashboard ».",
        )
        assertTrue(
            onAllNodesWithText("Vue d'ensemble").fetchSemanticsNodes().isEmpty(),
            "Après bascule, plus aucun « Vue d'ensemble » ne devrait subsister.",
        )
        assertTrue(
            onAllNodesWithText("€1,234.56", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "La devise devrait être reformatée en anglais « €1,234.56 ».",
        )

        // ── 5. Aller-retour : retour au FRANÇAIS ──────────────────────────────
        onNodeWithTag(LangToggleTags.FR).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Vue d'ensemble").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(onAllNodesWithText("Dashboard").fetchSemanticsNodes().isEmpty())
    }
}
