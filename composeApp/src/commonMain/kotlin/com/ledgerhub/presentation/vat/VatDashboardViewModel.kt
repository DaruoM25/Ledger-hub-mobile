package com.ledgerhub.presentation.vat

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.vat.CalculateVatMetricsUseCase
import com.ledgerhub.domain.vat.VatPeriodFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VatDashboardViewModel(
    private val calculateVatMetricsUseCase: CalculateVatMetricsUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _uiState = MutableStateFlow(VatDashboardUiState(isLoading = true))
    val uiState: StateFlow<VatDashboardUiState> = _uiState.asStateFlow()

    private var currentDeductible: Money = Money.ZERO

    init {
        loadMetrics()
    }

    fun processIntent(intent: VatDashboardIntent) {
        when (intent) {
            is VatDashboardIntent.LoadMetrics -> loadMetrics()
            is VatDashboardIntent.ChangePeriod -> {
                _uiState.update { it.copy(selectedPeriod = intent.period) }
                loadMetrics()
            }
            is VatDashboardIntent.UpdateDeductibleVat -> {
                currentDeductible = intent.amount
                val rawFormatted = (intent.amount.cents / 100.0).toString()
                _uiState.update { it.copy(deductibleVatInput = rawFormatted) }
                loadMetrics()
            }
        }
    }

    private fun loadMetrics() {
        coroutineScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val currentFilter = _uiState.value.selectedPeriod

            val result = calculateVatMetricsUseCase(
                period = currentFilter,
                simulatedDeductibleVat = currentDeductible,
            )

            result.fold(
                onSuccess = { metrics ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            metrics = metrics,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Erreur de calcul TVA",
                        )
                    }
                },
            )
        }
    }

    fun onCleared() {
        coroutineScope.cancel()
    }
}
