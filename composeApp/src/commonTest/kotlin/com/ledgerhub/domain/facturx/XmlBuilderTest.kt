package com.ledgerhub.domain.facturx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le constructeur XML est la brique la plus basse : une erreur d'échappement y produirait un
 * document invalide sans que rien d'autre ne le signale.
 */
class XmlBuilderTest {

    @Test
    fun declaration_isWrittenOnce_atTheTop() {
        val xml = XmlBuilder().declaration().text("a", "1").toString()
        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
    }

    @Test
    fun nesting_indentsDeterministically() {
        val xml = XmlBuilder().element("root") {
            element("child") { text("leaf", "v") }
        }.toString()

        assertEquals(
            """
            <root>
              <child>
                <leaf>v</leaf>
              </child>
            </root>

            """.trimIndent(),
            xml,
        )
    }

    @Test
    fun emptyElement_isSelfClosing() {
        assertEquals("<ram:ApplicableHeaderTradeDelivery/>\n", XmlBuilder().empty("ram:ApplicableHeaderTradeDelivery").toString())
    }

    @Test
    fun attributes_keepTheirDeclarationOrder() {
        // L'ordre doit être stable, sans quoi la sortie n'est pas reproductible en test.
        val xml = XmlBuilder().text("e", "v", listOf("b" to "2", "a" to "1")).toString()
        assertEquals("""<e b="2" a="1">v</e>""" + "\n", xml)
    }

    // ── Échappement ──────────────────────────────────────────────────────────────────────────

    @Test
    fun textContent_escapesAmpersandFirst() {
        // Si `&` n'était pas traité en premier, les `&` produits par `<` seraient réencodés.
        val xml = XmlBuilder().text("e", "Dupont & Fils <SARL>").toString()
        assertEquals("<e>Dupont &amp; Fils &lt;SARL&gt;</e>\n", xml)
    }

    @Test
    fun attributeValue_alsoEscapesQuotes() {
        val xml = XmlBuilder().text("e", "v", listOf("a" to """gui"llemet'apostrophe""")).toString()
        assertEquals("""<e a="gui&quot;llemet&apos;apostrophe">v</e>""" + "\n", xml)
    }

    @Test
    fun aCompanyNameWithMarkup_cannotBreakOutOfItsElement() {
        // Cas concret : une raison sociale saisie par l'utilisateur ne doit pas injecter de balise.
        val xml = XmlBuilder().text("ram:Name", "</ram:Name><evil/>").toString()
        assertTrue("<evil/>" !in xml, "Aucune balise ne doit être injectée : $xml")
        assertEquals("<ram:Name>&lt;/ram:Name&gt;&lt;evil/&gt;</ram:Name>\n", xml)
    }
}
