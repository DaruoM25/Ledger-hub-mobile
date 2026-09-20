package com.ledgerhub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.components.LangToggle
import com.ledgerhub.presentation.components.ThemeToggle
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.data.quote.SqlDelightQuoteRepository
import com.ledgerhub.data.creditnote.SqlDelightCreditNoteRepository
import com.ledgerhub.data.directory.CachingDirectoryRepository
import com.ledgerhub.data.directory.MockDirectoryRepository
import com.ledgerhub.data.reconciliation.MockBankTransactionRepository
import com.ledgerhub.data.reconciliation.SqlDelightReconciliationRepository
import com.ledgerhub.data.directory.SqlDelightDirectoryRepository
import com.ledgerhub.data.audit.SqlDelightAuditRepository
import com.ledgerhub.data.auth.SqlDelightAuthRepository
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.data.client.SqlDelightClientRepository
import com.ledgerhub.data.repository.LocalLedgerRepository
import com.ledgerhub.data.settings.SqlDelightTaxSettingsRepository
import com.ledgerhub.data.theme.SqlDelightThemePreferenceRepository
import com.ledgerhub.data.sirene.KtorSireneLookupService
import com.ledgerhub.data.ereporting.SqlDelightEReportingRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.dashboard.GetDashboardAnalyticsUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.ChangeInvoiceStatusUseCase
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.command.CommandAction
import com.ledgerhub.domain.command.CommandPaletteShortcut
import com.ledgerhub.domain.export.DocumentExporter
import com.ledgerhub.domain.export.NoOpDocumentExporter
import com.ledgerhub.domain.facturx.FacturXGenerator
import com.ledgerhub.domain.facturx.toFacturXDocument
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.theme.ResolvedTheme
import com.ledgerhub.domain.theme.ThemeMode
import com.ledgerhub.presentation.dashboard.DashboardIntent
import com.ledgerhub.presentation.dashboard.DashboardScreen
import com.ledgerhub.presentation.dashboard.DashboardViewModel
import com.ledgerhub.presentation.invoiceform.InvoiceFormScreen
import com.ledgerhub.presentation.invoiceform.InvoiceFormViewModel
import com.ledgerhub.presentation.invoices.InvoiceDetailScreen
import com.ledgerhub.presentation.invoices.InvoiceDetailViewModel
import com.ledgerhub.presentation.invoices.InvoiceListIntent
import com.ledgerhub.presentation.invoices.InvoiceStatusFilter
import com.ledgerhub.presentation.invoices.InvoiceListScreen
import com.ledgerhub.presentation.invoices.InvoiceListViewModel
import com.ledgerhub.presentation.clients.ClientsScreen
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormScreen
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormViewModel
import com.ledgerhub.presentation.clients.ClientsViewModel
import com.ledgerhub.presentation.clients.ClientsIntent
import com.ledgerhub.presentation.command.CommandPalette
import com.ledgerhub.presentation.command.CommandPaletteIntent
import com.ledgerhub.presentation.command.CommandPaletteTrigger
import com.ledgerhub.presentation.command.CommandPaletteViewModel
import com.ledgerhub.presentation.directory.DirectoryScreen
import com.ledgerhub.presentation.directory.DirectoryViewModel
import com.ledgerhub.presentation.export.ExportModalSheet
import com.ledgerhub.presentation.export.ExportModalTrigger
import com.ledgerhub.presentation.export.ExportViewModel
import com.ledgerhub.presentation.integrations.IntegrationsHubScreen
import com.ledgerhub.presentation.integrations.IntegrationsHubTrigger
import com.ledgerhub.presentation.integrations.IntegrationsHubViewModel
import com.ledgerhub.presentation.reconciliation.ReconciliationScreen
import com.ledgerhub.presentation.reconciliation.ReconciliationViewModel
import com.ledgerhub.presentation.settings.TaxSettingsScreen
import com.ledgerhub.presentation.ereporting.EReportingScreen
import com.ledgerhub.presentation.ereporting.EReportingViewModel
import com.ledgerhub.presentation.auth.AuthScreen
import com.ledgerhub.presentation.auth.AuthViewModel
import com.ledgerhub.data.auth.KtorAuthApiClient
import com.ledgerhub.domain.auth.DeleteAccountUseCase
import com.ledgerhub.domain.auth.RequestPasswordResetUseCase
import com.ledgerhub.presentation.auth.forgotpassword.ForgotPasswordScreen
import com.ledgerhub.presentation.auth.forgotpassword.ForgotPasswordViewModel
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteLine
import com.ledgerhub.domain.quote.QuoteStatus
import com.ledgerhub.domain.quote.SubmitQuoteUseCase
import com.ledgerhub.presentation.quotes.QuotesIntent
import com.ledgerhub.presentation.quotes.QuoteStatusFilter
import com.ledgerhub.presentation.quotes.QuotesView
import com.ledgerhub.presentation.quotes.QuotesViewModel
import com.ledgerhub.presentation.quoteform.QuoteFormScreen
import com.ledgerhub.presentation.quoteform.QuoteFormViewModel
import com.ledgerhub.presentation.settings.TaxSettingsViewModel
import com.ledgerhub.presentation.theme.LedgerHubTheme
import com.ledgerhub.presentation.theme.ThemeIntent
import com.ledgerhub.presentation.theme.ThemeViewModel
import com.ledgerhub.data.subscription.MockSubscriptionRepository
import com.ledgerhub.domain.subscription.CanAccessFeatureUseCase
import com.ledgerhub.domain.subscription.CheckInvoiceQuotaUseCase
import com.ledgerhub.domain.subscription.PremiumFeature
import com.ledgerhub.domain.subscription.SubscriptionRepository
import com.ledgerhub.domain.subscription.SubscriptionStatus
import com.ledgerhub.presentation.subscription.PaywallScreen
import com.ledgerhub.presentation.subscription.PaywallViewModel
import com.ledgerhub.data.degraded.SqlDelightSyncQueueRepository
import com.ledgerhub.domain.degraded.DegradedModeNetworkState
import com.ledgerhub.domain.degraded.EnqueueDegradedInvoiceUseCase
import com.ledgerhub.domain.degraded.ProcessSyncQueueBatchUseCase
import com.ledgerhub.presentation.degraded.NetworkSimulationSelector
import com.ledgerhub.presentation.degraded.SyncQueueViewModel
import com.ledgerhub.data.support.SqlDelightFeatureFeedbackRepository
import com.ledgerhub.data.support.SqlDelightSupportRepository
import com.ledgerhub.domain.support.CreateSupportTicketUseCase
import com.ledgerhub.domain.support.GetFeatureRequestsUseCase
import com.ledgerhub.domain.support.SubmitFeatureRequestUseCase
import com.ledgerhub.domain.support.VoteFeatureRequestUseCase
import com.ledgerhub.presentation.support.SupportFeedbackScreen
import com.ledgerhub.presentation.support.SupportFeedbackViewModel
import com.ledgerhub.domain.vat.CalculateVatMetricsUseCase
import com.ledgerhub.presentation.vat.VatDashboardScreen
import com.ledgerhub.presentation.vat.VatDashboardViewModel
import com.ledgerhub.data.vault.SqlDelightVaultRepository
import com.ledgerhub.domain.vault.GenerateInvoiceSealUseCase
import com.ledgerhub.domain.vault.VerifyVaultIntegrityUseCase
import com.ledgerhub.presentation.vault.VaultArchiveScreen
import com.ledgerhub.presentation.vault.VaultArchiveViewModel
import com.ledgerhub.data.inbox.SqlDelightInboxRepository
import com.ledgerhub.domain.inbox.ProcessReceivedInvoiceUseCase
import com.ledgerhub.presentation.inbox.IncomingInvoicesScreen
import com.ledgerhub.presentation.inbox.IncomingInvoicesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Compte utilisateur courant, en dur tant qu'il n'y a pas de flux d'authentification (v1). */
private const val CURRENT_USER_EMAIL_PLACEHOLDER = "demo@ledgerhub.app"

/** Seuil de largeur Material 3 « expanded » : au-delà, sidebar permanente ; en-deçà, barre du bas. */
private val ExpandedWidthThreshold = 840.dp

/** Destinations du shell de navigation — parité Web (sidebar / barre du bas). Libellés traduits via [titleKey]. */
internal enum class Destination(val titleKey: StringKey, val glyph: String) {
    OVERVIEW(StringKey.NAV_OVERVIEW, "▦"),
    QUOTES(StringKey.NAV_QUOTES, "📝"),
    INVOICES(StringKey.NAV_INVOICES, "🧾"),
    CLIENTS(StringKey.NAV_CLIENTS, "👥"),
    DIRECTORY(StringKey.NAV_DIRECTORY, "📇"),
    RECONCILIATION(StringKey.NAV_RECONCILIATION, "🔗"),
    SETTINGS(StringKey.NAV_SETTINGS, "⚙️"),
}

/** Écran affiché par-dessus les onglets (formulaire de création, détail d'une facture). */
private sealed interface Overlay {
    data object None : Overlay
    data object CreateInvoice : Overlay
    data object CreateQuote : Overlay
    data class EditQuote(val quote: Quote) : Overlay
    data class CreateInvoiceFromQuote(val quote: Quote) : Overlay

    /** Hub d'intégrations (US-20) — vitrine des connecteurs, tous verrouillés à ce stade. */
    data object Integrations : Overlay

    /**
     * Modale d'export comptable FEC & Factur-X (US-22).
     *
     * Superposée aux onglets et non substituée à eux : c'est une feuille, pas un écran. Le shell
     * continue donc de rendre l'onglet courant derrière elle (voir [TabsContent]).
     */
    data object ExportModal : Overlay

