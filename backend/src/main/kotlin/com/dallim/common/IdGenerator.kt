package com.dallim.common

import java.security.SecureRandom

/**
 * Generates short prefixed ids matching the shape used throughout docs/02-api-spec.md
 * examples (e.g. "usr_8f2a", "rt_001", "run_301"). Backed by a random suffix rather than
 * a sequential counter so ids don't leak creation order/volume.
 */
object IdGenerator {
    private val random = SecureRandom()
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    fun next(prefix: String, length: Int = 8): String {
        val suffix = buildString(length) {
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return "${prefix}_$suffix"
    }

    fun user(): String = next("usr")
    fun route(): String = next("rt")
    fun run(): String = next("run")
    fun gpsPoint(): String = next("gps")
    fun savedRoute(): String = next("sav")
    fun meetup(): String = next("mt")
    fun raceRecord(): String = next("race")
    fun feedbackTag(): String = next("fbt")
    fun shapeVote(): String = next("sv")
}
