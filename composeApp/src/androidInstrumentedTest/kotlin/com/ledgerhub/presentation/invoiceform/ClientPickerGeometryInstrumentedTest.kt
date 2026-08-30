package com.ledgerhub.presentation.invoiceform

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.presentation.components.ClientPickerTags
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3 — audit visuel du sélecteur client (US-11) sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`) : disposition géométrique + export d'une capture d'écran.
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ClientPickerGeometryInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val boulangerie = Party("Boulangerie Moreau SARL", "784102336", "78410233600004", "compta@moreau.fr")
    private val bouchon = Party("Bouchon Lyonnais SAS", "732829320", "73282932000074", "contact@bouchon.fr")

    private class FakeClientDirectory(initial: List<Party>) : ClientRepository {
        val clients = initial.toMutableList()

        override suspend fun fetchClients(): Result<List<Party>> =
            Result.success(clients.sortedBy { it.name.lowercase() })

        override suspend fun searchClients(query: String): Result<List<Party>> {
            val prefix = query.trim()
            val matches = if (prefix.isEmpty()) clients else clients.filter {
                it.name.startsWith(prefix, ignoreCase = true)
            }
            return Result.success(matches.sortedBy { it.name.lowercase() })
        }

        override suspend fun createClient(client: Party): Result<Unit> =
            if (clients.any { it.siret == client.siret }) {
                Result.failure(DuplicateClientException(client.siret))
            } else {
                clients += client
                Result.success(Unit)
            }

        override suspend fun updateClient(client: Party): Result<Unit> = Result.success(Unit)
        override suspend fun deleteClient(siret: String): Result<Unit> = Result.success(Unit)
        override suspend fun countInvoicesFor(siret: String): Result<Long> = Result.success(0L)
    }

    private fun renderForm() {
        val directory = FakeClientDirectory(listOf(boulangerie, bouchon))
        composeRule.setContent {
            LedgerHubTheme {
                InvoiceFormScreen(viewModel = InvoiceFormViewModel(clientRepository = directory))
            }
        }
        composeRule.waitForIdle()
    }

    /** Enregistre [bitmap] dans les fichiers de l'app **et** dans /sdcard/Download (adb pull). */
    private fun exportScreenshot(bitmap: Bitmap, name: String) {
        val appDir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("screenshots")!!
            .apply { mkdirs() }
        val appFile = File(appDir, name)
        appFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val sharedFile = File("/sdcard/Download", name).apply { parentFile?.mkdirs() }
        sharedFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        println("[screenshot] ${appFile.absolutePath}")
        println("[screenshot] ${sharedFile.absolutePath}")
        assertTrue(appFile.exists() && appFile.length() > 0L, "capture applicative écrite et non vide")
        assertTrue(sharedFile.exists() && sharedFile.length() > 0L, "capture partagée écrite et non vide")
    }

    @Test
    fun suggestionsSitBelowTheSearchField_withTouchSizedRows() {
        renderForm()

        composeRule.onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Bou")
        composeRule.waitForIdle()

        val field = composeRule.onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).getUnclippedBoundsInRoot()
        val list = composeRule.onNodeWithTag(ClientPickerTags.CLIENT_SUGGESTIONS_LIST)
            .performScrollTo().getUnclippedBoundsInRoot()
        val firstRow = composeRule.onNodeWithTag(ClientPickerTags.suggestionItem("73282932000074"))
            .getUnclippedBoundsInRoot()

        // 1. La liste s'ouvre sous le champ, jamais par-dessus.
        assertTrue(list.top >= field.bottom - 1.dp, "liste sous le champ (champ.bottom=${field.bottom}, liste.top=${list.top})")

        // 2. Chaque suggestion respecte la cible tactile M3.
        val rowHeight = firstRow.bottom - firstRow.top
        assertTrue(rowHeight >= 48.dp, "suggestion ≥ 48dp (mesuré $rowHeight)")

        // 3. La liste s'aligne sur la largeur du champ (à la bordure près).
        assertTrue(list.left >= field.left - 2.dp && list.right <= field.right + 2.dp, "liste alignée sur le champ")
    }

    @Test
    fun addNewClientButton_isFullWidth_andTouchSized() {
        renderForm()

        composeRule.onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT)
            .performScrollTo().performTextInput("Client Inconnu SAS")
        composeRule.waitForIdle()

        val field = composeRule.onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN)
            .performScrollTo().getUnclippedBoundsInRoot()

        assertTrue(button.top >= field.bottom - 1.dp, "bouton sous le champ de recherche")
        val buttonHeight = button.bottom - button.top
        assertTrue(buttonHeight >= 48.dp, "bouton ≥ 48dp — cible tactile M3 (mesuré $buttonHeight)")
        assertTrue(
            button.left <= field.left + 2.dp && button.right >= field.right - 2.dp,
            "bouton sur toute la largeur du champ",
        )
    }

    @Test
    fun exportsScreenshotOfTheSuggestionsForVisualAudit() {
        renderForm()

        composeRule.onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Bou")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ClientPickerTags.CLIENT_SUGGESTIONS_LIST).performScrollTo().assertIsDisplayed()

        val bitmap = composeRule.onNodeWithTag(InvoiceFormTags.SCREEN).captureToImage().asAndroidBitmap()

        exportScreenshot(bitmap, "US11_client_picker_suggestions_${Build.MODEL}.png".replace(' ', '_'))
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "le bitmap capturé doit avoir des dimensions")
    }

    @Test
    fun exportsScreenshotOfTheQuickClientDialogForVisualAudit() {
        renderForm()

        composeRule.onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT)
            .performScrollTo().performTextInput("Client Inconnu SAS")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ClientPickerTags.QUICK_CLIENT_NAME_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(ClientPickerTags.QUICK_CLIENT_SIRET_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(ClientPickerTags.QUICK_CLIENT_EMAIL_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(ClientPickerTags.QUICK_CLIENT_SAVE_BTN).assertIsDisplayed()

        val bitmap = composeRule.onNodeWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).captureToImage().asAndroidBitmap()

        exportScreenshot(bitmap, "US11_quick_client_dialog_${Build.MODEL}.png".replace(' ', '_'))
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "le bitmap capturé doit avoir des dimensions")
    }
}
