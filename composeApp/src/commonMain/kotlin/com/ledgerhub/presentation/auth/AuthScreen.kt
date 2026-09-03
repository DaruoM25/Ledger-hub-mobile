package com.ledgerhub.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.components.filterSiret
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

/**
 * Tags de test — contrat partagé entre l'UI (commonMain) et les trois niveaux de tests.
 *
 * Les valeurs `login_*` sont celles de l'écran de connexion d'origine : le renommage de l'objet
 * (`LoginTags` → [AuthTags], US-21) ne les touche pas, un tag étant un contrat avec la QA et non
 * un nom de variable. Les quatre valeurs `auth_*` sont **figées par le cahier des charges US-21**
 * et verrouillées par `AuthTagsTest` (niveau 1).
 */
object AuthTags {
    const val SCREEN = "login_screen"
    const val EMAIL_FIELD = "login_email_field"
    const val PASSWORD_FIELD = "login_password_field"
    const val SUBMIT_BUTTON = "login_submit_button"
    const val REGISTER_LINK = "login_register_link"
    const val ERROR_MESSAGE = "login_error_message"
    const val LOADING_INDICATOR = "login_loading_indicator"

    // ── US-21 : inscription intelligente par SIRET ────────────────────────────
    const val SIRET_INPUT = "auth_siret_input"
    const val SIRET_LOADER = "auth_siret_loader"
    const val COMPANY_NAME_INPUT = "auth_company_name_input"
    const val SIRENE_VERIFIED_BADGE = "auth_sirene_verified_badge"

    // ── Tags internes, hors cahier des charges ────────────────────────────────
    // Sans eux, aucun test ne peut viser l'onglet à basculer, l'icône de recherche au repos, ni
    // distinguer « SIRET inconnu » de « répertoire injoignable ».
    const val TAB_LOGIN = "auth_tab_login"
    const val TAB_REGISTER = "auth_tab_register"
    const val SIRET_SEARCH_ICON = "auth_siret_search_icon"
    const val SIRENE_MESSAGE = "auth_sirene_message"
}

/** Loupe au repos dans le champ SIRET — glyphe, comme partout ailleurs dans l'app (US-19). */
private const val SEARCH_GLYPH = "🔍"

/**
 * Taille de l'indicateur de vérification.
 *
 * 18 dp et non la taille par défaut (40 dp) : un `CircularProgressIndicator` pleine taille dans un
 * `trailingIcon` gonfle la hauteur de l'`OutlinedTextField`, et le champ SIRET sauterait de
 * quelques pixels à chaque vérification.
 */
private val LoaderSize = 18.dp

@Composable
fun AuthScreen(viewModel: AuthViewModel = remember { AuthViewModel() }) {
    val uiState by viewModel.uiState.collectAsState()
    AuthContent(uiState = uiState, onIntent = viewModel::processIntent)
}

/**
 * Écran d'authentification — connexion et **inscription intelligente par SIRET** (US-21).
 *
 * Thème sombre « slate » propre à l'authentification (voir [LedgerHubColors]), appliqué localement
 * via un [MaterialTheme] imbriqué : cet écran précède la navigation, donc le thème global de l'app.
 *
 * La carte défile ([verticalScroll]) : le formulaire d'inscription porte quatre champs, un badge et
 * un bouton, ce qui dépasse la hauteur utile d'un téléphone une fois le clavier ouvert.
 */
