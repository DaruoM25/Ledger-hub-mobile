package com.ledgerhub.presentation.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.presentation.components.filterSiret
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests. */
object ClientsTags {
    const val SCREEN = "clients_screen"
    const val ADD_BUTTON = "clients_add_button"
    const val SEARCH_INPUT = "clients_search_input"
    const val CLEAR_SEARCH_BUTTON = "clients_clear_search_button"
    const val SEARCH_EMPTY_STATE = "clients_search_empty_state"
    const val EMPTY_STATE = "clients_empty_state"
    const val FEEDBACK = "clients_feedback"
    const val ERROR = "clients_error"

    const val FORM_DIALOG = "client_form_dialog"
    const val FORM_NAME = "client_form_name"
    const val FORM_SIRET = "client_form_siret"
    const val FORM_EMAIL = "client_form_email"
    const val FORM_SAVE = "client_form_save"
    const val FORM_CANCEL = "client_form_cancel"
    const val SIRENE_LOADER = "client_form_sirene_loader"

    const val DELETE_DIALOG = "client_delete_dialog"
    const val DELETE_CONFIRM = "client_delete_confirm"

    fun card(siret: String) = "client_card_$siret"
    fun editButton(siret: String) = "client_edit_$siret"
    fun deleteButton(siret: String) = "client_delete_$siret"
    fun errorTag(field: ClientFormField) = "client_form_error_${field.name}"
}

/** Crayon d'édition et corbeille de suppression — repères couleur demandés par l'US-04. */
private val EditActionColor = Color(0xFFF59E0B)
private val DeleteActionColor = Color(0xFFEF4444)

@Composable
fun ClientsScreen(viewModel: ClientsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    ClientsView(uiState = uiState, onIntent = viewModel::processIntent)
}

