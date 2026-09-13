package com.ledgerhub.data.support

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.support.AlreadyVotedException
import com.ledgerhub.domain.support.FeatureCategory
import com.ledgerhub.domain.support.FeatureRequest
import com.ledgerhub.domain.support.FeatureStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlDelightFeatureFeedbackRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: LedgerHubDatabase
    private lateinit var repository: SqlDelightFeatureFeedbackRepository

    @BeforeTest
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        database = LedgerHubDatabase(driver)
        repository = SqlDelightFeatureFeedbackRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun submitAndGetFeatureRequests_ordersByPopularity() = runTest {
        val req1 = FeatureRequest(
            id = "f-1",
            title = "Export FEC immédiat",
            description = "Génération du journal FEC",
            category = FeatureCategory.TAX_COMPLIANCE,
            authorId = "user-1",
            authorEmail = "u1@ledgerhub.app",
            voteCount = 2L,
            status = FeatureStatus.PROPOSED,
            createdAt = "2026-09-01T00:00:00Z",
        )
        val req2 = FeatureRequest(
            id = "f-2",
            title = "Mode sombre OLED",
            description = "Palette pure black",
            category = FeatureCategory.UI_UX,
            authorId = "user-2",
            authorEmail = "u2@ledgerhub.app",
            voteCount = 10L,
            status = FeatureStatus.PLANNED,
            createdAt = "2026-09-02T00:00:00Z",
        )

        repository.submitFeatureRequest(req1).getOrThrow()
        repository.submitFeatureRequest(req2).getOrThrow()

        val list = repository.getFeatureRequests("user-1").getOrThrow()
        assertEquals(2, list.size)
        // Vérifier le tri par popularité (req2 avec 10 votes en premier)
        assertEquals("f-2", list[0].id)
        assertEquals("f-1", list[1].id)
    }

    @Test
    fun voteAndCancelVote_modifiesCountAndTrack() = runTest {
        val req = FeatureRequest(
            id = "f-vote-1",
            title = "Rapprochement bancaire auto",
            description = "Matching IA",
            category = FeatureCategory.AUTOMATION,
            authorId = "user-1",
            authorEmail = "u1@ledgerhub.app",
            voteCount = 0L,
            status = FeatureStatus.PROPOSED,
            createdAt = "2026-09-01T00:00:00Z",
        )
        repository.submitFeatureRequest(req).getOrThrow()

        assertFalse(repository.hasUserVoted("voter-1", "f-vote-1").getOrThrow())

        // Voter
        repository.voteFeatureRequest("voter-1", "f-vote-1", "2026-09-13T12:00:00Z").getOrThrow()

        assertTrue(repository.hasUserVoted("voter-1", "f-vote-1").getOrThrow())
        val updated = repository.getFeatureRequestById("f-vote-1", "voter-1").getOrThrow()
        assertEquals(1L, updated?.voteCount)
        assertTrue(updated?.hasVoted == true)

        // Tenter de revoter -> AlreadyVotedException
        assertFailsWith<AlreadyVotedException> {
            repository.voteFeatureRequest("voter-1", "f-vote-1", "2026-09-13T12:05:00Z").getOrThrow()
        }

        // Annuler le vote
        repository.cancelVote("voter-1", "f-vote-1").getOrThrow()
        assertFalse(repository.hasUserVoted("voter-1", "f-vote-1").getOrThrow())
        val cancelled = repository.getFeatureRequestById("f-vote-1", "voter-1").getOrThrow()
        assertEquals(0L, cancelled?.voteCount)
        assertFalse(cancelled?.hasVoted == true)
    }
}
