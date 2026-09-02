package com.ledgerhub.presentation.export

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import com.ledgerhub.domain.export.ExportFormat
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.components.filterIsoDate
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubColors

/**
 * Tags de test — contrat partagé entre l'UI (commonMain) et les trois niveaux de tests.
 * Les valeurs du conteneur, des deux champs de date, des trois cartes, du bouton de génération,
 * de la barre de progression et du bouton de téléchargement sont **figées par le cahier des
 * charges US-22** : les modifier casserait les suites QA. Elles sont verrouillées par
 * `ExportModalTagsTest` (N1).
 */
object ExportModalTags {
    const val DIALOG = "export_modal_dialog"
    const val DATE_FROM = "export_date_from"
    const val DATE_TO = "export_date_to"
    const val GENERATE_BTN = "export_generate_btn"
    const val PROGRESS_BAR = "export_progress_bar"
    const val DOWNLOAD_BTN = "export_download_btn"

    /**
     * Tag de la carte d'un format. `when` exhaustif plutôt qu'une interpolation sur l'identifiant :
     * la forme littérale est ce que le cahier des charges fige, et un quatrième format devra
     * déclarer son tag pour que le code compile (même parti pris qu'`IntegrationsHubTags`).
     */
    fun format(format: ExportFormat): String = when (format) {
        ExportFormat.FEC_OFFICIAL -> "export_format_fec"
        ExportFormat.FACTURX_ARCHIVE -> "export_format_facturx"
        ExportFormat.EXCEL_SUMMARY -> "export_format_excel"
    }

    // ── Tags internes, hors cahier des charges ────────────────────────────────
    // Sans eux, aucun test ne peut viser l'extérieur de la modale (un clic sur la modale étant
    // absorbé par elle), ni le bloc de succès indépendamment de son bouton. Même précédent que
    // `CommandPaletteTags.SCRIM` (US-19).

    /** Déclencheur posé dans le shell (en-tête compact et sidebar). */
    const val TRIGGER = "export_modal_trigger"

    /** Voile assombri, cliquable pour fermer. */
    const val SCRIM = "export_modal_scrim"

    /** Bloc de confirmation affiché une fois l'archive produite. */
    const val SUCCESS = "export_modal_success"

    /** Message de rejet d'une période incohérente. */
    const val PERIOD_ERROR = "export_period_error"
}

/** Voile de la modale — teinte reprise de la palette de commandes (US-19), un seul voile dans l'app. */
private val ScrimColor = Color(0xCC0B1020)

/** Au-delà, la feuille cesse de s'étirer : une modale pleine largeur sur tablette se lit mal. */
private val SheetMaxWidth = 560.dp

/**
 * Hauteur minimale d'une carte de format.
 *
 * **Ce n'est pas un réglage esthétique.** L'état le plus haut de la feuille — archive prête :
 * confirmation *et* bouton de téléchargement — doit tenir dans les quelque 800 dp utiles d'un
 * Pixel 5, sans quoi le bouton de téléchargement est rogné par le bas de l'écran. Constaté sur
 * l'appareil, où trois tests instrumentés échouaient pour cette seule raison. Les trois cartes
 * sont les plus gros postes du gabarit : les resserrer est ce qui rend la feuille tenable.
 *
 * 60 dp reste très au-dessus des 48 dp de cible tactile exigés, ce que le niveau 3b vérifie.
 */
private val FormatCardMinHeight = 60.dp

/**
 * Marges et espacements de la feuille — un **budget de hauteur**, et un budget se lit d'un seul
 * endroit plutôt que réparti en trois valeurs littérales. Resserrés depuis 16/14/8 dp pour la
 * raison exposée sur [FormatCardMinHeight].
 */
private val SheetHorizontalPadding = 20.dp
private val SheetVerticalPadding = 10.dp
private val SheetSectionSpacing = 10.dp

/** Espacement interne d'une section — son libellé et son contenu. */
private val SectionInnerSpacing = 6.dp

