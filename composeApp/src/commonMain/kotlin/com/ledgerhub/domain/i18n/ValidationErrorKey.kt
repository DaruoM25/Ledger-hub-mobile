package com.ledgerhub.domain.i18n

/**
 * Erreur de validation d'un champ de formulaire, **indépendante de la langue** : le ViewModel émet
 * une clé, l'UI Compose la résout via [AppTranslations] selon [LocalAppLanguage][com.ledgerhub.presentation.i18n.LocalAppLanguage].
 * Permet à un message d'erreur déjà affiché de changer de langue instantanément, sans re-validation.
 */
enum class ValidationErrorKey(val stringKey: StringKey) {
    INVOICE_NUMBER_REQUIRED(StringKey.VALIDATION_INVOICE_NUMBER_REQUIRED),
    DATE_FORMAT_INVALID(StringKey.VALIDATION_DATE_FORMAT_INVALID),
    DUE_DATE_BEFORE_ISSUE_DATE(StringKey.VALIDATION_DUE_DATE_BEFORE_ISSUE_DATE),
    CLIENT_NAME_REQUIRED(StringKey.VALIDATION_CLIENT_NAME_REQUIRED),
    CLIENT_SIRET_INVALID(StringKey.VALIDATION_CLIENT_SIRET_INVALID),
    CLIENT_EMAIL_INVALID(StringKey.VALIDATION_CLIENT_EMAIL_INVALID),
    LINE_LABEL_REQUIRED(StringKey.VALIDATION_LINE_LABEL_REQUIRED),
    LINE_QUANTITY_INVALID(StringKey.VALIDATION_LINE_QUANTITY_INVALID),
    LINE_UNIT_PRICE_INVALID(StringKey.VALIDATION_LINE_UNIT_PRICE_INVALID),
}
