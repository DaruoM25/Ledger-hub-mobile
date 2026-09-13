package com.ledgerhub.presentation.support

import com.ledgerhub.domain.support.FeatureCategory
import com.ledgerhub.domain.support.FeatureRequest
import com.ledgerhub.domain.support.SupportCategory
import com.ledgerhub.domain.support.SupportTicket

/**
 * Onglets disponibles sur l'écran Support & Feedback.
 */
enum class SupportTab {
    REGULATORY_HELP,
    FEATURE_IDEAS,
}

/**
 * État réactif de l'écran Support & Feedback (US-30).
 */
data class SupportFeedbackUiState(
    val selectedTab: SupportTab = SupportTab.REGULATORY_HELP,
    val isLoading: Boolean = false,
    val userEmail: String = "",
    val userId: String = "",

    // Formulaire Ticket
    val ticketSubject: String = "",
    val ticketDescription: String = "",
    val ticketCategory: SupportCategory = SupportCategory.MANDATORY_MENTIONS_2026,
    val isSubmittingTicket: Boolean = false,
    val tickets: List<SupportTicket> = emptyList(),

    // Boîte à Idées
    val featureRequests: List<FeatureRequest> = emptyList(),
    val showIdeaDialog: Boolean = false,
    val ideaTitle: String = "",
    val ideaDescription: String = "",
    val ideaCategory: FeatureCategory = FeatureCategory.INVOICING,
    val isSubmittingIdea: Boolean = false,

    // Feedback & Toast
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

/**
 * Intentions utilisateur (MVI).
 */
sealed interface SupportFeedbackIntent {
    data class SelectTab(val tab: SupportTab) : SupportFeedbackIntent
    data object LoadData : SupportFeedbackIntent

    // Ticket intents
    data class TicketSubjectChanged(val subject: String) : SupportFeedbackIntent
    data class TicketDescriptionChanged(val description: String) : SupportFeedbackIntent
    data class TicketCategoryChanged(val category: SupportCategory) : SupportFeedbackIntent
    data object SubmitTicket : SupportFeedbackIntent

    // Idea intents
    data class ShowIdeaDialog(val show: Boolean) : SupportFeedbackIntent
    data class IdeaTitleChanged(val title: String) : SupportFeedbackIntent
    data class IdeaDescriptionChanged(val description: String) : SupportFeedbackIntent
    data class IdeaCategoryChanged(val category: FeatureCategory) : SupportFeedbackIntent
    data object SubmitIdea : SupportFeedbackIntent

    // Vote intent (Optimistic update avec rollback)
    data class VoteIdea(val featureRequestId: String) : SupportFeedbackIntent

    // Dismiss banners
    data object DismissFeedback : SupportFeedbackIntent
}
