package com.ledgerhub.domain.i18n

/**
 * Clés du dictionnaire d'interface — une clé = une chaîne UI, miroir strict des clés du
 * dictionnaire Web (Prompt 2). Chaque clé DOIT être traduite dans les deux langues (garanti par
 * `AppTranslationsTest`). Regroupées par zone fonctionnelle.
 *
 * Hors périmètre US-02 (restent en français) : le corps prosaïque du détail facture
 * (émetteur/ventilation TVA/boutons d'action) et les modules Devis/Avoir. L'aperçu WYSIWYG
 * `InvoicePaperCanvas` en est sorti avec l'US-15 : devenu un mode de saisie à part entière, il est
 * traduit comme le formulaire classique. L'écran d'authentification en est sorti avec l'US-21 :
 * connexion **et** inscription sont désormais bilingues.
 */
enum class StringKey {
    // ── Application / shell ──────────────────────────────────────────────────
    APP_NAME,
    SIDEBAR_COMPLIANCE,
    ACTION_CREATE_INVOICE,
    OVERLAY_BACK_DASHBOARD,
    OVERLAY_BACK_INVOICES,

    // ── Bascule de thème (US-25) ────────────────────────────────────────────
    // Libellés d'accessibilité : le bouton n'affiche qu'un glyphe, ce sont ces chaînes que
    // TalkBack énonce. Elles nomment la **destination** de l'appui, pas l'état courant.
    THEME_TOGGLE_TO_LIGHT,
    THEME_TOGGLE_TO_DARK,

    // ── Navigation ──────────────────────────────────────────────────────────
    NAV_OVERVIEW,
    NAV_QUOTES,
    NAV_INVOICES,
    NAV_CLIENTS,
    NAV_SETTINGS,
    NAV_DIRECTORY,
    NAV_RECONCILIATION,

    // ── e-Reporting (US-26 : l'écran existait depuis l'US-08, sans point d'entrée) ───────────
    NAV_EREPORTING,
    EREPORTING_TRIGGER_SUBTITLE,

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
    ACTION_MARK_APPROVED,
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
    FILTER_APPROVED,
    FILTER_REJECTED,
    FILTER_REFUSED,
    FILTER_PAID,
    FILTER_CANCELLED,
    FILTER_OVERDUE,

    // ── Statuts de facture ──────────────────────────────────────────────────
    STATUS_DRAFT,
    // STATUS_VALIDATED / STATUS_SENT restent utilisés par les devis (QuoteStatus), dont le
    // cycle de vie est distinct de celui, réglementaire, des factures.
    STATUS_VALIDATED,
    STATUS_SENT,
    STATUS_DEPOSITED,
    STATUS_APPROVED,
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

    // ── Aperçu A4 de la facture (US-14) ────────────────────────────────────
    ACTION_PREVIEW_INVOICE,
    PREVIEW_TITLE,
    PREVIEW_CLOSE,
    PREVIEW_BILL_TO,
    PREVIEW_INVOICE_NUMBER,
    PREVIEW_DUE_DATE,
    PREVIEW_SIRET,
    PREVIEW_SIREN,
    PREVIEW_VAT_NUMBER,
    PREVIEW_COL_DESCRIPTION,
    PREVIEW_COL_QUANTITY,
    PREVIEW_COL_UNIT_PRICE_HT,
    PREVIEW_COL_VAT_RATE,
    PREVIEW_COL_TOTAL_HT,
    PREVIEW_TOTAL_HT,
    PREVIEW_TOTAL_VAT,
    PREVIEW_TOTAL_TTC,
    PREVIEW_LEGAL_ASSOCIATION,
    PREVIEW_LEGAL_LATE_PENALTY,
    PREVIEW_LEGAL_FIXED_INDEMNITY,
    PREVIEW_BANK_DETAILS,
    PREVIEW_IBAN,
    PREVIEW_BIC,

    // ── Mode de saisie de la facture (US-15) ───────────────────────────────
    FORM_MODE_SELECTOR_LABEL,
    FORM_MODE_CLASSIC,
    FORM_MODE_BLANK_PAGE,
    // Ventilation TVA de la feuille blanche : « TVA 20 % sur 100,00 € : 20,00 € ».
    PAPER_VAT_LABEL,
    PAPER_VAT_BASE_ON,

    // ── Réglementation B2B et pénalités de retard (US-16) ──────────────────
    FORM_SECTION_B2B,
    B2B_PENALTIES_CHECKBOX,
    /**
     * Mention imposée par l'article L.441-10 du Code de commerce. Sa formulation est
     * réglementaire, pas rédactionnelle : elle est figée par sentinelle dans
     * `AppTranslationsTest`, comme les statuts PPF de l'US-13.
     */
    B2B_LEGAL_MENTION,
    /** Repli quand les pénalités B2B ne s'appliquent pas — le pied de facture n'est jamais vide. */
    B2B_COURTESY_MENTION,

    // ── Traçabilité & horodatage réglementaire (US-17) ─────────────────────
    AUDIT_PANEL_TITLE,
    AUDIT_STEP_CREATED,
    AUDIT_STEP_SEALED,
    AUDIT_STEP_PPF,
    AUDIT_STEP_STATUS,
    AUDIT_SHA256_LABEL,
    AUDIT_STEP_PENDING,
    AUDIT_STEP_DONE,

    // ── Rapprochement bancaire (US-18) ──────────────────────────────────────
    RECONCILIATION_TITLE,
    RECONCILIATION_TRANSACTIONS_COLUMN,
    RECONCILIATION_INVOICES_COLUMN,
    RECONCILIATION_TAB_TRANSACTIONS,
    RECONCILIATION_TAB_INVOICES,
    RECONCILIATION_ACTION_MATCH,
    RECONCILIATION_BADGE_RECONCILED,
    RECONCILIATION_BADGE_AMOUNT_MISMATCH,
    RECONCILIATION_DELTA_LABEL,
    RECONCILIATION_TRANSACTIONS_EMPTY,
    RECONCILIATION_INVOICES_EMPTY,
    RECONCILIATION_SELECTION_HINT,

