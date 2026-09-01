package com.ledgerhub.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Garde-fou des migrations (dette A-01) — reprend en test JVM ce que vérifiait la tâche Gradle
 * `verifyCommonMainLedgerHubDatabaseMigration` (US-17).
 *
 * ## Pourquoi ce test existe
 *
 * La tâche SQLDelight s'exécute dans un *worker Gradle* : un processus lancé par le démon avec un
 * environnement épuré, sans `TMP`/`TEMP` et sans `-Djava.io.tmpdir`. Sous Windows, `java.io.tmpdir`
 * y retombe donc sur `C:\WINDOWS`, où sqlite-jdbc tente d'extraire sa bibliothèque native — d'où
 * `AccessDeniedException: C:\WINDOWS\sqlite-...-sqlitejdbc.dll.lck` sur tout poste non
 * administrateur. Ce worker est hors de portée du build : `SqlDelightWorkerTask` appelle
 * `workerExecutor.processIsolation { }` en ne configurant que le classpath, jamais les
 * `forkOptions`. Ni `System.setProperty` dans le script racine, ni `systemProperty` sur les tâches,
 * ni `TMP`/`JAVA_TOOL_OPTIONS` exportés par le wrapper ne l'atteignent.
 *
 * Cette JVM de test, elle, reçoit `org.sqlite.tmpdir` du bloc `allprojects` du build racine :
 * la même vérification s'y exécute sans jamais écrire hors du projet.
 *
 * ## Ce qui est vérifié
 *
 * Exactement la garantie de la tâche d'origine : pour **chaque** instantané `databases/N.db`,
 * appliquer les migrations `.sqm` postérieures doit produire le schéma courant, celui que les
 * fichiers `.sq` créent sur une base neuve. Autrement dit : une base déjà installée en version N
 * arrive, après mise à jour, dans l'état exact d'une base fraîchement créée. Un `.sq` qui évolue
 * sans son `.sqm` fait échouer ce test — le silence d'un « no such column » en production reste
 * impossible.
 */
class SchemaMigrationVerificationTest {

    /**
     * Instantanés de schéma figés par `schemaOutputDirectory`. Le répertoire de travail des tests
     * Gradle est celui du sous-projet ; le repli couvre une exécution lancée depuis la racine.
     */
    private val snapshotDirectory: File
        get() = listOf(
            File("src/commonMain/sqldelight/databases"),
            File("composeApp/src/commonMain/sqldelight/databases"),
        ).firstOrNull { it.isDirectory }
            ?: fail(
                "Instantanés de schéma introuvables depuis ${File(".").absolutePath} — " +
                    "schemaOutputDirectory a-t-il changé dans composeApp/build.gradle.kts ?",
            )

    /**
     * Description comparable d'un schéma, lue par introspection plutôt que dans le texte des
     * `CREATE`.
     *
     * SQLite conserve le SQL exact soumis à la création : une table créée d'un bloc depuis un `.sq`
     * garde ses commentaires, tandis que la même table obtenue par `ALTER TABLE ADD COLUMN` porte
     * l'ancien texte augmenté de la colonne. Deux schémas rigoureusement identiques s'écrivent donc
     * différemment, et une comparaison textuelle les déclarerait divergents. C'est la structure qui
     * est comparée ici — colonnes (avec leur position, leur type, leur nullabilité, leur défaut et
     * leur clé primaire), clés étrangères et index — soit ce que compare schemacrawler côté
     * SQLDelight.
     */
    private fun JdbcSqliteDriver.schemaDescription(): List<String> {
        val tables = rows("SELECT name FROM sqlite_master WHERE type = 'table' " +
            "AND name NOT LIKE 'sqlite_%' ORDER BY name", columns = 1).map { it.single() }

        val description = mutableListOf<String>()
        tables.forEach { table ->
            description += "TABLE $table"
            // `cid` donne la position de la colonne : elle fait partie du schéma, une colonne
            // ajoutée par migration devant tomber au même rang que dans le `.sq`.
            description += rows(
                """
                SELECT cid, name, type, "notnull", COALESCE(dflt_value, ''), pk
                FROM pragma_table_info('$table') ORDER BY cid
                """.trimIndent(),
                columns = 6,
            ).map { "  COLUMN $table ${it.joinToString(" | ")}" }

            description += rows(
                """
                SELECT "table", "from", "to", on_update, on_delete
                FROM pragma_foreign_key_list('$table') ORDER BY "from", "table"
                """.trimIndent(),
                columns = 5,
            ).map { "  FK $table ${it.joinToString(" | ")}" }
        }

        // Index déclarés uniquement : les index automatiques (`sqlite_autoindex_*`) découlent des
        // contraintes déjà comparées ci-dessus et n'ont pas de définition propre.
        description += rows(
            """
            SELECT m.name, il."unique", ii.seqno, ii.name
            FROM sqlite_master m
            JOIN pragma_index_list(m.tbl_name) il ON il.name = m.name
            JOIN pragma_index_info(m.name) ii
            WHERE m.type = 'index' AND m.sql IS NOT NULL
            ORDER BY m.name, ii.seqno
            """.trimIndent(),
            columns = 4,
        ).map { "INDEX ${it.joinToString(" | ")}" }

        description += rows(
            "SELECT type, name FROM sqlite_master WHERE type IN ('view', 'trigger') ORDER BY type, name",
            columns = 2,
        ).map { "OBJECT ${it.joinToString(" | ")}" }

        return description
    }

