package com.ledgerhub.presentation.vault

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.vault.DigitalArchive
import com.ledgerhub.domain.vault.GenerateInvoiceSealUseCase
import com.ledgerhub.domain.vault.PisteAuditEntry
import com.ledgerhub.domain.vault.VaultRepository
import com.ledgerhub.domain.vault.VerifyVaultIntegrityUseCase
import com.ledgerhub.presentation.theme.LedgerHubTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class VaultArchiveRobolectricTest {

    private val fakeIssuer = Party(name = "Mon Entreprise SAS", siren = "123456789", siret = "12345678901234")
    private val fakeClient = Party(name = "Client Test", siren = "987654321", siret = "98765432109876")

    private val dummyInvoice = Invoice(
        number = "FACT-2026-0001",
        status = InvoiceStatus.DEPOSITED,
        issueDate = "2026-09-14",
        dueDate = "2026-10-14",
        issuer = fakeIssuer,
        recipient = fakeClient,
        lines = listOf(
            InvoiceLine(label = "Prestation", quantity = 1, unitPriceHt = Money(100000), vatRate = VatRate.TAUX_NORMAL)
        ),
    )

    private class FakeVaultRepository : VaultRepository {
        val archives = mutableListOf<DigitalArchive>()
        val auditLogs = mutableListOf<PisteAuditEntry>()

        override suspend fun saveArchive(archive: DigitalArchive): Result<Unit> {
            archives.removeAll { it.id == archive.id }
            archives.add(archive)
            return Result.success(Unit)
        }

        override suspend fun fetchArchives(): Result<List<DigitalArchive>> {
            return Result.success(archives.toList())
        }

        override suspend fun getArchiveByInvoiceNumber(invoiceNumber: String): Result<DigitalArchive?> {
            return Result.success(archives.firstOrNull { it.invoiceNumber == invoiceNumber })
        }

        override suspend fun countSealedDocuments(): Result<Long> {
            return Result.success(archives.size.toLong())
        }

        override suspend fun appendAuditEntry(
            id: String,
            invoiceNumber: String?,
            action: String,
            details: String,
            timestamp: String,
        ): Result<PisteAuditEntry> {
            val previousChecksum = auditLogs.lastOrNull()?.checksum
            val source = listOf(id, invoiceNumber.orEmpty(), action, timestamp, previousChecksum.orEmpty()).joinToString("|")
            val checksum = com.ledgerhub.domain.audit.sha256Hex(source)
            val entry = PisteAuditEntry(
                id = id,
                invoiceNumber = invoiceNumber,
                action = action,
                details = details,
                timestamp = timestamp,
                previousChecksum = previousChecksum,
                checksum = checksum,
            )
            auditLogs.add(entry)
            return Result.success(entry)
        }

        override suspend fun fetchAuditTrail(): Result<List<PisteAuditEntry>> {
            return Result.success(auditLogs.toList())
        }

        override suspend fun getLatestAuditEntry(): Result<PisteAuditEntry?> {
            return Result.success(auditLogs.lastOrNull())
        }
    }

    private class FakeInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    @Test
    fun vaultScreen_rendersKpisButtonsAndArchiveList() = runComposeUiTest {
        val vaultRepo = FakeVaultRepository()
        val invoiceRepo = FakeInvoiceRepository(listOf(dummyInvoice))
        val generateSealUseCase = GenerateInvoiceSealUseCase(vaultRepo, invoiceRepo)
        val verifyIntegrityUseCase = VerifyVaultIntegrityUseCase(vaultRepo, invoiceRepo)

        kotlinx.coroutines.runBlocking {
            generateSealUseCase(dummyInvoice.number)
        }

        val testDispatcher = UnconfinedTestDispatcher()
        val viewModel = VaultArchiveViewModel(
            vaultRepository = vaultRepo,
            generateInvoiceSealUseCase = generateSealUseCase,
            verifyVaultIntegrityUseCase = verifyIntegrityUseCase,
            invoiceRepository = invoiceRepo,
            dispatcher = testDispatcher,
        )

        setContent {
            LedgerHubTheme {
                VaultArchiveScreen(viewModel = viewModel)
            }
        }

        onNodeWithTag(VaultArchiveTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(VaultArchiveTags.KPI_SEALED).assertIsDisplayed()
        onNodeWithTag(VaultArchiveTags.KPI_INTEGRITY).assertIsDisplayed()
        onNodeWithTag(VaultArchiveTags.KPI_STORAGE).assertIsDisplayed()
        onNodeWithTag(VaultArchiveTags.VERIFY_BUTTON).assertIsDisplayed()
        onNodeWithTag(VaultArchiveTags.EXPORT_BUTTON).assertIsDisplayed()
        onNodeWithTag(VaultArchiveTags.ARCHIVE_LIST).assertExists()
        onNodeWithTag(VaultArchiveTags.archiveRow("FACT-2026-0001")).assertExists()
    }
}
