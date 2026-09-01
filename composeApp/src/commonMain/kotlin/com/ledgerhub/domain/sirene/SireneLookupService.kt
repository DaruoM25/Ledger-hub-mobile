package com.ledgerhub.domain.sirene

/**
 * Accès au répertoire SIRENE de l'INSEE (US-21).
 *
 * Le contrat vit dans le domaine, l'implémentation simulée dans `data/`
 * ([com.ledgerhub.data.sirene.MockSireneLookupService]) : le jour où un vrai client Ktor la
 * remplacera, ni le ViewModel ni l'écran n'auront à bouger. C'est tout l'objet de ce découplage,
 * et la raison pour laquelle la simulation n'est pas écrite dans le ViewModel.
 *
 * @throws Exception si le répertoire est injoignable — l'appelant décide quoi en faire.
 */
interface SireneLookupService {

    /** @param siret 14 chiffres déjà normalisés (voir [SiretInput.sanitize]). */
    suspend fun lookup(siret: String): SireneLookupResult
}
