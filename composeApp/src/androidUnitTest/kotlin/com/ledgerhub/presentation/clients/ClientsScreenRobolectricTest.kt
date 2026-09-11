package com.ledgerhub.presentation.clients

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.Party
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Interface de l'écran Clients. Rendue à partir de [ClientsView], le composable sans état, pour
 * exercer le rendu réel sans dépendre d'un dépôt ni d'un ordonnanceur.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class ClientsScreenRobolectricTest {

    private val client = Party(
        name = "Boulangerie Moreau SARL",
        siren = "784102336",
        siret = "78410233600021",
        email = "compta@moreau.fr",
    )

    @Test
    fun emptyList_showsTheEmptyStateAndTheAddAction() = runComposeUiTest {
        setContent { ClientsView(uiState = ClientsUiState(isLoading = false)) }

        onNodeWithTag(ClientsTags.EMPTY_STATE).performScrollTo().assertIsDisplayed()
        onNodeWithTag(ClientsTags.ADD_BUTTON).performScrollTo().assertIsEnabled()
    }

    @Test
    fun eachClientCard_exposesItsEditAndDeleteActions() = runComposeUiTest {
        setContent { ClientsView(uiState = ClientsUiState(isLoading = false, clients = listOf(client))) }

        onNodeWithTag(ClientsTags.card(client.siret)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(ClientsTags.editButton(client.siret)).assertIsEnabled()
        onNodeWithTag(ClientsTags.deleteButton(client.siret)).assertIsEnabled()
    }

    @Test
    fun freshDialog_showsNoErrorBeforeAnyInput() = runComposeUiTest {
        setContent { ClientsView(uiState = ClientsUiState(isLoading = false, form = ClientFormState())) }

        onNodeWithTag(ClientsTags.FORM_DIALOG).assertIsDisplayed()
        onNodeWithTag(ClientsTags.errorTag(ClientFormField.NAME), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ClientsTags.errorTag(ClientFormField.SIRET), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ClientsTags.errorTag(ClientFormField.EMAIL), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun siretField_rejectsLetters_andStopsAt14Digits() = runComposeUiTest {
        // L'état est piloté par le test : on observe ce que le filtre laisse remonter.
        var siret = ""
        setContent {
            ClientsView(
                uiState = ClientsUiState(isLoading = false, form = ClientFormState(siret = siret)),
                onIntent = { intent -> if (intent is ClientsIntent.SiretChanged) siret = intent.value },
            )
        }

        onNodeWithTag(ClientsTags.FORM_SIRET).performTextInput("ab12cd34")
        // Seuls les chiffres franchissent le filtre.
        kotlin.test.assertEquals("1234", siret)

        siret = ""
        onNodeWithTag(ClientsTags.FORM_SIRET).performTextInput("123456789012345678")
        kotlin.test.assertEquals("12345678901234", siret)
    }

    @Test
    fun editingAClient_locksTheSiretField() = runComposeUiTest {
        setContent {
            ClientsView(
                uiState = ClientsUiState(
                    isLoading = false,
                    form = ClientFormState(
                        editedSiret = client.siret,
                        name = client.name,
                        siret = client.siret,
                        email = client.email,
                    ),
                ),
            )
        }

        // Le SIRET identifie la fiche : en édition il reste visible mais non modifiable.
        onNodeWithTag(ClientsTags.FORM_SIRET).assertIsNotEnabled()
        onNodeWithTag(ClientsTags.FORM_NAME).assertIsEnabled()
    }

    @Test
    fun saveButton_staysEnabledOnAnIncompleteForm() = runComposeUiTest {
        var submitted = false
        setContent {
            ClientsView(
                uiState = ClientsUiState(isLoading = false, form = ClientFormState()),
                onIntent = { if (it is ClientsIntent.FormSubmitted) submitted = true },
            )
        }

        // Comme le formulaire de facture (D-02) : c'est l'appui qui révèle les erreurs.
        onNodeWithTag(ClientsTags.FORM_SAVE).assertIsEnabled().performClick()
        kotlin.test.assertTrue(submitted)
    }

    @Test
    fun revealedErrors_areDisplayedAfterASaveAttempt() = runComposeUiTest {
        setContent {
            ClientsView(
                uiState = ClientsUiState(
                    isLoading = false,
                    form = ClientFormState(
                        saveAttempted = true,
                        errors = mapOf(ClientFormField.SIRET to "Le SIRET doit comporter exactement 14 chiffres"),
                    ),
                ),
            )
        }

        onNodeWithTag(ClientsTags.errorTag(ClientFormField.SIRET), useUnmergedTree = true)
            .assertTextEquals("Le SIRET doit comporter exactement 14 chiffres")
    }

    @Test
    fun deletionDialog_asksForConfirmation() = runComposeUiTest {
        var confirmed = false
        setContent {
            ClientsView(
                uiState = ClientsUiState(isLoading = false, clients = listOf(client), pendingDeletion = client),
                onIntent = { if (it is ClientsIntent.DeleteConfirmed) confirmed = true },
            )
        }

        onNodeWithTag(ClientsTags.DELETE_DIALOG).assertIsDisplayed()
        onNodeWithTag(ClientsTags.DELETE_CONFIRM).performClick()
        kotlin.test.assertTrue(confirmed)
    }

    @Test
    fun refusedDeletion_isReportedAsAnError() = runComposeUiTest {
        setContent {
            ClientsView(
                uiState = ClientsUiState(
                    isLoading = false,
                    clients = listOf(client),
                    errorMessage = "Suppression impossible : ce client figure sur 7 facture(s) émise(s)",
                ),
            )
        }

        onNodeWithTag(ClientsTags.ERROR).performScrollTo().assertIsDisplayed()
    }

    // ── Niveau 3 : UI / Ergonomie (Recherche, Loader SIRENE, État vide de recherche) ──

    @Test
    fun searchField_isDisplayed_andAllowsTypingAndClearing() = runComposeUiTest {
        val queryState = androidx.compose.runtime.mutableStateOf("")
        var cleared = false
        setContent {
            ClientsView(
                uiState = ClientsUiState(isLoading = false, searchQuery = queryState.value, clients = listOf(client)),
                onIntent = { intent ->
                    when (intent) {
                        is ClientsIntent.SearchQueryChanged -> queryState.value = intent.query
                        ClientsIntent.ClearSearch -> {
                            queryState.value = ""
                            cleared = true
                        }
                        else -> Unit
                    }
                },
            )
        }

        onNodeWithTag(ClientsTags.SEARCH_INPUT).assertIsDisplayed()
        onNodeWithTag(ClientsTags.SEARCH_INPUT).performTextInput("Boulangerie")
        kotlin.test.assertEquals("Boulangerie", queryState.value)

        // Quand searchQuery n'est pas vide, le bouton de purge est affiché
        onNodeWithTag(ClientsTags.CLEAR_SEARCH_BUTTON).assertIsDisplayed().performClick()
        kotlin.test.assertTrue(cleared)
        kotlin.test.assertEquals("", queryState.value)
    }

    @Test
    fun searchEmptyState_isDisplayed_whenNoMatch() = runComposeUiTest {
        setContent {
            ClientsView(
                uiState = ClientsUiState(
                    isLoading = false,
                    searchQuery = "Inconnu",
                    clients = listOf(client),
                ),
            )
        }

        onNodeWithTag(ClientsTags.SEARCH_EMPTY_STATE).assertIsDisplayed()
        onNodeWithTag(ClientsTags.card(client.siret)).assertDoesNotExist()
    }

    @Test
    fun sireneLoader_isDisplayed_whenResolvingSiret() = runComposeUiTest {
        setContent {
            ClientsView(
                uiState = ClientsUiState(
                    isLoading = false,
                    form = ClientFormState(
                        isSireneResolving = true,
                        siret = "90123456700013",
                    ),
                ),
            )
        }

        onNodeWithTag(ClientsTags.SIRENE_LOADER).assertIsDisplayed()
    }
}
