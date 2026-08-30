package com.ledgerhub.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.directory.LuhnChecksum
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.ValidationResult
import com.ledgerhub.presentation.theme.LedgerHubColors

/**
 * Tags de test du sélecteur client (US-11) — noms repris littéralement du contrat de test, en
 * dérogation assumée à la convention `snake_case` du reste de l'application.
 */
object ClientPickerTags {
    const val CLIENT_SEARCH_INPUT = "CLIENT_SEARCH_INPUT"
    const val CLIENT_SUGGESTIONS_LIST = "CLIENT_SUGGESTIONS_LIST"
    const val ADD_NEW_CLIENT_BTN = "ADD_NEW_CLIENT_BTN"
    const val QUICK_CLIENT_DIALOG = "QUICK_CLIENT_DIALOG"
    const val QUICK_CLIENT_NAME_INPUT = "QUICK_CLIENT_NAME_INPUT"
    const val QUICK_CLIENT_SIRET_INPUT = "QUICK_CLIENT_SIRET_INPUT"
    const val QUICK_CLIENT_EMAIL_INPUT = "QUICK_CLIENT_EMAIL_INPUT"
    const val QUICK_CLIENT_SAVE_BTN = "QUICK_CLIENT_SAVE_BTN"
    const val QUICK_CLIENT_CANCEL_BTN = "QUICK_CLIENT_CANCEL_BTN"

    /** Une suggestion, identifiée par le SIRET — identité métier de la fiche client. */
    fun suggestionItem(siret: String) = "CLIENT_SUGGESTION_ITEM_$siret"

    fun quickClientError(field: String) = "QUICK_CLIENT_ERROR_$field"
}

/** Champ de la modale de création rapide, pour rattacher une erreur. */
enum class QuickClientField {
    NAME,
    SIRET,
    EMAIL,
}

/**
 * Brouillon de la modale de création rapide, avec ses erreurs de validation.
 *
 * Vit dans l'état du formulaire hôte (facture, devis) : la modale n'a pas d'état local, elle est
 * pilotée par des intentions comme le reste de l'écran.
 */
data class QuickClientDraft(
    val name: String = "",
    val siret: String = "",
    val email: String = "",
    val errors: Map<QuickClientField, String> = emptyMap(),
    val isSaving: Boolean = false,
)

private val QUICK_CLIENT_EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

/**
 * Règles de validation d'une fiche créée à la volée — **source unique** partagée par les
 * formulaires de facture et de devis.
 *
 * Le SIRET passe par [LuhnChecksum.isValidSiret] (14 chiffres **et** clé de Luhn), et non par le
 * seul contrôle de longueur : une saisie rapide est précisément le moment où une coquille passe
 * inaperçue.
 *
 * @return les erreurs par champ ; vide si la fiche est enregistrable.
 */
fun validateQuickClient(name: String, siret: String, email: String): Map<QuickClientField, String> {
    val errors = mutableMapOf<QuickClientField, String>()
    if (FiscalValidation.validateCompanyName(name.trim()) is ValidationResult.Invalid) {
        errors[QuickClientField.NAME] = "La raison sociale est obligatoire"
    }
    if (!LuhnChecksum.isValidSiret(siret)) {
        errors[QuickClientField.SIRET] = "SIRET invalide : 14 chiffres et clé de Luhn correcte"
    }
    if (!QUICK_CLIENT_EMAIL_REGEX.matches(email.trim())) {
        errors[QuickClientField.EMAIL] = "Adresse email invalide"
    }
    return errors
}

/** Vert d'action positive du bouton d'ajout — Tailwind `emerald-600`. */
private val AddClientGreen = Color(0xFF059669)

/**
 * Sélecteur client : champ de recherche + suggestions filtrées + bouton d'ajout rapide.
 *
 * Composant **sans état** : tout vient de l'appelant, ce qui le rend réutilisable par les
 * formulaires de facture, de devis ou d'avoir sans les coupler entre eux.
 *
 * Les suggestions sont rendues **en ligne** sous le champ, et non dans un `DropdownMenu` : une
 * liste dans l'arbre principal reste mesurable et cliquable par les tests d'interface, là où un
 * popup vit dans une fenêtre séparée.
 *
 * @param showAddNewClientButton piloté par l'appelant : la règle « client inconnu » est une
 *   décision d'état, pas d'affichage.
 */
