package com.ledgerhub.presentation.auth

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 1 (US-21) — verrouillage du contrat de tags.
 *
 * Les quatre valeurs `auth_*` sont imposées par le cahier des charges et consommées par les
 * niveaux 3a et 3b. Les figer ici les sort du domaine de la relecture.
 *
 * Les valeurs `login_*` sont vérifiées au même titre : le renommage `LoginTags` → [AuthTags]
 * (US-21) ne devait toucher que le **nom de l'objet**, jamais les chaînes déjà connues de la QA.
 */
class AuthTagsTest {

    @Test
    fun theFourSpecifiedTags_matchTheSpecification() {
        assertEquals("auth_siret_input", AuthTags.SIRET_INPUT)
        assertEquals("auth_siret_loader", AuthTags.SIRET_LOADER)
        assertEquals("auth_company_name_input", AuthTags.COMPANY_NAME_INPUT)
        assertEquals("auth_sirene_verified_badge", AuthTags.SIRENE_VERIFIED_BADGE)
    }

    @Test
    fun theHistoricalLoginTags_areUnchangedByTheRename() {
        assertEquals("login_screen", AuthTags.SCREEN)
        assertEquals("login_email_field", AuthTags.EMAIL_FIELD)
        assertEquals("login_password_field", AuthTags.PASSWORD_FIELD)
        assertEquals("login_submit_button", AuthTags.SUBMIT_BUTTON)
        assertEquals("login_register_link", AuthTags.REGISTER_LINK)
        assertEquals("login_error_message", AuthTags.ERROR_MESSAGE)
        assertEquals("login_loading_indicator", AuthTags.LOADING_INDICATOR)
    }

    @Test
    fun everyTag_isDistinct() {
        val tags = listOf(
            AuthTags.SCREEN, AuthTags.EMAIL_FIELD, AuthTags.PASSWORD_FIELD, AuthTags.SUBMIT_BUTTON,
            AuthTags.REGISTER_LINK, AuthTags.ERROR_MESSAGE, AuthTags.LOADING_INDICATOR,
            AuthTags.SIRET_INPUT, AuthTags.SIRET_LOADER, AuthTags.COMPANY_NAME_INPUT,
            AuthTags.SIRENE_VERIFIED_BADGE, AuthTags.TAB_LOGIN, AuthTags.TAB_REGISTER,
            AuthTags.SIRET_SEARCH_ICON, AuthTags.SIRENE_MESSAGE,
        )
        assertEquals(tags.size, tags.toSet().size, "Tags dupliqués : $tags")
    }
}
