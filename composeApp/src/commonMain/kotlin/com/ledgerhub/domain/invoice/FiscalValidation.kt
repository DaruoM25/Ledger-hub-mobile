package com.ledgerhub.domain.invoice

/** Résultat d'une validation métier — pas d'exception, un état explicite consommable par l'UI. */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

/**
 * Validation stricte des identifiants d'entreprise avant tout envoi de payload au backend.
 * SIREN : exactement 9 chiffres. SIRET : exactement 14 chiffres.
 */
object FiscalValidation {

    fun validateSiren(siren: String): ValidationResult =
        if (siren.length == 9 && siren.all(Char::isDigit)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Le SIREN doit comporter exactement 9 chiffres")
        }

    fun validateSiret(siret: String): ValidationResult =
        if (siret.length == 14 && siret.all(Char::isDigit)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Le SIRET doit comporter exactement 14 chiffres")
        }

    /**
     * Adresse de contact — mention Factur-X 2026. Contrôle de forme volontairement permissif
     * (une partie locale, un `@`, un domaine pointé) : la seule vérification fiable d'une
     * adresse reste l'envoi, et un contrôle trop strict rejette des adresses légitimes.
     */
    fun validateEmail(email: String): ValidationResult =
        if (EMAIL_REGEX.matches(email.trim())) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Adresse email invalide")
        }

    /** Raison sociale — au moins [MIN_COMPANY_NAME_LENGTH] caractères une fois les blancs retirés. */
    fun validateCompanyName(name: String): ValidationResult =
        if (name.trim().length >= MIN_COMPANY_NAME_LENGTH) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("La raison sociale doit comporter au moins $MIN_COMPANY_NAME_LENGTH caractères")
        }

    /**
     * Numéro de TVA intracommunautaire français : `FR` + clé à 2 caractères alphanumériques
     * + les 9 chiffres du SIREN. Optionnel — une entreprise en franchise n'en a pas.
     */
    fun validateVatNumber(vatNumber: String, optional: Boolean = true): ValidationResult {
        val trimmed = vatNumber.trim().uppercase()
        if (trimmed.isEmpty() && optional) return ValidationResult.Valid
        return if (FR_VAT_REGEX.matches(trimmed)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Le numéro de TVA doit être au format FRXX999999999")
        }
    }

    const val MIN_COMPANY_NAME_LENGTH = 2
    private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    private val FR_VAT_REGEX = Regex("""^FR[0-9A-Z]{2}\d{9}$""")

    /**
     * Immutabilité fiscale — seule une facture au statut [InvoiceStatus.DRAFT] peut encore être
     * modifiée ou supprimée (voir [Invoice.isEditable]). Toute tentative de modification d'une
     * facture finalisée doit être rejetée avant même d'atteindre le repository, jamais après.
     */
    fun validateEditable(invoice: Invoice): ValidationResult =
        if (invoice.isEditable) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(
                "La facture ${invoice.number} est au statut ${invoice.status} : verrouillée, elle ne peut plus être modifiée"
            )
        }
}
