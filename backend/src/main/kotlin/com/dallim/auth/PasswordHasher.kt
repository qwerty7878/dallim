package com.dallim.auth

import org.mindrot.jbcrypt.BCrypt

/**
 * BCrypt wrapper. Callers must never log or serialize the input/output of these functions —
 * see CLAUDE.md rule 5 and docs/02-api-spec.md 1장 signup 검증 규칙.
 */
object PasswordHasher {
    fun hash(rawPassword: String): String = BCrypt.hashpw(rawPassword, BCrypt.gensalt())

    fun matches(rawPassword: String, hash: String): Boolean = BCrypt.checkpw(rawPassword, hash)
}