    // ── Palette de commandes (US-19) ────────────────────────────────────────
    COMMAND_PALETTE_TITLE,
    COMMAND_PALETTE_TRIGGER_LABEL,
    COMMAND_PALETTE_PLACEHOLDER,
    COMMAND_PALETTE_EMPTY,
    COMMAND_ACTION_CREATE_INVOICE,
    COMMAND_ACTION_REMIND_OVERDUE,
    COMMAND_ACTION_EXPORT_ACCOUNTING,

    // ── Hub d'intégrations (US-20) ──────────────────────────────────────────
    INTEGRATIONS_TITLE,
    INTEGRATIONS_SUBTITLE,
    INTEGRATIONS_TRIGGER_LABEL,
    INTEGRATIONS_BACK,
    INTEGRATIONS_LOCKED_NOTICE,
    INTEGRATION_BADGE_BETA,
    INTEGRATION_BADGE_COMING_SOON,
    INTEGRATION_STRIPE_TITLE,
    INTEGRATION_STRIPE_DESC,
    INTEGRATION_FEC_TITLE,
    INTEGRATION_FEC_DESC,
    INTEGRATION_SLACK_TITLE,
    INTEGRATION_SLACK_DESC,
    INTEGRATION_BANK_SYNC_TITLE,
    INTEGRATION_BANK_SYNC_DESC,

    // ── Authentification : connexion (traduite avec l'US-21) ────────────────
    AUTH_LOGIN_TITLE,
    AUTH_LOGIN_SUBTITLE,
    AUTH_EMAIL_LABEL,
    AUTH_EMAIL_PLACEHOLDER,
    AUTH_PASSWORD_LABEL,
    AUTH_LOGIN_SUBMIT,
    AUTH_NO_ACCOUNT_PROMPT,
    AUTH_REGISTER_LINK,
    AUTH_DISCLAIMER,

    // ── Inscription intelligente par SIRET (US-21) ──────────────────────────
    AUTH_TAB_LOGIN,
    AUTH_TAB_REGISTER,
    AUTH_REGISTER_TITLE,
    AUTH_REGISTER_SUBTITLE,
    AUTH_SIRET_LABEL,
    AUTH_SIRET_PLACEHOLDER,
    AUTH_SIRET_HELPER,
    AUTH_SIRET_SEARCH_DESC,
    AUTH_COMPANY_NAME_LABEL,
    AUTH_COMPANY_NAME_PLACEHOLDER,
    AUTH_SIRENE_VERIFYING,
    AUTH_SIRENE_VERIFIED_BADGE,
    AUTH_SIRENE_NOT_FOUND,
    AUTH_SIRENE_UNAVAILABLE,
    AUTH_REGISTER_SUBMIT,

    // ── Export comptable FEC & Factur-X (US-22) ─────────────────────────────
    EXPORT_MODAL_TITLE,
    EXPORT_MODAL_SUBTITLE,
    EXPORT_TRIGGER_LABEL,
    EXPORT_CLOSE,
    EXPORT_PERIOD_SECTION,
    EXPORT_DATE_FROM_LABEL,
    EXPORT_DATE_TO_LABEL,
    EXPORT_PERIOD_INVALID,
    EXPORT_FORMAT_SECTION,
    EXPORT_FORMAT_FEC_TITLE,
    EXPORT_FORMAT_FEC_DESC,
    EXPORT_FORMAT_FACTURX_TITLE,
    EXPORT_FORMAT_FACTURX_DESC,
    EXPORT_FORMAT_EXCEL_TITLE,
    EXPORT_FORMAT_EXCEL_DESC,
    EXPORT_GENERATE_ACTION,
    EXPORT_GENERATING_LABEL,
    EXPORT_SUCCESS_TITLE,
    EXPORT_DOCUMENT_COUNT_LABEL,
    EXPORT_DOWNLOAD_ACTION,

    // ── Audit de conformité Factur-X 2026 (US-24) ───────────────────────────
    COMPLIANCE_PANEL_TITLE,
    COMPLIANCE_PANEL_SUBTITLE,
    COMPLIANCE_SCAN_ACTION,
    COMPLIANCE_NOT_SCANNED,
    COMPLIANCE_ALL_PASSED,
    COMPLIANCE_ALERT_WARNING_TITLE,
    COMPLIANCE_ALERT_ERROR_TITLE,
    COMPLIANCE_CHECK_SIRET_TITLE,
    COMPLIANCE_CHECK_VAT_TITLE,
    COMPLIANCE_CHECK_LEGAL_TITLE,
    COMPLIANCE_CHECK_FACTURX_TITLE,
    COMPLIANCE_SIRET_OK,
    COMPLIANCE_SIRET_ISSUER_INVALID,
    COMPLIANCE_SIRET_CLIENT_INVALID,
    COMPLIANCE_SIRET_LUHN,
    COMPLIANCE_VAT_OK,
    COMPLIANCE_VAT_ABSENT,
    COMPLIANCE_VAT_MALFORMED,
    COMPLIANCE_VAT_KEY_MISMATCH,
    COMPLIANCE_LEGAL_OK,
    COMPLIANCE_LEGAL_MISSING,
    COMPLIANCE_FACTURX_OK,
    COMPLIANCE_FACTURX_NO_LINE,
    COMPLIANCE_FACTURX_TOTALS,
    COMPLIANCE_FACTURX_DISABLED,

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
