package com.ledgerhub.presentation.support

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.support.FeatureCategory
import com.ledgerhub.domain.support.FeatureRequest
import com.ledgerhub.domain.support.SupportCategory
import com.ledgerhub.domain.support.SupportTicket
import com.ledgerhub.domain.support.TicketStatus
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

/**
 * Écran Support & Feedback Loop — Material 3 Dark Theme (US-30).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SupportFeedbackScreen(
    viewModel: SupportFeedbackViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { testTag = SupportFeedbackTags.SCREEN },
    ) {
        // En-tête principal
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = tr(StringKey.SUPPORT_TITLE),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = tr(StringKey.SUPPORT_SUBTITLE),
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerHubTheme.palette.SecondaryText,
            )
        }

        // Bannières de retour (succès / erreur)
        uiState.successMessage?.let { msg ->
            FeedbackBanner(
                message = msg,
                isError = false,
                onDismiss = { viewModel.processIntent(SupportFeedbackIntent.DismissFeedback) },
                testTag = SupportFeedbackTags.SUCCESS_BANNER,
            )
        }
        uiState.errorMessage?.let { msg ->
            FeedbackBanner(
                message = msg,
                isError = true,
                onDismiss = { viewModel.processIntent(SupportFeedbackIntent.DismissFeedback) },
                testTag = SupportFeedbackTags.ERROR_BANNER,
            )
        }

        // Onglets M3
        PrimaryTabRow(
            selectedTabIndex = uiState.selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.TAB_ROW },
        ) {
            Tab(
                selected = uiState.selectedTab == SupportTab.REGULATORY_HELP,
                onClick = { viewModel.processIntent(SupportFeedbackIntent.SelectTab(SupportTab.REGULATORY_HELP)) },
                text = { Text(tr(StringKey.SUPPORT_TAB_REGULATORY)) },
                modifier = Modifier.semantics { testTag = SupportFeedbackTags.TAB_REGULATORY },
            )
            Tab(
                selected = uiState.selectedTab == SupportTab.FEATURE_IDEAS,
                onClick = { viewModel.processIntent(SupportFeedbackIntent.SelectTab(SupportTab.FEATURE_IDEAS)) },
                text = { Text(tr(StringKey.SUPPORT_TAB_IDEAS)) },
                modifier = Modifier.semantics { testTag = SupportFeedbackTags.TAB_IDEAS },
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LedgerHubTheme.palette.Accent)
            }
        } else {
            when (uiState.selectedTab) {
                SupportTab.REGULATORY_HELP -> RegulatoryHelpContent(
                    uiState = uiState,
                    onSubjectChanged = { viewModel.processIntent(SupportFeedbackIntent.TicketSubjectChanged(it)) },
                    onDescriptionChanged = { viewModel.processIntent(SupportFeedbackIntent.TicketDescriptionChanged(it)) },
                    onCategoryChanged = { viewModel.processIntent(SupportFeedbackIntent.TicketCategoryChanged(it)) },
                    onSubmitTicket = { viewModel.processIntent(SupportFeedbackIntent.SubmitTicket) },
                )

                SupportTab.FEATURE_IDEAS -> FeatureIdeasContent(
                    uiState = uiState,
                    onOpenSubmitDialog = { viewModel.processIntent(SupportFeedbackIntent.ShowIdeaDialog(true)) },
                    onVote = { viewModel.processIntent(SupportFeedbackIntent.VoteIdea(it)) },
                )
            }
        }

        // Modale d'ajout d'une idée
        if (uiState.showIdeaDialog) {
            SubmitIdeaDialog(
                uiState = uiState,
                onTitleChanged = { viewModel.processIntent(SupportFeedbackIntent.IdeaTitleChanged(it)) },
                onDescriptionChanged = { viewModel.processIntent(SupportFeedbackIntent.IdeaDescriptionChanged(it)) },
                onCategoryChanged = { viewModel.processIntent(SupportFeedbackIntent.IdeaCategoryChanged(it)) },
                onSubmit = { viewModel.processIntent(SupportFeedbackIntent.SubmitIdea) },
                onDismiss = { viewModel.processIntent(SupportFeedbackIntent.ShowIdeaDialog(false)) },
            )
        }
    }
}

@Composable
private fun RegulatoryHelpContent(
    uiState: SupportFeedbackUiState,
    onSubjectChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCategoryChanged: (SupportCategory) -> Unit,
    onSubmitTicket: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = tr(StringKey.SUPPORT_FORM_TITLE),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Sélecteur de catégorie réglementaire
                    Text(
                        text = tr(StringKey.SUPPORT_FIELD_CATEGORY),
                        style = MaterialTheme.typography.labelMedium,
                        color = LedgerHubTheme.palette.SecondaryText,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.SELECTOR_CATEGORY },
                    ) {
                        items(SupportCategory.entries) { cat ->
                            val isSelected = cat == uiState.ticketCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategoryChanged(cat) },
                                label = { Text(tr(cat.titleKey), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }

                    // Sujet
                    OutlinedTextField(
                        value = uiState.ticketSubject,
                        onValueChange = onSubjectChanged,
                        label = { Text(tr(StringKey.SUPPORT_FIELD_SUBJECT)) },
                        placeholder = { Text("Ex: Mention de dispense TVA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.INPUT_SUBJECT },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = LedgerHubTheme.palette.Border,
                        ),
                    )

                    // Description
                    OutlinedTextField(
                        value = uiState.ticketDescription,
                        onValueChange = onDescriptionChanged,
                        label = { Text(tr(StringKey.SUPPORT_FIELD_DESCRIPTION)) },
                        placeholder = { Text("Détaillez votre question ou cas fiscal...") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.INPUT_DESCRIPTION },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = LedgerHubTheme.palette.Border,
                        ),
                    )

                    Button(
                        onClick = onSubmitTicket,
                        enabled = !uiState.isSubmittingTicket && uiState.ticketSubject.isNotBlank() && uiState.ticketDescription.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.BTN_SUBMIT_TICKET },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        if (uiState.isSubmittingTicket) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Text("✉️  ${tr(StringKey.SUPPORT_SUBMIT_ACTION)}")
                        }
                    }
                }
            }
        }

        // Section historique des tickets
        item {
            Text(
                text = tr(StringKey.SUPPORT_MY_TICKETS_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (uiState.tickets.isEmpty()) {
            item {
                Text(
                    text = tr(StringKey.SUPPORT_NO_TICKETS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
        } else {
            items(uiState.tickets) { ticket ->
                TicketItemCard(ticket = ticket)
            }
        }
    }
}

@Composable
private fun TicketItemCard(ticket: SupportTicket) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.TICKET_ITEM_PREFIX + ticket.id },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = tr(ticket.category.titleKey),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                val statusColor = if (ticket.status == TicketStatus.RESOLVED) Color(0xFF10B981) else Color(0xFFF59E0B)
                val statusText = if (ticket.status == TicketStatus.RESOLVED) {
                    tr(StringKey.SUPPORT_TICKET_STATUS_RESOLVED)
                } else {
                    tr(StringKey.SUPPORT_TICKET_STATUS_OPEN)
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = ticket.subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = ticket.description,
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubTheme.palette.SecondaryText,
            )
        }
    }
}

@Composable
private fun FeatureIdeasContent(
    uiState: SupportFeedbackUiState,
    onOpenSubmitDialog: () -> Unit,
    onVote: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).semantics { testTag = SupportFeedbackTags.IDEAS_LIST },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr(StringKey.FEATURE_IDEAS_TITLE),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Button(
                    onClick = onOpenSubmitDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.semantics { testTag = SupportFeedbackTags.BTN_OPEN_SUBMIT_IDEA },
                ) {
                    Text("💡  ${tr(StringKey.FEATURE_SUBMIT_IDEA_ACTION)}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (uiState.featureRequests.isEmpty()) {
            item {
                Text(
                    text = tr(StringKey.FEATURE_NO_IDEAS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
        } else {
            items(uiState.featureRequests) { idea ->
                IdeaItemCard(idea = idea, onVote = { onVote(idea.id) })
            }
        }
    }
}

@Composable
private fun IdeaItemCard(
    idea: FeatureRequest,
    onVote: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.IDEA_ITEM_PREFIX + idea.id },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (idea.hasVoted) MaterialTheme.colorScheme.primary else LedgerHubTheme.palette.Border),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bouton de vote (Optimistic Upvote)
            Surface(
                color = if (idea.hasVoted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (idea.hasVoted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .size(width = 54.dp, height = 58.dp)
                    .clickable(onClick = onVote)
                    .semantics { testTag = SupportFeedbackTags.BTN_VOTE_PREFIX + idea.id },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text("▲", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${idea.voteCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { testTag = SupportFeedbackTags.VOTE_COUNT_PREFIX + idea.id },
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = tr(idea.category.titleKey),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "• ${idea.authorEmail}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerHubTheme.palette.SecondaryText,
                    )
                }

                Text(
                    text = idea.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = idea.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                )
            }
        }
    }
}

@Composable
private fun SubmitIdeaDialog(
    uiState: SupportFeedbackUiState,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCategoryChanged: (FeatureCategory) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(tr(StringKey.FEATURE_SUBMIT_IDEA_ACTION), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.ideaTitle,
                    onValueChange = onTitleChanged,
                    label = { Text(tr(StringKey.FEATURE_FIELD_TITLE)) },
                    placeholder = { Text("Ex: Mode hors-ligne étendu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.INPUT_IDEA_TITLE },
                )

                Text(tr(StringKey.FEATURE_FIELD_CATEGORY), style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FeatureCategory.entries) { cat ->
                        FilterChip(
                            selected = cat == uiState.ideaCategory,
                            onClick = { onCategoryChanged(cat) },
                            label = { Text(tr(cat.titleKey), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.ideaDescription,
                    onValueChange = onDescriptionChanged,
                    label = { Text(tr(StringKey.FEATURE_FIELD_DESCRIPTION)) },
                    placeholder = { Text("Expliquez pourquoi cette idée serait utile...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().semantics { testTag = SupportFeedbackTags.INPUT_IDEA_DESC },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !uiState.isSubmittingIdea && uiState.ideaTitle.isNotBlank() && uiState.ideaDescription.isNotBlank(),
                modifier = Modifier.semantics { testTag = SupportFeedbackTags.BTN_SUBMIT_IDEA },
            ) {
                Text(tr(StringKey.FEATURE_SUBMIT_IDEA_ACTION))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
    )
}

@Composable
private fun FeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    testTag: String,
) {
    val bgColor = if (isError) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f)
    val textColor = if (isError) Color(0xFFF87171) else Color(0xFF34D399)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics { this.testTag = testTag },
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (if (isError) "⚠️  " else "✓  ") + message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "✕",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 4.dp),
            )
        }
    }
}
