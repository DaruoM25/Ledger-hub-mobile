package com.ledgerhub.domain.invoice

/**
 * Validateur conditionnel d'identifiant d'entreprise (SIREN / SIRET) selon le mode de transaction
 * pour la réforme 2026.
 *
 * - En mode [TransactionMode.E_INVOICING] (B2B France) : obligatoire, exactement 9 chiffres (SIREN)
 *   ou 14 chiffres (SIRET), composé exclusivement de chiffres.
 * - En mode [TransactionMode.E_REPORTING] (B2C / International) : optionnel. Si vide -> valide.
 *   Si renseigné, doit comporter exactement 9 ou 14 chiffres numériques.
 */
object SirenValidator {

    fun validate(raw: String, mode: TransactionMode): ValidationResult {
        val sanitized = raw.trim().replace(" ", "").replace(".", "").replace("-", "")

        return when (mode) {
            TransactionMode.E_INVOICING -> {
                if (sanitized.isEmpty()) {
                    ValidationResult.Invalid("L'identifiant SIREN/SIRET est obligatoire en B2B France")
                } else if (!sanitized.all { it.isDigit() }) {
                    ValidationResult.Invalid("L'identifiant ne doit comporter que des chiffres")
                } else if (sanitized.length != 9 && sanitized.length != 14) {
                    ValidationResult.Invalid("L'identifiant doit comporter exactement 9 (SIREN) ou 14 (SIRET) chiffres")
                } else {
                    ValidationResult.Valid
                }
            }
            TransactionMode.E_REPORTING -> {
                if (sanitized.isEmpty()) {
                    ValidationResult.Valid
                } else if (!sanitized.all { it.isDigit() }) {
                    ValidationResult.Invalid("L'identifiant ne doit comporter que des chiffres")
                } else if (sanitized.length != 9 && sanitized.length != 14) {
                    ValidationResult.Invalid("L'identifiant doit comporter exactement 9 (SIREN) ou 14 (SIRET) chiffres")
                } else {
                    ValidationResult.Valid
                }
            }
        }
    }
}