    /**
     * Déclarations e-Reporting (US-08), rendues joignables en US-26.
     *
     * En surimpression et non en septième onglet : la barre de navigation compacte porte déjà six
     * destinations, dont les libellés se coupent sur un Pixel 5 — un septième les rendrait
     * illisibles. Le point d'entrée vit donc dans l'onglet Paramètres, où l'on va déjà régler la
     * conformité.
     */
    data object EReporting : Overlay

    data class InvoiceDetail(val number: String) : Overlay

    /** Émission d'un avoir annulant [invoice] — US-05. */
    data class CreditNote(val invoice: Invoice) : Overlay

    /** Écran Paywall & Monétisation (Sprint 2 — RevenueCat & Quotas). */
    data class Paywall(val reason: PremiumFeature? = null) : Overlay

    /** Support & Feedback Loop (US-30). */
    data object SupportFeedback : Overlay

    /** Module Analytique TVA & Déclaration 3310-CA3 (US-31). */
    data object VatDashboard : Overlay

    /** Coffre-fort Numérique & Piste d'Audit Fiable (US-32). */
    data object VaultArchive : Overlay

    /** Boîte de Réception Factures Fournisseurs & Anti-Doublon (US-33). */
    data object IncomingInvoices : Overlay
}

/**
 * Point d'entrée Compose Multiplatform commun — appelé depuis androidMain et iosMain.
 * [database] est construite côté plateforme (MainActivity / MainViewController).
 *
 * Shell responsive : barre de navigation en bas sur mobile portrait (Compact), sidebar permanente
 * + canvas central sur tablette/paysage (Expanded ≥ [ExpandedWidthThreshold]). Les quatre écrans
 * partagent les MÊMES repositories SQLDelight, donc toute facture émise via le formulaire est
 * immédiatement visible dans « Factures » et sur le tableau de bord (voir [LocalLedgerRepository]).
 */
