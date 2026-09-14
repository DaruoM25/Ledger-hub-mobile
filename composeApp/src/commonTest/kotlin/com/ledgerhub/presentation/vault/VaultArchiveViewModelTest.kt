package com.ledgerhub.presentation.vault

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VaultArchiveViewModelTest {

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
            InvoiceLine(label = "Prestation", quantity = 1, unitPriceHt = Money(50000), vatRate = VatRate.TAUX_NORMAL)
        ),
    )

    private class MockVaultRepository : VaultRepository {
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

    private class MockInvoiceRepository(var invoices: List<Invoice> = emptyList()) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    @Test
    fun loadArchives_populatesUiState_withArchivesAndTotalSize() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vaultRepo = MockVaultRepository()
        val invoiceRepo = MockInvoiceRepository(listOf(dummyInvoice))
        val generateSealUseCase = GenerateInvoiceSealUseCase(vaultRepo, invoiceRepo)
        val verifyIntegrityUseCase = VerifyVaultIntegrityUseCase(vaultRepo, invoiceRepo)

        // Pre-seal invoice
        generateSealUseCase(dummyInvoice.number)

        val viewModel = VaultArchiveViewModel(
            vaultRepository = vaultRepo,
            generateInvoiceSealUseCase = generateSealUseCase,
            verifyVaultIntegrityUseCase = verifyIntegrityUseCase,
            invoiceRepository = invoiceRepo,
            dispatcher = testDispatcher,
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.sealedDocumentsCount)
        assertEquals(1, state.archives.size)
        assertEquals("FACT-2026-0001", state.archives[0].invoiceNumber)
        assertTrue(state.totalSizeInBytes > 0)
    }

    @Test
    fun verifyIntegrity_triggersVerification_andShowsReportDialog() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vaultRepo = MockVaultRepository()
        val invoiceRepo = MockInvoiceRepository(listOf(dummyInvoice))
        val generateSealUseCase = GenerateInvoiceSealUseCase(vaultRepo, invoiceRepo)
        val verifyIntegrityUseCase = VerifyVaultIntegrityUseCase(vaultRepo, invoiceRepo)

        generateSealUseCase(dummyInvoice.number)

        val viewModel = VaultArchiveViewModel(
            vaultRepository = vaultRepo,
            generateInvoiceSealUseCase = generateSealUseCase,
            verifyVaultIntegrityUseCase = verifyIntegrityUseCase,
            invoiceRepository = invoiceRepo,
            dispatcher = testDispatcher,
        )

        viewModel.processIntent(VaultArchiveIntent.VerifyIntegrity)

        val state = viewModel.uiState.value
        assertFalse(state.isVerifying)
        assertTrue(state.showReportDialog)
        assertNotNull(state.verificationReport)
        assertTrue(state.verificationReport!!.isCompletelyValid)
        assertTrue(state.isIntegrityValid)
    }

    @Test
    fun sealPendingInvoices_sealsAllUnsealedInvoices() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vaultRepo = MockVaultRepository()
        val invoiceRepo = MockInvoiceRepository(listOf(
            dummyInvoice.copy(number = "FACT-01"),
            dummyInvoice.copy(number = "FACT-02")
        ))
        val generateSealUseCase = GenerateInvoiceSealUseCase(vaultRepo, invoiceRepo)
        val verifyIntegrityUseCase = VerifyVaultIntegrityUseCase(vaultRepo, invoiceRepo)

        val viewModel = VaultArchiveViewModel(
            vaultRepository = vaultRepo,
            generateInvoiceSealUseCase = generateSealUseCase,
            verifyVaultIntegrityUseCase = verifyIntegrityUseCase,
            invoiceRepository = invoiceRepo,
            dispatcher = testDispatcher,
        )

        assertEquals(0, viewModel.uiState.value.sealedDocumentsCount)

        viewModel.processIntent(VaultArchiveIntent.SealPendingInvoices)

        assertEquals(2, viewModel.uiState.value.sealedDocumentsCount)
        assertEquals(2, vaultRepo.archives.size)
    }

    @Test
    fun exportAuditLog_setsSuccessMessage() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val vaultRepo = MockVaultRepository()
        val invoiceRepo = MockInvoiceRepository(listOf(dummyInvoice))
        val generateSealUseCase = GenerateInvoiceSealUseCase(vaultRepo, invoiceRepo)
        val verifyIntegrityUseCase = VerifyVaultIntegrityUseCase(vaultRepo, invoiceRepo)

        generateSealUseCase(dummyInvoice.number)

        val viewModel = VaultArchiveViewModel(
            vaultRepository = vaultRepo,
            generateInvoiceSealUseCase = generateSealUseCase,
            verifyVaultIntegrityUseCase = verifyIntegrityUseCase,
            invoiceRepository = invoiceRepo,
            dispatcher = testDispatcher,
        )

        viewModel.processIntent(VaultArchiveIntent.ExportAuditLog)

        assertNotNull(viewModel.uiState.value.exportSuccessMessage)
        assertTrue(viewModel.uiState.value.exportSuccessMessage!!.contains("1 entrées"))
    }
}
