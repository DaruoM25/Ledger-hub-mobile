package com.ledgerhub.domain.auth

/**
 * Validateur d'adresse e-mail pur Kotlin multiplateforme (RFC 5322 simplifiée).
 *
 * Indépendant de toute plateforme (zéro import `android.util.Patterns` ou `java.util.regex`)
 * pour une exécution fluide sur Android, iOS, Desktop et Web.
 */
object EmailValidator {

    /**
     * Expression régulière conforme aux règles de syntaxe courantes d'adresses e-mail :
     * - Partie locale : caractères alphanumériques et symboles autorisés (`.`, `_`, `%`, `+`, `-`)
     * - Domaine : segments alphanumériques séparés par des points
     * - TLD : extension de domaine d'au moins 2 caractères alphabétiques
     */
    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9_%+-]+(?:\\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$"
    )

    /**
     * Valide si la chaîne fournie correspond à un format d'e-mail valide.
     *
     * @param email Adresse e-mail à vérifier
     * @return `true` si l'e-mail est syntaxiquement valide, `false` sinon.
     */
    fun isValid(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || trimmed.length > 254) return false
        return EMAIL_REGEX.matches(trimmed)
    }
}