@Composable
fun App(
    database: LedgerHubDatabase,
    /** Remise des documents générés à la plateforme — voir [DocumentExporter]. */
    documentExporter: DocumentExporter = NoOpDocumentExporter,
    /**
     * Ouvre directement le shell, sans passer par l'écran d'authentification (US-26).
     *
     * Le défaut est `false` — la porte est fermée par défaut, et c'est le sens sûr : un appelant
     * qui oublierait le paramètre livrerait une application **gardée**, jamais une application
     * ouverte. Les tests d'interface du shell, eux, passent `true` : ils éprouvent la navigation,
     * pas la porte, et celle-ci a ses propres tests.
     */
    startAuthenticated: Boolean = false,
) {
    val authApiClient = remember { KtorAuthApiClient() }
    val authRepository = remember(database) { SqlDelightAuthRepository(database) }
    val currentAccount by authRepository.observeCurrentAccount().collectAsState(initial = null)
    val currentUserEmail = currentAccount?.email ?: (if (startAuthenticated) CURRENT_USER_EMAIL_PLACEHOLDER else "")

    val invoiceRepository = remember(database, currentUserEmail) {
        SqlDelightInvoiceRepository(database, userEmail = currentUserEmail.ifEmpty { CURRENT_USER_EMAIL_PLACEHOLDER })
    }
    val creditNoteRepository = remember(database, currentUserEmail) {
        SqlDelightCreditNoteRepository(database, userEmail = currentUserEmail.ifEmpty { CURRENT_USER_EMAIL_PLACEHOLDER })
    }
    val quoteRepository = remember(database, currentUserEmail) {
        SqlDelightQuoteRepository(database, userEmail = currentUserEmail.ifEmpty { CURRENT_USER_EMAIL_PLACEHOLDER })
    }
    val ledgerRepository = remember(invoiceRepository) { LocalLedgerRepository(invoiceRepository) }
    val clientRepository = remember(database) { SqlDelightClientRepository(database) }
    // Annuaire DGFIP (US-09) : résolution locale (Mock) doublée d'un cache SQLDelight persistant.
    val directoryRepository = remember(database) {
        CachingDirectoryRepository(
            source = MockDirectoryRepository(),
            cache = SqlDelightDirectoryRepository(database),
        )
    }
    val auditRepository = remember(database) { SqlDelightAuditRepository(database) }
    val subscriptionRepository = remember { MockSubscriptionRepository() }
    val checkInvoiceQuotaUseCase = remember(invoiceRepository, subscriptionRepository) {
        CheckInvoiceQuotaUseCase(invoiceRepository, subscriptionRepository)
    }
    val canAccessFeatureUseCase = remember(subscriptionRepository, checkInvoiceQuotaUseCase) {
        CanAccessFeatureUseCase(subscriptionRepository, checkInvoiceQuotaUseCase)
    }
    // Rapprochement bancaire (US-18) : releve simule (aucun connecteur bancaire n'existe encore),
    // lettrages persistes en SQLDelight.
    val reconciliationRepository = remember(database) { SqlDelightReconciliationRepository(database) }
    val bankTransactionRepository = remember { MockBankTransactionRepository() }
    // Toute transition de statut passe par ce use case : il valide contre la machine d'états
    // avant que le dépôt n'écrive statut et trace d'audit dans une même transaction.
    val changeInvoiceStatusUseCase = remember(invoiceRepository) {
        ChangeInvoiceStatusUseCase(invoiceRepository)
    }
    val taxSettingsRepository = remember(database) { SqlDelightTaxSettingsRepository(database) }
    // Préférence de thème (US-25) : persistée comme le reste, dans la base SQLDelight partagée.
    val themePreferenceRepository = remember(database) { SqlDelightThemePreferenceRepository(database) }
    // e-Reporting (US-08), branché sur la base partagée en US-26.
    val eReportingRepository = remember(database) { SqlDelightEReportingRepository(database) }

    // ViewModels des onglets — recréés ou réactualisés dès que le compte change.
    val dashboardViewModel = remember(invoiceRepository, creditNoteRepository, quoteRepository) {
        DashboardViewModel(
            GetDashboardAnalyticsUseCase(invoiceRepository, creditNoteRepository, quoteRepository),
        )
    }
    val invoiceListViewModel = remember(ledgerRepository, creditNoteRepository) {
        InvoiceListViewModel(ledgerRepository, creditNoteRepository)
    }
    val quotesViewModel = remember(quoteRepository, invoiceRepository) {
        QuotesViewModel(
            quoteRepository = quoteRepository,
            submitInvoiceUseCase = SubmitInvoiceUseCase(invoiceRepository),
        )
    }
    val sireneLookupService = remember { com.ledgerhub.data.sirene.KtorSireneLookupService() }
    val clientsViewModel = remember(clientRepository, sireneLookupService) {
        ClientsViewModel(clientRepository, sireneLookupService)
    }
    val directoryViewModel = remember { DirectoryViewModel(directoryRepository) }
    val deleteAccountUseCase = remember(authRepository, authApiClient) {
        DeleteAccountUseCase(authRepository, authApiClient)
    }
    val taxSettingsViewModel = remember(taxSettingsRepository, deleteAccountUseCase, authRepository, currentUserEmail) {
        TaxSettingsViewModel(
            repository = taxSettingsRepository,
            deleteAccountUseCase = deleteAccountUseCase,
            authRepository = authRepository,
            currentUserEmail = currentUserEmail.ifEmpty { CURRENT_USER_EMAIL_PLACEHOLDER },
        )
    }
    val themeViewModel = remember { ThemeViewModel(themePreferenceRepository) }
    val reconciliationViewModel = remember(invoiceRepository, bankTransactionRepository, reconciliationRepository) {
        ReconciliationViewModel(
            invoiceRepository = invoiceRepository,
            bankTransactionRepository = bankTransactionRepository,
            reconciliationRepository = reconciliationRepository,
        )
    }

    // Mode Dégradé & Continuité Économique (US-29)
    val syncQueueRepository = remember(database) { SqlDelightSyncQueueRepository(database) }
    val enqueueDegradedInvoiceUseCase = remember(invoiceRepository, syncQueueRepository) {
        EnqueueDegradedInvoiceUseCase(invoiceRepository, syncQueueRepository)
    }
    val processSyncQueueBatchUseCase = remember(syncQueueRepository, invoiceRepository) {
        ProcessSyncQueueBatchUseCase(syncQueueRepository, invoiceRepository)
    }
    val syncQueueViewModel = remember(syncQueueRepository, processSyncQueueBatchUseCase) {
        SyncQueueViewModel(syncQueueRepository, processSyncQueueBatchUseCase)
    }
    DisposableEffect(syncQueueViewModel) {
        onDispose { syncQueueViewModel.onCleared() }
    }
    var networkSimulationState by remember { mutableStateOf(DegradedModeNetworkState.OPERATIONAL) }
    val onToggleNetworkSimulation = {
        networkSimulationState = if (networkSimulationState == DegradedModeNetworkState.OPERATIONAL) {
            DegradedModeNetworkState.OUTAGE
        } else {
            DegradedModeNetworkState.OPERATIONAL
        }
    }

    // Support & Feedback Loop (US-30)
    val supportRepository = remember(database) { SqlDelightSupportRepository(database) }
    val featureFeedbackRepository = remember(database) { SqlDelightFeatureFeedbackRepository(database) }
    val vaultRepository = remember(database) { SqlDelightVaultRepository(database) }
    val inboxRepository = remember(database) { SqlDelightInboxRepository(database) }
    val createSupportTicketUseCase = remember(supportRepository) { CreateSupportTicketUseCase(supportRepository) }
    val getFeatureRequestsUseCase = remember(featureFeedbackRepository) { GetFeatureRequestsUseCase(featureFeedbackRepository) }
    val submitFeatureRequestUseCase = remember(featureFeedbackRepository) { SubmitFeatureRequestUseCase(featureFeedbackRepository) }
    val voteFeatureRequestUseCase = remember(featureFeedbackRepository) { VoteFeatureRequestUseCase(featureFeedbackRepository) }
    val supportFeedbackViewModel = remember(supportRepository, featureFeedbackRepository, currentUserEmail) {
        SupportFeedbackViewModel(
            supportRepository = supportRepository,
            createSupportTicketUseCase = createSupportTicketUseCase,
            getFeatureRequestsUseCase = getFeatureRequestsUseCase,
            submitFeatureRequestUseCase = submitFeatureRequestUseCase,
            voteFeatureRequestUseCase = voteFeatureRequestUseCase,
            currentUserId = currentUserEmail.ifEmpty { CURRENT_USER_EMAIL_PLACEHOLDER },
            currentUserEmail = currentUserEmail.ifEmpty { CURRENT_USER_EMAIL_PLACEHOLDER },
        )
    }
    DisposableEffect(supportFeedbackViewModel) {
        onDispose { supportFeedbackViewModel.onCleared() }
    }

    // Le semis est strictement réservé au compte de démo ou au mode startAuthenticated de test.
    // Un compte nouvellement enregistré commence avec une base vierge (Empty State).
    LaunchedEffect(invoiceRepository, quoteRepository, currentUserEmail, startAuthenticated) {
        if (currentUserEmail == CURRENT_USER_EMAIL_PLACEHOLDER && startAuthenticated) {
            val seeded = withContext(Dispatchers.Default) {
                val invoicesSeeded = seedDemoDataIfEmpty(invoiceRepository)
                val quotesSeeded = seedDemoQuotesIfEmpty(quoteRepository)
                invoicesSeeded || quotesSeeded
            }
            if (seeded) {
                dashboardViewModel.processIntent(DashboardIntent.LoadDashboard)
                invoiceListViewModel.processIntent(InvoiceListIntent.Retry)
                quotesViewModel.processIntent(QuotesIntent.LoadQuotes)
            }
        } else {
            dashboardViewModel.processIntent(DashboardIntent.LoadDashboard)
            invoiceListViewModel.processIntent(InvoiceListIntent.Retry)
            quotesViewModel.processIntent(QuotesIntent.LoadQuotes)
        }
    }

    var destination by remember { mutableStateOf(Destination.OVERVIEW) }

    // Les paramètres fiscaux alimentent l'émetteur et le taux par défaut du formulaire de facture.
    // Relus à chaque changement d'onglet : quitter l'écran Paramètres suffit à les propager, sans
    // couplage entre les deux ViewModels. La table ne compte qu'une ligne, la lecture est triviale.
    var taxSettings by remember { mutableStateOf(TaxSettings.Default) }
    // Hors thread principal pour la même raison que le semis ci-dessus : lecture SQLite synchrone.
    LaunchedEffect(taxSettingsRepository, destination) {
        taxSettings = withContext(Dispatchers.Default) {
            taxSettingsRepository.loadSettings().getOrDefault(TaxSettings.Default)
        }
    }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    val onOpenSupportFeedback = { overlay = Overlay.SupportFeedback }
    val onOpenVatDashboard = { overlay = Overlay.VatDashboard }
    val onOpenVaultArchive = { overlay = Overlay.VaultArchive }
    val onOpenIncomingInvoices = { overlay = Overlay.IncomingInvoices }
    // Langue active — propagée à tout l'arbre via LocalAppLanguage (WS2). Défaut : français.
    var language by remember { mutableStateOf(AppLanguage.FR) }

    // Thème actif (US-25). La préférence est relue une seule fois : c'est ensuite le bouton de
    // bascule qui fait autorité, et rien d'autre dans l'app ne l'écrit.
    val themeState by themeViewModel.uiState.collectAsState()
    LaunchedEffect(themeViewModel) { themeViewModel.processIntent(ThemeIntent.Load) }

    val appScope = rememberCoroutineScope()
    val onCreateInvoice = {
        appScope.launch {
            val quota = checkInvoiceQuotaUseCase()
            if (quota.isQuotaReached) {
                overlay = Overlay.Paywall(PremiumFeature.UNLIMITED_INVOICES)
            } else {
                overlay = Overlay.CreateInvoice
            }
        }
        Unit
    }
    val onCreateQuote = { overlay = Overlay.CreateQuote }
    val onEditQuote = { quote: Quote -> overlay = Overlay.EditQuote(quote) }
    val onConvertToInvoice = { quote: Quote -> overlay = Overlay.CreateInvoiceFromQuote(quote) }
    val onNavigateToQuotesWithFilter = { filter: QuoteStatusFilter ->
        destination = Destination.QUOTES
        overlay = Overlay.None
        quotesViewModel.processIntent(QuotesIntent.FilterSelected(filter))
    }
    val onOpenIntegrations = { overlay = Overlay.Integrations }
    val onOpenExportModal = { overlay = Overlay.ExportModal }
    val onOpenEReporting = { overlay = Overlay.EReporting }
    val onCreateCreditNote = { invoice: Invoice -> overlay = Overlay.CreditNote(invoice) }

    // Export Factur-X (US-06) : le XML est généré à la demande depuis les données déjà en
    // mémoire, puis remis à la plateforme. Aucun état persisté — un export est un geste, pas
    // un document de plus à stocker.
    val exportScope = rememberCoroutineScope()
    val onExportInvoiceXml = { invoice: Invoice ->
        exportScope.launch {
            documentExporter.export(
                fileName = DocumentExporter.FACTUR_X_FILE_NAME,
                mimeType = DocumentExporter.XML_MIME_TYPE,
                content = FacturXGenerator.generate(invoice.toFacturXDocument(taxSettings)),
            )
        }
        Unit
    }
    // ── Palette de commandes (US-19) ─────────────────────────────────────────
    // Le ViewModel ne connait aucune destination : il publie l'action choisie, le shell decide
    // ou elle mene. C'est ce qui permet de tester la palette sans navigation ni composition.
    val commandPaletteViewModel = remember { CommandPaletteViewModel() }
    val commandPaletteState by commandPaletteViewModel.uiState.collectAsState()

    val onOpenCommandPalette = {
        commandPaletteViewModel.processIntent(CommandPaletteIntent.Open, language)
    }

    val onExportCreditNoteXml = { creditNoteNumber: String ->
        exportScope.launch {
            creditNoteRepository.fetchCreditNotes().getOrNull()
                ?.firstOrNull { it.number == creditNoteNumber }
                ?.let { creditNote ->
                    documentExporter.export(
                        fileName = DocumentExporter.FACTUR_X_FILE_NAME,
                        mimeType = DocumentExporter.XML_MIME_TYPE,
                        content = FacturXGenerator.generate(creditNote.toFacturXDocument(taxSettings)),
                    )
                }
        }
        Unit
    }
    val onBackToTabs = {
        overlay = Overlay.None
        // La liste et le tableau de bord peuvent avoir de nouvelles données après une émission.
        invoiceListViewModel.processIntent(InvoiceListIntent.Retry)
        quotesViewModel.processIntent(QuotesIntent.LoadQuotes)
        dashboardViewModel.processIntent(DashboardIntent.LoadDashboard)
        clientsViewModel.processIntent(ClientsIntent.Load)
    }

    // L'action choisie est prise en charge ici, puis acquittee : sans accuse, elle resterait dans
    // l'etat et se rejouerait a la moindre recomposition.
    LaunchedEffect(commandPaletteState.executedAction) {
        when (commandPaletteState.executedAction) {
            null -> Unit
            CommandAction.CREATE_INVOICE -> {
                onCreateInvoice()
                commandPaletteViewModel.processIntent(CommandPaletteIntent.ActionConsumed, language)
            }

            CommandAction.REMIND_OVERDUE -> {
                // La liste des factures, deja filtree sur les creances echues : l'utilisateur
                // arrive sur ce qu'il a demande, pas sur une liste ou tout reste a trouver.
                overlay = Overlay.None
                destination = Destination.INVOICES
                invoiceListViewModel.processIntent(
                    InvoiceListIntent.FilterSelected(InvoiceStatusFilter.OVERDUE),
                )
                commandPaletteViewModel.processIntent(CommandPaletteIntent.ActionConsumed, language)
            }

            CommandAction.EXPORT_ACCOUNTING -> {
                // L'export ne part plus en un clic aveugle (US-19) : la palette ouvre desormais la
                // modale, ou l'utilisateur choisit sa periode et son format avant de generer.
                onOpenExportModal()
                commandPaletteViewModel.processIntent(CommandPaletteIntent.ActionConsumed, language)
            }
        }
    }

    // Le raccourci exige un noeud focalise : sans focus, aucun evenement clavier n'atteint jamais
    // l'arbre. La regle de reconnaissance elle-meme vit dans le domaine (CommandPaletteShortcut),
    // seul moyen de la couvrir par un test — ni Robolectric ni l'emulateur n'ont de clavier.
    val shortcutFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { shortcutFocusRequester.requestFocus() } }

    // ── Porte d'authentification (US-26) ────────────────────────────────────────────────────
    // `rememberSaveable` : une rotation ne doit pas redemander une connexion. La session ne
    // survit en revanche pas à la fermeture du processus — aucun jeton n'est persisté, et
    // prétendre le contraire supposerait un stockage sécurisé qui n'existe pas encore ici.
    var authenticated by rememberSaveable { mutableStateOf(startAuthenticated) }
    var isAuthResolved by remember { mutableStateOf(startAuthenticated) }

    LaunchedEffect(authRepository) {
        if (!startAuthenticated) {
            try {
                val account = authRepository.getCurrentAccount()
                authenticated = (account != null)
            } catch (e: Throwable) {
                authenticated = false
            } finally {
                isAuthResolved = true
            }
        } else {
            isAuthResolved = true
        }
    }

    // Déconnexion réactive dès la confirmation de la suppression de compte (RGPD Art. 17) ou déconnexion explicite
    val taxSettingsUiState by taxSettingsViewModel.uiState.collectAsState()
    LaunchedEffect(taxSettingsUiState.accountDeleted, taxSettingsUiState.loggedOut) {
        if (taxSettingsUiState.accountDeleted || taxSettingsUiState.loggedOut) {
            authenticated = false
            destination = Destination.OVERVIEW
            overlay = Overlay.None
        }
    }

    if (!isAuthResolved) {
        LedgerHubTheme(mode = themeState.mode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LedgerHubTheme.palette.Background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = LedgerHubTheme.palette.Accent)
            }
        }
        return
    }

    if (!authenticated) {
        LedgerHubTheme(mode = themeState.mode) {
            CompositionLocalProvider(LocalAppLanguage provides language) {
                AuthGate(
                    database = database,
                    authRepository = authRepository,
                    onAuthenticated = { authenticated = true },
                )
            }
        }
        // Sortie anticipée plutôt qu'un `else` enveloppant tout le shell : la composition du shell
        // n'a alors simplement pas lieu tant que la porte est fermée, et le corps de `App` reste
        // lisible au lieu d'être décalé d'un niveau sur plusieurs centaines de lignes.
        return
    }

    LedgerHubTheme(mode = themeState.mode) {
        // Lue SOUS LedgerHubTheme : c'est lui qui résout `SYSTEM`, le shell n'a pas à le refaire.
        val resolvedTheme = LedgerHubTheme.resolved
        val onToggleTheme = { mode: ThemeMode -> themeViewModel.processIntent(ThemeIntent.Select(mode)) }

        CompositionLocalProvider(LocalAppLanguage provides language) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(shortcutFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val triggered = CommandPaletteShortcut.isTriggeredBy(
                            keyLabel = event.key.toString(),
                            isCtrlPressed = event.isCtrlPressed,
                            isMetaPressed = event.isMetaPressed,
                        )
                        if (triggered) onOpenCommandPalette()
                        triggered
                    },
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val windowSizeClass = com.ledgerhub.presentation.adaptive.calculateWindowSizeClass(maxWidth, maxHeight)

                    CompositionLocalProvider(com.ledgerhub.presentation.adaptive.LocalWindowSizeClass provides windowSizeClass) {
                        when (windowSizeClass.widthSizeClass) {
                            com.ledgerhub.presentation.adaptive.WindowWidthSizeClass.EXPANDED -> {
                                // Mode Grand Écran / Tablette Paysage (>= 840 dp) : Sidebar permanente + Canvas central
                                Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                                    LedgerSidebar(
                                        selected = destination,
                                        language = language,
                                        themeMode = themeState.mode,
                                        resolvedTheme = resolvedTheme,
                                        onSelect = { destination = it; overlay = Overlay.None },
                                        onCreateInvoice = onCreateInvoice,
                                        onSelectLanguage = { language = it },
                                        onToggleTheme = onToggleTheme,
                                        onOpenCommandPalette = onOpenCommandPalette,
                                        onOpenIntegrations = onOpenIntegrations,
                                        onOpenExportModal = onOpenExportModal,
                                        networkState = networkSimulationState,
                                        onToggleNetwork = onToggleNetworkSimulation,
                                    )
                                    Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                                        Card(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        ) {
                                            ShellContent(
                                                destination = destination,
                                                onSelectDestination = { destination = it },
                                                overlay = overlay,
                                                onCreateInvoice = onCreateInvoice,
                                                onBack = onBackToTabs,
                                                onOpenInvoice = { overlay = Overlay.InvoiceDetail(it) },
                                                invoiceRepository = invoiceRepository,
                                                ledgerRepository = ledgerRepository,
                                                creditNoteRepository = creditNoteRepository,
                                                quoteRepository = quoteRepository,
                                                auditRepository = auditRepository,
                                                changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
                                                onCreateCreditNote = onCreateCreditNote,
                                                onCreateQuote = onCreateQuote,
                                                onEditQuote = onEditQuote,
                                                onConvertToInvoice = onConvertToInvoice,
                                                onNavigateToQuotesWithFilter = onNavigateToQuotesWithFilter,
                                                onExportInvoiceXml = onExportInvoiceXml,
                                                onExportCreditNoteXml = onExportCreditNoteXml,
                                                dashboardViewModel = dashboardViewModel,
                                                invoiceListViewModel = invoiceListViewModel,
                                                quotesViewModel = quotesViewModel,
                                                clientsViewModel = clientsViewModel,
                                                clientRepository = clientRepository,
                                                sireneLookupService = sireneLookupService,
                                                directoryViewModel = directoryViewModel,
                                                reconciliationViewModel = reconciliationViewModel,
                                                taxSettingsViewModel = taxSettingsViewModel,
                                                taxSettings = taxSettings,
                                                eReportingRepository = eReportingRepository,
                                                onOpenEReporting = onOpenEReporting,
                                                subscriptionRepository = subscriptionRepository,
                                                networkState = networkSimulationState,
                                                enqueueDegradedInvoiceUseCase = enqueueDegradedInvoiceUseCase,
                                                syncQueueViewModel = syncQueueViewModel,
                                                supportFeedbackViewModel = supportFeedbackViewModel,
                                                vaultRepository = vaultRepository,
                                                inboxRepository = inboxRepository,
                                                onOpenSupportFeedback = onOpenSupportFeedback,
                                                onOpenVatDashboard = onOpenVatDashboard,
                                                onOpenVaultArchive = onOpenVaultArchive,
                                                onOpenIncomingInvoices = onOpenIncomingInvoices,
                                            )
                                        }
                                    }
                                }
                            }

                            com.ledgerhub.presentation.adaptive.WindowWidthSizeClass.MEDIUM -> {
                                // Mode Foldable Déplié / Tablette Portrait (600 dp à 839 dp) : NavigationRail compact (80 dp)
                                Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                                    com.ledgerhub.presentation.adaptive.AdaptiveNavigationRail(
                                        selected = destination,
                                        language = language,
                                        themeMode = themeState.mode,
                                        resolvedTheme = resolvedTheme,
                                        onSelect = { destination = it; overlay = Overlay.None },
                                        onCreateInvoice = onCreateInvoice,
                                        onSelectLanguage = { language = it },
                                        onToggleTheme = onToggleTheme,
                                        onOpenCommandPalette = onOpenCommandPalette,
                                        onOpenIntegrations = onOpenIntegrations,
                                        onOpenExportModal = onOpenExportModal,
                                    )
                                    Box(modifier = Modifier.weight(1f).padding(12.dp)) {
                                        Card(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        ) {
                                            ShellContent(
                                                destination = destination,
                                                onSelectDestination = { destination = it },
                                                overlay = overlay,
                                                onCreateInvoice = onCreateInvoice,
                                                onBack = onBackToTabs,
                                                onOpenInvoice = { overlay = Overlay.InvoiceDetail(it) },
                                                invoiceRepository = invoiceRepository,
                                                ledgerRepository = ledgerRepository,
                                                creditNoteRepository = creditNoteRepository,
                                                quoteRepository = quoteRepository,
                                                auditRepository = auditRepository,
                                                changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
                                                onCreateCreditNote = onCreateCreditNote,
                                                onCreateQuote = onCreateQuote,
                                                onEditQuote = onEditQuote,
                                                onConvertToInvoice = onConvertToInvoice,
                                                onNavigateToQuotesWithFilter = onNavigateToQuotesWithFilter,
                                                onExportInvoiceXml = onExportInvoiceXml,
                                                onExportCreditNoteXml = onExportCreditNoteXml,
                                                dashboardViewModel = dashboardViewModel,
                                                invoiceListViewModel = invoiceListViewModel,
                                                quotesViewModel = quotesViewModel,
                                                clientsViewModel = clientsViewModel,
                                                clientRepository = clientRepository,
                                                sireneLookupService = sireneLookupService,
                                                directoryViewModel = directoryViewModel,
                                                reconciliationViewModel = reconciliationViewModel,
                                                taxSettingsViewModel = taxSettingsViewModel,
                                                taxSettings = taxSettings,
                                                eReportingRepository = eReportingRepository,
                                                onOpenEReporting = onOpenEReporting,
                                                subscriptionRepository = subscriptionRepository,
                                                networkState = networkSimulationState,
                                                enqueueDegradedInvoiceUseCase = enqueueDegradedInvoiceUseCase,
                                                syncQueueViewModel = syncQueueViewModel,
                                                supportFeedbackViewModel = supportFeedbackViewModel,
                                                vaultRepository = vaultRepository,
                                                inboxRepository = inboxRepository,
                                                onOpenSupportFeedback = onOpenSupportFeedback,
                                                onOpenVatDashboard = onOpenVatDashboard,
                                                onOpenVaultArchive = onOpenVaultArchive,
                                                onOpenIncomingInvoices = onOpenIncomingInvoices,
                                            )
                                        }
                                    }
                                }
                            }

                            com.ledgerhub.presentation.adaptive.WindowWidthSizeClass.COMPACT -> {
                                // Mode Smartphone Portrait / Écran Externe Foldable (< 600 dp) : Header + BottomBar
                                Scaffold(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    topBar = {
                                        LedgerHeader(
                                            language = language,
                                            themeMode = themeState.mode,
                                            resolvedTheme = resolvedTheme,
                                            onSelectLanguage = { language = it },
                                            onToggleTheme = onToggleTheme,
                                            onOpenCommandPalette = onOpenCommandPalette,
                                            onOpenIntegrations = onOpenIntegrations,
                                            onOpenExportModal = onOpenExportModal,
                                            networkState = networkSimulationState,
                                            onToggleNetwork = onToggleNetworkSimulation,
                                        )
                                    },
                                    bottomBar = {
                                        if (overlay is Overlay.None || overlay is Overlay.ExportModal) {
                                            LedgerBottomBar(
                                                selected = destination,
                                                onSelect = { destination = it },
                                            )
                                        }
                                    },
                                ) { inner ->
                                    Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                                        ShellContent(
                                            destination = destination,
                                            onSelectDestination = { destination = it },
                                            overlay = overlay,
                                            onCreateInvoice = onCreateInvoice,
                                            onBack = onBackToTabs,
                                            onOpenInvoice = { overlay = Overlay.InvoiceDetail(it) },
                                            invoiceRepository = invoiceRepository,
                                            ledgerRepository = ledgerRepository,
                                            creditNoteRepository = creditNoteRepository,
                                            quoteRepository = quoteRepository,
                                            auditRepository = auditRepository,
                                            changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
                                            onCreateCreditNote = onCreateCreditNote,
                                            onCreateQuote = onCreateQuote,
                                            onEditQuote = onEditQuote,
                                            onConvertToInvoice = onConvertToInvoice,
                                            onNavigateToQuotesWithFilter = onNavigateToQuotesWithFilter,
                                            onExportInvoiceXml = onExportInvoiceXml,
                                            onExportCreditNoteXml = onExportCreditNoteXml,
                                            dashboardViewModel = dashboardViewModel,
                                            invoiceListViewModel = invoiceListViewModel,
                                            quotesViewModel = quotesViewModel,
                                            clientsViewModel = clientsViewModel,
                                            clientRepository = clientRepository,
                                            sireneLookupService = sireneLookupService,
                                            directoryViewModel = directoryViewModel,
                                            reconciliationViewModel = reconciliationViewModel,
                                            taxSettingsViewModel = taxSettingsViewModel,
                                            taxSettings = taxSettings,
                                            eReportingRepository = eReportingRepository,
                                            onOpenEReporting = onOpenEReporting,
                                            subscriptionRepository = subscriptionRepository,
                                            networkState = networkSimulationState,
                                            enqueueDegradedInvoiceUseCase = enqueueDegradedInvoiceUseCase,
                                            syncQueueViewModel = syncQueueViewModel,
                                            supportFeedbackViewModel = supportFeedbackViewModel,
                                            vaultRepository = vaultRepository,
                                            inboxRepository = inboxRepository,
                                            onOpenSupportFeedback = onOpenSupportFeedback,
                                            onOpenVatDashboard = onOpenVatDashboard,
                                            onOpenVaultArchive = onOpenVaultArchive,
                                            onOpenIncomingInvoices = onOpenIncomingInvoices,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Rendue a la racine : la palette surplombe les deux agencements et tout ecran
                // superpose, comme l'exige une commande globale.
                CommandPalette(
                    uiState = commandPaletteState,
                    onIntent = { commandPaletteViewModel.processIntent(it, language) },
                )

                // Rendue elle aussi a la racine : la feuille surplombe les deux agencements, et
                // son ViewModel nait et meurt avec l'overlay — il ne porte aucun etat a conserver
                // au-dela d'un export.
                if (overlay is Overlay.ExportModal) {
                    val exportViewModel = remember(taxSettings, subscriptionRepository) {
                        ExportViewModel(
                            invoiceRepository = invoiceRepository,
                            documentExporter = documentExporter,
                            taxSettings = taxSettings,
                            subscriptionRepository = subscriptionRepository,
                        )
                    }
                    DisposableEffect(exportViewModel) {
                        onDispose { exportViewModel.onCleared() }
                    }
                    ExportModalSheet(
                        viewModel = exportViewModel,
                        onDismiss = { overlay = Overlay.None },
                    )
                }
            }
        }
    }
}

