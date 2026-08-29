package com.ledgerhub.domain.facturx

import com.ledgerhub.domain.invoice.InvoiceLine

/**
 * Génère l'arborescence XML CII (`CrossIndustryInvoice`), profil **BASIC**, conformité EN 16931.
 *
 * L'ordre d'écriture n'est pas une question de style : le schéma décrit chaque type par une
 * `xs:sequence`, donc un élément placé au mauvais endroit rend le document invalide. Les
 * commentaires signalent les endroits où cet ordre est contre-intuitif.
 *
 * Fonction pure, sans état ni entrée-sortie : elle rend une chaîne. L'écriture du fichier et le
 * partage relèvent de [com.ledgerhub.domain.export.DocumentExporter], côté plateforme.
 */
object FacturXGenerator {

    // ── Namespaces légaux ────────────────────────────────────────────────────────────────────
    private const val NS_RSM = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
    private const val NS_RAM =
        "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
    private const val NS_QDT = "urn:un:unece:uncefact:data:standard:QualifiedDataType:100"
    private const val NS_UDT = "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100"

    /** Identifiant du profil — c'est lui qui engage la conformité EN 16931 du document. */
    private const val GUIDELINE_BASIC =
        "urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic"

    /** Date au format 102 (`AAAAMMJJ`), le seul admis par le profil BASIC. */
    private const val DATE_FORMAT_102 = "102"

    /** `C62` = « unité » dans la nomenclature UN/ECE Rec. 20. */
    private const val UNIT_CODE_PIECE = "C62"

    /** `VAT` = taxe sur la valeur ajoutée (UNTDID 5153). */
    private const val TAX_TYPE_VAT = "VAT"

    /** `0002` = SIRET dans la liste des schémas d'identification ISO 6523. */
    private const val SCHEME_SIRET = "0002"

    /** `VA` = numéro de TVA intracommunautaire. */
    private const val SCHEME_VAT = "VA"

    /**
     * Pays de l'émetteur et du destinataire.
     *
     * `ram:CountryID` est obligatoire dans `ram:PostalTradeAddress`, mais le modèle ne stocke
     * aucune adresse (ni `Party`, ni la table `Customer`, ni les paramètres fiscaux). Valeur fixée
     * à `FR` — arbitrage PO de l'US-06 : produit franco-français. Une adresse complète relève
     * d'une US dédiée, avec évolution de schéma et enrichissement des deux formulaires.
     */
    const val DEFAULT_COUNTRY = "FR"

    fun generate(document: FacturXDocument): String = XmlBuilder().apply {
        declaration()
        element(
            "rsm:CrossIndustryInvoice",
            listOf(
                "xmlns:rsm" to NS_RSM,
                "xmlns:ram" to NS_RAM,
                "xmlns:qdt" to NS_QDT,
                "xmlns:udt" to NS_UDT,
            ),
        ) {
            exchangedDocumentContext()
            exchangedDocument(document)
            supplyChainTradeTransaction(document)
        }
    }.toString()

    private fun XmlBuilder.exchangedDocumentContext() {
        element("rsm:ExchangedDocumentContext") {
            element("ram:GuidelineSpecifiedDocumentContextParameter") {
                text("ram:ID", GUIDELINE_BASIC)
            }
        }
    }

    private fun XmlBuilder.exchangedDocument(document: FacturXDocument) {
        element("rsm:ExchangedDocument") {
            text("ram:ID", document.documentNumber)
            // 380 = facture, 381 = avoir. C'est ce code qui porte la nature de la pièce.
            text("ram:TypeCode", document.type.code)
            element("ram:IssueDateTime") {
                text(
                    "udt:DateTimeString",
                    FacturXFormat.date102(document.issueDate),
                    listOf("format" to DATE_FORMAT_102),
                )
            }
        }
    }

    private fun XmlBuilder.supplyChainTradeTransaction(document: FacturXDocument) {
        element("rsm:SupplyChainTradeTransaction") {
            // Les lignes précèdent l'en-tête dans le schéma — ordre contre-intuitif mais imposé.
            document.lines.forEachIndexed { index, line ->
                tradeLineItem(document, line, lineNumber = index + 1)
            }
            headerTradeAgreement(document)
            // Obligatoire, et vide en BASIC : aucune donnée de livraison n'est collectée.
            empty("ram:ApplicableHeaderTradeDelivery")
            headerTradeSettlement(document)
        }
    }

