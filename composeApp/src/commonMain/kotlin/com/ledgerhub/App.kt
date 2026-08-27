package com.ledgerhub

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerhub.data.creditnote.SqlDelightCreditNoteRepository
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.data.quote.SqlDelightQuoteRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.creditnote.SubmitCreditNoteUseCase
import com.ledgerhub.domain.dashboard.GetDashboardAnalyticsUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase
import com.ledgerhub.presentation.auth.LoginScreen
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormScreen
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormViewModel
import com.ledgerhub.presentation.dashboard.DashboardScreen
import com.ledgerhub.presentation.dashboard.DashboardViewModel
import com.ledgerhub.presentation.invoicedetail.InvoiceDetailScreen
import com.ledgerhub.presentation.invoicedetail.InvoiceDetailViewModel
import com.ledgerhub.presentation.invoiceform.InvoiceFormScreen
import com.ledgerhub.presentation.invoiceform.InvoiceFormViewModel
import com.ledgerhub.presentation.quotes.QuotesView
import com.ledgerhub.presentation.quotes.QuotesViewModel

/** Compte utilisateur courant, en dur tant qu'il n'y a pas de flux d'authentification (v1). */
private const val CURRENT_USER_EMAIL_PLACEHOLDER = "demo@ledgerhub.app"

/** Numéro de la facture de démonstration semée au premier lancement — voir câblage temporaire ci-dessous. */
private const val DEMO_INVOICE_NUMBER = "F-2026-DEMO"

/**
 * Écrans accessibles depuis le câblage temporaire de validation manuelle — voir [App]. Chacun est
 * branché sur les MÊMES repositories SQLDelight, donc toute donnée créée dans un écran (ex : une
 * facture Payée créée via [INVOICE_FORM]) est immédiatement visible dans les autres (ex : au
 * [DASHBOARD]).
 */
private enum class DemoDestination(val label: String) {
    AVOIR("Avoir"),
    DASHBOARD("Tableau de bord"),
    INVOICE_FORM("Facture"),
    QUOTES("Devis"),
    LOGIN("Connexion"),
}

/**
 * Point d'entrée Compose Multiplatform commun — appelé depuis androidMain et iosMain.
 * [database] est construite côté plateforme (MainActivity / MainViewController) via l'actual
 * [com.ledgerhub.db.DatabaseDriverFactory], qui a besoin d'un Context sur Android.
 *
 * CÂBLAGE TEMPORAIRE DE VALIDATION MANUELLE : en l'absence de navigation réelle, une rangée
 * d'onglets bascule entre les écrans listés dans [DemoDestination] — voir cet enum pour le détail
 * du partage des repositories. À remplacer par un vrai flux de navigation au démarrage du module
 * Dashboard.
 */
@Composable
fun App(database: LedgerHubDatabase) {
    val invoiceRepository = remember(database) {
        SqlDelightInvoiceRepository(database, userEmail = CURRENT_USER_EMAIL_PLACEHOLDER)
    }
    val creditNoteRepository = remember(database) {
        SqlDelightCreditNoteRepository(database, userEmail = CURRENT_USER_EMAIL_PLACEHOLDER)
    }
    val quoteRepository = remember(database) {
        SqlDelightQuoteRepository(database, userEmail = CURRENT_USER_EMAIL_PLACEHOLDER)
    }

    var demoInvoice by remember { mutableStateOf<Invoice?>(null) }
    var showCreditNoteForm by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf(DemoDestination.AVOIR) }

    LaunchedEffect(invoiceRepository) {
        val existing = invoiceRepository.fetchInvoices().getOrDefault(emptyList())
            .firstOrNull { it.number == DEMO_INVOICE_NUMBER }
        demoInvoice = existing ?: buildDemoInvoice().also { invoiceRepository.submitInvoice(it) }
    }

    MaterialTheme {
        // .statusBarsPadding() — QA manuelle sur émulateur (campagne 1) : la barre de statut
        // système (136px de haut sur Pixel_5_API_35) chevauchait la rangée d'onglets ci-dessous,
        // interceptant silencieusement les taps dans sa moitié supérieure. Appliqué au conteneur
        // racine plutôt qu'à la seule Row des onglets, pour que tout futur contenu ajouté en haut
        // de cet écran hérite automatiquement de la même protection.
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp, 16.dp, 16.dp, 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DemoDestination.entries.forEach { entry ->
                    if (entry == destination) {
                        Button(onClick = { destination = entry }) { Text(entry.label) }
                    } else {
                        OutlinedButton(onClick = { destination = entry }) { Text(entry.label) }
                    }
                }
            }

            when (destination) {
                DemoDestination.DASHBOARD -> DashboardScreen(
                    viewModel = remember {
                        DashboardViewModel(
                            GetDashboardAnalyticsUseCase(
                                invoiceRepository = invoiceRepository,
                                creditNoteRepository = creditNoteRepository,
                                quoteRepository = quoteRepository,
                            )
                        )
                    }
                )

                DemoDestination.INVOICE_FORM -> InvoiceFormScreen(
                    viewModel = remember {
                        InvoiceFormViewModel(submitInvoiceUseCase = SubmitInvoiceUseCase(invoiceRepository))
                    }
                )

                DemoDestination.QUOTES -> QuotesView(
                    viewModel = remember {
                        QuotesViewModel(
                            quoteRepository = quoteRepository,
                            convertQuoteToInvoiceUseCase = ConvertQuoteToInvoiceUseCase(),
                            submitInvoiceUseCase = SubmitInvoiceUseCase(invoiceRepository),
                        )
                    }
                )

                DemoDestination.LOGIN -> LoginScreen()

                DemoDestination.AVOIR -> {
                    val invoice = demoInvoice
                    when {
                        invoice == null -> Unit // Semis initial de la facture de démo en cours.
                        showCreditNoteForm -> CreditNoteFormScreen(
                            viewModel = remember(invoice) {
                                CreditNoteFormViewModel(
                                    sourceInvoice = invoice,
                                    submitCreditNoteUseCase = SubmitCreditNoteUseCase(creditNoteRepository),
                                )
                            }
                        )
                        else -> InvoiceDetailScreen(
                            viewModel = remember(invoice) { InvoiceDetailViewModel(invoice, creditNoteRepository) },
                            onCreateCreditNoteClick = { showCreditNoteForm = true },
                        )
                    }
                }
            }
        }
    }
}

private fun buildDemoInvoice(): Invoice = Invoice(
    number = DEMO_INVOICE_NUMBER,
    issueDate = "2026-08-01",
    issuer = Party("Vendeur SARL", "123456789", "12345678900012"),
    recipient = Party("Client SAS", "987654321", "98765432100045"),
    lines = listOf(
        InvoiceLine("Prestation de conseil", quantity = 2, unitPriceHt = Money(50000), vatRate = VatRate.TAUX_NORMAL),
        InvoiceLine("Formation", quantity = 1, unitPriceHt = Money(20000), vatRate = VatRate.TAUX_REDUIT),
    ),
    status = InvoiceStatus.VALIDATED,
)