/**
 * Porte d'authentification (US-26) — l'écran de connexion/inscription, relié au shell et à la base.
 *
 * Développé et testé en US-21, il n'était référencé nulle part : la recette manuelle l'avait
 * relevé comme dette de navigation (voir `qa/MASTER_TEST_PLAN_MOBILE.md`). Le voici branché.
 *
 * ## Les dépendances injectées ici, et nulle part ailleurs
 *
 * [AuthViewModel] retombe par défaut sur [com.ledgerhub.data.sirene.MockSireneLookupService] —
 * pratique pour un aperçu isolé ou un test, mais ce n'est pas ce que l'application doit servir.
 * L'implémentation réelle est donc fournie explicitement ici, au seul endroit où l'application
 * compose l'écran pour de bon. Le dépôt d'authentification, lui, n'a aucune valeur par défaut :
 * il lui faut la [database], que seule cette fonction reçoit.
 *
 * ## Les deux chemins d'entrée
 *
 * Connexion **et** inscription ouvrent la porte, mais aucune des deux ne l'ouvre gratuitement
 * depuis l'US-26 : l'inscription écrit un compte dans la base locale, la connexion le retrouve.
 * Il n'y a plus de serveur à démarrer pour entrer — l'application est autonome — et plus de porte
 * qui s'ouvre sur un simple formulaire rempli.
 */
