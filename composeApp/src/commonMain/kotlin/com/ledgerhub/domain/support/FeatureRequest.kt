package com.ledgerhub.domain.support

import com.ledgerhub.domain.i18n.StringKey

/**
 * Catégories d'idées ou d'évolutions proposées par la communauté (US-30).
 */
enum class FeatureCategory(val rawValue: String, val titleKey: StringKey) {
    INVOICING("invoicing", StringKey.FEATURE_CAT_INVOICING),
    TAX_COMPLIANCE("tax_compliance", StringKey.FEATURE_CAT_TAX_COMPLIANCE),
    AUTOMATION("automation", StringKey.FEATURE_CAT_AUTOMATION),
    INTEGRATIONS("integrations", StringKey.FEATURE_CAT_INTEGRATIONS),
    UI_UX("ui_ux", StringKey.FEATURE_CAT_UI_UX),
    OTHER("other", StringKey.FEATURE_CAT_OTHER);

    companion object {
        fun fromRaw(raw: String): FeatureCategory =
            entries.firstOrNull { it.rawValue == raw } ?: OTHER
    }
}

/**
 * Statut d'une idée dans la boîte à idées.
 */
enum class FeatureStatus(val rawValue: String) {
    PROPOSED("PROPOSED"),
    UNDER_REVIEW("UNDER_REVIEW"),
    PLANNED("PLANNED"),
    IN_DEVELOPMENT("IN_DEVELOPMENT"),
    COMPLETED("COMPLETED"),
    DECLINED("DECLINED");

    companion object {
        fun fromRaw(raw: String): FeatureStatus =
            entries.firstOrNull { it.rawValue == raw } ?: PROPOSED
    }
}

/**
 * Modèle de domaine représentant une idée soumise dans la boîte à idées.
 */
data class FeatureRequest(
    val id: String,
    val title: String,
    val description: String,
    val category: FeatureCategory,
    val authorId: String,
    val authorEmail: String,
    val voteCount: Long = 0L,
    val status: FeatureStatus = FeatureStatus.PROPOSED,
    val createdAt: String,
    val hasVoted: Boolean = false,
)
