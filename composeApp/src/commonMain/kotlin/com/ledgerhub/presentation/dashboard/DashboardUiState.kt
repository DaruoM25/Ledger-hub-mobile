package com.ledgerhub.presentation.dashboard

import com.ledgerhub.domain.dashboard.DashboardAnalytics
import com.ledgerhub.domain.dashboard.MonthlyRevenue
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
    val monthlyRevenue: List<MonthlyRevenue> get() = analytics?.monthlyRevenue ?: emptyList()
    val recentDocuments: List<RecentDocument> get() = analytics?.recentDocuments ?: emptyList()
}