@Composable
private fun AuthGate(
    database: LedgerHubDatabase,
    authRepository: AuthRepository,
    onAuthenticated: () -> Unit,
) {
    var showForgotPassword by remember { mutableStateOf(false) }
    val sireneLookupService = remember { KtorSireneLookupService(fallbackOnOffline = true) }
    val authViewModel = remember(sireneLookupService, authRepository) {
        AuthViewModel(
            authRepository = authRepository,
            sireneLookupService = sireneLookupService,
        )
    }
    DisposableEffect(authViewModel) { onDispose { authViewModel.onCleared() } }

    val uiState by authViewModel.uiState.collectAsState()
    LaunchedEffect(uiState.loginSucceeded, uiState.registrationSucceeded) {
        if (uiState.loginSucceeded || uiState.registrationSucceeded) onAuthenticated()
    }

    if (showForgotPassword) {
        val authApiClient = remember { KtorAuthApiClient() }
        val forgotPasswordViewModel = remember(authRepository, authApiClient) {
            ForgotPasswordViewModel(
                requestPasswordResetUseCase = RequestPasswordResetUseCase(authRepository, authApiClient),
            )
        }
        DisposableEffect(forgotPasswordViewModel) { onDispose { forgotPasswordViewModel.onCleared() } }
        ForgotPasswordScreen(
            viewModel = forgotPasswordViewModel,
            onBackToLogin = { showForgotPassword = false },
        )
    } else {
        AuthScreen(
            viewModel = authViewModel,
            onForgotPasswordClick = { showForgotPassword = true },
        )
    }
}

/**
 * Point d'entrée de l'e-Reporting, posé en tête de l'onglet Paramètres (US-26).
 *
 * Même forme que [CreateInvoiceAction] au-dessus du tableau de bord : une action qui précède
 * l'écran plutôt qu'un septième onglet, la barre de navigation compacte étant déjà pleine.
 */
@Composable
private fun EReportingAction(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .sizeIn(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = EREPORTING_TRIGGER_TAG },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧾", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr(StringKey.NAV_EREPORTING),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(StringKey.EREPORTING_TRIGGER_SUBTITLE),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerHubTheme.palette.SecondaryText)
        }
    }
}

/** Tag du déclencheur e-Reporting — figé par `AppShellRobolectricTest`. */
internal const val EREPORTING_TRIGGER_TAG = "ereporting_trigger"

/**
 * Point d'entrée de l'Aide & Boîte à Idées (US-30).
 */
@Composable
private fun SupportFeedbackAction(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .sizeIn(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = SUPPORT_FEEDBACK_TRIGGER_TAG },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("💡", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr(StringKey.NAV_SUPPORT_FEEDBACK),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(StringKey.SUPPORT_TRIGGER_SUBTITLE),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerHubTheme.palette.SecondaryText)
        }
    }
}

/** Tag du déclencheur Support & Idées (US-30). */
internal const val SUPPORT_FEEDBACK_TRIGGER_TAG = "support_feedback_trigger"

/**
 * Point d'entrée du Pilotage TVA & CA3 (US-31).
 */
@Composable
private fun VatDashboardAction(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .sizeIn(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = VAT_DASHBOARD_TRIGGER_TAG },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📊", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr(StringKey.NAV_VAT_DASHBOARD),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(StringKey.VAT_TRIGGER_SUBTITLE),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerHubTheme.palette.SecondaryText)
        }
    }
}

/** Tag du déclencheur TVA & CA3 (US-31). */
internal const val VAT_DASHBOARD_TRIGGER_TAG = "vat_dashboard_trigger"

/**
 * Point d'entrée du Coffre-fort Numérique & Audit (US-32).
 */
@Composable
private fun VaultArchiveAction(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .sizeIn(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = VAULT_ARCHIVE_TRIGGER_TAG },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔒", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr(StringKey.NAV_DIGITAL_VAULT),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(StringKey.VAULT_TRIGGER_SUBTITLE),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerHubTheme.palette.SecondaryText)
        }
    }
}

/** Tag du déclencheur Coffre-fort Numérique (US-32). */
internal const val VAULT_ARCHIVE_TRIGGER_TAG = "vault_archive_trigger"

/**
 * Point d'entrée de la Boîte de Réception Factures Fournisseurs (US-33).
 */
