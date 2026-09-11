package com.ledgerhub.presentation.directory

import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryRepository
import com.ledgerhub.domain.directory.DirectoryStatus
import com.ledgerhub.domain.directory.FrenchVatNumber
import com.ledgerhub.domain.directory.IdentifierKind
import com.ledgerhub.domain.directory.ResolveDirectoryEntryUseCase
import com.ledgerhub.domain.directory.RoutingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class StubDirectoryRepository(entries: List<DirectoryEntry> = emptyList()) : DirectoryRepository {
    private val store = entries.associateBy { it.siren }.toMutableMap()
    override suspend fun findBySiren(siren: String) = store[siren]
    override suspend fun findBySiret(siret: String) = store.values.firstOrNull { it.siret == siret }
    override suspend fun all() = store.values.toList()
    override suspend fun cache(entry: DirectoryEntry) { store[entry.siren] = entry }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun renault() = DirectoryEntry(
        siren = "732829320",
        siret = "73282932000074",
        companyName = "RENAULT SAS",
        vatNumber = FrenchVatNumber.format("732829320"),
        routingMode = RoutingMode.PPF,
        pdpIdentifier = null,
        isVatSubject = true,
        status = DirectoryStatus.ACTIVE,
        lastSyncAt = "2026-08-30T09:00:00Z",
    )

    private fun newViewModel(vararg entries: DirectoryEntry): DirectoryViewModel {
        val repo = StubDirectoryRepository(entries.toList())
        return DirectoryViewModel(repo, ResolveDirectoryEntryUseCase(repo), dispatcher)
    }

    @Test
    fun initialState_isEmpty_andSearchDisabled() {
        val state = newViewModel().uiState.value
        assertEquals("", state.query)
        assertNull(state.luhnValid)
        assertFalse(state.isSearchEnabled)
    }

    @Test
    fun queryChanged_withInvalidSiren_flagsLuhnKo_andKeepsSearchDisabled() {
        val vm = newViewModel()
        vm.processIntent(DirectoryIntent.QueryChanged("123456789"))

        val state = vm.uiState.value
        assertEquals(IdentifierKind.SIREN, state.identifierKind)
        assertEquals(false, state.luhnValid)
        assertFalse(state.isSearchEnabled)
    }

    @Test
    fun queryChanged_withValidSiren_flagsLuhnOk_andEnablesSearch() {
        val vm = newViewModel()
        vm.processIntent(DirectoryIntent.QueryChanged("732 829 320"))

        val state = vm.uiState.value
        assertEquals(IdentifierKind.SIREN, state.identifierKind)
        assertEquals(true, state.luhnValid)
        assertTrue(state.isSearchEnabled)
    }

    @Test
    fun queryChanged_withValidSiret_isRecognisedAsSiret() {
        val vm = newViewModel()
        vm.processIntent(DirectoryIntent.QueryChanged("73282932000074"))

        assertEquals(IdentifierKind.SIRET, vm.uiState.value.identifierKind)
        assertEquals(true, vm.uiState.value.luhnValid)
    }

    @Test
    fun search_onKnownIdentifier_populatesResolvedEntry() = runTest {
        val vm = newViewModel(renault())
        vm.processIntent(DirectoryIntent.QueryChanged("732829320"))

        vm.processIntent(DirectoryIntent.Search)

        val state = vm.uiState.value
        assertFalse(state.isSearching)
        assertNotNull(state.resolved)
        assertEquals("RENAULT SAS", state.resolved.companyName)
        assertEquals(RoutingMode.PPF, state.resolved.routingMode)
        assertFalse(state.notFound)
    }

    @Test
    fun search_onValidButUnknownIdentifier_setsNotFound() = runTest {
        val vm = newViewModel() // dépôt vide
        vm.processIntent(DirectoryIntent.QueryChanged("732829320"))

        vm.processIntent(DirectoryIntent.Search)

        val state = vm.uiState.value
        assertTrue(state.notFound)
        assertNull(state.resolved)
    }

    @Test
    fun queryChanged_afterAResult_clearsThePreviousResolvedEntry() = runTest {
        val vm = newViewModel(renault())
        vm.processIntent(DirectoryIntent.QueryChanged("732829320"))
        vm.processIntent(DirectoryIntent.Search)
        assertNotNull(vm.uiState.value.resolved)

        vm.processIntent(DirectoryIntent.QueryChanged("7328293"))

        assertNull(vm.uiState.value.resolved)
        assertFalse(vm.uiState.value.notFound)
    }

    @Test
    fun search_isIgnored_whenLuhnInvalid() = runTest {
        val vm = newViewModel(renault())
        vm.processIntent(DirectoryIntent.QueryChanged("123456789"))

        vm.processIntent(DirectoryIntent.Search)

        assertNull(vm.uiState.value.resolved)
        assertFalse(vm.uiState.value.isSearching)
    }

    @Test
    fun search_withValid14DigitSiret_resolvesEntryWithBothSirenAndSiret() = runTest {
        val orange = DirectoryEntry(
            siren = "380129866",
            siret = null,
            companyName = "ORANGE SA",
            vatNumber = FrenchVatNumber.format("380129866"),
            routingMode = RoutingMode.PPF,
            pdpIdentifier = null,
            isVatSubject = true,
            status = DirectoryStatus.ACTIVE,
            lastSyncAt = "2026-08-30T09:00:00Z",
        )
        val vm = newViewModel(orange)
        val orangeSiret = "38012986648625"

        vm.processIntent(DirectoryIntent.QueryChanged(orangeSiret))
        assertTrue(vm.uiState.value.isSearchEnabled)

        vm.processIntent(DirectoryIntent.Search)

        val state = vm.uiState.value
        assertFalse(state.isSearching)
        val resolved = assertNotNull(state.resolved)
        assertEquals("ORANGE SA", resolved.companyName)
        assertEquals("380129866", resolved.siren)
        assertEquals(orangeSiret, resolved.siret)
        assertEquals("FR89380129866", resolved.vatNumber)
        assertEquals(RoutingMode.PPF, resolved.routingMode)
        assertEquals(DirectoryStatus.ACTIVE, resolved.status)
    }
}
