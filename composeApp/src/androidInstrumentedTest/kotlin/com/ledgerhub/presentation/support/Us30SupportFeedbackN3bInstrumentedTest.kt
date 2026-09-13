package com.ledgerhub.presentation.support

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.support.CreateSupportTicketUseCase
import com.ledgerhub.domain.support.FeatureCategory
import com.ledgerhub.domain.support.FeatureFeedbackRepository
import com.ledgerhub.domain.support.FeatureRequest
import com.ledgerhub.domain.support.FeatureStatus
import com.ledgerhub.domain.support.GetFeatureRequestsUseCase
import com.ledgerhub.domain.support.SubmitFeatureRequestUseCase
import com.ledgerhub.domain.support.SupportCategory
import com.ledgerhub.domain.support.SupportRepository
import com.ledgerhub.domain.support.SupportTicket
import com.ledgerhub.domain.support.TicketStatus
import com.ledgerhub.domain.support.VoteFeatureRequestUseCase
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau N3b US-30 : Qualification sur terminal physique (Samsung S23+) du module Support & Feedback Loop.
 * Capture de preuve visuelle enregistrée dans Pictures/us-30/01_n3b_support_feedback.png.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class Us30SupportFeedbackN3bInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeSupportRepository : SupportRepository {
        val tickets = mutableListOf(
            SupportTicket(
                id = "t-101",
                userId = "demo@ledgerhub.app",
                userEmail = "demo@ledgerhub.app",
                category = SupportCategory.MANDATORY_MENTIONS_2026,
                subject = "Mention d'option pour la TVA sur les débits",
                description = "Comment formaliser la mention légale obligatoire sur Factur-X ?",
                status = TicketStatus.RESOLVED,
                createdAt = "2026-09-13T09:30:00Z",
            ),
        )
        override suspend fun getAllTickets(): Result<List<SupportTicket>> = Result.success(tickets)
        override suspend fun getTicketsByUser(userId: String): Result<List<SupportTicket>> = Result.success(tickets)
        override suspend fun getTicketById(id: String): Result<SupportTicket?> = Result.success(tickets.firstOrNull { it.id == id })
        override suspend fun createTicket(ticket: SupportTicket): Result<Unit> {
            tickets.add(ticket)
            return Result.success(Unit)
        }
        override suspend fun updateTicketStatus(id: String, status: TicketStatus, updatedAt: String): Result<Unit> = Result.success(Unit)
        override suspend fun countOpenTicketsByUser(userId: String): Result<Long> = Result.success(tickets.count { it.status == TicketStatus.OPEN }.toLong())
    }

    private class FakeFeatureFeedbackRepository : FeatureFeedbackRepository {
        val requests = mutableListOf(
            FeatureRequest(
                id = "feat-101",
                title = "Export Chorus Pro Direct API",
                description = "Télétransmission directe sans dépôt manuel",
                category = FeatureCategory.INTEGRATIONS,
                authorId = "expert@comptable.fr",
                authorEmail = "expert@comptable.fr",
                voteCount = 42L,
                status = FeatureStatus.PLANNED,
                createdAt = "2026-09-01T00:00:00Z",
            ),
            FeatureRequest(
                id = "feat-102",
                title = "Mode sombre OLED haute fidélité",
                description = "Optimisation du contraste et de la batterie sur écran AMOLED",
                category = FeatureCategory.UI_UX,
                authorId = "designer@ledgerhub.app",
                authorEmail = "designer@ledgerhub.app",
                voteCount = 18L,
                status = FeatureStatus.PROPOSED,
                createdAt = "2026-09-05T00:00:00Z",
            ),
        )
        val votes = mutableSetOf<Pair<String, String>>()

        override suspend fun getFeatureRequests(currentUserId: String): Result<List<FeatureRequest>> =
            Result.success(requests.map { it.copy(hasVoted = votes.contains(currentUserId to it.id)) }.sortedByDescending { it.voteCount })

        override suspend fun getFeatureRequestById(id: String, currentUserId: String): Result<FeatureRequest?> =
            Result.success(requests.firstOrNull { it.id == id }?.copy(hasVoted = votes.contains(currentUserId to id)))

        override suspend fun submitFeatureRequest(featureRequest: FeatureRequest): Result<Unit> {
            requests.add(featureRequest)
            return Result.success(Unit)
        }

        override suspend fun hasUserVoted(userId: String, featureRequestId: String): Result<Boolean> =
            Result.success(votes.contains(userId to featureRequestId))

        override suspend fun voteFeatureRequest(userId: String, featureRequestId: String, votedAt: String): Result<Unit> {
            votes.add(userId to featureRequestId)
            val idx = requests.indexOfFirst { it.id == featureRequestId }
            if (idx >= 0) {
                val current = requests[idx]
                requests[idx] = current.copy(voteCount = current.voteCount + 1)
            }
            return Result.success(Unit)
        }

        override suspend fun cancelVote(userId: String, featureRequestId: String): Result<Unit> {
            votes.remove(userId to featureRequestId)
            return Result.success(Unit)
        }
    }

    @Test
    fun supportFeedback_isValidatedAndCapturedOnDevice() {
        val supportRepo = FakeSupportRepository()
        val featureRepo = FakeFeatureFeedbackRepository()
        val viewModel = SupportFeedbackViewModel(
            supportRepository = supportRepo,
            createSupportTicketUseCase = CreateSupportTicketUseCase(supportRepo),
            getFeatureRequestsUseCase = GetFeatureRequestsUseCase(featureRepo),
            submitFeatureRequestUseCase = SubmitFeatureRequestUseCase(featureRepo),
            voteFeatureRequestUseCase = VoteFeatureRequestUseCase(featureRepo),
            currentUserId = "demo@ledgerhub.app",
            currentUserEmail = "demo@ledgerhub.app",
        )

        composeRule.setContent {
            LedgerHubTheme {
                SupportFeedbackScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        // 1. Vérification de l'écran et des onglets
        composeRule.onNodeWithTag(SupportFeedbackTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SupportFeedbackTags.TAB_ROW).assertIsDisplayed()

        // 2. Bascule vers l'onglet Boîte à idées
        composeRule.onNodeWithTag(SupportFeedbackTags.TAB_IDEAS).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SupportFeedbackTags.IDEAS_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(SupportFeedbackTags.IDEA_ITEM_PREFIX + "feat-101").assertIsDisplayed()

        // 3. Vote interactif sur l'idée Chorus Pro (42 -> 43)
        composeRule.onNodeWithTag(SupportFeedbackTags.BTN_VOTE_PREFIX + "feat-101").performClick()
        composeRule.waitForIdle()

        assertEquals(43L, viewModel.uiState.value.featureRequests.first { it.id == "feat-101" }.voteCount)

        // 4. Capture d'écran haute fidélité pour qualification N3b
        val bitmap = composeRule.onNodeWithTag(SupportFeedbackTags.SCREEN)
            .captureToImage()
            .asAndroidBitmap()

        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null)!!,
            "01_n3b_support_feedback.png",
        )
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-30 absente: ${output.absolutePath}")
        assertTrue(output.length() > 0L, "Capture US-30 vide: ${output.absolutePath}")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "Dimensions de capture invalides")

        val mediaValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, output.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/us-30",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val mediaUri = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaValues)
            ?: error("Publication MediaStore US-30 impossible")
        try {
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .openOutputStream(mediaUri)
                ?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                ?: error("Ouverture MediaStore US-30 impossible")
            mediaValues.clear()
            mediaValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .update(mediaUri, mediaValues, null, null)
        } catch (error: Throwable) {
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .delete(mediaUri, null, null)
            throw error
        }
    }
}