@Composable
private fun IncomingInvoicesAction(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .sizeIn(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = INCOMING_INVOICES_TRIGGER_TAG },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📥", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr(StringKey.NAV_INBOX_INVOICES),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(StringKey.INBOX_TRIGGER_SUBTITLE),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerHubTheme.palette.SecondaryText)
        }
    }
}

/** Tag du déclencheur Factures Reçues (US-33). */
internal const val INCOMING_INVOICES_TRIGGER_TAG = "incoming_invoices_trigger"

/**
 * Point d'entrée de l'Annuaire DGFIP dans le hub Plus/Paramètres.
 */
@Composable
private fun DirectoryAction(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .sizeIn(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = DIRECTORY_TRIGGER_TAG },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📇", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr(StringKey.NAV_DIRECTORY),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Annuaire DGFIP des entreprises & plateformes",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerHubTheme.palette.SecondaryText)
        }
    }
}

/** Tag du déclencheur Annuaire DGFIP. */
internal const val DIRECTORY_TRIGGER_TAG = "directory_trigger"

/**
 * Point d'entrée du Rapprochement bancaire dans le hub Plus/Paramètres.
 */
@Composable
private fun ReconciliationAction(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .sizeIn(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = RECONCILIATION_TRIGGER_TAG },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔗", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr(StringKey.NAV_RECONCILIATION),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Lettrage & Rapprochement bancaire des règlements",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerHubTheme.palette.SecondaryText)
        }
    }
}

/** Tag du déclencheur Rapprochement bancaire. */
internal const val RECONCILIATION_TRIGGER_TAG = "reconciliation_trigger"

/**
 * En-tête mobile : nom de l'app + bascule de thème + sélecteur de langue (le « Header » demandé par
 * l'US-02, côté mobile ; la bascule clair/sombre s'y ajoute en US-25).
 */
@Composable
private fun LedgerHeader(
    language: AppLanguage,
    themeMode: ThemeMode,
    resolvedTheme: ResolvedTheme,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleTheme: (ThemeMode) -> Unit,
    onOpenCommandPalette: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenExportModal: () -> Unit,
    networkState: DegradedModeNetworkState = DegradedModeNetworkState.OPERATIONAL,
    onToggleNetwork: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Zone gauche : Statut PPF & Actions rapides ───────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetworkSimulationSelector(networkState = networkState, onToggle = onToggleNetwork, compact = true)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExportModalTrigger(onClick = onOpenExportModal, compact = true)
                IntegrationsHubTrigger(onClick = onOpenIntegrations, compact = true)
                CommandPaletteTrigger(onClick = onOpenCommandPalette, compact = true)
            }
        }

        // ── Zone droite : Actions système (Thème & Langue) ──────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeToggle(mode = themeMode, resolved = resolvedTheme, onToggle = onToggleTheme)
            LangToggle(current = language, onSelect = onSelectLanguage)
        }
    }
}



@Composable
private fun ShellContent(
    destination: Destination,
    onSelectDestination: (Destination) -> Unit = {},
    overlay: Overlay,
    onCreateInvoice: () -> Unit,
    onBack: () -> Unit,
    onOpenInvoice: (String) -> Unit,
    invoiceRepository: SqlDelightInvoiceRepository,
    ledgerRepository: LocalLedgerRepository,
    creditNoteRepository: SqlDelightCreditNoteRepository,
    quoteRepository: SqlDelightQuoteRepository,
    auditRepository: SqlDelightAuditRepository,
    changeInvoiceStatusUseCase: ChangeInvoiceStatusUseCase,
    onCreateCreditNote: (Invoice) -> Unit,
    onCreateQuote: () -> Unit,
    onEditQuote: (Quote) -> Unit,
    onConvertToInvoice: (Quote) -> Unit,
    onNavigateToQuotesWithFilter: (QuoteStatusFilter) -> Unit,
    onExportInvoiceXml: (Invoice) -> Unit,
    onExportCreditNoteXml: (String) -> Unit,
    dashboardViewModel: DashboardViewModel,
    invoiceListViewModel: InvoiceListViewModel,
    quotesViewModel: QuotesViewModel,
    clientsViewModel: ClientsViewModel,
    clientRepository: SqlDelightClientRepository,
    sireneLookupService: com.ledgerhub.domain.sirene.SireneLookupService = com.ledgerhub.data.sirene.MockSireneLookupService(),
    directoryViewModel: DirectoryViewModel,
    reconciliationViewModel: ReconciliationViewModel,
    taxSettingsViewModel: TaxSettingsViewModel,
    taxSettings: TaxSettings,
    eReportingRepository: SqlDelightEReportingRepository,
    onOpenEReporting: () -> Unit,
    subscriptionRepository: SubscriptionRepository,
    networkState: DegradedModeNetworkState = DegradedModeNetworkState.OPERATIONAL,
    enqueueDegradedInvoiceUseCase: EnqueueDegradedInvoiceUseCase? = null,
    syncQueueViewModel: SyncQueueViewModel? = null,
    supportFeedbackViewModel: SupportFeedbackViewModel? = null,
    vaultRepository: SqlDelightVaultRepository? = null,
    inboxRepository: SqlDelightInboxRepository? = null,
    onOpenSupportFeedback: () -> Unit = {},
    onOpenVatDashboard: () -> Unit = {},
    onOpenVaultArchive: () -> Unit = {},
    onOpenIncomingInvoices: () -> Unit = {},
) {
    when (overlay) {
        Overlay.IncomingInvoices -> {
            if (inboxRepository != null) {
                val processReceivedInvoiceUseCase = remember(inboxRepository) {
                    ProcessReceivedInvoiceUseCase(inboxRepository)
                }
                val incomingInvoicesViewModel = remember(inboxRepository, processReceivedInvoiceUseCase) {
                    IncomingInvoicesViewModel(
                        inboxRepository = inboxRepository,
                        processReceivedInvoiceUseCase = processReceivedInvoiceUseCase,
                    )
                }
                IncomingInvoicesScreen(
                    viewModel = incomingInvoicesViewModel,
                    onBack = onBack,
                )
            }
        }
        Overlay.VaultArchive -> {
            if (vaultRepository != null) {
                val generateInvoiceSealUseCase = remember(vaultRepository, invoiceRepository) {
                    GenerateInvoiceSealUseCase(vaultRepository, invoiceRepository)
                }
                val verifyVaultIntegrityUseCase = remember(vaultRepository, invoiceRepository) {
                    VerifyVaultIntegrityUseCase(vaultRepository, invoiceRepository)
                }
                val vaultArchiveViewModel = remember(vaultRepository, generateInvoiceSealUseCase, verifyVaultIntegrityUseCase, invoiceRepository) {
                    VaultArchiveViewModel(
                        vaultRepository = vaultRepository,
                        generateInvoiceSealUseCase = generateInvoiceSealUseCase,
                        verifyVaultIntegrityUseCase = verifyVaultIntegrityUseCase,
                        invoiceRepository = invoiceRepository,
                    )
                }
                DisposableEffect(vaultArchiveViewModel) {
                    onDispose { vaultArchiveViewModel.onCleared() }
                }
                VaultArchiveScreen(
                    viewModel = vaultArchiveViewModel,
                    onBack = onBack,
                )
            }
        }
        Overlay.SupportFeedback -> {
            if (supportFeedbackViewModel != null) {
                OverlayScaffold(title = tr(StringKey.INTEGRATIONS_BACK), onBack = onBack) {
                    SupportFeedbackScreen(viewModel = supportFeedbackViewModel)
                }
            }
        }
        Overlay.VatDashboard -> {
            val calculateVatMetricsUseCase = remember(invoiceRepository, creditNoteRepository) {
                CalculateVatMetricsUseCase(invoiceRepository, creditNoteRepository)
            }
            val vatDashboardViewModel = remember(calculateVatMetricsUseCase) {
                VatDashboardViewModel(calculateVatMetricsUseCase)
            }
            DisposableEffect(vatDashboardViewModel) {
                onDispose { vatDashboardViewModel.onCleared() }
            }
            OverlayScaffold(title = tr(StringKey.INTEGRATIONS_BACK), onBack = onBack) {
                VatDashboardScreen(viewModel = vatDashboardViewModel)
            }
        }
        is Overlay.Paywall -> {
            val paywallViewModel = remember(overlay.reason, subscriptionRepository) {
                PaywallViewModel(
                    subscriptionRepository = subscriptionRepository,
                    reasonFeature = overlay.reason,
                )
            }
            DisposableEffect(paywallViewModel) { onDispose { paywallViewModel.onCleared() } }
            PaywallScreen(
                viewModel = paywallViewModel,
                onDismiss = onBack,
            )
        }
        Overlay.Integrations -> {
            // Le hub ne lit rien et n'écrit rien : son ViewModel peut naître et mourir avec
            // l'overlay, contrairement aux ViewModels d'onglets hissés dans App().
            val integrationsHubViewModel = remember { IntegrationsHubViewModel() }
            OverlayScaffold(title = tr(StringKey.INTEGRATIONS_BACK), onBack = onBack) {
                IntegrationsHubScreen(viewModel = integrationsHubViewModel)
            }
        }

        Overlay.EReporting -> {
            // Le ViewModel charge ses déclarations dès sa création (bloc `init`) : le lier à
            // l'overlay suffit, et une déclaration transmise sera relue au prochain passage.
            val eReportingViewModel = remember(eReportingRepository) {
                EReportingViewModel(repository = eReportingRepository)
            }
            DisposableEffect(eReportingViewModel) { onDispose { eReportingViewModel.onCleared() } }
            OverlayScaffold(title = tr(StringKey.INTEGRATIONS_BACK), onBack = onBack) {
                EReportingScreen(viewModel = eReportingViewModel)
            }
        }

        Overlay.CreateInvoice -> {
            val formViewModel = remember(taxSettings, networkState, sireneLookupService) {
                InvoiceFormViewModel(
                    submitInvoiceUseCase = SubmitInvoiceUseCase(invoiceRepository),
                    issuer = taxSettings.issuerParty,
                    defaultVatRate = taxSettings.defaultVatRate,
                    // Annuaire du sélecteur client (US-11) — même dépôt que l'écran Clients,
                    // donc une fiche créée à la volée y apparaît immédiatement.
                    clientRepository = clientRepository,
                    sireneLookupService = sireneLookupService,
                    enqueueDegradedInvoiceUseCase = enqueueDegradedInvoiceUseCase,
                    initialNetworkState = networkState,
                )
            }
            DisposableEffect(Unit) { onDispose { formViewModel.onCleared() } }
            OverlayScaffold(title = tr(StringKey.OVERLAY_BACK_DASHBOARD), onBack = onBack) {
                InvoiceFormScreen(
                    viewModel = formViewModel,
                    onNavigateBack = onBack,
                )
            }
        }

        Overlay.CreateQuote -> {
            val quoteFormViewModel = remember(sireneLookupService) {
                QuoteFormViewModel(
                    submitQuoteUseCase = SubmitQuoteUseCase(quoteRepository),
                    clientRepository = clientRepository,
                    sireneLookupService = sireneLookupService,
                )
            }
            DisposableEffect(Unit) { onDispose { quoteFormViewModel.onCleared() } }
            OverlayScaffold(title = "Retour aux devis", onBack = onBack) {
                QuoteFormScreen(
                    viewModel = quoteFormViewModel,
                    onNavigateBack = onBack,
                )
            }
        }

        is Overlay.EditQuote -> {
            val quoteFormViewModel = remember(overlay.quote.number, sireneLookupService) {
                QuoteFormViewModel(
                    submitQuoteUseCase = SubmitQuoteUseCase(quoteRepository),
                    clientRepository = clientRepository,
                    sireneLookupService = sireneLookupService,
                    initialQuote = overlay.quote,
                )
            }
            DisposableEffect(overlay.quote.number) { onDispose { quoteFormViewModel.onCleared() } }
            OverlayScaffold(title = "Retour aux devis", onBack = onBack) {
                QuoteFormScreen(
                    viewModel = quoteFormViewModel,
                    onNavigateBack = onBack,
                )
            }
        }

        is Overlay.CreateInvoiceFromQuote -> {
            val formViewModel = remember(overlay.quote.number, taxSettings, networkState, sireneLookupService) {
                InvoiceFormViewModel(
                    submitInvoiceUseCase = SubmitInvoiceUseCase(invoiceRepository),
                    issuer = taxSettings.issuerParty,
                    defaultVatRate = taxSettings.defaultVatRate,
                    clientRepository = clientRepository,
                    sireneLookupService = sireneLookupService,
                    sourceQuote = overlay.quote,
                    enqueueDegradedInvoiceUseCase = enqueueDegradedInvoiceUseCase,
                    initialNetworkState = networkState,
                )
            }
            DisposableEffect(overlay.quote.number) { onDispose { formViewModel.onCleared() } }
            OverlayScaffold(title = "Retour aux devis", onBack = onBack) {
                InvoiceFormScreen(
                    viewModel = formViewModel,
                    onNavigateBack = onBack,
                )
            }
        }

        is Overlay.InvoiceDetail -> {
            val detailViewModel = remember(overlay.number) {
                InvoiceDetailViewModel(
                    invoiceNumber = overlay.number,
                    ledgerRepository = ledgerRepository,
                    creditNoteRepository = creditNoteRepository,
                    auditRepository = auditRepository,
                    changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
                )
            }
            DisposableEffect(overlay.number) { onDispose { detailViewModel.onCleared() } }
            OverlayScaffold(title = tr(StringKey.OVERLAY_BACK_INVOICES), onBack = onBack) {
                InvoiceDetailScreen(
                    viewModel = detailViewModel,
                    onCreateCreditNoteClick = onCreateCreditNote,
                    onExportInvoiceXml = onExportInvoiceXml,
                    onExportCreditNoteXml = onExportCreditNoteXml,
                )
            }
        }

        is Overlay.CreditNote -> {
            val creditNoteViewModel = remember(overlay.invoice.number) {
                CreditNoteFormViewModel(
                    sourceInvoice = overlay.invoice,
                    creditNoteRepository = creditNoteRepository,
                )
            }
            DisposableEffect(overlay.invoice.number) { onDispose { creditNoteViewModel.onCleared() } }
            // L'écran d'avoir (US-10) porte son propre Scaffold + barre supérieure violette :
            // pas d'OverlayScaffold générique ici, on lui transmet seulement le retour.
            CreditNoteFormScreen(viewModel = creditNoteViewModel, onBack = onBack)
        }

        // La modale d'export est une feuille : elle se superpose au contenu, elle ne le remplace
        // pas. Les deux branches rendent donc le meme onglet — c'est la racine d'App() qui pose la
        // feuille par-dessus.
        Overlay.None, Overlay.ExportModal -> TabsContent(
            destination = destination,
            onSelectDestination = onSelectDestination,
            onCreateInvoice = onCreateInvoice,
            onOpenInvoice = onOpenInvoice,
            onCreateCreditNote = onCreateCreditNote,
            dashboardViewModel = dashboardViewModel,
            invoiceListViewModel = invoiceListViewModel,
            quotesViewModel = quotesViewModel,
            onCreateQuote = onCreateQuote,
            onEditQuote = onEditQuote,
            onConvertToInvoice = onConvertToInvoice,
            onNavigateToQuotesWithFilter = onNavigateToQuotesWithFilter,
            clientsViewModel = clientsViewModel,
            directoryViewModel = directoryViewModel,
            reconciliationViewModel = reconciliationViewModel,
            taxSettingsViewModel = taxSettingsViewModel,
            onOpenEReporting = onOpenEReporting,
            ledgerRepository = ledgerRepository,
            creditNoteRepository = creditNoteRepository,
            auditRepository = auditRepository,
            changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
            onExportInvoiceXml = onExportInvoiceXml,
            onExportCreditNoteXml = onExportCreditNoteXml,
            syncQueueViewModel = syncQueueViewModel,
            onOpenSupportFeedback = onOpenSupportFeedback,
            onOpenVatDashboard = onOpenVatDashboard,
            onOpenVaultArchive = onOpenVaultArchive,
            onOpenIncomingInvoices = onOpenIncomingInvoices,
        )
    }
}