@Composable
internal fun AuthContent(
    uiState: AuthUiState,
    onIntent: (AuthIntent) -> Unit = {},
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
                .semantics { testTag = AuthTags.SCREEN },
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(LedgerHubTheme.palette.Accent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📄", fontSize = 26.sp)
                }

                ModeTabs(
                    isRegistering = uiState.isRegistering,
                    onSelect = { onIntent(AuthIntent.ModeChanged(it)) },
                )

                Text(
                    text = if (uiState.isRegistering) {
                        tr(StringKey.AUTH_REGISTER_TITLE)
                    } else {
                        tr(StringKey.AUTH_LOGIN_TITLE)
                    },
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (uiState.isRegistering) {
                        tr(StringKey.AUTH_REGISTER_SUBTITLE)
                    } else {
                        tr(StringKey.AUTH_LOGIN_SUBTITLE)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                    textAlign = TextAlign.Center,
                )

                if (uiState.isRegistering) {
                    SiretSection(uiState = uiState, onIntent = onIntent)
                }

                AuthField(
                    label = tr(StringKey.AUTH_EMAIL_LABEL),
                    value = uiState.email,
                    placeholder = tr(StringKey.AUTH_EMAIL_PLACEHOLDER),
                    tag = AuthTags.EMAIL_FIELD,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.padding(top = if (uiState.isRegistering) 12.dp else 20.dp),
                    onValueChange = { onIntent(AuthIntent.EmailChanged(it)) },
                )
                AuthField(
                    label = tr(StringKey.AUTH_PASSWORD_LABEL),
                    value = uiState.password,
                    placeholder = "••••••••",
                    tag = AuthTags.PASSWORD_FIELD,
                    enabled = !uiState.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = 12.dp),
                    onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
                )

                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp).semantics {
                            testTag = AuthTags.ERROR_MESSAGE
                            contentDescription = uiState.errorMessage
                        },
                    )
                }

                Button(
                    onClick = { onIntent(AuthIntent.Submit) },
                    enabled = if (uiState.isRegistering) uiState.isRegisterEnabled else uiState.isSubmitEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = LedgerHubTheme.palette.Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .semantics { testTag = AuthTags.SUBMIT_BUTTON },
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .semantics { testTag = AuthTags.LOADING_INDICATOR },
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            if (uiState.isRegistering) {
                                tr(StringKey.AUTH_REGISTER_SUBMIT)
                            } else {
                                tr(StringKey.AUTH_LOGIN_SUBMIT)
                            },
                        )
                    }
                }

                if (!uiState.isRegistering) {
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = tr(StringKey.AUTH_NO_ACCOUNT_PROMPT),
                            color = LedgerHubTheme.palette.SecondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = tr(StringKey.AUTH_REGISTER_LINK),
                            color = LedgerHubTheme.palette.Accent,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onIntent(AuthIntent.ModeChanged(true)) }
                                .semantics { testTag = AuthTags.REGISTER_LINK },
                        )
                    }
                }

                Text(
                    text = tr(StringKey.AUTH_DISCLAIMER),
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerHubTheme.palette.SecondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

/** Bascule Connexion / Inscription — deux libellés, l'actif souligné d'un trait d'accent. */
@Composable
private fun ModeTabs(isRegistering: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
        ModeTab(
            label = tr(StringKey.AUTH_TAB_LOGIN),
            tag = AuthTags.TAB_LOGIN,
            selected = !isRegistering,
            onClick = { onSelect(false) },
        )
        ModeTab(
            label = tr(StringKey.AUTH_TAB_REGISTER),
            tag = AuthTags.TAB_REGISTER,
            selected = isRegistering,
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun ModeTab(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            // `width(IntrinsicSize.Max)` et non une largeur libre : le soulignement ci-dessous
            // demande `fillMaxWidth`, ce qui étirait l'onglet à toute la largeur disponible — le
            // premier onglet prenait la ligne entière et le second était mesuré à zéro, donc
            // invisible et intouchable. La largeur intrinsèque la borne au texte.
            .width(IntrinsicSize.Max)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = tag },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else LedgerHubTheme.palette.SecondaryText,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) LedgerHubTheme.palette.Accent else Color.Transparent),
        )
    }
}

/**
 * Bloc SIRET de l'inscription : le champ, son indicateur de vérification, puis le badge vert ou le
 * message d'échec, puis la raison sociale.
 *
 * La raison sociale reste **modifiable** après complétion automatique : le répertoire donne une
 * dénomination légale, qui n'est pas toujours celle sous laquelle on facture.
 */
