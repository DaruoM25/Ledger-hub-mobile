package com.ledgerhub.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.App
import com.ledgerhub.data.export.AndroidDocumentExporter
import com.ledgerhub.db.DatabaseDriverFactory
import com.ledgerhub.db.LedgerHubDatabase

/**
 * Point d'entrée Android.
 *
 * L'ouverture de la base est **gardée**. Elle ne l'était pas, et c'est ce qui produisait l'écran
 * blanc au démarrage sur appareil physique : une base illisible (schéma migré par une version
 * antérieure, stockage saturé, fichier corrompu par une installation précédente) faisait lever
 * `onCreate` avant `setContent`, la fenêtre restait donc peinte de son seul `windowBackground` —
 * blanc, avec le thème d'origine — sans qu'aucun message n'atteigne l'utilisateur.
 *
 * Désormais l'échec est **montré** : trace complète en Logcat sous le tag [LOG_TAG] pour la QA,
 * et écran d'erreur lisible sur l'appareil pour l'utilisateur. Une application qui explique ce
 * qui ne va pas vaut mieux qu'une page blanche muette.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = runCatching {
            LedgerHubDatabase(DatabaseDriverFactory(applicationContext).createDriver())
        }.onFailure { error ->
            Log.e(LOG_TAG, "Ouverture de la base LedgerHub impossible", error)
        }.getOrNull()

        setContent {
            if (database == null) {
                StartupFailureScreen(
                    message = "La base de données locale n'a pas pu être ouverte.\n\n" +
                        "Désinstallez puis réinstallez l'application, ou videz ses données " +
                        "(Paramètres ▸ Applications ▸ LedgerHub ▸ Stockage).",
                )
            } else {
                App(database, AndroidDocumentExporter(applicationContext))
            }
        }
    }

    private companion object {
        /** `adb logcat -s LedgerHub` suffit alors à voir la cause réelle d'un démarrage raté. */
        const val LOG_TAG = "LedgerHub"
    }
}

/**
 * Repli affiché quand l'application ne peut pas démarrer. Volontairement sans dépendance au
 * thème de l'application ni à la base : il doit s'afficher précisément dans les cas où celles-ci
 * font défaut.
 */
@Composable
private fun StartupFailureScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Démarrage impossible",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = message,
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
