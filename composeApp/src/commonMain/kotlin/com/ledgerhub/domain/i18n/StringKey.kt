package com.ledgerhub.domain.i18n

/**
 * Clés du dictionnaire d'interface — une clé = une chaîne UI, miroir strict des clés du
 * dictionnaire Web (Prompt 2). Chaque clé DOIT être traduite dans les deux langues (garanti par
 * `AppTranslationsTest`). Regroupées par zone fonctionnelle.
 *
 * Hors périmètre US-02 (restent en français) : l'aperçu WYSIWYG `InvoicePaperCanvas`, l'écran
 * `LoginScreen` (avant navigation), le corps prosaïque du détail facture (émetteur/ventilation TVA/
 * boutons d'action) et les modules Devis/Avoir.
 */
enum class StringKey {
    // ── Application / shell ──────────────────────────────────────────────────
    APP_NAME,
    SIDEBAR_COMPLIANCE,
    ACTION_CREATE_INVOICE,
    OVERLAY_BACK_DASHBOARD,
    OVERLAY_BACK_INVOICES,

    // ── Navigation ──────────────────────────────────────────────────────────
    NAV_OVERVIEW,
    NAV_INVOICES,
    NAV_CLIENTS,
    NAV_SETTINGS,

    // ── Tableau de bord ─────────────────────────────────────────────────────
    DASHBOARD_SUBTITLE,
    DASHBOARD_LOADING,
    KPI_REVENUE_TITLE,
    KPI_REVENUE_CAPTION,
    KPI_PENDING_TITLE,
    KPI_PENDING_CAPTION,
    KPI_ISSUED_TITLE,
    KPI_ISSUED_CAPTION,
    REVENUE_SECTION_SUBTITLE,
    CHART_MIN,
    CHART_MAX,
    RECENT_INVOICES_TITLE,
    RECENT_EMPTY,
    COL_INVOICE_NO,
    COL_CLIENT,
    COL_DATE,
    COL_TTC,

    // ── Formulaire de facture ───────────────────────────────────────────────
    FORM_TITLE,
    FORM_SUBTITLE,
    FORM_SECTION_CLIENT,
    FORM_SECTION_DETAILS,
    FORM_SECTION_LINES,
    FORM_SECTION_RECAP,
    FIELD_CLIENT_NAME,
    FIELD_CLIENT_SIRET,
    FIELD_CLIENT_EMAIL,
    FIELD_INVOICE_NUMBER,
    FIELD_ISSUE_DATE,
    FIELD_DUE_DATE,
    FIELD_LINE_LABEL,
    FIELD_LINE_QTY,
    FIELD_LINE_UNIT_PRICE,
    FIELD_VAT_RATE,
    LINE_HEADER,
    ACTION_ADD_LINE,
    RECAP_TOTAL_HT,
    RECAP_TOTAL_VAT,
    RECAP_TOTAL_TTC,
    FACTURX_TOGGLE_LABEL,
    ACTION_SUBMIT_INVOICE,
    FORM_ARCHIVE_NOTICE,
    FORM_SENDING,

    // ── Toasts / bannières ──────────────────────────────────────────────────
    TOAST_INVOICE_SUCCESS,
    TOAST_INVOICE_SUBMIT_FAILED_PREFIX,
    TOAST_DASHBOARD_LOAD_FAILED,
    TOAST_INVOICES_LOAD_FAILED,

    // ── Liste des factures ──────────────────────────────────────────────────
    LIST_LOADING,
    LIST_EMPTY,
    LIST_RETRY,
    LIST_ISSUED_ON,
    DETAIL_ISSUE_DATE,
    FACTURX_BADGE,
    FILTER_ALL,
    FILTER_DRAFT,
    FILTER_VALIDATED,
    FILTER_SENT,
    FILTER_PAID,
    FILTER_CANCELLED,

    // ── Statuts de facture ──────────────────────────────────────────────────
    STATUS_DRAFT,
    STATUS_VALIDATED,
    STATUS_SENT,
    STATUS_PAID,
    STATUS_CANCELLED,

    // ── Écrans "à venir" ────────────────────────────────────────────────────
    PLACEHOLDER_COMING_SOON,

    // ── Erreurs de validation (résolues depuis ValidationErrorKey) ──────────
    VALIDATION_INVOICE_NUMBER_REQUIRED,
    VALIDATION_DATE_FORMAT_INVALID,
    VALIDATION_CLIENT_NAME_REQUIRED,
    VALIDATION_CLIENT_SIRET_INVALID,
    VALIDATION_CLIENT_EMAIL_INVALID,
    VALIDATION_LINE_LABEL_REQUIRED,
    VALIDATION_LINE_QUANTITY_INVALID,
    VALIDATION_LINE_UNIT_PRICE_INVALID,

    // ── Mois abrégés (format de date EN "MMM d, yyyy") ──────────────────────
    MONTH_ABBR_1, MONTH_ABBR_2, MONTH_ABBR_3, MONTH_ABBR_4, MONTH_ABBR_5, MONTH_ABBR_6,
    MONTH_ABBR_7, MONTH_ABBR_8, MONTH_ABBR_9, MONTH_ABBR_10, MONTH_ABBR_11, MONTH_ABBR_12,
}
