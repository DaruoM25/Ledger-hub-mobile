package com.ledgerhub.presentation.subscription

import com.ledgerhub.data.subscription.MockSubscriptionRepository
import com.ledgerhub.domain.subscription.PremiumFeature
import com.ledgerhub.domain.subscription.SubscriptionRepository
import com.ledgerhub.domain.subscription.SubscriptionStatus
import com.ledgerhub.domain.subscription.SubscriptionTier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de l'écran Paywall — Pattern MVVM / UDF.
 */
class PaywallViewModel(
    private val subscriptionRepository: SubscriptionRepository = MockSubscriptionRepository(),
    reasonFeature: PremiumFeature? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(
        PaywallUiState(reasonFeature = reasonFeature)
    )
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        subscriptionRepository.observeSubscription()
            .onEach { status ->
                _uiState.update { it.copy(currentStatus = status) }
            }
            .launchIn(scope)
    }

    fun processIntent(intent: PaywallIntent) {
        when (intent) {
            is PaywallIntent.SelectTier -> {
                _uiState.update { it.copy(selectedTier = intent.tier, errorMessage = null) }
            }

            PaywallIntent.PurchaseSelected -> purchase()

            PaywallIntent.RestorePurchases -> restore()

            is PaywallIntent.PromoCodeChanged -> {
                _uiState.update {
                    it.copy(
                        promoCode = intent.code,
                        promoErrorMessage = null,
                        promoSuccessMessage = null,
                    )
                }
            }

            PaywallIntent.ApplyPromoCode -> applyPromo()

            PaywallIntent.Dismiss -> {
                _uiState.update { it.copy(isDismissed = true) }
            }

            PaywallIntent.ClearError -> {
                _uiState.update { it.copy(errorMessage = null, promoErrorMessage = null) }
            }
        }
    }

    private fun purchase() {
        val tier = _uiState.value.selectedTier
        if (tier == SubscriptionTier.FREE) return

        _uiState.update { it.copy(isPurchasing = true, errorMessage = null) }
        scope.launch {
            val result = subscriptionRepository.purchase(tier)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { status ->
                        current.copy(
                            isPurchasing = false,
                            currentStatus = status,
                            promoSuccessMessage = "Félicitations ! Vous êtes désormais LedgerHub Pro.",
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isPurchasing = false,
                            errorMessage = error.message ?: "Échec du traitement de l'achat.",
                        )
                    }
                )
            }
        }
    }

    private fun restore() {
        _uiState.update { it.copy(isRestoring = true, errorMessage = null) }
        scope.launch {
            val result = subscriptionRepository.restorePurchases()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { status ->
                        val msg = if (status.isPro) "Achats Pro restaurés avec succès." else "Aucun achat actif trouvé."
                        current.copy(isRestoring = false, currentStatus = status, promoSuccessMessage = msg)
                    },
                    onFailure = { error ->
                        current.copy(isRestoring = false, errorMessage = error.message ?: "Impossible de restaurer les achats.")
                    }
                )
            }
        }
    }

    private fun applyPromo() {
        val code = _uiState.value.promoCode.trim()
        if (code.isBlank()) {
            _uiState.update { it.copy(promoErrorMessage = "Veuillez saisir un code.") }
            return
        }

        _uiState.update { it.copy(isApplyingPromo = true, promoErrorMessage = null, promoSuccessMessage = null) }
        scope.launch {
            val result = subscriptionRepository.applyPromoCode(code)
            _uiState.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isApplyingPromo = false,
                            promoSuccessMessage = "Code validé ! Statut Pro activé pour le jury Devpost.",
                            promoCode = "",
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isApplyingPromo = false,
                            promoErrorMessage = error.message ?: "Code promo invalide ou expiré.",
                        )
                    }
                )
            }
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
