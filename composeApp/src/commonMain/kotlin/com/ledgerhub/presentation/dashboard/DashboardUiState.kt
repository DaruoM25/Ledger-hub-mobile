package com.ledgerhub.presentation.dashboard

import com.ledgerhub.domain.dashboard.DashboardAnalytics
import com.ledgerhub.domain.dashboard.MonthlyRevenue
import com.ledgerhub.domain.dashboard.QuoteFollowUpItem
import com.ledgerhub.domain.dashboard.RecentDocument

/** État immuable de l'écran tableau de bord — pattern UDF, symétrique à QuotesUiState. */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val loadErrorMessage: String? = null,
    val analytics: DashboardAnalytics? = null,
) {
    val collectedRevenueCents: Long get() = analytics?.collectedRevenue?.cents ?: 0L
    val pendingRevenueCents: Long get() = analytics?.pendingRevenue?.cents ?: 0L
    val overdueRevenueCents: Long get() = analytics?.overdueRevenue?.cents ?: 0L

    /** Nombre total de factures émises (3ᵉ KPI) — voir [DashboardAnalytics.issuedCount]. */
    val issuedCount: Int get() = analytics?.issuedCount ?: 0
    val monthlyRevenue: List<MonthlyRevenue> get() = analytics?.monthlyRevenue ?: emptyList()
    val recentDocuments: List<RecentDocument> get() = analytics?.recentDocuments ?: emptyList()

    /** Montant **HT** cumulé des devis envoyés — 4ᵉ KPI « Devis en attente » (US-12). */
    val pendingQuotesTotalCents: Long get() = analytics?.pendingQuotesTotal?.cents ?: 0L
    val pendingQuotesCount: Int get() = analytics?.pendingQuotesCount ?: 0

    /** Devis envoyés dont l'échéance approche, du plus urgent au moins urgent. */
    val quotesToFollowUp: List<QuoteFollowUpItem> get() = analytics?.quotesToFollowUp ?: emptyList()

    val hasQuotesToFollowUp: Boolean get() = quotesToFollowUp.isNotEmpty()
}
