package com.ledgerhub.presentation.hello

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ── Test Tags ─────────────────────────────────────────────────────────────────
// Partagés entre le code UI (commonMain) et les tests (commonTest).
// Utilisation de constantes string pour éviter les fautes de frappe dans les tests.
object HelloTags {
    const val SCREEN        = "hello_screen"
    const val LOADER        = "hello_loader"
    const val MESSAGE       = "hello_message"
    const val ERROR_TEXT    = "hello_error_text"
    const val RETRY_BUTTON  = "hello_retry_button"
}

// ── Composable avec ViewModel (avec état) ─────────────────────────────────────

@Composable
fun HelloScreen(
    viewModel: HelloViewModel = remember { HelloViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    HelloScreenContent(
        uiState  = uiState,
        onIntent = viewModel::processIntent
    )
}

// ── Composable pur / stateless (testable sans ViewModel) ─────────────────────

/**
 * Séparé de [HelloScreen] intentionnellement.
 * Avantage QA (Skill 2) : les tests UI reçoivent un [HelloUiState] statique
 * et n'ont pas besoin de ViewModel, de coroutines, ni de mocks.
 */
@Composable
internal fun HelloScreenContent(
    uiState  : HelloUiState,
    onIntent : (HelloIntent) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = HelloTags.SCREEN },
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.fillMaxSize().padding(24.dp)
        ) {
            AnimatedContent(
                targetState  = uiState,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 2 }).togetherWith(fadeOut())
                },
                label = "HelloStateTransition"
            ) { state ->
                when {
                    state.isLoading      -> LoadingContent()
                    state.error != null  -> ErrorContent(
                        error   = state.error,
                        onRetry = { onIntent(HelloIntent.RetryLoad) }
                    )
                    else                 -> SuccessContent(message = state.message)
                }
            }
        }
    }
}

// ── Sous-composants privés ────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    CircularProgressIndicator(
        modifier = Modifier.semantics { testTag = HelloTags.LOADER },
        color    = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SuccessContent(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text      = message,
            modifier  = Modifier.semantics { testTag = HelloTags.MESSAGE },
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text  = "RevenueCat Shipaton",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text     = error,
            modifier = Modifier.semantics { testTag = HelloTags.ERROR_TEXT },
            color    = MaterialTheme.colorScheme.error,
            style    = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Button(
            onClick  = onRetry,
            modifier = Modifier.semantics { testTag = HelloTags.RETRY_BUTTON }
        ) {
            Text("Réessayer")
        }
    }
}
