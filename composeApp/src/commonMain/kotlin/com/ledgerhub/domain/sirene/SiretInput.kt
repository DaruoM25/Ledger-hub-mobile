package com.ledgerhub.domain.sirene

/**
 * Normalisation d'un SIRET saisi (US-21).
 *
 * ## Pourquoi ce n'est pas `filterSiret`
 *
 * [com.ledgerhub.presentation.components.filterSiret] est un **filtre de frappe** : il vit dans la
 * présentation, écarte le caractère au moment où il est tapé et borne la saisie à 14 chiffres.
 * [sanitize] est une **normalisation de domaine** : elle accepte un SIRET *collé* depuis un extrait
 * Kbis ou un courriel — « 901 234 567 00013 », « 901.234.567.00013 » — et en tire les chiffres.
 * Le ViewModel décide sur cette forme normalisée, jamais sur la frappe brute.
 *
 * ## Pourquoi la clé de Luhn n'est pas contrôlée ici
 *
 * Le déclenchement de la vérification tient à un seul critère : **14 chiffres**. Le contrôle de
 * Luhn existe pourtant dans le dépôt ([com.ledgerhub.domain.directory.LuhnChecksum]) et reste
 * disponible pour l'annuaire DGFIP — mais en faire une condition d'accès à l'inscription
 * rejetterait les SIRET du propre jeu de démonstration de l'application, qui ne le respectent pas.
 * Une porte d'entrée qui refuse les données de démonstration de l'app est un piège, pas un
 * contrôle. C'est au répertoire de dire si l'entreprise existe.
 */
object SiretInput {

    /** Longueur d'un SIRET : 9 chiffres de SIREN + 5 de NIC. */
    const val LENGTH = 14

    /** Ne conserve que les chiffres — espaces, points et tirets de mise en forme sont écartés. */
    fun sanitize(raw: String): String = raw.filter { it in '0'..'9' }

    /** `true` quand la saisie porte exactement [LENGTH] chiffres : le seuil de déclenchement. */
    fun isComplete(raw: String): Boolean = sanitize(raw).length == LENGTH
}
