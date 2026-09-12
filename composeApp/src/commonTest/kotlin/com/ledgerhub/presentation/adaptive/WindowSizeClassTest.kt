package com.ledgerhub.presentation.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests unitaires purs KMP pour le calcul des breakpoints adaptatifs Material 3.
 */
class WindowSizeClassTest {

    @Test
    fun compactBreakpoint_under600dp() {
        val phonePortrait = calculateWindowSizeClass(360.dp, 800.dp)
        assertEquals(WindowWidthSizeClass.COMPACT, phonePortrait.widthSizeClass)
        assertTrue(phonePortrait.widthSizeClass.isCompact)
        assertFalse(phonePortrait.widthSizeClass.isMedium)
        assertFalse(phonePortrait.widthSizeClass.isExpanded)

        val largePhonePortrait = calculateWindowSizeClass(599.dp, 900.dp)
        assertEquals(WindowWidthSizeClass.COMPACT, largePhonePortrait.widthSizeClass)
    }

    @Test
    fun mediumBreakpoint_between600dpAnd839dp() {
        val foldableUnfolded = calculateWindowSizeClass(600.dp, 800.dp)
        assertEquals(WindowWidthSizeClass.MEDIUM, foldableUnfolded.widthSizeClass)
        assertFalse(foldableUnfolded.widthSizeClass.isCompact)
        assertTrue(foldableUnfolded.widthSizeClass.isMedium)
        assertFalse(foldableUnfolded.widthSizeClass.isExpanded)

        val smallTabletPortrait = calculateWindowSizeClass(720.dp, 1024.dp)
        assertEquals(WindowWidthSizeClass.MEDIUM, smallTabletPortrait.widthSizeClass)

        val boundaryMedium = calculateWindowSizeClass(839.dp, 1200.dp)
        assertEquals(WindowWidthSizeClass.MEDIUM, boundaryMedium.widthSizeClass)
    }

    @Test
    fun expandedBreakpoint_840dpAndAbove() {
        val tabletLandscape = calculateWindowSizeClass(840.dp, 600.dp)
        assertEquals(WindowWidthSizeClass.EXPANDED, tabletLandscape.widthSizeClass)
        assertFalse(tabletLandscape.widthSizeClass.isCompact)
        assertFalse(tabletLandscape.widthSizeClass.isMedium)
        assertTrue(tabletLandscape.widthSizeClass.isExpanded)

        val largeDesktop = calculateWindowSizeClass(1280.dp, 800.dp)
        assertEquals(WindowWidthSizeClass.EXPANDED, largeDesktop.widthSizeClass)
    }
}
