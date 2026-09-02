package com.ledgerhub.domain.export

/**
 * Document produit par un export comptable (US-22), prêt à être remis à la plateforme via
 * [DocumentExporter].
 *
 * ## Dette assumée : l'archive n'est pas un `.zip`
 *
 * Le bouton de l'écran annonce « Télécharger l'archive (.zip) » — c'est le libellé imposé par le
 * cahier des charges, et il décrit fidèlement l'intention de l'utilisateur. Le contenu remis, lui,
 * est **textuel** : [DocumentExporter] ne transporte que des chaînes, et fabriquer un conteneur
 * ZIP binaire supposerait d'élargir cette interface jusqu'aux `ByteArray` puis d'écrire un
 * archiveur en Kotlin pur. Le choix retenu est de livrer le contenu réel du format choisi sous sa
 * propre extension (`.txt`, `.xml`, `.csv`) plutôt qu'un `.zip` corrompu qui ne s'ouvrirait nulle
 * part. Dette consignée dans `logs/audit.md`.
 *
 * @param documentCount nombre de pièces couvertes — ce que l'écran annonce à l'utilisateur avant
 *   qu'il ne transmette le fichier à son cabinet. Un export vide se voit alors immédiatement.
 */
data class AccountingArchive(
    val fileName: String,
    val mimeType: String,
    val content: String,
    val format: ExportFormat,
    val documentCount: Int,
)
