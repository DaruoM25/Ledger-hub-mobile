package com.ledgerhub.presentation.auth.forgotpassword

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.auth.AuthTags
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

/**
 * Écran autonome de réinitialisation de mot de passe (US-26).
 *
 * MVI pur, déconnecté de tout framework Android natif, avec gestion d'état anti-énumération.
 */
@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onBackToLogin: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                ForgotPasswordSideEffect.NavigateBackToLogin -> onBackToLogin()
            }
        }
    }

    ForgotPasswordContent(
        uiState = uiState,
        onIntent = viewModel::processIntent,
        onBackToLogin = { viewModel.onBackToLogin() },
    )
}

@Composable
internal fun ForgotPasswordContent(
    uiState: ForgotPasswordUiState,
    onIntent: (ForgotPasswordIntent) -> Unit = {},
    onBackToLogin: () -> Unit = {},
) {
    val colorScheme = darkColorScheme(
        primary = LedgerHubTheme.palette.Accent,
        background = LedgerHubTheme.palette.Background,
        surface = LedgerHubTheme.palette.Surface,
        onBackground = Color.White,
        onSurface = Color.White,
        error = Color(0xFFF87171),
    )

    MaterialTheme(colorScheme = colorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LedgerHubTheme.palette.Background)
                .statusBarsPadding()
                .semantics { testTag = AuthTags.FORGOT_PASSWORD_SCREEN },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .background(LedgerHubTheme.palette.Surface, RoundedCornerShape(20.dp))
                .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(LedgerHubTheme.palette.Accent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🔑", fontSize = 26.sp)
                }

                Text(
                    text = tr(StringKey.AUTH_FORGOT_PASSWORD_TITLE),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = tr(StringKey.AUTH_FORGOT_PASSWORD_SUBTITLE),
                    color = LedgerHubTheme.palette.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                if (uiState.isSubmitted) {
                    // Carte de confirmation anti-fuite d'information
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { testTag = AuthTags.FORGOT_PASSWORD_SUCCESS_MESSAGE },
                    ) {
                        Text(
                            text = tr(StringKey.AUTH_FORGOT_PASSWORD_SUCCESS),
                            color = Color(0xFF34D399),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { onIntent(ForgotPasswordIntent.EmailChanged(it)) },
                        label = { Text(tr(StringKey.AUTH_EMAIL_LABEL)) },
                        placeholder = { Text(tr(StringKey.AUTH_EMAIL_PLACEHOLDER)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LedgerHubTheme.palette.Accent,
                            unfocusedBorderColor = LedgerHubTheme.palette.Border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { testTag = AuthTags.FORGOT_PASSWORD_EMAIL_FIELD },
                    )

                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Button(
                        onClick = { onIntent(ForgotPasswordIntent.Submit) },
                        enabled = uiState.canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = LedgerHubTheme.palette.Accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { testTag = AuthTags.FORGOT_PASSWORD_SUBMIT_BUTTON },
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(tr(StringKey.AUTH_FORGOT_PASSWORD_SUBMIT))
                        }
                    }
                }

                // Lien retour avec zone tactile minimale 48dp
                TextButton(
                    onClick = onBackToLogin,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics { testTag = AuthTags.FORGOT_PASSWORD_BACK_BUTTON },
                ) {
                    Text(
                        text = "←  ${tr(StringKey.AUTH_FORGOT_PASSWORD_BACK_TO_LOGIN)}",
                        color = LedgerHubTheme.palette.Accent,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
