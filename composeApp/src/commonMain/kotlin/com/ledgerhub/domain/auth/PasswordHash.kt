package com.ledgerhub.domain.auth

import com.ledgerhub.domain.audit.sha256Hex
import kotlin.random.Random

/**
 * Empreinte d'un mot de passe — salée et itérée (US-26).
 *
 * ## Pourquoi pas le mot de passe en clair
 *
 * La base SQLDelight est un fichier ordinaire du stockage applicatif : lisible sur un appareil
 * rooté, et embarquée dans toute sauvegarde. Un mot de passe en clair y serait le mot de passe
 * *réutilisé ailleurs* par l'utilisateur. Ce qui est stocké est donc une empreinte à sens unique,
 * dont la vérification ([matches]) recalcule sans jamais relire le secret.
 *
 * ## Le sel, puis les itérations
 *
 * Le sel — 16 octets tirés au hasard par compte — interdit les tables précalculées et fait que deux
 * espaces ouverts avec le même mot de passe portent deux empreintes différentes. Les [ITERATIONS]
 * passages ralentissent délibérément une recherche exhaustive : le coût est invisible pour la
 * connexion unique d'un utilisateur, et multiplié par autant pour qui essaierait un dictionnaire.
 *
 * ## Ce que ce n'est pas
 *
 * Ce n'est pas Argon2 ni PBKDF2 : [sha256Hex] est l'implémentation Kotlin pure déjà présente pour
 * le scellement des factures, et aucune primitive de dérivation de clé n'est disponible en
 * `commonMain` sans dépendance nouvelle. Pour un compte **local**, dont le secret ne franchit
 * jamais l'appareil et ne protège aucun service distant, l'écart est acceptable. Il ne le serait
 * plus le jour où ces comptes seraient synchronisés — le remplacement se ferait ici, seul endroit
 * qui connaisse la forme de l'empreinte.
 */
object PasswordHash {

    /**
     * Coût de dérivation. Assez élevé pour peser sur une attaque par dictionnaire, assez bas pour
     * qu'une connexion reste imperceptible sur un téléphone d'entrée de gamme.
     */
    const val ITERATIONS: Int = 4_096

    /** 16 octets, soit 32 caractères hexadécimaux. */
    private const val SALT_HEX_LENGTH = 32

    private const val HEX_ALPHABET = "0123456789abcdef"

    /** Sel d'un compte neuf. [random] est injectable pour rendre un test reproductible. */
    fun newSalt(random: Random = Random.Default): String =
        buildString(SALT_HEX_LENGTH) {
            repeat(SALT_HEX_LENGTH) { append(HEX_ALPHABET[random.nextInt(HEX_ALPHABET.length)]) }
        }

    /** Empreinte de [password] sous [salt], en hexadécimal minuscule (64 caractères). */
    fun hash(password: String, salt: String): String {
        // Le sel est réintroduit à chaque tour, et pas seulement au premier : sans cela, les
        // itérations suivantes ne dépendraient plus que du condensat précédent, et une chaîne
        // calculée une fois servirait pour tous les sels.
        var digest = sha256Hex("$salt:$password")
        repeat(ITERATIONS - 1) { digest = sha256Hex("$salt:$digest") }
        return digest
    }

    /**
     * Vérifie [password] contre l'empreinte lue en base.
     *
     * La comparaison parcourt toute la longueur au lieu de s'arrêter au premier caractère qui
     * diffère : un `==` sur chaîne rendrait le temps de réponse dépendant du nombre de caractères
     * corrects trouvés. La fuite est ténue sur un appareil local, l'écrire correctement ne coûte
     * rien.
     */
    fun matches(password: String, salt: String, expectedHash: String): Boolean {
        val candidate = hash(password, salt)
        if (candidate.length != expectedHash.length) return false
        var difference = 0
        for (index in candidate.indices) {
            difference = difference or (candidate[index].code xor expectedHash[index].code)
        }
        return difference == 0
    }
}
