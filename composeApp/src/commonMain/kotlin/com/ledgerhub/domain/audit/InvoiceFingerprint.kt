package com.ledgerhub.domain.audit

import com.ledgerhub.domain.invoice.Invoice

/**
 * Empreinte d'intégrité SHA-256 d'une facture — preuve de scellement affichée par la timeline de
 * traçabilité (US-17).
 *
 * ## Pourquoi une implémentation à la main
 *
 * `java.security.MessageDigest` est JVM-only : indisponible en `commonMain`, donc sur la cible
 * iOS. Plutôt que d'introduire une dépendance multiplateforme entière (okio) pour une seule
 * fonction, l'algorithme — public, figé depuis 2001 (FIPS 180-4) et tenant en une centaine de
 * lignes — est implémenté ici. Il est verrouillé par les vecteurs de test officiels du NIST dans
 * `InvoiceFingerprintTest`, et produit donc le même résultat sur Android et sur iOS.
 */

/**
 * Chaîne canonique dont dérive l'empreinte.
 *
 * Volontairement construite sur les seules données **fiscalement engageantes** de la pièce, et
 * non sur son rendu : une empreinte calculée sur la mise en page changerait à la première
 * retouche de gabarit et ne prouverait plus rien. Le séparateur `|` ne peut apparaître ni dans
 * un SIRET, ni dans une date ISO, ni dans un montant en centimes.
 *
 * Le statut en fait partie : une facture déposée puis approuvée n'est plus la même pièce au sens
 * du circuit légal, et son empreinte doit le refléter.
 */
fun Invoice.canonicalFingerprintSource(): String = listOf(
    number,
    issueDate,
    issuer.siret,
    recipient.siret,
    totalTtc.cents.toString(),
    status.name,
).joinToString("|")

/** Empreinte SHA-256 de la facture, en hexadécimal minuscule (64 caractères). */
fun Invoice.fingerprintSha256(): String = sha256Hex(canonicalFingerprintSource())

/**
 * Empreinte tronquée pour l'affichage — l'écran d'un téléphone ne peut pas montrer 64 caractères
 * lisiblement, et les 16 premiers suffisent à un contrôle visuel. La valeur complète reste
 * accessible par [fingerprintSha256].
 */
fun String.abbreviateFingerprint(visibleChars: Int = 16): String =
    if (length <= visibleChars) this else take(visibleChars) + "…"

// ── SHA-256 (FIPS 180-4) ─────────────────────────────────────────────────────

/** Constantes de ronde : 32 premiers bits des parties fractionnaires des racines cubiques des 64 premiers nombres premiers. */
private val ROUND_CONSTANTS: IntArray = longArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
).map { it.toInt() }.toIntArray()

/** Valeurs initiales : 32 premiers bits des parties fractionnaires des racines carrées des 8 premiers nombres premiers. */
private val INITIAL_HASH: IntArray = longArrayOf(
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
).map { it.toInt() }.toIntArray()

private const val BLOCK_BYTES = 64
private const val SCHEDULE_WORDS = 64

private fun rotr(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))

/**
 * SHA-256 d'une chaîne, encodée en UTF-8, rendue en hexadécimal minuscule.
 *
 * Encodage explicite en UTF-8 (`encodeToByteArray`) : c'est ce qui rend l'empreinte identique
 * d'une plateforme à l'autre, une facture pouvant porter des accents (raison sociale) ou un
 * symbole monétaire.
 */
fun sha256Hex(input: String): String {
    val message = padded(input.encodeToByteArray())
    val hash = INITIAL_HASH.copyOf()
    val schedule = IntArray(SCHEDULE_WORDS)

    var offset = 0
    while (offset < message.size) {
        // Les 16 premiers mots sont le bloc lui-même, en big-endian.
        for (i in 0 until 16) {
            val base = offset + i * 4
            schedule[i] = (message[base].toInt() and 0xFF shl 24) or
                (message[base + 1].toInt() and 0xFF shl 16) or
                (message[base + 2].toInt() and 0xFF shl 8) or
                (message[base + 3].toInt() and 0xFF)
        }
        // Les 48 suivants sont dérivés — c'est l'expansion du message.
        for (i in 16 until SCHEDULE_WORDS) {
            val s0 = rotr(schedule[i - 15], 7) xor rotr(schedule[i - 15], 18) xor (schedule[i - 15] ushr 3)
            val s1 = rotr(schedule[i - 2], 17) xor rotr(schedule[i - 2], 19) xor (schedule[i - 2] ushr 10)
            schedule[i] = schedule[i - 16] + s0 + schedule[i - 7] + s1
        }

        var a = hash[0]
        var b = hash[1]
        var c = hash[2]
        var d = hash[3]
        var e = hash[4]
        var f = hash[5]
        var g = hash[6]
        var h = hash[7]

        for (i in 0 until SCHEDULE_WORDS) {
            val sigma1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + sigma1 + choice + ROUND_CONSTANTS[i] + schedule[i]
            val sigma0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = sigma0 + majority

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        hash[0] += a
        hash[1] += b
        hash[2] += c
        hash[3] += d
        hash[4] += e
        hash[5] += f
        hash[6] += g
        hash[7] += h

        offset += BLOCK_BYTES
    }

    return buildString(64) {
        hash.forEach { word ->
            for (shift in intArrayOf(24, 16, 8, 0)) {
                append(hexByte((word ushr shift) and 0xFF))
            }
        }
    }
}

/**
 * Bourrage FIPS 180-4 : un bit à 1, puis des zéros jusqu'à 56 octets modulo 64, puis la longueur
 * du message **en bits** sur 8 octets big-endian.
 */
private fun padded(data: ByteArray): ByteArray {
    val bitLength = data.size.toLong() * 8
    // +1 pour l'octet 0x80 ; le bourrage amène à un multiple de 64 en réservant 8 octets de longueur.
    var paddedSize = data.size + 1
    while (paddedSize % BLOCK_BYTES != 56) paddedSize++
    val result = ByteArray(paddedSize + 8)

    data.copyInto(result)
    result[data.size] = 0x80.toByte()
    for (i in 0 until 8) {
        result[result.size - 1 - i] = ((bitLength ushr (8 * i)) and 0xFF).toByte()
    }
    return result
}

private const val HEX_DIGITS = "0123456789abcdef"

private fun hexByte(value: Int): String =
    "${HEX_DIGITS[(value ushr 4) and 0x0F]}${HEX_DIGITS[value and 0x0F]}"
