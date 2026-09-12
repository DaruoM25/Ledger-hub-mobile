package com.ledgerhub.presentation.subscription

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.subscription.PremiumFeature
import com.ledgerhub.domain.subscription.SubscriptionTier
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

private val GoldAccent = Color(0xFFF59E0B)
private val GoldLight = Color(0xFFFEF3C7)
private val ProGradientDark = Color(0xFF1E1B4B)

@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel = remember { PaywallViewModel() },
    onDismiss: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isDismissed) {
        onDismiss()
        return
    }

    PaywallContent(
        uiState = uiState,
        onIntent = viewModel::processIntent,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun PaywallContent(
    uiState: PaywallUiState,
    onIntent: (PaywallIntent) -> Unit,
    onDismiss: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = PaywallTags.SCREEN },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header bar avec bouton Fermer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = GoldAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("👑", fontSize = 14.sp)
                        Text(
                            text = tr(StringKey.PRO_BADGE_LABEL),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onIntent(PaywallIntent.Dismiss)
                        onDismiss()
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                        .semantics { testTag = PaywallTags.CLOSE_BUTTON },
                ) {
                    Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Titre & Sous-titre
            Text(
                text = tr(StringKey.PAYWALL_TITLE),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { testTag = PaywallTags.TITLE },
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tr(StringKey.PAYWALL_SUBTITLE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { testTag = PaywallTags.SUBTITLE },
            )

            // Bannière de motif (si déclenché par une feature)
            if (uiState.reasonFeature != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(uiState.reasonFeature.glyph, fontSize = 20.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(uiState.reasonFeature.titleKey),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = tr(uiState.reasonFeature.descriptionKey),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sélecteur de forfaits (Mensuel / Annuel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.plans.forEach { plan ->
                    val isSelected = uiState.selectedTier == plan.tier
                    val tag = if (plan.tier == SubscriptionTier.PRO_MONTHLY) PaywallTags.PLAN_MONTHLY else PaywallTags.PLAN_ANNUAL

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onIntent(PaywallIntent.SelectTier(plan.tier)) }
                            .semantics { testTag = tag },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) GoldAccent else LedgerHubTheme.palette.Border,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (plan.badge != null) {
                                Surface(
                                    color = GoldAccent,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(bottom = 6.dp),
                                ) {
                                    Text(
                                        text = plan.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(18.dp))
                            }

                            Text(
                                text = plan.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = plan.priceFormatted,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) GoldAccent else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = plan.periodFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Liste des fonctionnalités incluses
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    uiState.features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { testTag = PaywallTags.featureTag(feature.name) },
                        ) {
                            Text(feature.glyph, fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr(feature.titleKey),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = tr(feature.descriptionKey),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bouton Principal CTA
            Button(
                onClick = { onIntent(PaywallIntent.PurchaseSelected) },
                enabled = !uiState.isPurchasing && !uiState.isRestoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .semantics { testTag = PaywallTags.CTA_BUTTON },
            ) {
                if (uiState.isPurchasing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "🚀  ${tr(StringKey.PAYWALL_CTA_PRO)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Code Promo / Jury Devpost
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "🎟️ ${tr(StringKey.PAYWALL_PROMO_SECTION_TITLE)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = uiState.promoCode,
                            onValueChange = { onIntent(PaywallIntent.PromoCodeChanged(it)) },
                            placeholder = { Text(tr(StringKey.PAYWALL_PROMO_PLACEHOLDER), style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            enabled = !uiState.isApplyingPromo,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onIntent(PaywallIntent.ApplyPromoCode) }),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = LedgerHubTheme.palette.Border,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { testTag = PaywallTags.PROMO_CODE_INPUT },
                        )
                        Button(
                            onClick = { onIntent(PaywallIntent.ApplyPromoCode) },
                            enabled = !uiState.isApplyingPromo && uiState.promoCode.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .semantics { testTag = PaywallTags.PROMO_CODE_SUBMIT },
                        ) {
                            if (uiState.isApplyingPromo) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(tr(StringKey.PAYWALL_PROMO_VALIDATE), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (uiState.promoSuccessMessage != null) {
                        Text(
                            text = "✓ ${uiState.promoSuccessMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { testTag = PaywallTags.PROMO_SUCCESS_MESSAGE },
                        )
                    }

                    if (uiState.promoErrorMessage != null) {
                        Text(
                            text = "⚠ ${uiState.promoErrorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { testTag = PaywallTags.PROMO_ERROR_MESSAGE },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bouton Restaurer
            TextButton(
                onClick = { onIntent(PaywallIntent.RestorePurchases) },
                enabled = !uiState.isRestoring && !uiState.isPurchasing,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { testTag = PaywallTags.RESTORE_BUTTON },
            ) {
                if (uiState.isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = tr(StringKey.PAYWALL_RESTORE_ACTION),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = tr(StringKey.PAYWALL_TERMS_NOTICE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { testTag = PaywallTags.ERROR_BANNER },
                )
            }
        }
    }
}
