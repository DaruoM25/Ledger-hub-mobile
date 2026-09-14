package com.ledgerhub.presentation.vat

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.vat.VatMetrics
import com.ledgerhub.domain.vat.VatPeriodFilter

sealed interface VatDashboardIntent {
    data object LoadMetrics : VatDashboardIntent
    data class ChangePeriod(val period: VatPeriodFilter) : VatDashboardIntent
    data class UpdateDeductibleVat(val amount: Money) : VatDashboardIntent
}

data class VatDashboardUiState(
    val isLoading: Boolean = false,
    val selectedPeriod: VatPeriodFilter = VatPeriodFilter.ALL,
    val deductibleVatInput: String = "0.00",
    val metrics: VatMetrics = VatMetrics(),
    val errorMessage: String? = null,
)
