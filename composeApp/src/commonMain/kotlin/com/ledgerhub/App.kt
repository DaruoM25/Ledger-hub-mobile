package com.ledgerhub

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.components.LangToggle
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.data.quote.SqlDelightQuoteRepository
import com.ledgerhub.data.creditnote.SqlDelightCreditNoteRepository
import com.ledgerhub.data.directory.CachingDirectoryRepository
import com.ledgerhub.data.directory.MockDirectoryRepository
import com.ledgerhub.data.directory.SqlDelightDirectoryRepository
import com.ledgerhub.data.audit.SqlDelightAuditRepository
import com.ledgerhub.data.client.SqlDelightClientRepository
import com.ledgerhub.data.repository.LocalLedgerRepository
import com.ledgerhub.data.settings.SqlDelightTaxSettingsRepository
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
import com.ledgerhub.domain.export.DocumentExporter
import com.ledgerhub.domain.export.NoOpDocumentExporter
import com.ledgerhub.domain.facturx.FacturXGenerator
import com.ledgerhub.domain.facturx.toFacturXDocument
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.presentation.dashboard.DashboardIntent
import com.ledgerhub.presentation.dashboard.DashboardScreen
import com.ledgerhub.presentation.dashboard.DashboardViewModel
import com.ledgerhub.presentation.invoiceform.InvoiceFormScreen
import com.ledgerhub.presentation.invoiceform.InvoiceFormViewModel
import com.ledgerhub.presentation.invoices.InvoiceDetailScreen
import com.ledgerhub.presentation.invoices.InvoiceDetailViewModel
import com.ledgerhub.presentation.invoices.InvoiceListIntent
import com.ledgerhub.presentation.invoices.InvoiceListScreen
import com.ledgerhub.presentation.invoices.InvoiceListViewModel
import com.ledgerhub.presentation.clients.ClientsScreen
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormScreen
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormViewModel
import com.ledgerhub.presentation.clients.ClientsViewModel
import com.ledgerhub.presentation.directory.DirectoryScreen
import com.ledgerhub.presentation.directory.DirectoryViewModel
import com.ledgerhub.presentation.settings.TaxSettingsScreen
import com.ledgerhub.presentation.settings.TaxSettingsViewModel
import com.ledgerhub.presentation.theme.LedgerHubColors
import com.ledgerhub.presentation.theme.LedgerHubTheme
import kotlinx.coroutines.launch

/** Compte utilisateur courant, en dur tant qu'il n'y a pas de flux d'authentification (v1). */
private const val CURRENT_USER_EMAIL_PLACEHOLDER = "demo@ledgerhub.app"

/** Seuil de largeur Material 3 « expanded » : au-delà, sidebar permanente ; en-deçà, barre du bas. */
private val ExpandedWidthThreshold = 840.dp

/** Destinations du shell de navigation — parité Web (sidebar / barre du bas). Libellés traduits via [titleKey]. */
private enum class Destination(val titleKey: StringKey, val glyph: String) {
    OVERVIEW(StringKey.NAV_OVERVIEW, "▦"),
    INVOICES(StringKey.NAV_INVOICES, "🧾"),
    CLIENTS(StringKey.NAV_CLIENTS, "👥"),
    DIRECTORY(StringKey.NAV_DIRECTORY, "📇"),
    SETTINGS(StringKey.NAV_SETTINGS, "⚙️"),
}

/** Écran affiché par-dessus les onglets (formulaire de création, détail d'une facture). */
private sealed interface Overlay {
    data object None : Overlay
    data object CreateInvoice : Overlay
    data class InvoiceDetail(val number: String) : Overlay