/**
 * Arrondi du haut de la feuille — ce qui la fait lire comme une bottom sheet et non comme une boîte.
 *
 * ## Pourquoi il est *peint* et non confié au `shape` de [Surface]
 *
 * `Surface(shape = …)` applique un `Modifier.clip(shape)`, et un arrondi **non uniforme** (haut
 * arrondi, bas droit) rend alors toute la descendance de la feuille inatteignable au toucher :
 * chaque tap traverse la feuille et va au voile, qui referme la modale. Les champs, les cartes et
 * les boutons sont visibles, annoncés correctement à l'accessibilité, et pourtant morts sous le
 * doigt. Constaté en niveau 3a, où seuls les tests qui *touchent* échouaient, l'action sémantique
 * équivalente passant sans problème.
 *
 * Le même dessin est donc obtenu par `background(color, shape)` + `border(width, color, shape)`,
 * qui peignent l'arrondi sans découper quoi que ce soit. Le contenu n'est plus rogné par la forme,
 * ce qui est sans conséquence ici : les 20 dp de marge intérieure tiennent tout le contenu loin
 * des coins.
 */
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * Déclencheur de l'export comptable, posé dans le shell (US-22).
 *
 * [compact] réduit le bouton à son seul glyphe. Ce n'est pas un réglage esthétique : l'en-tête
 * d'un téléphone porte déjà le nom de l'application, le hub d'intégrations, la palette de
 * commandes et le sélecteur de langue. Un quatrième libellé en toutes lettres y pousserait le
 * sélecteur hors de l'écran — ce que la sidebar, elle, a la place d'afficher.
 */
@Composable
fun ExportModalTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = ExportModalTags.TRIGGER },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("📤", style = MaterialTheme.typography.labelLarge)
            if (!compact) {
                Text(
                    text = tr(StringKey.EXPORT_TRIGGER_LABEL),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Modale d'export comptable branchée sur son [ExportViewModel] — ce que le shell instancie.
 *
 * La fermeture passe **toujours** par l'intention `Dismissed` avant de remonter à l'appelant :
 * une génération en vol doit être annulée, faute de quoi elle publierait son archive dans une
 * modale déjà refermée.
 */
@Composable
fun ExportModalSheet(viewModel: ExportViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    ExportModalContent(
        uiState = uiState,
        onIntent = viewModel::processIntent,
        onDismiss = {
            viewModel.processIntent(ExportIntent.Dismissed)
            onDismiss()
        },
    )
}

/**
 * Feuille d'export comptable (US-22) — ancrée en bas, par-dessus un voile assombri.
 *
 * ## Pourquoi un [Dialog] ancré en bas, et non un `ModalBottomSheet`
 *
 * Le cahier des charges laisse le choix. `ModalBottomSheet` porte un `SheetState` animé en
 * permanence : sous `runComposeUiTest`, `waitForIdle()` ne rend alors la main qu'au terme de
 * transitions qui, elles, ne s'arrêtent pas franchement — de quoi rendre la suite QA intermittente
 * pour une raison sans rapport avec ce qu'elle éprouve. Le projet a déjà tranché deux fois dans ce
 * sens (`CommandPalette`, `InvoicePreviewSheet`) : voile maison, surface ancrée, aucun état animé
 * à piloter. L'apparence est celle d'une bottom sheet ; le comportement reste inspectable.
 *
 * La fermeture au clic extérieur et à la touche Retour est celle de [Dialog] : les redéclarer
 * exposerait à ce que les deux chemins divergent.
 */
@Composable
internal fun ExportModalContent(
    uiState: ExportUiState,
    onIntent: (ExportIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Le voile est porte par la Box elle-meme, et la feuille en est l'enfant : un enfant est
        // toujours teste avant son parent, ce qui garantit que le doigt atteint la feuille avant le
        // voile. En faire deux freres superposes rendait la feuille inerte au toucher (US-19 avait
        // deja tranche ainsi).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                // `pointerInput` et non `clickable` : ce dernier declare une semantique de clic,
                // donc un noeud fusionne — et ce voile enveloppant toute la feuille, il en
                // absorberait le contenu entier, champs et cartes compris.
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .semantics { testTag = ExportModalTags.SCRIM },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                // Fond et bordure sont peints par le `modifier` ci-dessous, pas par `Surface` :
                // voir [SheetShape]. La couleur de contenu, elle, reste portée par `Surface` —
                // c'est ce qui donne aux textes leur teinte par défaut sans la répéter partout.
                color = Color.Transparent,
                contentColor = LedgerHubColors.PrimaryText,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = SheetMaxWidth)
                    .background(LedgerHubColors.Surface, SheetShape)
                    .border(1.dp, LedgerHubColors.Border, SheetShape)
                    // Absorbe le tap : sans cela, toucher la feuille la fermerait, le voile
                    // recevant l'evenement juste derriere.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { testTag = ExportModalTags.DIALOG },
            ) {
                Column(
                    modifier = Modifier
                        // Barre de gestes : sans ce retrait, le bouton de génération tomberait
                        // sous elle sur un appareil sans boutons physiques.
                        .navigationBarsPadding()
                        // La feuille tient sur un Pixel 5 sans défiler, y compris dans son état
                        // le plus haut — voir [FormatCardMinHeight]. Le défilement reste là pour
                        // les écrans plus courts et le clavier ouvert sur un champ de date.
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = SheetHorizontalPadding,
                            vertical = SheetVerticalPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(SheetSectionSpacing),
                ) {
                    SheetHandle()
                    Header(onDismiss = onDismiss)
                    PeriodSection(uiState = uiState, onIntent = onIntent)
                    FormatSection(uiState = uiState, onIntent = onIntent)
                    ActionSection(uiState = uiState, onIntent = onIntent)
                }
            }
        }
    }
}

