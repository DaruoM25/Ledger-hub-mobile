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

    @Test
    fun appLanguage_toggle_isBinaryAndSymmetric() {
        assertEquals(AppLanguage.EN, AppLanguage.FR.toggled())
        assertEquals(AppLanguage.FR, AppLanguage.EN.toggled())
    }
}
