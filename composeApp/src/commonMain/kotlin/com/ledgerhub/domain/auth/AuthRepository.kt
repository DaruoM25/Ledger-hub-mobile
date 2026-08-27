package com.ledgerhub.domain.auth

/** Abstraction de l'authentification — la couche présentation ne connaît que ce contrat. */
interface AuthRepository {
    suspend fun login(email: String, password: String): Result<Unit>
}
