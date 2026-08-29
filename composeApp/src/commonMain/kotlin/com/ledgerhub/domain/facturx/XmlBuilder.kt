package com.ledgerhub.domain.facturx

/**
 * Micro-constructeur XML, sans dépendance — `commonMain` n'a ni DOM Java ni bibliothèque XML
 * multiplateforme légère, et le projet a fait le choix constant de ne pas ajouter de dépendance
 * là où quelques dizaines de lignes suffisent (voir le rejet de `kotlinx-datetime` en v1).
 *
 * La sortie est **déterministe** — indentation et ordre d'écriture fixes — ce qui rend le XML
 * généré comparable au caractère près dans les tests de référence.
 *
 * Ce constructeur ne connaît rien de Factur-X : il ne garantit ni l'ordre des éléments ni la
 * présence des balises obligatoires. Cette conformité-là est vérifiée par la validation XSD
 * (voir `FacturXSchemaValidationTest`), pas ici.
 */
class XmlBuilder(private val indentStep: String = "  ") {

    private val out = StringBuilder()
    private var depth = 0

    /** Écrit la déclaration XML. À appeler une seule fois, en tête de document. */
    fun declaration(): XmlBuilder = apply {
        out.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
    }

    /**
     * Élément conteneur. [attributes] est une liste de paires plutôt qu'une map : l'ordre des
     * attributs doit rester stable pour que la sortie soit reproductible.
     */
    fun element(
        name: String,
        attributes: List<Pair<String, String>> = emptyList(),
        content: XmlBuilder.() -> Unit,
    ): XmlBuilder = apply {
        indent().append('<').append(name).appendAttributes(attributes).append('>').append('\n')
        depth++
        content()
        depth--
        indent().append("</").append(name).append('>').append('\n')
    }

    /** Élément terminal porteur de texte : `<ram:ID>FAC-2026-0001</ram:ID>`. */
    fun text(
        name: String,
        value: String,
        attributes: List<Pair<String, String>> = emptyList(),
    ): XmlBuilder = apply {
        indent().append('<').append(name).appendAttributes(attributes).append('>')
            .append(escape(value))
            .append("</").append(name).append('>').append('\n')
    }

    /** Élément vide : `<ram:ApplicableHeaderTradeDelivery/>`. Requis par le schéma même sans contenu. */
    fun empty(name: String, attributes: List<Pair<String, String>> = emptyList()): XmlBuilder = apply {
        indent().append('<').append(name).appendAttributes(attributes).append("/>").append('\n')
    }

    override fun toString(): String = out.toString()

    private fun indent(): StringBuilder = out.apply { repeat(depth) { append(indentStep) } }

    private fun StringBuilder.appendAttributes(attributes: List<Pair<String, String>>): StringBuilder =
        apply {
            attributes.forEach { (key, value) ->
                append(' ').append(key).append("=\"").append(escapeAttribute(value)).append('"')
            }
        }

    private companion object {
        /**
         * Échappement du contenu textuel. `&` en premier, sans quoi les `&` introduits par les
         * substitutions suivantes seraient échappés une seconde fois.
         */
        fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        /** Idem, plus les guillemets — une valeur d'attribut est délimitée par des `"`. */
        fun escapeAttribute(value: String): String = escape(value)
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
