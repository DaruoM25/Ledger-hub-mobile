package com.ledgerhub.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerhub.presentation.theme.LedgerHubColors

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object LoginTags {
    const val SCREEN = "login_screen"
    const val EMAIL_FIELD = "login_email_field"
    const val PASSWORD_FIELD = "login_password_field"
    const val SUBMIT_BUTTON = "login_submit_button"
    const val REGISTER_LINK = "login_register_link"
    const val ERROR_MESSAGE = "login_error_message"
    const val LOADING_INDICATOR = "login_loading_indicator"
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = remember { LoginViewModel() },
    onNavigateToRegister: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    LoginContent(uiState = uiState, onIntent = viewModel::processIntent, onNavigateToRegister = onNavigateToRegister)
}

/**
 * Thème sombre "slate" propre à l'authentification (voir [LedgerHubColors]) — appliqué localement
 * via un [MaterialTheme] imbriqué, sans toucher au thème clair par défaut du reste de l'app.
 */
@Composable
internal fun LoginContent(
    uiState: LoginUiState,
    onIntent: (LoginIntent) -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
) {
    val colorScheme = darkColorScheme(
        primary = LedgerHubColors.Accent,
        background = LedgerHubColors.Background,
        surface = LedgerHubColors.Surface,
        onBackground = Color.White,
        onSurface = Color.White,
        error = Color(0xFFF87171),
    )

    MaterialTheme(colorScheme = colorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LedgerHubColors.Background)
                .statusBarsPadding()
                .semantics { testTag = LoginTags.SCREEN },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .background(LedgerHubColors.Surface, RoundedCornerShape(20.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(LedgerHubColors.Accent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📄", fontSize = 26.sp)
                }

                Text(
                    text = "Connexion à LedgerHub",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "Accédez à votre espace de facturation conforme 2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubColors.SecondaryText,
                    textAlign = TextAlign.Center,
                )

                AuthField(
                    label = "Adresse email",
                    value = uiState.email,
                    placeholder = "vous@cabinet.fr",
                    tag = LoginTags.EMAIL_FIELD,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.padding(top = 20.dp),
                    onValueChange = { onIntent(LoginIntent.EmailChanged(it)) },
                )
                AuthField(
                    label = "Mot de passe",
                    value = uiState.password,
                    placeholder = "••••••••",
                    tag = LoginTags.PASSWORD_FIELD,
                    enabled = !uiState.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = 12.dp),
                    onValueChange = { onIntent(LoginIntent.PasswordChanged(it)) },
                )

                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp).semantics {
                            testTag = LoginTags.ERROR_MESSAGE
                            contentDescription = uiState.errorMessage
                        },
                    )
                }

                Button(
                    onClick = { onIntent(LoginIntent.Submit) },
                    enabled = uiState.isSubmitEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = LedgerHubColors.Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .semantics { testTag = LoginTags.SUBMIT_BUTTON },
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).semantics { testTag = LoginTags.LOADING_INDICATOR },
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Se connecter")
                    }
                }

                Row(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Pas encore de compte ? ", color = LedgerHubColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "S'inscrire",
                        color = LedgerHubColors.Accent,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToRegister)
                            .semantics { testTag = LoginTags.REGISTER_LINK },
                    )
                }

                Text(
                    text = "Authentification fictive — aucun identifiant réel n'est stocké",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerHubColors.SecondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    placeholder: String,
    tag: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LedgerHubColors.SecondaryText)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = { Text(placeholder, color = LedgerHubColors.SecondaryText.copy(alpha = 0.6f)) },
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LedgerHubColors.InputBackground,
                unfocusedContainerColor = LedgerHubColors.InputBackground,
                disabledContainerColor = LedgerHubColors.InputBackground,
                focusedBorderColor = LedgerHubColors.Accent,
                unfocusedBorderColor = LedgerHubColors.InputBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = LedgerHubColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        )
    }
}
