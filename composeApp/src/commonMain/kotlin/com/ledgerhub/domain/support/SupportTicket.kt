package com.ledgerhub.domain.support

import com.ledgerhub.domain.i18n.StringKey

/**
 * Catégories de questions et tickets d'assistance réglementaire et technique (US-30).
 */
enum class SupportCategory(val rawValue: String, val titleKey: StringKey) {
    MANDATORY_MENTIONS_2026("mandatory_mentions_2026", StringKey.SUPPORT_CAT_MANDATORY_MENTIONS),
    FACTUR_X_FORMAT("factur_x_format", StringKey.SUPPORT_CAT_FACTUR_X),
    E_REPORTING("e_reporting", StringKey.SUPPORT_CAT_E_REPORTING),
    VAT_CALCULATION("vat_calculation", StringKey.SUPPORT_CAT_VAT),
    DEGRADED_MODE("degraded_mode", StringKey.SUPPORT_CAT_DEGRADED_MODE),
    GENERAL_SUPPORT("general_support", StringKey.SUPPORT_CAT_GENERAL);

    companion object {
        fun fromRaw(raw: String): SupportCategory =
            entries.firstOrNull { it.rawValue == raw } ?: GENERAL_SUPPORT
    }
}

/**
 * Statut d'avancement d'un ticket de support.
 */
enum class TicketStatus(val rawValue: String) {
    OPEN("OPEN"),
    IN_PROGRESS("IN_PROGRESS"),
    RESOLVED("RESOLVED"),
    CLOSED("CLOSED");

    companion object {
        fun fromRaw(raw: String): TicketStatus =
            entries.firstOrNull { it.rawValue == raw } ?: OPEN
    }
}

/**
 * Modèle de domaine représentant un ticket d'aide réglementaire / technique.
 */
data class SupportTicket(
    val id: String,
    val userId: String,
    val userEmail: String,
    val category: SupportCategory,
    val subject: String,
    val description: String,
    val status: TicketStatus = TicketStatus.OPEN,
    val createdAt: String,
    val updatedAt: String? = null,
)
