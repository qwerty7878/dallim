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

    // com.dallim.race (대회 캘린더, 2026-09-07) — NOT to be confused with com.dallim.racerecord's
    // "race_..." prefix (완주 이력/자기신고). Deliberately different prefixes ("rce"/"rcc"/"rcs")
    // so ids never collide/confuse between the two unrelated domains.
    fun race(): String = next("rce")
    fun raceCategoryOption(): String = next("rcc")
    fun raceSave(): String = next("rcs")

    // com.dallim.social (소셜 세션, S-30~S-39, 1단계는 S-30~S-34) — "ss"는 참가 신청(applicant)
    // 행에는 안 쓴다, 그건 (sessionId, userId) 복합 PK라 별도 id가 필요 없다.
    fun socialSession(): String = next("ss")

    // com.dallim.social 2단계 (S-35 채팅/신고, 2026-09-13) -- 체크인/평가/Running Mate는 전부
    // 복합 PK라 별도 id가 필요 없다(위 socialSession() 주석과 동일 원칙).
    fun socialChatMessage(): String = next("scm")
    fun contentReport(): String = next("rpt")

    // com.dallim.trainingplan (대회 목표 훈련 플랜, S-86, 2026-09-18) — 세션 row는 워커
    // (worker/trainingplan_db.py)가 직접 psycopg2로 id를 만들어 넣으므로 여기 Kotlin 쪽엔
    // plan() 하나만 있으면 된다.
    fun trainingPlan(): String = next("tp")
}
