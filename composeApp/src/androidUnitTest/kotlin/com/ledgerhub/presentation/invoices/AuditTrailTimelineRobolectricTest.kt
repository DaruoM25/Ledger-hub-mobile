package com.ledgerhub.presentation.invoices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.audit.AuditEntry
import com.ledgerhub.domain.audit.AuditMilestoneId
import com.ledgerhub.domain.audit.buildAuditTimeline
import com.ledgerhub.domain.audit.fingerprintSha256
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.invoices.components.AuditTrailTags
import com.ledgerhub.presentation.invoices.components.AuditTrailTimeline
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Niveau 3a (US-17) — rendu du panneau de traçabilité sous Robolectric (JVM, CI sans émulateur).
 *
 * La résolution des états est déjà verrouillée par `AuditTimelineTest` : ce niveau ne re-teste
 * pas la logique, il vérifie que les quatre jalons **arrivent bien à l'écran** avec leur libellé
 * et leur badge, dans les deux langues.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists` : un nœud présent dans
 * l'arbre mais invisible ne prouve rien à l'utilisateur. Les textes attendus sont résolus depuis
 * `AppTranslations`, jamais réécrits en dur — leur formulation est figée par la sentinelle N1.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class AuditTrailTimelineRobolectricTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    private fun invoice(status: InvoiceStatus) = Invoice(
        number = "FAC-2026-0217",
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    private fun entries(vararg pairs: Pair<InvoiceStatus?, InvoiceStatus>): List<AuditEntry> =
        pairs.mapIndexed { index, (from, to) ->
            AuditEntry(
                id = "audit-$index",
                invoiceNumber = "FAC-2026-0217",
                fromStatus = from,
                toStatus = to,
                reason = null,
                createdAt = "2026-08-3${index + 1}T09:00:00Z",
            )
        }

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    // ── Présence et structure ───────────────────────────────────────────────

    @Test
    fun thePanel_showsItsTitleAndTheFourMilestones() = runComposeUiTest {
        setContent {
            ScrollingHost {
                AuditTrailTimeline(milestones = buildAuditTimeline(invoice(InvoiceStatus.DRAFT)))
            }
        }

        onNodeWithTag(AuditTrailTags.PANEL).assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUDIT_PANEL_TITLE)).assertIsDisplayed()

        AuditMilestoneId.entries.forEach { id ->
            onNodeWithTag(AuditTrailTags.stepTag(id)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun eachMilestone_carriesItsRegulatoryWording() = runComposeUiTest {
        setContent {
            ScrollingHost {
                AuditTrailTimeline(milestones = buildAuditTimeline(invoice(InvoiceStatus.DRAFT)))
            }
        }

        onNodeWithText(tr(StringKey.AUDIT_STEP_CREATED)).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUDIT_STEP_SEALED)).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUDIT_STEP_PPF)).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUDIT_STEP_STATUS)).performScrollTo().assertIsDisplayed()
    }

    // ── Empreinte ───────────────────────────────────────────────────────────

    @Test
    fun theSealedMilestone_displaysTheSha256Fingerprint() = runComposeUiTest {
        val subject = invoice(InvoiceStatus.DRAFT)
        setContent {
            ScrollingHost { AuditTrailTimeline(milestones = buildAuditTimeline(subject)) }
        }

        // Arbre non fusionné : l'empreinte vit à l'intérieur du jalon « scellée », qui se présente
        // aux lecteurs d'écran comme un nœud unique — son tag n'existe donc que sous cette forme.
        onNodeWithTag(AuditTrailTags.SHA256, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        // Les 16 premiers caractères sont affichés — la valeur complète est illisible sur mobile.
        onNodeWithTag(AuditTrailTags.SHA256, useUnmergedTree = true)
            .assertTextContains(subject.fingerprintSha256().take(16), substring = true)
        onNodeWithTag(AuditTrailTags.SHA256, useUnmergedTree = true)
            .assertTextContains(tr(StringKey.AUDIT_SHA256_LABEL), substring = true)
    }

    // ── Badges selon le statut ──────────────────────────────────────────────

    @Test
    fun draftInvoice_showsPendingBadgesForSubmissionAndStatus() = runComposeUiTest {
        setContent {
            ScrollingHost {
                AuditTrailTimeline(milestones = buildAuditTimeline(invoice(InvoiceStatus.DRAFT)))
            }
        }

        onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.PPF)).performScrollTo()
            .assertTextContains(tr(StringKey.AUDIT_STEP_PENDING), substring = true)
        onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.STATUS)).performScrollTo()
            .assertTextContains(tr(StringKey.AUDIT_STEP_PENDING), substring = true)
    }

    @Test
    fun depositedInvoice_marksSubmissionDone_whileTheAdministrationIsStillPending() = runComposeUiTest {
        setContent {
            ScrollingHost {
                AuditTrailTimeline(
                    milestones = buildAuditTimeline(
                        invoice(InvoiceStatus.DEPOSITED),
                        entries(InvoiceStatus.DRAFT to InvoiceStatus.DEPOSITED),
                    ),
                )
            }
        }

        onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.PPF)).performScrollTo()
            .assertTextContains(tr(StringKey.AUDIT_STEP_DONE), substring = true)
        onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.STATUS)).performScrollTo()
            .assertTextContains(tr(StringKey.AUDIT_STEP_PENDING), substring = true)
    }

    @Test
    fun approvedInvoice_showsTheOfficialApprovedWording() = runComposeUiTest {
        setContent {
            ScrollingHost {
                AuditTrailTimeline(
                    milestones = buildAuditTimeline(
                        invoice(InvoiceStatus.APPROVED),
                        entries(
                            InvoiceStatus.DRAFT to InvoiceStatus.DEPOSITED,
                            InvoiceStatus.DEPOSITED to InvoiceStatus.APPROVED,
                        ),
                    ),
                )
            }
        }

        onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.STATUS)).performScrollTo()
            .assertTextContains(tr(StringKey.STATUS_APPROVED), substring = true)
    }

    @Test
    fun rejectedInvoice_showsTheOfficialRejectedWording() = runComposeUiTest {
        setContent {
            ScrollingHost {
                AuditTrailTimeline(
                    milestones = buildAuditTimeline(
                        invoice(InvoiceStatus.REJECTED),
                        entries(
                            InvoiceStatus.DRAFT to InvoiceStatus.DEPOSITED,
                            InvoiceStatus.DEPOSITED to InvoiceStatus.REJECTED,
                        ),
                    ),
                )
            }
        }

        onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.STATUS)).performScrollTo()
            .assertTextContains(tr(StringKey.STATUS_REJECTED), substring = true)
        // La transmission reste acquise malgré le rejet.
        onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.PPF)).performScrollTo()
            .assertTextContains(tr(StringKey.AUDIT_STEP_DONE), substring = true)
    }

    // ── Horodatages ─────────────────────────────────────────────────────────

    @Test
    fun aMilestoneWithARealTimestamp_displaysItsUtcTime() = runComposeUiTest {
        setContent {
            ScrollingHost {
                AuditTrailTimeline(
                    milestones = buildAuditTimeline(
                        invoice(InvoiceStatus.DEPOSITED),
                        entries(InvoiceStatus.DRAFT to InvoiceStatus.DEPOSITED),
                    ),
                )
            }
        }

        // L'heure UTC est celle qui fait foi dans une piste d'audit : elle est affichée telle quelle.
        onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.PPF)).performScrollTo()
            .assertTextContains("09:00:00 UTC", substring = true)
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun inEnglish_thePanelIsFullyTranslated() = runComposeUiTest {
        setContent {
            ScrollingHost {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    AuditTrailTimeline(milestones = buildAuditTimeline(invoice(InvoiceStatus.APPROVED)))
                }
            }
        }

        onNodeWithText(tr(StringKey.AUDIT_PANEL_TITLE, AppLanguage.EN)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUDIT_STEP_CREATED, AppLanguage.EN)).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUDIT_STEP_SEALED, AppLanguage.EN)).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUDIT_STEP_PPF, AppLanguage.EN)).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUDIT_STEP_STATUS, AppLanguage.EN)).performScrollTo().assertIsDisplayed()

        onNodeWithTag(AuditTrailTags.SHA256, useUnmergedTree = true).performScrollTo()
            .assertTextContains(tr(StringKey.AUDIT_SHA256_LABEL, AppLanguage.EN), substring = true)
    }
}

/**
 * Hôte de test reproduisant la seule condition d'affichage réelle du panneau : l'écran de détail
 * le pose dans une colonne défilante. Composé nu, il n'a aucun ancêtre scrollable et
 * [performScrollTo] échoue avant même d'évaluer l'assertion — l'échec porterait alors sur le
 * montage du test, pas sur l'interface. Le défilement reste indispensable ici : sur la hauteur
 * d'un téléphone, les derniers jalons tombent hors de l'écran.
 */
@Composable
private fun ScrollingHost(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
}
