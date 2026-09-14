package com.ledgerhub.presentation.vat

/**
 * Constantes et tags sémantiques pour les tests d'interface (Robolectric & Device).
 */
object VatDashboardTags {
    const val SCREEN = "vat_dashboard_screen"
    const val LOADING = "vat_dashboard_loading"
    const val PERIOD_SELECTOR = "vat_period_selector"
    fun periodChip(filter: String) = "vat_period_chip_$filter"

    const val KPI_COLLECTED_EXIGIBLE = "vat_kpi_collected_exigible"
    const val KPI_PENDING_COLLECTION = "vat_kpi_pending_collection"
    const val KPI_DEDUCTIBLE = "vat_kpi_deductible"
    const val KPI_NET_BALANCE = "vat_kpi_net_balance"

    const val CA3_SECTION = "vat_ca3_section"
    fun ca3Line(lineCode: String) = "vat_ca3_line_$lineCode"

    const val DEDUCTIBLE_INPUT = "vat_deductible_input"
    const val REFRESH_BUTTON = "vat_refresh_button"
}