    private fun XmlBuilder.tradeLineItem(
        document: FacturXDocument,
        line: InvoiceLine,
        lineNumber: Int,
    ) {
        element("ram:IncludedSupplyChainTradeLineItem") {
            element("ram:AssociatedDocumentLineDocument") {
                text("ram:LineID", lineNumber.toString())
            }
            element("ram:SpecifiedTradeProduct") {
                text("ram:Name", line.label)
            }
            element("ram:SpecifiedLineTradeAgreement") {
                element("ram:NetPriceProductTradePrice") {
                    // Prix unitaire, toujours positif : c'est le total de ligne qui porte le signe.
                    text("ram:ChargeAmount", FacturXFormat.amount(line.unitPriceHt))
                }
            }
            element("ram:SpecifiedLineTradeDelivery") {
                text(
                    "ram:BilledQuantity",
                    line.quantity.toString(),
                    listOf("unitCode" to UNIT_CODE_PIECE),
                )
            }
            element("ram:SpecifiedLineTradeSettlement") {
                element("ram:ApplicableTradeTax") {
                    text("ram:TypeCode", TAX_TYPE_VAT)
                    text("ram:CategoryCode", categoryOf(line))
                    text("ram:RateApplicablePercent", FacturXFormat.percent(line.vatRate.basisPoints))
                }
                element("ram:SpecifiedTradeSettlementLineMonetarySummation") {
                    text("ram:LineTotalAmount", FacturXFormat.amount(document.lineTotal(line)))
                }
            }
        }
    }

    private fun XmlBuilder.headerTradeAgreement(document: FacturXDocument) {
        element("ram:ApplicableHeaderTradeAgreement") {
            tradeParty("ram:SellerTradeParty", document.seller, document.sellerVatNumber)
            // Le destinataire ne porte pas de numéro de TVA : l'application ne le collecte pas.
            tradeParty("ram:BuyerTradeParty", document.buyer, vatNumber = "")
        }
    }

    private fun XmlBuilder.tradeParty(elementName: String, party: com.ledgerhub.domain.invoice.Party, vatNumber: String) {
        element(elementName) {
            text("ram:Name", party.name)
            element("ram:SpecifiedLegalOrganization") {
                text("ram:ID", party.siret, listOf("schemeID" to SCHEME_SIRET))
            }
            element("ram:PostalTradeAddress") {
                text("ram:CountryID", DEFAULT_COUNTRY)
            }
            if (vatNumber.isNotBlank()) {
                element("ram:SpecifiedTaxRegistration") {
                    text("ram:ID", vatNumber, listOf("schemeID" to SCHEME_VAT))
                }
            }
        }
    }

    private fun XmlBuilder.headerTradeSettlement(document: FacturXDocument) {
        element("ram:ApplicableHeaderTradeSettlement") {
            text("ram:InvoiceCurrencyCode", FacturXFormat.CURRENCY)

            document.taxes.forEach { tax ->
                element("ram:ApplicableTradeTax") {
                    text("ram:CalculatedAmount", FacturXFormat.amount(tax.calculatedAmount))
                    text("ram:TypeCode", TAX_TYPE_VAT)
                    text("ram:BasisAmount", FacturXFormat.amount(tax.basisAmount))
                    text("ram:CategoryCode", tax.category.code)
                    text("ram:RateApplicablePercent", FacturXFormat.percent(tax.rate.basisPoints))
                }
            }

            element("ram:SpecifiedTradeSettlementHeaderMonetarySummation") {
                text("ram:LineTotalAmount", FacturXFormat.amount(document.totalHt))
                text("ram:TaxBasisTotalAmount", FacturXFormat.amount(document.totalHt))
                // Seul montant de la sommation à porter la devise, par convention du profil.
                text(
                    "ram:TaxTotalAmount",
                    FacturXFormat.amount(document.totalVat),
                    listOf("currencyID" to FacturXFormat.CURRENCY),
                )
                text("ram:GrandTotalAmount", FacturXFormat.amount(document.totalTtc))
                text("ram:DuePayableAmount", FacturXFormat.amount(document.totalTtc))
            }

            // Chaînage de l'avoir — après la sommation, jamais avant (xs:sequence).
            document.referencedDocument?.let { reference ->
                element("ram:InvoiceReferencedDocument") {
                    text("ram:IssuerAssignedID", reference.documentNumber)
                    element("ram:FormattedIssueDateTime") {
                        text(
                            "qdt:DateTimeString",
                            FacturXFormat.date102(reference.issueDate),
                            listOf("format" to DATE_FORMAT_102),
                        )
                    }
                }
            }
        }
    }

    private fun categoryOf(line: InvoiceLine): String =
        FacturXTax(line.vatRate, line.totalHt, line.totalHt).category.code
}