/** Poignée de préhension : c'est elle qui fait lire la surface comme une feuille, pas une boîte. */
@Composable
private fun SheetHandle() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .heightIn(min = 4.dp, max = 4.dp)
                .background(LedgerHubColors.Border, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun Header(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = tr(StringKey.EXPORT_MODAL_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            // Borné à deux lignes : un sous-titre qui s'enroulerait sur trois lignes pousserait
            // tout ce qui suit vers le bas de l'écran.
            Text(
                text = tr(StringKey.EXPORT_MODAL_SUBTITLE),
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubColors.SecondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val closeLabel = tr(StringKey.EXPORT_CLOSE)
        Text(
            text = "✕",
            style = MaterialTheme.typography.titleMedium,
            color = LedgerHubColors.SecondaryText,
            modifier = Modifier
                .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                .clickable(onClick = onDismiss)
                .padding(8.dp)
                .semantics { contentDescription = closeLabel },
        )
    }
}

// ── Période ───────────────────────────────────────────────────────────────────

/**
 * Deux champs texte au format ISO plutôt qu'un `DatePicker`.
 *
 * L'application saisit déjà toutes ses dates ainsi (`FIELD_ISSUE_DATE`, `FIELD_DUE_DATE`), et
 * [filterIsoDate] réinsère les tirets : l'utilisateur ne tape que des chiffres. Un sélecteur de
 * calendrier introduirait un second modèle de saisie de date dans l'app, pour une valeur que
 * l'utilisateur connaît par cœur — les bornes de son exercice comptable.
 */
@Composable
private fun PeriodSection(uiState: ExportUiState, onIntent: (ExportIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionInnerSpacing)) {
        SectionLabel(tr(StringKey.EXPORT_PERIOD_SECTION))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(
                label = tr(StringKey.EXPORT_DATE_FROM_LABEL),
                value = uiState.period.from,
                tag = ExportModalTags.DATE_FROM,
                enabled = !uiState.isGenerating,
                modifier = Modifier.weight(1f),
                onValueChange = { onIntent(ExportIntent.DateFromChanged(filterIsoDate(it))) },
            )
            DateField(
                label = tr(StringKey.EXPORT_DATE_TO_LABEL),
                value = uiState.period.to,
                tag = ExportModalTags.DATE_TO,
                enabled = !uiState.isGenerating,
                modifier = Modifier.weight(1f),
                onValueChange = { onIntent(ExportIntent.DateToChanged(filterIsoDate(it))) },
            )
        }
        uiState.periodError?.let { error ->
            val message = tr(error.stringKey)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubColors.ErrorText,
                modifier = Modifier.semantics {
                    testTag = ExportModalTags.PERIOD_ERROR
                    contentDescription = message
                },
            )
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    tag: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        // Clavier numérique : la saisie ne contient que des chiffres, les tirets étant réinsérés
        // par le filtre. Un clavier alphabétique n'offrirait ici que des touches inopérantes.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LedgerHubColors.InputBackground,
            unfocusedContainerColor = LedgerHubColors.InputBackground,
            disabledContainerColor = LedgerHubColors.InputBackground,
            focusedTextColor = LedgerHubColors.PrimaryText,
            unfocusedTextColor = LedgerHubColors.PrimaryText,
            focusedIndicatorColor = LedgerHubColors.Accent,
            unfocusedIndicatorColor = LedgerHubColors.InputBorder,
            focusedLabelColor = LedgerHubColors.SecondaryText,
            unfocusedLabelColor = LedgerHubColors.SecondaryText,
        ),
        modifier = modifier.semantics { testTag = tag },
    )
}

// ── Formats ───────────────────────────────────────────────────────────────────

