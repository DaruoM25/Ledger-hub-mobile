package com.ledgerhub.domain.facturx

import com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.settings.TaxSettings
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contenu du XML CII produit. La conformité **structurelle** au schéma est vérifiée séparément
 * par `FacturXSchemaValidationTest` (validation XSD, JVM) ; ici on vérifie que les bonnes
 * valeurs métier atterrissent aux bons endroits.
 */
class FacturXGeneratorTest {

    private val settings = TaxSettings.Default
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027", "facturation@ledgerhub.app")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021", "compta@moreau.fr")

    private fun invoice(
        number: String = "FAC-2026-0137",
        issueDate: String = "2026-07-12",
        status: InvoiceStatus = InvoiceStatus.DEPOSITED,
        lines: List<InvoiceLine> = listOf(
            InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(220_000), vatRate = VatRate.TAUX_NORMAL),
        ),
    ) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = status,
    )

    private fun generateInvoice(invoice: Invoice = invoice()) =
        FacturXGenerator.generate(invoice.toFacturXDocument(settings))

    private fun generateCreditNote(): String {
        val source = invoice()
        val creditNote = CreateCreditNoteUseCase()(
            source, "AV-2026-0001", "2026-08-29", "Erreur de facturation",
        ).getOrThrow()
        return FacturXGenerator.generate(creditNote.toFacturXDocument(settings))
    }

    // ── Racine et namespaces ─────────────────────────────────────────────────────────────────

    @Test
    fun rootElement_declaresTheFourLegalNamespaces() {
        val xml = generateInvoice()

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertContains(xml, "<rsm:CrossIndustryInvoice")
        assertContains(xml, """xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"""")
        assertContains(xml, """xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"""")
        assertContains(xml, """xmlns:qdt="urn:un:unece:uncefact:data:standard:QualifiedDataType:100"""")
        assertContains(xml, """xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100"""")
        assertTrue(xml.trimEnd().endsWith("</rsm:CrossIndustryInvoice>"))
    }

    @Test
    fun context_declaresTheEn16931BasicGuideline() {
        // C'est cette balise qui engage la conformité annoncée du document.
        assertContains(
            generateInvoice(),
            "<ram:ID>urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic</ram:ID>",
        )
    }

    // ── Type de pièce : 380 / 381 ────────────────────────────────────────────────────────────

    @Test
    fun invoice_carriesTypeCode380() {
        val xml = generateInvoice()
        assertContains(xml, "<ram:TypeCode>380</ram:TypeCode>")
        assertFalse("<ram:TypeCode>381</ram:TypeCode>" in xml)
    }

    @Test
    fun creditNote_carriesTypeCode381() {
        val xml = generateCreditNote()
        assertContains(xml, "<ram:TypeCode>381</ram:TypeCode>")
        assertFalse("<ram:TypeCode>380</ram:TypeCode>" in xml)
    }

    // ── En-tête ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun header_carriesTheNumberAndTheDateInFormat102() {
        val xml = generateInvoice()
        assertContains(xml, "<ram:ID>FAC-2026-0137</ram:ID>")
        assertContains(xml, """<udt:DateTimeString format="102">20260712</udt:DateTimeString>""")
    }

    @Test
    fun settlement_declaresEuroAsTheInvoiceCurrency() {
        assertContains(generateInvoice(), "<ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>")
    }

    // ── Chaînage de l'avoir ──────────────────────────────────────────────────────────────────

    @Test
    fun creditNote_referencesTheCancelledInvoice_byNumberAndDate() {
        val xml = generateCreditNote()

        assertContains(xml, "<ram:InvoiceReferencedDocument>")
        assertContains(xml, "<ram:IssuerAssignedID>FAC-2026-0137</ram:IssuerAssignedID>")
        // Date de la facture annulée, pas celle de l'avoir.
        assertContains(xml, """<qdt:DateTimeString format="102">20260712</qdt:DateTimeString>""")
        assertContains(xml, "<ram:ID>AV-2026-0001</ram:ID>")
    }

    @Test
    fun invoice_carriesNoReferencedDocument() {
        assertFalse("<ram:InvoiceReferencedDocument>" in generateInvoice())
    }

    @Test
    fun referencedDocument_comesAfterTheMonetarySummation() {
        // Ordre imposé par la xs:sequence du schéma — une inversion invaliderait le document.
        val xml = generateCreditNote()
        assertTrue(
            xml.indexOf("<ram:SpecifiedTradeSettlementHeaderMonetarySummation>") <
                xml.indexOf("<ram:InvoiceReferencedDocument>"),
        )
    }

    @Test
    fun aCreditNoteWithoutReference_isRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            FacturXDocument(
                type = FacturXDocumentType.CREDIT_NOTE,
                documentNumber = "AV-2026-0001",
                issueDate = "2026-08-29",
                seller = issuer,
                buyer = recipient,
                sellerVatNumber = "",
                lines = listOf(InvoiceLine("x", 1, Money(100), VatRate.TAUX_NORMAL)),
                taxes = emptyList(),
                totalHt = Money(-100),
                totalVat = Money(-20),
                totalTtc = Money(-120),
                referencedDocument = null,
            )
        }
    }

    // ── Parties ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun sellerAndBuyer_carryNameSiretAndCountry() {
        val xml = generateInvoice()

        assertContains(xml, "<ram:SellerTradeParty>")
        assertContains(xml, "<ram:Name>Cabinet LedgerHub</ram:Name>")
        assertContains(xml, """<ram:ID schemeID="0002">82032933100027</ram:ID>""")
        assertContains(xml, "<ram:BuyerTradeParty>")
        assertContains(xml, "<ram:Name>Boulangerie Moreau SARL</ram:Name>")
        assertContains(xml, """<ram:ID schemeID="0002">78410233600021</ram:ID>""")
        // CountryID est obligatoire ; le modèle ne stockant pas d'adresse, il est fixé à FR.
        assertEquals(2, Regex("<ram:CountryID>FR</ram:CountryID>").findAll(xml).count())
    }

    @Test
    fun seller_carriesTheVatRegistration_whenConfigured() {
        val xml = generateInvoice()
        assertContains(xml, """<ram:ID schemeID="VA">${settings.vatNumber}</ram:ID>""")
    }

    @Test
    fun seller_carriesNoVatRegistration_whenTheCompanyHasNone() {
        // Entreprise en franchise : la balise doit disparaître, pas rester vide.
        val xml = FacturXGenerator.generate(
            invoice().toFacturXDocument(TaxSettings.Default.copy(vatNumber = "")),
        )
        assertFalse("<ram:SpecifiedTaxRegistration>" in xml)
    }

    @Test
    fun sellerComesBeforeBuyer() {
        val xml = generateInvoice()
        assertTrue(xml.indexOf("<ram:SellerTradeParty>") < xml.indexOf("<ram:BuyerTradeParty>"))
    }

    // ── Lignes ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun eachLine_carriesItsRankNamePriceAndQuantity() {
        val xml = generateInvoice(
            invoice(
                lines = listOf(
                    InvoiceLine("Conseil", quantity = 2, unitPriceHt = Money(50_000), vatRate = VatRate.TAUX_NORMAL),
                    InvoiceLine("Livre", quantity = 1, unitPriceHt = Money(2_000), vatRate = VatRate.TAUX_REDUIT),
                ),
            ),
        )

        assertEquals(2, Regex("<ram:IncludedSupplyChainTradeLineItem>").findAll(xml).count())
        assertContains(xml, "<ram:LineID>1</ram:LineID>")
        assertContains(xml, "<ram:LineID>2</ram:LineID>")
        assertContains(xml, "<ram:Name>Conseil</ram:Name>")
        assertContains(xml, "<ram:ChargeAmount>500.00</ram:ChargeAmount>")
        assertContains(xml, """<ram:BilledQuantity unitCode="C62">2</ram:BilledQuantity>""")
        // Total de ligne = quantité × prix unitaire.
        assertContains(xml, "<ram:LineTotalAmount>1000.00</ram:LineTotalAmount>")
    }

    @Test
    fun creditNoteLines_areCredited_soTheySumToTheNegativeTotal() {
        // Prix unitaire positif — c'est le total de ligne qui porte le signe, sinon la somme des
        // lignes ne correspondrait pas à la sommation du document.
        val xml = generateCreditNote()
        assertContains(xml, "<ram:ChargeAmount>2200.00</ram:ChargeAmount>")
        assertContains(xml, "<ram:LineTotalAmount>-2200.00</ram:LineTotalAmount>")
    }

    // ── Ventilation de TVA ───────────────────────────────────────────────────────────────────

    @Test
    fun oneTradeTaxBlockIsEmittedPerRate() {
        val xml = generateInvoice(
            invoice(
                lines = listOf(
                    InvoiceLine("Conseil", 1, Money(100_000), VatRate.TAUX_NORMAL),
                    InvoiceLine("Formation", 1, Money(50_000), VatRate.TAUX_NORMAL),
                    InvoiceLine("Livre", 1, Money(2_000), VatRate.TAUX_REDUIT),
                ),
            ),
        )

        // Deux taux distincts, donc deux blocs d'en-tête — les lignes en ont un chacune en plus.
        assertContains(xml, "<ram:BasisAmount>1500.00</ram:BasisAmount>")
        assertContains(xml, "<ram:CalculatedAmount>300.00</ram:CalculatedAmount>")
        assertContains(xml, "<ram:BasisAmount>20.00</ram:BasisAmount>")
        assertContains(xml, "<ram:CalculatedAmount>1.10</ram:CalculatedAmount>")
        assertContains(xml, "<ram:RateApplicablePercent>20.00</ram:RateApplicablePercent>")
        assertContains(xml, "<ram:RateApplicablePercent>5.50</ram:RateApplicablePercent>")
        assertContains(xml, "<ram:TypeCode>VAT</ram:TypeCode>")
    }

    @Test
    fun exemptRate_usesCategoryE_whereTaxedRatesUseS() {
        val exempt = generateInvoice(
            invoice(lines = listOf(InvoiceLine("Export", 1, Money(100_000), VatRate.EXONERE))),
        )
        assertContains(exempt, "<ram:CategoryCode>E</ram:CategoryCode>")
        assertFalse("<ram:CategoryCode>S</ram:CategoryCode>" in exempt)

        assertContains(generateInvoice(), "<ram:CategoryCode>S</ram:CategoryCode>")
    }

    // ── Sommation ────────────────────────────────────────────────────────────────────────────

    @Test
    fun monetarySummation_isExactToTheCent() {
        val xml = generateInvoice()

        assertContains(xml, "<ram:LineTotalAmount>2200.00</ram:LineTotalAmount>")
        assertContains(xml, "<ram:TaxBasisTotalAmount>2200.00</ram:TaxBasisTotalAmount>")
        assertContains(xml, """<ram:TaxTotalAmount currencyID="EUR">440.00</ram:TaxTotalAmount>""")
        assertContains(xml, "<ram:GrandTotalAmount>2640.00</ram:GrandTotalAmount>")
        assertContains(xml, "<ram:DuePayableAmount>2640.00</ram:DuePayableAmount>")
    }

    @Test
    fun creditNoteSummation_isEntirelyNegative() {
        val xml = generateCreditNote()

        assertContains(xml, "<ram:TaxBasisTotalAmount>-2200.00</ram:TaxBasisTotalAmount>")
        assertContains(xml, """<ram:TaxTotalAmount currencyID="EUR">-440.00</ram:TaxTotalAmount>""")
        assertContains(xml, "<ram:GrandTotalAmount>-2640.00</ram:GrandTotalAmount>")
    }

    @Test
    fun aRateWithOddCents_isRoundedByTheDomainEngine_notReComputedHere() {
        // 950,75 HT à 20 % -> 190,15 : la valeur doit venir du moteur fiscal, au centime près.
        val xml = generateInvoice(
            invoice(lines = listOf(InvoiceLine("Audit", 1, Money(95_075), VatRate.TAUX_NORMAL))),
        )
        assertContains(xml, "<ram:CalculatedAmount>190.15</ram:CalculatedAmount>")
        assertContains(xml, "<ram:GrandTotalAmount>1140.90</ram:GrandTotalAmount>")
    }

    // ── Robustesse ───────────────────────────────────────────────────────────────────────────

    @Test
    fun aCompanyNameCarryingMarkup_isEscaped() {
        val xml = FacturXGenerator.generate(
            invoice().copy(recipient = Party("Dupont & Fils <SARL>", "784102336", "78410233600021"))
                .toFacturXDocument(settings),
        )
        assertContains(xml, "<ram:Name>Dupont &amp; Fils &lt;SARL&gt;</ram:Name>")
    }
}
