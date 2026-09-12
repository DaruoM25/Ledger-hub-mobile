package com.ledgerhub.presentation.subscription

/**
 * Identifiants sémantiques (testTag) pour l'écran Paywall et ses composants.
 */
object PaywallTags {
    const val SCREEN = "paywall_screen"
    const val CLOSE_BUTTON = "paywall_close_button"
    const val TITLE = "paywall_title"
    const val SUBTITLE = "paywall_subtitle"
    const val PLAN_MONTHLY = "paywall_plan_monthly"
    const val PLAN_ANNUAL = "paywall_plan_annual"
    const val CTA_BUTTON = "paywall_cta_button"
    const val RESTORE_BUTTON = "paywall_restore_button"
    const val PROMO_CODE_INPUT = "paywall_promo_code_input"
    const val PROMO_CODE_SUBMIT = "paywall_promo_code_submit"
    const val PROMO_SUCCESS_MESSAGE = "paywall_promo_success_message"
    const val PROMO_ERROR_MESSAGE = "paywall_promo_error_message"
    const val ERROR_BANNER = "paywall_error_banner"
    const val FEATURE_ITEM_PREFIX = "paywall_feature_"

    fun featureTag(id: String) = "${FEATURE_ITEM_PREFIX}$id"
}