    /** Émission d'un avoir annulant [invoice] — US-05. */
    data class CreditNote(val invoice: Invoice) : Overlay
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
) {
    val invoiceRepository = remember(database) {
        SqlDelightInvoiceRepository(database, userEmail = CURRENT_USER_EMAIL_PLACEHOLDER)
    }
    val creditNoteRepository = remember(database) {
        SqlDelightCreditNoteRepository(database, userEmail = CURRENT_USER_EMAIL_PLACEHOLDER)
    }
    val quoteRepository = remember(database) {
        SqlDelightQuoteRepository(database, userEmail = CURRENT_USER_EMAIL_PLACEHOLDER)
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
    // Toute transition de statut passe par ce use case : il valide contre la machine d'états
    // avant que le dépôt n'écrive statut et trace d'audit dans une même transaction.
    val changeInvoiceStatusUseCase = remember(invoiceRepository) {
        ChangeInvoiceStatusUseCase(invoiceRepository)
    }
    val taxSettingsRepository = remember(database) { SqlDelightTaxSettingsRepository(database) }

    // ViewModels des onglets — créés une fois, conservés entre les changements d'onglet.
    val dashboardViewModel = remember {
        DashboardViewModel(
            GetDashboardAnalyticsUseCase(invoiceRepository, creditNoteRepository, quoteRepository),
        )
    }
    val invoiceListViewModel = remember { InvoiceListViewModel(ledgerRepository, creditNoteRepository) }
    val clientsViewModel = remember { ClientsViewModel(clientRepository) }
    val directoryViewModel = remember { DirectoryViewModel(directoryRepository) }
    val taxSettingsViewModel = remember { TaxSettingsViewModel(taxSettingsRepository) }

    // Le semis tourne en parallèle du chargement initial des ViewModels, qui lisent donc une base
    // encore vide au tout premier lancement. On relance explicitement la lecture s'il a semé —
    // sans quoi le tableau de bord et la liste restent à zéro jusqu'au redémarrage suivant.
    LaunchedEffect(invoiceRepository) {
        if (seedDemoDataIfEmpty(invoiceRepository)) {
            dashboardViewModel.processIntent(DashboardIntent.LoadDashboard)
            invoiceListViewModel.processIntent(InvoiceListIntent.Retry)
        }
    }

    var destination by remember { mutableStateOf(Destination.OVERVIEW) }

    // Les paramètres fiscaux alimentent l'émetteur et le taux par défaut du formulaire de facture.
    // Relus à chaque changement d'onglet : quitter l'écran Paramètres suffit à les propager, sans
    // couplage entre les deux ViewModels. La table ne compte qu'une ligne, la lecture est triviale.
    var taxSettings by remember { mutableStateOf(TaxSettings.Default) }
    LaunchedEffect(taxSettingsRepository, destination) {
        taxSettings = taxSettingsRepository.loadSettings().getOrDefault(TaxSettings.Default)
    }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    // Langue active — propagée à tout l'arbre via LocalAppLanguage (WS2). Défaut : français.
    var language by remember { mutableStateOf(AppLanguage.FR) }

    val onCreateInvoice = { overlay = Overlay.CreateInvoice }
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
        dashboardViewModel.processIntent(DashboardIntent.LoadDashboard)
    }

    LedgerHubTheme {
        CompositionLocalProvider(LocalAppLanguage provides language) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val expanded = maxWidth >= ExpandedWidthThreshold

                    if (expanded) {
                        Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            LedgerSidebar(
                                selected = destination,
                                language = language,
                                onSelect = { destination = it; overlay = Overlay.None },
                                onCreateInvoice = onCreateInvoice,
                                onSelectLanguage = { language = it },
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
                                        overlay = overlay,
                                        onCreateInvoice = onCreateInvoice,
                                        onBack = onBackToTabs,
                                        onOpenInvoice = { overlay = Overlay.InvoiceDetail(it) },
                                        invoiceRepository = invoiceRepository,
                                        ledgerRepository = ledgerRepository,
                                        creditNoteRepository = creditNoteRepository,
                                        auditRepository = auditRepository,
                                        changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
                                        onCreateCreditNote = onCreateCreditNote,
                                        onExportInvoiceXml = onExportInvoiceXml,
                                        onExportCreditNoteXml = onExportCreditNoteXml,
                                        dashboardViewModel = dashboardViewModel,
                                        invoiceListViewModel = invoiceListViewModel,
                                        clientsViewModel = clientsViewModel,
                                        clientRepository = clientRepository,
                                        directoryViewModel = directoryViewModel,
                                        taxSettingsViewModel = taxSettingsViewModel,
                                        taxSettings = taxSettings,
                                    )
                                }
                            }
                        }
                    } else {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = {
                                LedgerHeader(language = language, onSelectLanguage = { language = it })
                            },
                            bottomBar = {
                                if (overlay is Overlay.None) {
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
                                    overlay = overlay,
                                    onCreateInvoice = onCreateInvoice,
                                    onBack = onBackToTabs,
                                    onOpenInvoice = { overlay = Overlay.InvoiceDetail(it) },
                                    invoiceRepository = invoiceRepository,
                                    ledgerRepository = ledgerRepository,
                                    creditNoteRepository = creditNoteRepository,
                                    auditRepository = auditRepository,
                                    changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
                                    onCreateCreditNote = onCreateCreditNote,
                                    onExportInvoiceXml = onExportInvoiceXml,
                                    onExportCreditNoteXml = onExportCreditNoteXml,
                                    dashboardViewModel = dashboardViewModel,
                                    invoiceListViewModel = invoiceListViewModel,
                                    clientsViewModel = clientsViewModel,
                                    clientRepository = clientRepository,
                                    directoryViewModel = directoryViewModel,
                                    taxSettingsViewModel = taxSettingsViewModel,
                                    taxSettings = taxSettings,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** En-tête mobile : nom de l'app + sélecteur de langue (le « Header » demandé par l'US-02, côté mobile). */
@Composable
private fun LedgerHeader(language: AppLanguage, onSelectLanguage: (AppLanguage) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr(StringKey.APP_NAME), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LangToggle(current = language, onSelect = onSelectLanguage)
    }
}

@Composable
private fun ShellContent(
    destination: Destination,
    overlay: Overlay,
    onCreateInvoice: () -> Unit,
    onBack: () -> Unit,
    onOpenInvoice: (String) -> Unit,
    invoiceRepository: SqlDelightInvoiceRepository,
    ledgerRepository: LocalLedgerRepository,
    creditNoteRepository: SqlDelightCreditNoteRepository,
    auditRepository: SqlDelightAuditRepository,
    changeInvoiceStatusUseCase: ChangeInvoiceStatusUseCase,
    onCreateCreditNote: (Invoice) -> Unit,
    onExportInvoiceXml: (Invoice) -> Unit,
    onExportCreditNoteXml: (String) -> Unit,
    dashboardViewModel: DashboardViewModel,
    invoiceListViewModel: InvoiceListViewModel,
    clientsViewModel: ClientsViewModel,
    clientRepository: SqlDelightClientRepository,
    directoryViewModel: DirectoryViewModel,
    taxSettingsViewModel: TaxSettingsViewModel,
    taxSettings: TaxSettings,
) {
    when (overlay) {
        Overlay.CreateInvoice -> {
            val formViewModel = remember(taxSettings) {
                InvoiceFormViewModel(
                    submitInvoiceUseCase = SubmitInvoiceUseCase(invoiceRepository),
                    issuer = taxSettings.issuerParty,
                    defaultVatRate = taxSettings.defaultVatRate,
                    // Annuaire du sélecteur client (US-11) — même dépôt que l'écran Clients,
                    // donc une fiche créée à la volée y apparaît immédiatement.
                    clientRepository = clientRepository,
                )
            }
            DisposableEffect(Unit) { onDispose { formViewModel.onCleared() } }
            OverlayScaffold(title = tr(StringKey.OVERLAY_BACK_DASHBOARD), onBack = onBack) {
                InvoiceFormScreen(viewModel = formViewModel)
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

        Overlay.None -> when (destination) {
            Destination.OVERVIEW -> Column(modifier = Modifier.fillMaxSize()) {
                CreateInvoiceAction(onCreateInvoice)
                DashboardScreen(viewModel = dashboardViewModel)
            }

            Destination.INVOICES -> Column(modifier = Modifier.fillMaxSize()) {
                CreateInvoiceAction(onCreateInvoice)
                InvoiceListScreen(
                    viewModel = invoiceListViewModel,
                    onInvoiceClick = onOpenInvoice,
                    onCreateCreditNote = onCreateCreditNote,
                )
            }

            Destination.CLIENTS -> ClientsScreen(viewModel = clientsViewModel)
            Destination.DIRECTORY -> DirectoryScreen(viewModel = directoryViewModel)
            Destination.SETTINGS -> TaxSettingsScreen(viewModel = taxSettingsViewModel)
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
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Destination.entries.forEach { entry ->
            NavigationBarItem(
                selected = entry == selected,
                onClick = { onSelect(entry) },
                icon = { Text(entry.glyph) },
                label = { Text(tr(entry.titleKey), style = MaterialTheme.typography.labelSmall) },
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

@Composable
private fun LedgerSidebar(
    selected: Destination,
    language: AppLanguage,
    onSelect: (Destination) -> Unit,
    onCreateInvoice: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
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
            LangToggle(current = language, onSelect = onSelectLanguage)
        }
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
            color = LedgerHubColors.SecondaryText,
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
    if (existingCount > 0) return false
    demoInvoices().forEach { repository.submitInvoice(it) }
    return true
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
        demo("FAC-2026-0142", InvoiceStatus.PAID, "2026-02-24", "2026-03-24", 104_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0141", InvoiceStatus.PAID, "2026-03-22", "2026-04-22", 390_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0140", InvoiceStatus.PAID, "2026-04-20", "2026-05-20", 800_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0139", InvoiceStatus.PAID, "2026-05-18", "2026-06-18", 192_500, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0138", InvoiceStatus.PAID, "2026-06-15", "2026-07-15", 618_800, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0137", InvoiceStatus.DEPOSITED, "2026-07-12", "2026-08-12", 220_000, VatRate.TAUX_NORMAL),
        demo("FAC-2026-0136", InvoiceStatus.DRAFT, "2026-07-28", "2026-08-28", 73_400, VatRate.TAUX_NORMAL),
    )
}