@Composable
private fun FormatSection(uiState: ExportUiState, onIntent: (ExportIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionInnerSpacing)) {
        SectionLabel(tr(StringKey.EXPORT_FORMAT_SECTION))
        ExportFormat.ordered().forEach { format ->
            FormatCard(
                format = format,
                selected = format == uiState.selectedFormat,
                enabled = !uiState.isGenerating,
                onSelect = { onIntent(ExportIntent.FormatSelected(format)) },
            )
        }
    }
}

/**
 * Carte de format sélectionnable.
 *
 * `Modifier.selectable(role = Role.RadioButton)` porte la sémantique du choix sur la **carte
 * entière** : un lecteur d'écran annonce un bouton radio, et le doigt n'a pas à viser la pastille.
 * Le [RadioButton] affiché n'est plus alors qu'un témoin visuel — d'où son `onClick = null`, qui
 * évite de déclarer deux fois la même action au système d'accessibilité.
 */
@Composable
private fun FormatCard(
    format: ExportFormat,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        color = if (selected) LedgerHubColors.Accent.copy(alpha = 0.14f) else LedgerHubColors.InputBackground,
        contentColor = LedgerHubColors.PrimaryText,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) LedgerHubColors.Accent else LedgerHubColors.Border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FormatCardMinHeight)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics(mergeDescendants = true) { testTag = ExportModalTags.format(format) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = LedgerHubColors.Accent,
                    unselectedColor = LedgerHubColors.SecondaryText,
                ),
            )
            Text(text = format.glyph, style = MaterialTheme.typography.titleMedium)
            // Une ligne par texte. Trois cartes enroulées sur deux lignes de titre et deux de
            // description ajoutent près de 120 dp à la feuille — exactement ce qui faisait sortir
            // le bouton de téléchargement de l'écran d'un Pixel 5. Les libellés sont écrits pour
            // tenir sur une ligne dans les deux langues.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = tr(format.titleKey),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tr(format.descriptionKey),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Action : générer, patienter, télécharger ─────────────────────────────────

/**
 * Les trois étapes occupent la **même** place au bas de la feuille et ne coexistent jamais : la
 * modale ne montre à aucun instant un bouton « Générer » à côté d'une archive déjà prête. C'est
 * aussi ce qui rend les assertions des niveaux 3 sans ambiguïté.
 */
@Composable
private fun ActionSection(uiState: ExportUiState, onIntent: (ExportIntent) -> Unit) {
    when (uiState.stage) {
        ExportStage.IDLE -> Button(
            onClick = { onIntent(ExportIntent.GenerateRequested) },
            enabled = uiState.isGenerateEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = LedgerHubColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { testTag = ExportModalTags.GENERATE_BTN },
        ) {
            Text(tr(StringKey.EXPORT_GENERATE_ACTION))
        }

        ExportStage.GENERATING -> GeneratingSection(progress = uiState.progress)

        ExportStage.READY -> SuccessSection(uiState = uiState, onIntent = onIntent)
    }
}

/**
 * Barre **déterminée**, alimentée par l'état du ViewModel et lissée par une courte interpolation.
 *
 * Une barre indéterminée aurait été plus simple à écrire et strictement moins honnête : la
 * compression a une fin connue, l'utilisateur a le droit de savoir où elle en est. Elle aurait de
 * surcroît tourné sans fin, empêchant `waitForIdle()` de rendre la main dans les tests.
 */
@Composable
private fun GeneratingSection(progress: Float) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "export_progress",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { animated },
            color = LedgerHubColors.Accent,
            trackColor = LedgerHubColors.InputBorder,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 8.dp)
                .semantics { testTag = ExportModalTags.PROGRESS_BAR },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = tr(StringKey.EXPORT_GENERATING_LABEL),
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubColors.SecondaryText,
            )
            Text(
                text = "${(progress * 100).toInt()} %",
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubColors.SecondaryText,
            )
        }
    }
}

@Composable
private fun SuccessSection(uiState: ExportUiState, onIntent: (ExportIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = LedgerHubColors.StatusPaidBg,
            contentColor = LedgerHubColors.StatusPaidFg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { testTag = ExportModalTags.SUCCESS },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "✓", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = tr(StringKey.EXPORT_SUCCESS_TITLE),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${uiState.archive?.documentCount ?: 0} " +
                            tr(StringKey.EXPORT_DOCUMENT_COUNT_LABEL),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Button(
            onClick = { onIntent(ExportIntent.DownloadRequested) },
            colors = ButtonDefaults.buttonColors(containerColor = LedgerHubColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { testTag = ExportModalTags.DOWNLOAD_BTN },
        ) {
            Text(tr(StringKey.EXPORT_DOWNLOAD_ACTION))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = LedgerHubColors.SecondaryText,
    )
}
