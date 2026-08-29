package com.ledgerhub.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ledgerhub.domain.export.DocumentExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Partage Android d'un document généré, via `Intent.ACTION_SEND`.
 *
 * Le fichier est écrit dans un sous-dossier du cache, exposé par un [FileProvider] : depuis
 * Android 7 une `file://` déclenche une `FileUriExposedException`, seule une `content://` est
 * recevable par l'application destinataire.
 *
 * Le sous-dossier est vidé avant chaque écriture. La norme impose le nom `factur-x.xml`, donc
 * deux exports successifs se recouvriraient : on garde un dossier propre plutôt que d'accumuler.
 */
class AndroidDocumentExporter(
    private val context: Context,
    private val exportDirectoryName: String = DEFAULT_EXPORT_DIRECTORY,
) : DocumentExporter {

    override suspend fun export(
        fileName: String,
        mimeType: String,
        content: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, exportDirectoryName).apply {
                deleteRecursively()
                mkdirs()
            }
            val file = File(directory, fileName).apply { writeText(content) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, fileName)
                // Le destinataire n'a pas la permission du provider : il faut la lui accorder.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Lancé hors d'une Activity : NEW_TASK est requis sur le chooser.
            val chooser = Intent.createChooser(share, fileName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    private companion object {
        const val DEFAULT_EXPORT_DIRECTORY = "exports"
    }
}
