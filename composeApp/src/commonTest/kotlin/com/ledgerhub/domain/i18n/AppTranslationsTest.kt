package com.ledgerhub.domain.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests QA — complétude et exactitude du dictionnaire bilingue. */
class AppTranslationsTest {

    @Test
    fun everyStringKey_isTranslated_inBothLanguages() {
        // AppTranslations.get lève NoSuchElementException si une clé manque dans une langue.
        StringKey.entries.forEach { key ->
            AppLanguage.entries.forEach { lang ->
                val value = AppTranslations.get(key, lang)
                assertTrue(value.isNotBlank(), "Traduction vide pour $key / $lang")
            }
        }
    }

    @Test
    fun sentinelValues_matchTheWebDictionary() {
        assertEquals("Vue d'ensemble", AppTranslations.get(StringKey.NAV_OVERVIEW, AppLanguage.FR))
        assertEquals("Dashboard", AppTranslations.get(StringKey.NAV_OVERVIEW, AppLanguage.EN))
        assertEquals("Factures", AppTranslations.get(StringKey.NAV_INVOICES, AppLanguage.FR))
        assertEquals("Invoices", AppTranslations.get(StringKey.NAV_INVOICES, AppLanguage.EN))
        assertEquals("Chiffre d'affaires", AppTranslations.get(StringKey.KPI_REVENUE_TITLE, AppLanguage.FR))
        assertEquals("Revenue", AppTranslations.get(StringKey.KPI_REVENUE_TITLE, AppLanguage.EN))
        assertEquals(
            "Le SIRET doit comporter exactement 14 chiffres",
            AppTranslations.get(ValidationErrorKey.CLIENT_SIRET_INVALID.stringKey, AppLanguage.FR),
        )
        assertEquals(
            "SIRET must be exactly 14 digits",
            AppTranslations.get(ValidationErrorKey.CLIENT_SIRET_INVALID.stringKey, AppLanguage.EN),
        )
    }

    /**
     * Libellés réglementaires PPF 2026 (US-13). Leur formulation est imposée par le référentiel :
     * elle est donc figée par des sentinelles, et non laissée à l'appréciation d'une relecture.
     */
    @Test
    fun ppfRegulatoryStatuses_useTheOfficialWording() {
        assertEquals("Déposée", AppTranslations.get(StringKey.STATUS_DEPOSITED, AppLanguage.FR))
        assertEquals("Submitted", AppTranslations.get(StringKey.STATUS_DEPOSITED, AppLanguage.EN))
        assertEquals(
            "Approuvée par l'administration",
            AppTranslations.get(StringKey.STATUS_APPROVED, AppLanguage.FR),
        )
        assertEquals(
            "Approved by administration",
            AppTranslations.get(StringKey.STATUS_APPROVED, AppLanguage.EN),
        )
        assertEquals(
            "Rejetée par la plateforme",
            AppTranslations.get(StringKey.STATUS_REJECTED, AppLanguage.FR),
        )
        assertEquals(
            "Rejected by platform",
            AppTranslations.get(StringKey.STATUS_REJECTED, AppLanguage.EN),
        )
    }

    @Test
    fun appLanguage_toggle_isBinaryAndSymmetric() {
        assertEquals(AppLanguage.EN, AppLanguage.FR.toggled())
        assertEquals(AppLanguage.FR, AppLanguage.EN.toggled())
    }
}
