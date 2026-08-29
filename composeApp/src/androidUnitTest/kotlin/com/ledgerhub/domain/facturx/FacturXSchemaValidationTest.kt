package com.ledgerhub.domain.facturx

import com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.settings.TaxSettings
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validation du XML généré **contre le schéma officiel Factur-X, profil BASIC**.
 *
 * C'est ce test qui autorise à parler de conformité EN 16931 : les autres n'attestent que de la
 * présence des bonnes valeurs, celui-ci de la validité structurelle — ordre des éléments,
 * cardinalités, types. Une `xs:sequence` mal respectée y échoue immédiatement.
 *
 * Les XSD sont versés dans `androidUnitTest/resources/facturx/` (4 fichiers, ~20 Ko) depuis le
 * dépôt ZUGFeRD/mustangproject, sous licence Apache 2.0. Ils sont résolus entre eux par leurs
 * `xs:import` relatifs, d'où le passage par une URL de ressource et non par un flux.
 *
 * Test JVM : `javax.xml.validation` n'existe pas en commonMain — la validation reste donc un
 * outil de vérification, jamais une dépendance du code de production.
 */
class FacturXSchemaValidationTest {

    private val settings = TaxSettings.Default
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027", "facturation@ledgerhub.app")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021", "compta@moreau.fr")

    private fun invoice(
        number: String = "FAC-2026-0137",
        lines: List<InvoiceLine> = listOf(
            InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(220_000), vatRate = VatRate.TAUX_NORMAL),
        ),
    ) = Invoice(
        number = number,
        issueDate = "2026-07-12",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = InvoiceStatus.VALIDATED,
    )

    /** Lève [SAXParseException] si le document ne satisfait pas le schéma. */
    private fun validateAgainstBasicSchema(xml: String) {
        val schemaUrl = assertNotNull(
            javaClass.classLoader?.getResource("facturx/FACTUR-X_BASIC.xsd"),
            "XSD Factur-X BASIC introuvable dans les ressources de test",
        )
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(schemaUrl)
            .newValidator()
            .validate(StreamSource(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))))
    }

    // ── Le générateur produit des documents valides ──────────────────────────────────────────

    @Test
    fun generatedInvoice_validatesAgainstTheOfficialBasicSchema() {
        validateAgainstBasicSchema(FacturXGenerator.generate(invoice().toFacturXDocument(settings)))
    }

    @Test
    fun generatedCreditNote_validatesAgainstTheOfficialBasicSchema() {
        val creditNote = CreateCreditNoteUseCase()(
            invoice(), "AV-2026-0001", "2026-08-29", "Erreur de facturation",
        ).getOrThrow()

        validateAgainstBasicSchema(FacturXGenerator.generate(creditNote.toFacturXDocument(settings)))
    }

    @Test
    fun aMultiRateMultiLineInvoice_alsoValidates() {
        val xml = FacturXGenerator.generate(
            invoice(
                lines = listOf(
                    InvoiceLine("Conseil", 2, Money(50_000), VatRate.TAUX_NORMAL),
                    InvoiceLine("Formation", 1, Money(30_000), VatRate.TAUX_INTERMEDIAIRE),
                    InvoiceLine("Livre", 3, Money(2_000), VatRate.TAUX_REDUIT),
                    InvoiceLine("Presse", 1, Money(1_000), VatRate.TAUX_PARTICULIER),
                    InvoiceLine("Export", 1, Money(500_000), VatRate.EXONERE),
                ),
            ).toFacturXDocument(settings),
        )

        validateAgainstBasicSchema(xml)
    }

    @Test
    fun anInvoiceFromACompanyWithoutVatNumber_alsoValidates() {
        val xml = FacturXGenerator.generate(
            invoice().toFacturXDocument(TaxSettings.Default.copy(vatNumber = "")),
        )

        validateAgainstBasicSchema(xml)
    }

    @Test
    fun aCompanyNameCarryingMarkup_doesNotBreakTheDocument() {
        val xml = FacturXGenerator.generate(
            invoice().copy(recipient = Party("Dupont & Fils <SARL>", "784102336", "78410233600021"))
                .toFacturXDocument(settings),
        )

        validateAgainstBasicSchema(xml)
    }

    // ── Contre-épreuve : le validateur rejette bien ce qui doit l'être ───────────────────────

    @Test
    fun theValidatorRejectsADocumentWithElementsOutOfSequence() {
        // Sans cette contre-épreuve, un validateur silencieusement inopérant rendrait tous les
        // tests ci-dessus verts sans rien prouver.
        val valid = FacturXGenerator.generate(invoice().toFacturXDocument(settings))
        val outOfOrder = valid.replace(
            "    <ram:ApplicableHeaderTradeAgreement>",
            "    <ram:ApplicableHeaderTradeSettlementOutOfPlace/>\n    <ram:ApplicableHeaderTradeAgreement>",
        )

        assertTrue(outOfOrder != valid, "La contre-épreuve doit réellement altérer le document")
        assertFailsWith<SAXParseException> { validateAgainstBasicSchema(outOfOrder) }
    }

    @Test
    fun theValidatorRejectsAMissingMandatoryElement() {
        val valid = FacturXGenerator.generate(invoice().toFacturXDocument(settings))
        val withoutCurrency = valid.replace(
            "      <ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>\n",
            "",
        )

        assertTrue(withoutCurrency != valid, "La contre-épreuve doit réellement altérer le document")
        assertFailsWith<SAXParseException> { validateAgainstBasicSchema(withoutCurrency) }
    }
}
