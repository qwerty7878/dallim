package com.dallim.route

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Daily batch job (docs/01-feature-spec.md 2.3 "RouteStatusUpdateJob", 매일 03:00) that promotes
 * each SketchRoute.status forward along DISCOVERY -> VERIFIED -> POPULAR based on the
 * `finisher_count` column (docs/01-feature-spec.md 2.2.C transition rule).
 *
 * `finisher_count` itself is kept accurate by com.dallim.run.FinisherCountSync's own periodic
 * Redis-INCR-then-flush job (docs/01-feature-spec.md 2.2.D) — this job only *reads* that
 * already-synced column and reacts to it; it never touches Redis itself.
 *
 * Ktor has no Spring-style `@Scheduled` (docs/01-feature-spec.md 2.3 explicitly calls this out),
 * so the periodic loop is a plain coroutine started from com.dallim.Application, following the
 * same convention as FinisherCountSync's own scheduling there (startFinisherCountSyncJob). [runOnce]
 * is deliberately kept as the standalone DB-touching half — no coroutine/delay logic inside it —
 * so it can be unit/integration-tested and manually triggered (local verification, qa-engineer
 * fixtures) without waiting for either the 03:00 initial delay or the 24h interval.
 */
class RouteStatusUpdateJob(private val database: Database) {

    companion object {
        /** "완주 1건 이상" — the exact figure docs/01-feature-spec.md 2.2.C pins down for
         * DISCOVERY -> VERIFIED. */
        const val VERIFIED_THRESHOLD = 1

        // SPEC says "완주자 수 N건 이상, 임계치 설정값" for VERIFIED -> POPULAR but does not pin N
        // (same "TBD, needs real-traffic tuning" situation as RunJudgementService.Companion's own
        // thresholds — see that file's KDoc for the established pattern this follows). 50 is a
        // deliberate default chosen to sit inside the range the existing curated-route seed data
        // (V2__seed_curated_routes.sql) already implies: rt_002/rt_003 sit at finisher_count 12/6
        // and are seeded VERIFIED (not POPULAR), while rt_001 sits at 148 and is seeded POPULAR —
        // so the real intended threshold is somewhere in (12, 148], and 50 is a round number
        // comfortably inside that band. Revisit once real usage data exists.
        const val POPULAR_THRESHOLD = 50
    }

    /**
     * Pure transition function — a route's status only ever moves forward. A route already at
     * [RouteStatus.POPULAR] always stays there regardless of [finisherCount] (defensive; in
     * practice [runOnce] never even queries POPULAR rows, since there's nowhere further to go).
     */
    fun nextStatus(current: RouteStatus, finisherCount: Int): RouteStatus {
        if (current == RouteStatus.POPULAR) return RouteStatus.POPULAR
        return when {
            finisherCount >= POPULAR_THRESHOLD -> RouteStatus.POPULAR
            finisherCount >= VERIFIED_THRESHOLD -> RouteStatus.VERIFIED
            else -> current
        }
    }

    /**
     * One batch pass: loads every non-POPULAR route (MVP1 has only a handful of operator-curated
     * routes, so a full scan every run is fine — no pagination/indexing needed at this scale),
     * computes [nextStatus] for each, and issues one UPDATE per route whose status actually
     * changes. Returns the number of routes updated (log-friendly, and handy for tests/local
     * verification).
     */
    fun runOnce(): Int = transaction(database) {
        val changes = SketchRouteTable
            .selectAll()
            .where { SketchRouteTable.status neq RouteStatus.POPULAR }
            .mapNotNull { row ->
                val current = row[SketchRouteTable.status]
                val next = nextStatus(current, row[SketchRouteTable.finisherCount])
                if (next != current) row[SketchRouteTable.id] to next else null
            }

        for ((routeId, next) in changes) {
            SketchRouteTable.update({ SketchRouteTable.id eq routeId }) {
                it[status] = next
            }
        }
        changes.size
    }
}

/**
 * Milliseconds from [now] until the next 03:00 in [zone] — today's 03:00 if it hasn't passed yet,
 * otherwise tomorrow's. The initial-delay half of docs/01-feature-spec.md 2.3's "매일 03:00"
 * schedule for [RouteStatusUpdateJob]. Kept as a standalone top-level function (rather than baked
 * directly into com.dallim.Application's start-up wiring) so it's independently unit-testable.
 */
fun untilNext3AmMillis(now: ZonedDateTime = ZonedDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
    val nowInZone = now.withZoneSameInstant(zone)
    val today3am = nowInZone.toLocalDate().atTime(3, 0).atZone(zone)
    val next3am = if (today3am.isAfter(nowInZone)) today3am else today3am.plusDays(1)
    return Duration.between(nowInZone, next3am).toMillis()
}