/** Contenu de l'onglet courant, hors de tout ecran superpose. */
@Composable
private fun TabsContent(
    destination: Destination,
    onSelectDestination: (Destination) -> Unit = {},
    onCreateInvoice: () -> Unit,
    onOpenInvoice: (String) -> Unit,
    onCreateCreditNote: (Invoice) -> Unit,
    dashboardViewModel: DashboardViewModel,
    invoiceListViewModel: InvoiceListViewModel,
    quotesViewModel: QuotesViewModel,
    onCreateQuote: () -> Unit,
    onEditQuote: (Quote) -> Unit,
    onConvertToInvoice: (Quote) -> Unit,
    onNavigateToQuotesWithFilter: (QuoteStatusFilter) -> Unit,
    clientsViewModel: ClientsViewModel,
    directoryViewModel: DirectoryViewModel,
    reconciliationViewModel: ReconciliationViewModel,
    taxSettingsViewModel: TaxSettingsViewModel,
    onOpenEReporting: () -> Unit,
    ledgerRepository: LocalLedgerRepository,
    creditNoteRepository: SqlDelightCreditNoteRepository,
    auditRepository: SqlDelightAuditRepository,
    changeInvoiceStatusUseCase: ChangeInvoiceStatusUseCase,
    onExportInvoiceXml: (Invoice) -> Unit,
    onExportCreditNoteXml: (String) -> Unit,
    syncQueueViewModel: SyncQueueViewModel? = null,
    onOpenSupportFeedback: () -> Unit = {},
    onOpenVatDashboard: () -> Unit = {},
    onOpenVaultArchive: () -> Unit = {},
    onOpenIncomingInvoices: () -> Unit = {},
) {
    when (destination) {
        Destination.OVERVIEW -> DashboardScreen(
            viewModel = dashboardViewModel,
            onCreateInvoice = onCreateInvoice,
            onQuotesPendingClick = { onNavigateToQuotesWithFilter(QuoteStatusFilter.SENT) },
            onQuotesFollowUpClick = { onNavigateToQuotesWithFilter(QuoteStatusFilter.SENT) },
        )

        Destination.QUOTES -> QuotesView(
            viewModel = quotesViewModel,
            onCreateQuote = onCreateQuote,
            onEditQuote = onEditQuote,
            onConvertToInvoice = onConvertToInvoice,
        )

        Destination.INVOICES -> com.ledgerhub.presentation.invoices.InvoiceAdaptivePane(
            invoiceListViewModel = invoiceListViewModel,
            onInvoiceClick = onOpenInvoice,
            onCreateInvoice = onCreateInvoice,
            onCreateCreditNote = onCreateCreditNote,
            ledgerRepository = ledgerRepository,
            creditNoteRepository = creditNoteRepository,
            auditRepository = auditRepository,
            changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
            onExportInvoiceXml = onExportInvoiceXml,
            onExportCreditNoteXml = onExportCreditNoteXml,
            syncQueueViewModel = syncQueueViewModel,
        )

        Destination.CLIENTS -> ClientsScreen(viewModel = clientsViewModel)
        Destination.DIRECTORY -> Column(modifier = Modifier.fillMaxSize()) {
            TextButton(
                onClick = { onSelectDestination(Destination.SETTINGS) },
                modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 0.dp),
            ) {
                Text("←  ${tr(StringKey.NAV_SETTINGS)}")
            }
            DirectoryScreen(viewModel = directoryViewModel)
        }
        Destination.RECONCILIATION -> Column(modifier = Modifier.fillMaxSize()) {
            TextButton(
                onClick = { onSelectDestination(Destination.SETTINGS) },
                modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 0.dp),
            ) {
                Text("←  ${tr(StringKey.NAV_SETTINGS)}")
            }
            ReconciliationScreen(viewModel = reconciliationViewModel)
        }
        // L'e-Reporting et les services avancés sont regroupés dans l'onglet Paramètres/Plus (US-26 / Recette).
        Destination.SETTINGS -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            DirectoryAction { onSelectDestination(Destination.DIRECTORY) }
            ReconciliationAction { onSelectDestination(Destination.RECONCILIATION) }
            EReportingAction(onOpenEReporting)
            IncomingInvoicesAction(onOpenIncomingInvoices)
            VaultArchiveAction(onOpenVaultArchive)
            VatDashboardAction(onOpenVatDashboard)
            SupportFeedbackAction(onOpenSupportFeedback)
            TaxSettingsScreen(viewModel = taxSettingsViewModel, scrollable = false)
        }
    }
}

