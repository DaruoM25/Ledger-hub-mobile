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
    NAV_DIRECTORY,

    // ── Tableau de bord ─────────────────────────────────────────────────────
    DASHBOARD_SUBTITLE,
    DASHBOARD_LOADING,
    KPI_REVENUE_TITLE,
    KPI_REVENUE_CAPTION,
    KPI_PENDING_TITLE,
    KPI_PENDING_CAPTION,
    KPI_ISSUED_TITLE,
    KPI_ISSUED_CAPTION,
    KPI_QUOTES_PENDING_TITLE,
    KPI_QUOTES_PENDING_CAPTION,
    REVENUE_SECTION_SUBTITLE,
    CHART_MIN,
    CHART_MAX,
    RECENT_INVOICES_TITLE,
    RECENT_EMPTY,
    COL_INVOICE_NO,
    COL_CLIENT,
    COL_DATE,
    COL_TTC,

    // ── Devis à relancer (US-12) ────────────────────────────────────────────
    QUOTES_FOLLOWUP_TITLE,
    QUOTES_FOLLOWUP_SUBTITLE,
    QUOTES_FOLLOWUP_EMPTY,
    COL_QUOTE_NO,
    COL_VALIDITY,
    QUOTE_EXPIRED,
    QUOTE_DUE_TODAY,
    QUOTE_DAYS_LEFT,

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
    ACTION_SAVE_DRAFT,
    ACTION_SUBMIT_INVOICE,
    FORM_ARCHIVE_NOTICE,
    FORM_PROCESSING,

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

    // ── Immutabilité fiscale (verrouillage hors brouillon) ──────────────────
    ACTION_EDIT_INVOICE,
    ACTION_CANCEL_BY_CREDIT_NOTE,
    DETAIL_CANCELLED_READ_ONLY,
    INVOICE_LOCKED_HINT,
    INVOICE_CREDITED_BY,

    // ── Cycle de vie DGFIP & Piste d'Audit Fiable (US-07) ───────────────────
    ACTION_MARK_DEPOSITED,
    ACTION_MARK_PAID,
    ACTION_MARK_REJECTED,
    ACTION_MARK_REFUSED,
    ACTION_REOPEN_DRAFT,
    AUDIT_TRAIL_TITLE,
    AUDIT_TRAIL_EMPTY,
    AUDIT_REASON_LABEL,
    AUDIT_REASON_REQUIRED,
    AUDIT_CONFIRM,
    LIFECYCLE_TITLE,
    ACTION_EXPORT_INVOICE_XML,
    ACTION_EXPORT_CREDIT_NOTE_XML,
    EXPORT_SUCCESS,
    EXPORT_FAILED,

    FACTURX_BADGE,
    FILTER_ALL,
    FILTER_DRAFT,
    FILTER_DEPOSITED,
    FILTER_REJECTED,
    FILTER_REFUSED,
    FILTER_PAID,
    FILTER_CANCELLED,

    // ── Statuts de facture ──────────────────────────────────────────────────
    STATUS_DRAFT,
    // STATUS_VALIDATED / STATUS_SENT restent utilisés par les devis (QuoteStatus), dont le
    // cycle de vie est distinct de celui, réglementaire, des factures.
    STATUS_VALIDATED,
    STATUS_SENT,
    STATUS_DEPOSITED,
    STATUS_REJECTED,
    STATUS_REFUSED,
    STATUS_PAID,
    STATUS_CANCELLED,

    // ── Écrans "à venir" ────────────────────────────────────────────────────
    PLACEHOLDER_COMING_SOON,

    // ── Écran Clients ───────────────────────────────────────────────────────
    CLIENTS_TITLE,
    CLIENTS_SUBTITLE,
    CLIENTS_ADD,
    CLIENTS_EMPTY,
    CLIENTS_LOADING,
    CLIENT_FORM_NEW_TITLE,
    CLIENT_FORM_EDIT_TITLE,
    CLIENT_FIELD_NAME,
    CLIENT_FIELD_SIRET,
    CLIENT_FIELD_EMAIL,
    CLIENT_SIRET_LOCKED_HINT,
    CLIENT_ACTION_EDIT,
    CLIENT_ACTION_DELETE,
    CLIENT_DELETE_TITLE,
    CLIENT_DELETE_CONFIRM,
    ACTION_SAVE,
    ACTION_CANCEL,

    // ── Écran Paramètres fiscaux ────────────────────────────────────────────
    SETTINGS_TITLE,
    SETTINGS_SUBTITLE,
    SETTINGS_SECTION_ISSUER,
    SETTINGS_SECTION_VAT,
    SETTINGS_SECTION_COMPLIANCE,
    SETTINGS_FIELD_ISSUER_NAME,
    SETTINGS_FIELD_ISSUER_SIRET,
    SETTINGS_FIELD_SIREN_DERIVED,
    SETTINGS_FIELD_VAT_NUMBER,
    SETTINGS_VAT_RATES_REFERENCE,
    SETTINGS_DEFAULT_VAT_RATE,
    SETTINGS_FACTURX_SWITCH,
    SETTINGS_SAVE,

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