@Composable
fun ClientPicker(
    label: String,
    query: String,
    suggestions: List<Party>,
    isExpanded: Boolean,
    showAddNewClientButton: Boolean,
    enabled: Boolean,
    error: String?,
    errorTag: String,
    onQueryChanged: (String) -> Unit,
    onClientSelected: (Party) -> Unit,
    onAddNewClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            isError = error != null,
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = ledgerFieldColors(),
            modifier = Modifier.fillMaxWidth().semantics { testTag = ClientPickerTags.CLIENT_SEARCH_INPUT },
        )

        if (isExpanded && suggestions.isNotEmpty()) {
            Surface(
                color = LedgerHubColors.Surface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LedgerHubColors.InputBorder),
                modifier = Modifier.fillMaxWidth().semantics { testTag = ClientPickerTags.CLIENT_SUGGESTIONS_LIST },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    suggestions.forEach { client ->
                        SuggestionRow(client = client, enabled = enabled, onClick = { onClientSelected(client) })
                    }
                }
            }
        }

        if (showAddNewClientButton) {
            Button(
                onClick = onAddNewClient,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = AddClientGreen, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { testTag = ClientPickerTags.ADD_NEW_CLIENT_BTN },
            ) {
                Text("+  Ajouter comme nouveau client", fontWeight = FontWeight.SemiBold)
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics {
                    testTag = errorTag
                    contentDescription = error
                },
            )
        }
    }
}

@Composable
private fun SuggestionRow(client: Party, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { testTag = ClientPickerTags.suggestionItem(client.siret) },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(client.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = if (client.email.isBlank()) "SIRET ${client.siret}" else "SIRET ${client.siret} · ${client.email}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Modale de création rapide d'une fiche client — trois champs et deux actions.
 *
 * Le SIRET est contrôlé par la clé de Luhn côté ViewModel : la modale se contente d'afficher
 * l'erreur qu'on lui donne, et **reste ouverte** tant que la saisie est refusée.
 */
@Composable
fun QuickClientDialog(
    name: String,
    siret: String,
    email: String,
    errors: Map<String, String>,
    isSaving: Boolean,
    onFieldChanged: (name: String, siret: String, email: String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTag = ClientPickerTags.QUICK_CLIENT_DIALOG },
        title = { Text("Nouveau client") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogField(
                    label = "Raison sociale",
                    value = name,
                    tag = ClientPickerTags.QUICK_CLIENT_NAME_INPUT,
                    error = errors["NAME"],
                    errorTag = ClientPickerTags.quickClientError("NAME"),
                    enabled = !isSaving,
                    onValueChange = { onFieldChanged(it, siret, email) },
                )
                DialogField(
                    label = "SIRET (14 chiffres)",
                    value = siret,
                    tag = ClientPickerTags.QUICK_CLIENT_SIRET_INPUT,
                    error = errors["SIRET"],
                    errorTag = ClientPickerTags.quickClientError("SIRET"),
                    enabled = !isSaving,
                    onValueChange = { onFieldChanged(name, filterSiret(it), email) },
                    keyboardType = KeyboardType.Number,
                )
                DialogField(
                    label = "Email",
                    value = email,
                    tag = ClientPickerTags.QUICK_CLIENT_EMAIL_INPUT,
                    error = errors["EMAIL"],
                    errorTag = ClientPickerTags.quickClientError("EMAIL"),
                    enabled = !isSaving,
                    onValueChange = { onFieldChanged(name, siret, it) },
                    keyboardType = KeyboardType.Email,
                )
            }
        },
        confirmButton = {
            // Actif même sur formulaire incomplet : l'appui révèle les erreurs plutôt que de
            // laisser l'utilisateur devant un bouton grisé sans explication (cf. D-02).
            Button(
                onClick = onSave,
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AddClientGreen, contentColor = Color.White),
                modifier = Modifier.semantics { testTag = ClientPickerTags.QUICK_CLIENT_SAVE_BTN },
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.semantics { testTag = ClientPickerTags.QUICK_CLIENT_CANCEL_BTN },
            ) {
                Text("Annuler")
            }
        },
    )
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    tag: String,
    error: String?,
    errorTag: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            isError = error != null,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = ledgerFieldColors(),
            modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics {
                    testTag = errorTag
                    contentDescription = error
                },
            )
        }
    }
}

/** Habillage commun des champs — repris à l'identique du formulaire de facture. */
@Composable
private fun ledgerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LedgerHubColors.InputBackground,
    unfocusedContainerColor = LedgerHubColors.InputBackground,
    disabledContainerColor = LedgerHubColors.InputBackground,
    focusedBorderColor = LedgerHubColors.Accent,
    unfocusedBorderColor = LedgerHubColors.InputBorder,
    focusedTextColor = LedgerHubColors.PrimaryText,
    unfocusedTextColor = LedgerHubColors.PrimaryText,
    cursorColor = LedgerHubColors.Accent,
)