@Composable
private fun SiretSection(uiState: AuthUiState, onIntent: (AuthIntent) -> Unit) {
    AuthField(
        label = tr(StringKey.AUTH_SIRET_LABEL),
        value = uiState.siret,
        placeholder = tr(StringKey.AUTH_SIRET_PLACEHOLDER),
        tag = AuthTags.SIRET_INPUT,
        enabled = !uiState.isLoading,
        modifier = Modifier.padding(top = 20.dp),
        keyboardType = KeyboardType.Number,
        // Le filtre de frappe borne à 14 chiffres ; la normalisation d'un SIRET collé avec des
        // espaces est faite côté domaine (SiretInput.sanitize).
        onValueChange = { onIntent(AuthIntent.SiretChanged(filterSiret(it))) },
        trailingIcon = {
            if (uiState.isVerifying) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(LoaderSize)
                        .semantics { testTag = AuthTags.SIRET_LOADER },
                    color = LedgerHubTheme.palette.Accent,
                    strokeWidth = 2.dp,
                )
            } else {
                val description = tr(StringKey.AUTH_SIRET_SEARCH_DESC)
                Text(
                    text = SEARCH_GLYPH,
                    modifier = Modifier.semantics {
                        testTag = AuthTags.SIRET_SEARCH_ICON
                        contentDescription = description
                    },
                )
            }
        },
    )

    Text(
        text = if (uiState.isVerifying) {
            tr(StringKey.AUTH_SIRENE_VERIFYING)
        } else {
            tr(StringKey.AUTH_SIRET_HELPER)
        },
        style = MaterialTheme.typography.labelSmall,
        color = LedgerHubTheme.palette.SecondaryText,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )

    when (uiState.sireneStatus) {
        SireneVerificationStatus.VERIFIED -> SireneVerifiedBadge()

        SireneVerificationStatus.NOT_FOUND -> SireneMessage(tr(StringKey.AUTH_SIRENE_NOT_FOUND))

        SireneVerificationStatus.UNAVAILABLE -> SireneMessage(tr(StringKey.AUTH_SIRENE_UNAVAILABLE))

        SireneVerificationStatus.IDLE, SireneVerificationStatus.VERIFYING -> Unit
    }

    AuthField(
        label = tr(StringKey.AUTH_COMPANY_NAME_LABEL),
        value = uiState.companyName,
        placeholder = tr(StringKey.AUTH_COMPANY_NAME_PLACEHOLDER),
        tag = AuthTags.COMPANY_NAME_INPUT,
        enabled = !uiState.isLoading,
        modifier = Modifier.padding(top = 12.dp),
        onValueChange = { onIntent(AuthIntent.CompanyNameChanged(it)) },
    )
}

/**
 * Badge de vérification.
 *
 * Nœud sémantique fusionné : le badge s'annonce d'un bloc, et son libellé — figé par le cahier des
 * charges, coche comprise — reste lisible dans l'arbre fusionné comme dans l'arbre brut.
 */
@Composable
private fun SireneVerifiedBadge() {
    Surface(
        color = LedgerHubTheme.palette.StatusPaidBg,
        contentColor = LedgerHubTheme.palette.StatusPaidFg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics(mergeDescendants = true) { testTag = AuthTags.SIRENE_VERIFIED_BADGE },
    ) {
        Text(
            text = tr(StringKey.AUTH_SIRENE_VERIFIED_BADGE),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** SIRET inconnu ou répertoire injoignable — deux causes distinctes, un même emplacement. */
@Composable
private fun SireneMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics {
                testTag = AuthTags.SIRENE_MESSAGE
                contentDescription = message
            },
    )
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
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LedgerHubTheme.palette.SecondaryText)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = { Text(placeholder, color = LedgerHubTheme.palette.SecondaryText.copy(alpha = 0.6f)) },
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LedgerHubTheme.palette.InputBackground,
                unfocusedContainerColor = LedgerHubTheme.palette.InputBackground,
                disabledContainerColor = LedgerHubTheme.palette.InputBackground,
                focusedBorderColor = LedgerHubTheme.palette.Accent,
                unfocusedBorderColor = LedgerHubTheme.palette.InputBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = LedgerHubTheme.palette.Accent,
            ),
            modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        )
    }
}