@Composable
private fun OverlayScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 0.dp)) {
            Text("←  $title")
        }
        content()
    }
}

@Composable
private fun CreateInvoiceAction(onCreateInvoice: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 0.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onCreateInvoice) { Text("＋  ${tr(StringKey.ACTION_CREATE_INVOICE)}") }
    }
}

@Composable
private fun LedgerBottomBar(selected: Destination, onSelect: (Destination) -> Unit) {
    val bottomBarEntries = listOf(
        Destination.OVERVIEW,
        Destination.INVOICES,
        Destination.QUOTES,
        Destination.CLIENTS,
        Destination.SETTINGS,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.semantics { testTag = BOTTOM_BAR_TAG },
    ) {
        bottomBarEntries.forEach { entry ->
            val isSelected = if (entry == Destination.SETTINGS) {
                selected == Destination.SETTINGS || selected == Destination.DIRECTORY || selected == Destination.RECONCILIATION
            } else {
                selected == entry
            }
            NavigationBarItem(
                modifier = Modifier.semantics { testTag = bottomBarItemTag(entry) },
                selected = isSelected,
                onClick = { onSelect(entry) },
                icon = { Text(entry.glyph) },
                label = {
                    Text(
                        tr(entry.titleKey),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

internal const val BOTTOM_BAR_TAG = "ledger_bottom_bar"
internal fun bottomBarItemTag(destination: Destination) = "bottom_bar_item_${destination.name.lowercase()}"

@Composable
private fun LedgerSidebar(
    selected: Destination,
    language: AppLanguage,
    themeMode: ThemeMode,
    resolvedTheme: ResolvedTheme,
    onSelect: (Destination) -> Unit,
    onCreateInvoice: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleTheme: (ThemeMode) -> Unit,
    onOpenCommandPalette: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenExportModal: () -> Unit,
    networkState: DegradedModeNetworkState = DegradedModeNetworkState.OPERATIONAL,
    onToggleNetwork: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .width(248.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp, 8.dp, 8.dp, 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr(StringKey.APP_NAME),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Meme raison que pour la palette et le hub : cantonnee a l'en-tete compact, la
                // bascule disparaitrait du shell tablette.
                ThemeToggle(mode = themeMode, resolved = resolvedTheme, onToggle = onToggleTheme)
                LangToggle(current = language, onSelect = onSelectLanguage)
            }
        }
        // Le declencheur existe dans les DEUX shells : cantonne a l'en-tete compact, il
        // disparaitrait sur tablette, ou la palette est justement la plus utile (clavier branche).
        CommandPaletteTrigger(
            onClick = onOpenCommandPalette,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        // Même raison que pour la palette : cantonné à l'en-tête compact, le hub disparaîtrait
        // du shell tablette.
        IntegrationsHubTrigger(
            onClick = onOpenIntegrations,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        // La sidebar a la place d'afficher le libelle en toutes lettres, contrairement a l'en-tete.
        ExportModalTrigger(
            onClick = onOpenExportModal,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        NetworkSimulationSelector(
            networkState = networkState,
            onToggle = onToggleNetwork,
            compact = false,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Destination.entries.forEach { entry ->
            val isSelected = entry == selected
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(entry) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.glyph)
                    Text(tr(entry.titleKey), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(
            onClick = onCreateInvoice,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("＋  ${tr(StringKey.ACTION_CREATE_INVOICE)}")
        }
        Text(
            tr(StringKey.SIDEBAR_COMPLIANCE),
            style = MaterialTheme.typography.labelSmall,
            color = LedgerHubTheme.palette.SecondaryText,
            modifier = Modifier.padding(8.dp),
        )
    }
}

// ── Jeu de démonstration ─────────────────────────────────────────────────────────

/**
 * Sème quelques factures au premier lancement (base vide) pour que le tableau de bord et la liste
 * ne s'affichent pas vides en QA manuelle. Aucun effet si des factures existent déjà.
 *
 * @return `true` si des factures ont effectivement été semées — l'appelant doit alors redéclencher
 *   la lecture des écrans, qui ont chargé la base avant que le semis ne se termine.
 */
private suspend fun seedDemoDataIfEmpty(repository: SqlDelightInvoiceRepository): Boolean {
    // Comptage direct, et non lecture de toutes les factures : une seule ligne indésérialisable
    // faisait échouer la lecture, `getOrDefault(emptyList())` concluait « base vide », et le semis
    // réécrivait les factures de démonstration par-dessus les vraies — à chaque lancement.
    // Un comptage ne construit aucun objet de domaine, il ne peut pas échouer pour cette raison.
    //
    // En cas d'échec malgré tout (base inaccessible), on s'abstient : ne rien semer laisse un
    // écran vide, semer à tort détruit des données.
    val existingCount = repository.countInvoices().getOrElse { return false }
    if (existingCount == 0L) {
        demoInvoices().forEach { repository.submitInvoice(it) }
        return true
    }
    // Migration du jeu de démo (alignement 6 mois glissants Janvier-Juin) :
    // Si l'application avait déjà semé les 7 factures d'origine (Fév à Juil), on insère
    // la facture de Janvier FAC-2026-0143 manquante pour compléter la série temporelle.
    if (existingCount == 7L) {
        val janInvoice = demoInvoices().firstOrNull { it.number == "FAC-2026-0143" }
        if (janInvoice != null) {
            repository.submitInvoice(janInvoice)
            return true
        }
    }
    return false
}

private fun demoInvoices(): List<Invoice> {
    val client = Party("Boulangerie Moreau SARL", "784102336", "78410233600021", "compta@boulangerie-moreau.fr")
    fun demo(number: String, status: InvoiceStatus, issueDate: String, dueDate: String, unitPriceHtCents: Long, rate: VatRate) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027", "facturation@ledgerhub.app"),
        recipient = client,
        lines = listOf(InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(unitPriceHtCents), vatRate = rate)),
        status = status,
        dueDate = dueDate,
    )
    return listOf(
        demo("FAC-2026-0143", InvoiceStatus.PAID, "2026-01-20", "2026-02-20", 250_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0142", InvoiceStatus.PAID, "2026-02-24", "2026-03-24", 104_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0141", InvoiceStatus.PAID, "2026-03-22", "2026-04-22", 390_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0140", InvoiceStatus.PAID, "2026-04-20", "2026-05-20", 800_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0139", InvoiceStatus.PAID, "2026-05-18", "2026-06-18", 192_500, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0138", InvoiceStatus.PAID, "2026-06-15", "2026-07-15", 618_800, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0137", InvoiceStatus.DEPOSITED, "2026-07-12", "2026-08-12", 220_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0136", InvoiceStatus.DRAFT, "2026-07-28", "2026-08-28", 73_400, VatRate.TAUX_NORMAL),
    )
}

private suspend fun seedDemoQuotesIfEmpty(repository: SqlDelightQuoteRepository): Boolean {
    val existing = repository.fetchQuotes().getOrNull() ?: return false
    if (existing.isNotEmpty()) return false
    demoQuotes().forEach { repository.submitQuote(it) }
    return true
}

private fun demoQuotes(): List<Quote> {
    val client = Party("Boulangerie Moreau SARL", "784102336", "78410233600021", "compta@boulangerie-moreau.fr")
    val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027", "facturation@ledgerhub.app")
    fun demo(number: String, status: QuoteStatus, issueDate: String, validityDate: String, unitPriceHtCents: Long, rate: VatRate) = Quote(
        number = number,
        issueDate = issueDate,
        validityDate = validityDate,
        issuer = issuer,
        recipient = client,
        lines = listOf(QuoteLine("Prestation d'accompagnement devis", quantity = 1, unitPriceHt = Money(unitPriceHtCents), vatRate = rate)),
        status = status,
    )
    return listOf(
        demo("DEV-2026-001", QuoteStatus.DRAFT, "2026-08-01", "2026-09-01", 50_000, VatRate.TAUX_NORMAL),
        demo("DEV-2026-002", QuoteStatus.SENT, "2026-08-02", "2026-09-01", 100_000, VatRate.TAUX_NORMAL),
        demo("DEV-2026-003", QuoteStatus.ACCEPTED, "2026-08-03", "2026-09-01", 150_000, VatRate.TAUX_NORMAL),
        demo("DEV-2026-004", QuoteStatus.REJECTED, "2026-08-04", "2026-09-01", 200_000, VatRate.TAUX_NORMAL),
    )
}