    /** Exécute une requête d'introspection et rend ses lignes, chaque cellule en texte. */
    private fun JdbcSqliteDriver.rows(sql: String, columns: Int): List<List<String>> {
        val collected = mutableListOf<List<String>>()
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                while (cursor.next().value) {
                    collected += (0 until columns).map { cursor.getString(it).orEmpty() }
                }
                app.cash.sqldelight.db.QueryResult.Unit
            },
            parameters = 0,
        )
        return collected
    }

    private fun newDriver(url: String): JdbcSqliteDriver {
        // Chargement explicite du driver — robustesse face au classloader sandboxé de Robolectric.
        Class.forName("org.sqlite.JDBC")
        return JdbcSqliteDriver(url)
    }

    /** Le schéma de référence : celui qu'une base neuve obtient depuis les seuls fichiers `.sq`. */
    private fun currentSchema(): List<String> {
        val driver = newDriver(JdbcSqliteDriver.IN_MEMORY)
        return try {
            LedgerHubDatabase.Schema.create(driver).value
            driver.schemaDescription()
        } finally {
            driver.close()
        }
    }

    @Test
    fun everySnapshot_migratesUpToTheCurrentSchema() {
        val expected = currentSchema()
        assertTrue(expected.isNotEmpty(), "Le schéma courant est vide — génération SQLDelight cassée ?")

        val snapshots = snapshotDirectory.listFiles { file -> file.extension == "db" }
            ?.sortedBy { it.nameWithoutExtension.toLong() }
            .orEmpty()
        assertTrue(snapshots.isNotEmpty(), "Aucun instantané .db dans ${snapshotDirectory.absolutePath}")

        snapshots.forEach { snapshot ->
            val fromVersion = snapshot.nameWithoutExtension.toLong()

            // L'instantané est recopié : la vérification applique des migrations, et le fichier de
            // référence versionné ne doit pas en sortir modifié.
            val working = File.createTempFile("ledgerhub-schema-$fromVersion-", ".db").apply {
                deleteOnExit()
                snapshot.copyTo(this, overwrite = true)
            }

            val driver = newDriver("jdbc:sqlite:${working.absolutePath}")
            val migrated = try {
                LedgerHubDatabase.Schema.migrate(
                    driver = driver,
                    oldVersion = fromVersion,
                    newVersion = LedgerHubDatabase.Schema.version,
                ).value
                driver.schemaDescription()
            } finally {
                driver.close()
                working.delete()
            }

            assertEquals(
                expected,
                migrated,
                "La base en version $fromVersion, une fois migrée en " +
                    "${LedgerHubDatabase.Schema.version}, ne retombe pas sur le schéma courant. " +
                    "Un fichier .sq a probablement évolué sans le .sqm correspondant.",
            )
        }
    }

    /**
     * L'instantané le plus récent doit porter la version du schéma : c'est lui qui sert de point de
     * départ à la prochaine migration. S'il prend du retard, la chaîne vérifiée ci-dessus laisse un
     * trou — la version courante n'est plus couverte par aucun instantané.
     */
    @Test
    fun theLatestSnapshot_matchesTheCurrentSchemaVersion() {
        val latest = snapshotDirectory.listFiles { file -> file.extension == "db" }
            ?.maxOfOrNull { it.nameWithoutExtension.toLong() }
            ?: fail("Aucun instantané .db dans ${snapshotDirectory.absolutePath}")

        assertEquals(
            LedgerHubDatabase.Schema.version,
            latest,
            "Le dernier instantané est en version $latest alors que le schéma est en " +
                "${LedgerHubDatabase.Schema.version} — regenerer le schéma " +
                "(tâche generateCommonMainLedgerHubDatabaseSchema).",
        )
    }
}
