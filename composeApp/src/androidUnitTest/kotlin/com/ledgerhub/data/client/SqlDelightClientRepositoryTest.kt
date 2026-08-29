package com.ledgerhub.data.client

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.data.invoice.SqlDelightInvoiceRepository
import com.ledgerhub.data.settings.SqlDelightTaxSettingsRepository
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.client.ClientInUseException
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.settings.TaxSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Persistance des fiches clients et des paramètres fiscaux sur `JdbcSqliteDriver` **en mémoire** :
 * base neuve à chaque test, suite rapide et indépendante de l'ordre d'exécution.
 */
class SqlDelightClientRepositoryTest {

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — classloader sandboxé de Robolectric ailleurs dans la suite.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun client(name: String = "Boulangerie Moreau SARL", siret: String = "78410233600021") =
        Party(name = name, siren = siret.take(9), siret = siret, email = "compta@moreau.fr")

    // ── CRUD ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun createThenFetch_roundTripsEveryField() = runTest {
        val repository = SqlDelightClientRepository(newDatabase())

        repository.createClient(client()).getOrThrow()

        val fetched = repository.fetchClients().getOrThrow().single()
        assertEquals("Boulangerie Moreau SARL", fetched.name)
        assertEquals("78410233600021", fetched.siret)
        assertEquals("784102336", fetched.siren)
        assertEquals("compta@moreau.fr", fetched.email)
    }

    @Test
    fun fetch_ordersClientsByName() = runTest {
        val repository = SqlDelightClientRepository(newDatabase())
        repository.createClient(client("Zeta SAS", "11111111111111")).getOrThrow()
        repository.createClient(client("alpha SARL", "22222222222222")).getOrThrow()

        assertEquals(
            listOf("alpha SARL", "Zeta SAS"),
            repository.fetchClients().getOrThrow().map { it.name },
        )
    }

    @Test
    fun createOnAKnownSiret_failsWithoutOverwritingTheRecord() = runTest {
        val repository = SqlDelightClientRepository(newDatabase())
        repository.createClient(client()).getOrThrow()

        val result = repository.createClient(client(name = "Autre Raison SAS"))

        assertIs<DuplicateClientException>(result.exceptionOrNull())
        assertEquals("Boulangerie Moreau SARL", repository.fetchClients().getOrThrow().single().name)
    }

    @Test
    fun updateClient_changesNameAndEmail_butKeepsTheSiret() = runTest {
        val repository = SqlDelightClientRepository(newDatabase())
        repository.createClient(client()).getOrThrow()

        repository.updateClient(
            Party("Boulangerie Moreau SAS", "784102336", "78410233600021", "nouveau@moreau.fr"),
        ).getOrThrow()

        val fetched = repository.fetchClients().getOrThrow().single()
        assertEquals("Boulangerie Moreau SAS", fetched.name)
        assertEquals("nouveau@moreau.fr", fetched.email)
        assertEquals("78410233600021", fetched.siret)
    }

    @Test
    fun deleteClient_withoutInvoices_removesTheRecord() = runTest {
        val repository = SqlDelightClientRepository(newDatabase())
        repository.createClient(client()).getOrThrow()

        repository.deleteClient("78410233600021").getOrThrow()

        assertTrue(repository.fetchClients().getOrThrow().isEmpty())
    }

    // ── Garde-fou fiscal ─────────────────────────────────────────────────────────────────────

    @Test
    fun deleteClient_carryingInvoices_isRefused_andTheRecordSurvives() = runTest {
        val database = newDatabase()
        val clients = SqlDelightClientRepository(database)
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        clients.createClient(client()).getOrThrow()
        invoices.submitInvoice(
            Invoice(
                number = "FAC-2026-0700",
                issueDate = "2026-06-24",
                issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
                recipient = client(),
                lines = listOf(InvoiceLine("Conseil", 1, Money(100_000), VatRate.TAUX_NORMAL)),
                status = InvoiceStatus.DEPOSITED,
            ),
        ).getOrThrow()

        val result = clients.deleteClient("78410233600021")

        val failure = assertIs<ClientInUseException>(result.exceptionOrNull())
        assertEquals(1L, failure.invoiceCount)
        assertEquals(1, clients.fetchClients().getOrThrow().size)
        // La facture reste intacte et lisible.
        assertEquals(1, invoices.fetchInvoices().getOrThrow().size)
    }

    @Test
    fun countInvoicesFor_reportsTheNumberOfIssuedInvoices() = runTest {
        val database = newDatabase()
        val clients = SqlDelightClientRepository(database)
        val invoices = SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        clients.createClient(client()).getOrThrow()
        repeat(3) { index ->
            invoices.submitInvoice(
                Invoice(
                    number = "FAC-2026-080$index",
                    issueDate = "2026-06-24",
                    issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
                    recipient = client(),
                    lines = listOf(InvoiceLine("Conseil", 1, Money(100_000), VatRate.TAUX_NORMAL)),
                ),
            ).getOrThrow()
        }

        assertEquals(3L, clients.countInvoicesFor("78410233600021").getOrThrow())
        assertEquals(0L, clients.countInvoicesFor("00000000000000").getOrThrow())
    }
}

/** Paramètres fiscaux — table mono-ligne, valeurs par défaut sur base vierge. */
class SqlDelightTaxSettingsRepositoryTest {

    private fun newDatabase(): LedgerHubDatabase {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    @Test
    fun load_onAVirginBase_returnsTheDefaults() = runTest {
        val settings = SqlDelightTaxSettingsRepository(newDatabase()).loadSettings().getOrThrow()

        assertEquals(TaxSettings.Default, settings)
    }

    @Test
    fun saveThenLoad_roundTripsEveryField() = runTest {
        val repository = SqlDelightTaxSettingsRepository(newDatabase())
        val settings = TaxSettings(
            issuerName = "Atelier Dupont",
            issuerSiren = "111111111",
            issuerSiret = "11111111100011",
            issuerEmail = "contact@dupont.fr",
            vatNumber = "FR11111111111",
            defaultVatRate = VatRate.TAUX_REDUIT,
            facturXEnabled = false,
        )

        repository.saveSettings(settings).getOrThrow()

        assertEquals(settings, repository.loadSettings().getOrThrow())
    }

    @Test
    fun savingTwice_keepsASingleRow() = runTest {
        val database = newDatabase()
        val repository = SqlDelightTaxSettingsRepository(database)

        repository.saveSettings(TaxSettings.Default.copy(issuerName = "Premier")).getOrThrow()
        repository.saveSettings(TaxSettings.Default.copy(issuerName = "Second")).getOrThrow()

        // Le CHECK (id = 1) rend une seconde ligne structurellement impossible.
        assertEquals("Second", repository.loadSettings().getOrThrow().issuerName)
        assertNull(database.taxSettingsQueries.select().executeAsList().getOrNull(1))
    }
}
