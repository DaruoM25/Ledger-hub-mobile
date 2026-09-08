package com.ledgerhub.domain.invoice

/**
 * Adresse de livraison spécifique (réforme Factur-X 2026).
 * Renseignée lorsque le lieu de livraison diffère de l'adresse de facturation du client.
 */
data class DeliveryAddress(
    val street: String = "",
    val zip: String = "",
    val city: String = "",
    val country: String = "France",
) {
    val isNotBlank: Boolean
        get() = street.isNotBlank() || zip.isNotBlank() || city.isNotBlank()
}
