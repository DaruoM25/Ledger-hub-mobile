package com.ledgerhub.presentation.support

import com.ledgerhub.domain.support.AlreadyVotedException
import com.ledgerhub.domain.support.CreateSupportTicketUseCase
import com.ledgerhub.domain.support.GetFeatureRequestsUseCase
import com.ledgerhub.domain.support.SubmitFeatureRequestUseCase
import com.ledgerhub.domain.support.SupportRepository
import com.ledgerhub.domain.support.VoteFeatureRequestUseCase
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

/**
 * ViewModel réactif MVI pour la gestion du Support et de la Boîte à Idées (US-30).
 *
 * Implémente une mise à jour optimiste (Optimistic Update) sur le vote avec rollback automatique
 * en cas de rejet (par exemple en cas de doublon de vote ou d'erreur réseau).
 */
class SupportFeedbackViewModel(
    private val supportRepository: SupportRepository,
    private val createSupportTicketUseCase: CreateSupportTicketUseCase,
    private val getFeatureRequestsUseCase: GetFeatureRequestsUseCase,
    private val submitFeatureRequestUseCase: SubmitFeatureRequestUseCase,
    private val voteFeatureRequestUseCase: VoteFeatureRequestUseCase,
    private val currentUserId: String = "default-user",
    private val currentUserEmail: String = "demo@ledgerhub.app",
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(
        SupportFeedbackUiState(
            userId = currentUserId,
            userEmail = currentUserEmail,
        ),
    )
    val uiState: StateFlow<SupportFeedbackUiState> = _uiState.asStateFlow()

    init {
        processIntent(SupportFeedbackIntent.LoadData)
    }

    fun processIntent(intent: SupportFeedbackIntent) {
        when (intent) {
            is SupportFeedbackIntent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = intent.tab, errorMessage = null, successMessage = null) }
            }

            SupportFeedbackIntent.LoadData -> loadAllData()

            is SupportFeedbackIntent.TicketSubjectChanged -> {
                _uiState.update { it.copy(ticketSubject = intent.subject) }
            }

            is SupportFeedbackIntent.TicketDescriptionChanged -> {
                _uiState.update { it.copy(ticketDescription = intent.description) }
            }

            is SupportFeedbackIntent.TicketCategoryChanged -> {
                _uiState.update { it.copy(ticketCategory = intent.category) }
            }

            SupportFeedbackIntent.SubmitTicket -> submitTicket()

            is SupportFeedbackIntent.ShowIdeaDialog -> {
                _uiState.update { it.copy(showIdeaDialog = intent.show, errorMessage = null) }
            }

            is SupportFeedbackIntent.IdeaTitleChanged -> {
                _uiState.update { it.copy(ideaTitle = intent.title) }
            }

            is SupportFeedbackIntent.IdeaDescriptionChanged -> {
                _uiState.update { it.copy(ideaDescription = intent.description) }
            }

            is SupportFeedbackIntent.IdeaCategoryChanged -> {
                _uiState.update { it.copy(ideaCategory = intent.category) }
            }

            SupportFeedbackIntent.SubmitIdea -> submitIdea()

            is SupportFeedbackIntent.VoteIdea -> voteIdea(intent.featureRequestId)

            SupportFeedbackIntent.DismissFeedback -> {
                _uiState.update { it.copy(successMessage = null, errorMessage = null) }
            }
        }
    }

    private fun loadAllData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val ticketsResult = supportRepository.getTicketsByUser(_uiState.value.userId)
            val ideasResult = getFeatureRequestsUseCase(_uiState.value.userId)

            val tickets = ticketsResult.getOrDefault(emptyList())
            val ideas = ideasResult.getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    isLoading = false,
                    tickets = tickets,
                    featureRequests = ideas,
                )
            }
        }
    }

    private fun submitTicket() {
        val state = _uiState.value
        scope.launch {
            _uiState.update { it.copy(isSubmittingTicket = true, errorMessage = null, successMessage = null) }
            val result = createSupportTicketUseCase(
                userId = state.userId,
                userEmail = state.userEmail,
                category = state.ticketCategory,
                subject = state.ticketSubject,
                description = state.ticketDescription,
            )

            result.fold(
                onSuccess = { newTicket ->
                    _uiState.update {
                        it.copy(
                            isSubmittingTicket = false,
                            ticketSubject = "",
                            ticketDescription = "",
                            tickets = (listOf(newTicket) + it.tickets).distinctBy { t -> t.id },
                            successMessage = "Votre ticket d'assistance a été enregistré avec succès.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSubmittingTicket = false,
                            errorMessage = error.message ?: "Erreur lors de la création du ticket.",
                        )
                    }
                },
            )
        }
    }

    private fun submitIdea() {
        val state = _uiState.value
        scope.launch {
            _uiState.update { it.copy(isSubmittingIdea = true, errorMessage = null) }
            val result = submitFeatureRequestUseCase(
                title = state.ideaTitle,
                description = state.ideaDescription,
                category = state.ideaCategory,
                authorId = state.userId,
                authorEmail = state.userEmail,
            )

            result.fold(
                onSuccess = { newIdea ->
                    _uiState.update {
                        it.copy(
                            isSubmittingIdea = false,
                            showIdeaDialog = false,
                            ideaTitle = "",
                            ideaDescription = "",
                            featureRequests = (listOf(newIdea) + it.featureRequests).distinctBy { f -> f.id },
                            successMessage = "Votre idée a été publiée dans la boîte à idées !",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSubmittingIdea = false,
                            errorMessage = error.message ?: "Erreur lors de la soumission de l'idée.",
                        )
                    }
                },
            )
        }
    }

    private fun voteIdea(featureRequestId: String) {
        val currentIdeas = _uiState.value.featureRequests
        val target = currentIdeas.firstOrNull { it.id == featureRequestId } ?: return

        if (target.hasVoted) {
            _uiState.update { it.copy(errorMessage = "Vous avez déjà voté pour cette idée.") }
            return
        }

        // 1. Mise à jour optimiste (StateFlow)
        val optimisticList = currentIdeas.map { idea ->
            if (idea.id == featureRequestId) {
                idea.copy(hasVoted = true, voteCount = idea.voteCount + 1)
            } else {
                idea
            }
        }.sortedByDescending { it.voteCount }

        _uiState.update { it.copy(featureRequests = optimisticList, errorMessage = null) }

        // 2. Persistance asynchrone & rollback en cas d'échec
        scope.launch {
            val result = voteFeatureRequestUseCase(
                userId = _uiState.value.userId,
                featureRequestId = featureRequestId,
            )

            result.onFailure { error ->
                // Rollback de l'état
                _uiState.update { current ->
                    val rolledBack = current.featureRequests.map { idea ->
                        if (idea.id == featureRequestId) {
                            idea.copy(hasVoted = false, voteCount = (idea.voteCount - 1).coerceAtLeast(0L))
                        } else {
                            idea
                        }
                    }.sortedByDescending { it.voteCount }

                    val errorMsg = if (error is AlreadyVotedException) {
                        "Vous avez déjà voté pour cette idée."
                    } else {
                        error.message ?: "Échec de l'enregistrement du vote."
                    }

                    current.copy(
                        featureRequests = rolledBack,
                        errorMessage = errorMsg,
                    )
                }
            }
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