@Composable
internal fun ClientsView(
    uiState: ClientsUiState,
    onIntent: (ClientsIntent) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = ClientsTags.SCREEN }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)) {
                Text(
                    tr(StringKey.CLIENTS_TITLE),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(StringKey.CLIENTS_SUBTITLE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Champ de recherche compact sous le titre
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { onIntent(ClientsIntent.SearchQueryChanged(it)) },
            placeholder = {
                Text(
                    tr(StringKey.CLIENTS_SEARCH_PLACEHOLDER),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            leadingIcon = {
                Text("🔍", style = MaterialTheme.typography.bodyMedium)
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onIntent(ClientsIntent.ClearSearch) },
                        modifier = Modifier.semantics { testTag = ClientsTags.CLEAR_SEARCH_BUTTON },
                    ) {
                        Text("✕", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LedgerHubTheme.palette.InputBackground,
                unfocusedContainerColor = LedgerHubTheme.palette.InputBackground,
                disabledContainerColor = LedgerHubTheme.palette.InputBackground,
                focusedBorderColor = LedgerHubTheme.palette.Accent,
                unfocusedBorderColor = LedgerHubTheme.palette.InputBorder,
                focusedTextColor = LedgerHubTheme.palette.PrimaryText,
                unfocusedTextColor = LedgerHubTheme.palette.PrimaryText,
                cursorColor = LedgerHubTheme.palette.Accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = ClientsTags.SEARCH_INPUT },
        )

        Button(
            onClick = { onIntent(ClientsIntent.AddClicked) },
            modifier = Modifier.fillMaxWidth().semantics { testTag = ClientsTags.ADD_BUTTON },
        ) {
            Text("＋  ${tr(StringKey.CLIENTS_ADD)}")
        }

        uiState.feedbackMessage?.let { message ->
            Banner(
                text = message,
                tag = ClientsTags.FEEDBACK,
                containerColor = LedgerHubTheme.palette.StatusPaidBg,
                contentColor = LedgerHubTheme.palette.StatusPaidFg,
            )
        }
        uiState.errorMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3500L)
                onIntent(ClientsIntent.DismissMessage)
            }
            Banner(
                text = message,
                tag = ClientsTags.ERROR,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        when {
            uiState.isLoading -> Text(tr(StringKey.CLIENTS_LOADING))
            uiState.isEmpty -> Text(
                tr(StringKey.CLIENTS_EMPTY),
                modifier = Modifier.semantics { testTag = ClientsTags.EMPTY_STATE },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.isSearchEmpty -> Text(
                tr(StringKey.CLIENTS_SEARCH_EMPTY),
                modifier = Modifier.semantics { testTag = ClientsTags.SEARCH_EMPTY_STATE },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> uiState.filteredClients.forEach { client ->
                ClientCard(
                    client = client,
                    onEdit = { onIntent(ClientsIntent.EditClicked(client)) },
                    onDelete = { onIntent(ClientsIntent.DeleteClicked(client)) },
                )
            }
        }
    }

    uiState.form?.let { form ->
        ClientFormDialog(
            form = form,
            onIntent = onIntent,
        )
    }

    uiState.pendingDeletion?.let { target ->
        DeleteConfirmationDialog(
            client = target,
            onConfirm = { onIntent(ClientsIntent.DeleteConfirmed) },
            onDismiss = { onIntent(ClientsIntent.DeleteDismissed) },
        )
    }
}

@Composable
private fun ClientCard(client: Party, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = ClientsTags.card(client.siret) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(client.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "SIRET ${client.siret}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (client.email.isNotBlank()) {
                    Text(
                        client.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val editLabel = tr(StringKey.CLIENT_ACTION_EDIT)
            val deleteLabel = tr(StringKey.CLIENT_ACTION_DELETE)
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics {
                    testTag = ClientsTags.editButton(client.siret)
                    contentDescription = editLabel
                },
            ) {
                Text("✏️", color = EditActionColor)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics {
                    testTag = ClientsTags.deleteButton(client.siret)
                    contentDescription = deleteLabel
                },
            ) {
                Text("🗑", color = DeleteActionColor)
            }
        }
    }
}

@Composable
private fun ClientFormDialog(form: ClientFormState, onIntent: (ClientsIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(ClientsIntent.FormDismissed) },
        modifier = Modifier.semantics { testTag = ClientsTags.FORM_DIALOG },
        title = {
            Text(
                tr(
                    if (form.isEditing) StringKey.CLIENT_FORM_EDIT_TITLE else StringKey.CLIENT_FORM_NEW_TITLE,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogField(
                    label = tr(StringKey.CLIENT_FIELD_SIRET),
                    value = form.siret,
                    tag = ClientsTags.FORM_SIRET,
                    error = form.visibleErrors[ClientFormField.SIRET],
                    errorTag = ClientsTags.errorTag(ClientFormField.SIRET),
                    // Le SIRET est la clé d'identité : verrouillé dès qu'on édite une fiche.
                    enabled = !form.isSaving && !form.isEditing,
                    onValueChange = { onIntent(ClientsIntent.SiretChanged(it)) },
                    keyboardType = KeyboardType.Number,
                    inputFilter = ::filterSiret,
                    helper = if (form.isEditing) tr(StringKey.CLIENT_SIRET_LOCKED_HINT) else null,
                    trailingIcon = if (form.isSireneResolving) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .semantics { testTag = ClientsTags.SIRENE_LOADER },
                                strokeWidth = 2.dp,
                                color = LedgerHubTheme.palette.Accent,
                            )
                        }
                    } else null,
                )
                DialogField(
                    label = tr(StringKey.CLIENT_FIELD_NAME),
                    value = form.name,
                    tag = ClientsTags.FORM_NAME,
                    error = form.visibleErrors[ClientFormField.NAME],
                    errorTag = ClientsTags.errorTag(ClientFormField.NAME),
                    enabled = !form.isSaving,
                    onValueChange = { onIntent(ClientsIntent.NameChanged(it)) },
                )
                DialogField(
                    label = tr(StringKey.CLIENT_FIELD_EMAIL),
                    value = form.email,
                    tag = ClientsTags.FORM_EMAIL,
                    error = form.visibleErrors[ClientFormField.EMAIL],
                    errorTag = ClientsTags.errorTag(ClientFormField.EMAIL),
                    enabled = !form.isSaving,
                    onValueChange = { onIntent(ClientsIntent.EmailChanged(it)) },
                    keyboardType = KeyboardType.Email,
                )
            }
        },
        confirmButton = {
            // Actif même sur formulaire incomplet : l'appui révèle les erreurs plutôt que de
            // laisser l'utilisateur devant un bouton grisé sans explication (cf. D-02).
            Button(
                onClick = { onIntent(ClientsIntent.FormSubmitted) },
                enabled = !form.isSaving,
                modifier = Modifier.semantics { testTag = ClientsTags.FORM_SAVE },
            ) {
                Text(tr(StringKey.ACTION_SAVE))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onIntent(ClientsIntent.FormDismissed) },
                enabled = !form.isSaving,
                modifier = Modifier.semantics { testTag = ClientsTags.FORM_CANCEL },
            ) {
                Text(tr(StringKey.ACTION_CANCEL))
            }
        },
    )
}

@Composable
private fun DeleteConfirmationDialog(client: Party, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTag = ClientsTags.DELETE_DIALOG },
        title = { Text(tr(StringKey.CLIENT_DELETE_TITLE)) },
        text = { Text("${client.name}\n\n${tr(StringKey.CLIENT_DELETE_CONFIRM)}") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.semantics { testTag = ClientsTags.DELETE_CONFIRM },
            ) {
                Text(tr(StringKey.CLIENT_ACTION_DELETE))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(StringKey.ACTION_CANCEL)) }
        },
    )
}

@Composable
internal fun DialogField(
    label: String,
    value: String,
    tag: String,
    error: String?,
    errorTag: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    inputFilter: (String) -> String = { it },
    helper: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(inputFilter(it)) },
            isError = error != null,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LedgerHubTheme.palette.InputBackground,
                unfocusedContainerColor = LedgerHubTheme.palette.InputBackground,
                disabledContainerColor = LedgerHubTheme.palette.InputBackground,
                focusedBorderColor = LedgerHubTheme.palette.Accent,
                unfocusedBorderColor = LedgerHubTheme.palette.InputBorder,
                focusedTextColor = LedgerHubTheme.palette.PrimaryText,
                unfocusedTextColor = LedgerHubTheme.palette.PrimaryText,
                cursorColor = LedgerHubTheme.palette.Accent,
            ),
            modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        )
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics {
                    testTag = errorTag
                    contentDescription = error
                },
            )
        } else if (helper != null) {
            Text(
                helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun Banner(text: String, tag: String, containerColor: Color, contentColor: Color) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            testTag = tag
            contentDescription = text
        },
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
