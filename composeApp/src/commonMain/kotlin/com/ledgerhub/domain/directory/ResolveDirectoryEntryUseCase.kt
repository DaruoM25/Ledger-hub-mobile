package com.ledgerhub.domain.directory

/**
 * Résout une saisie libre (SIREN ou SIRET) en fiche d'annuaire.
 *
 * 1. Normalise : retire espaces et séparateurs courants.
 * 2. Décide de la nature par la longueur (9 → SIREN, 14 → SIRET), toute autre longueur ou la
 *    présence de caractères non numériques → [DirectoryLookupResult.InvalidChecksum].
 * 3. Contrôle la clé de Luhn ([LuhnChecksum]). En cas d'échec →
 *    [DirectoryLookupResult.InvalidChecksum].
 * 4. Interroge le [DirectoryRepository] → [DirectoryLookupResult.Resolved] ou
 *    [DirectoryLookupResult.NotFound].
 */
class ResolveDirectoryEntryUseCase(
    private val repository: DirectoryRepository,
) {

    suspend operator fun invoke(rawQuery: String): DirectoryLookupResult {
        val cleanQuery = normalize(rawQuery)
        val kind = identifierKindOf(cleanQuery)

        val checksumOk = when (kind) {
            IdentifierKind.SIREN -> LuhnChecksum.isValidSiren(cleanQuery)
            IdentifierKind.SIRET -> LuhnChecksum.isValidSiret(cleanQuery)
            IdentifierKind.UNKNOWN -> false
        }
        if (!checksumOk) return DirectoryLookupResult.InvalidChecksum

        val siren = if (cleanQuery.length == 14) cleanQuery.take(9) else cleanQuery
        val entry = when (kind) {
            IdentifierKind.SIREN -> repository.findBySiren(siren)
            IdentifierKind.SIRET -> repository.findBySiret(cleanQuery)
                ?: repository.findBySiren(siren)?.copy(siret = cleanQuery)
            IdentifierKind.UNKNOWN -> null
        }
        return entry?.let { DirectoryLookupResult.Resolved(it) } ?: DirectoryLookupResult.NotFound
    }

    companion object {
        /** Retire tout ce qui n'est pas un chiffre (espaces, points, tirets de mise en forme). */
        fun normalize(raw: String): String = raw.filter { it in '0'..'9' }

        fun identifierKindOf(digits: String): IdentifierKind = when (digits.length) {
            9 -> IdentifierKind.SIREN
            14 -> IdentifierKind.SIRET
            else -> IdentifierKind.UNKNOWN
        }
    }
}
