package com.ledgerhub.domain.export

/**
 * Remise d'un document généré à la plateforme hôte — feuille de partage Android, `UIActivity`
 * iOS, téléchargement navigateur.
 *
 * Interface injectée plutôt qu'`expect`/`actual` : la règle du projet réserve ce mécanisme au
 * dernier recours, et une simple abstraction suffit ici. Elle a de surcroît l'avantage de rendre
 * l'action substituable en test par un double, sans runtime de plateforme
 * (voir `RecordingDocumentExporter`).
 */
interface DocumentExporter {

    /**
     * @param fileName nom proposé à l'utilisateur — `factur-x.xml` pour une pièce Factur-X, comme
     *   le prescrit la norme pour la pièce jointe XML.
     * @param mimeType type MIME du contenu.
     * @param content contenu textuel, déjà généré.
     */
    suspend fun export(fileName: String, mimeType: String, content: String): Result<Unit>

    companion object {
        const val FACTUR_X_FILE_NAME = "factur-x.xml"
        const val XML_MIME_TYPE = "application/xml"
    }
}

/**
 * Implémentation neutre — utilisée là où aucun mécanisme de partage n'est encore branché
 * (iOS aujourd'hui) et comme valeur par défaut dans les prévisualisations Compose.
 * Elle réussit sans rien faire : un écran ne doit pas échouer parce que la plateforme ne sait
 * pas encore partager.
 */
object NoOpDocumentExporter : DocumentExporter {
    override suspend fun export(fileName: String, mimeType: String, content: String): Result<Unit> =
        Result.success(Unit)
}
