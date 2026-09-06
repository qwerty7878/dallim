package com.dallim.discovery

import io.lettuce.core.api.StatefulRedisConnection
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Redis-backed daily quota for `POST /routes/discovery` (v1.3 문서 S-12/13, docs/02-api-spec.md
 * 8.4: "무료 3회/일 · 광고 리워드 +2회"). Deliberately no DB migration/table — two plain Redis
 * counters per (userId, Asia/Seoul calendar date) are enough, and a new date simply produces a
 * brand-new, unseen key, so the daily reset needs no explicit rollover job or exact-midnight
 * calculation.
 *
 *  - `discovery:used:{userId}:{date}`  — INCR'd once per successful `POST /routes/discovery`
 *    (see [recordUsed], called from DiscoveryService only after generation actually succeeds).
 *  - `discovery:bonus:{userId}:{date}` — INCRBY [REWARD_BONUS_COUNT] once per rewarded-ad unlock
 *    (see [addRewardBonus], `POST /routes/discovery/reward-unlock`). No cap on how many times this
 *    can be called in a day (docs/02-api-spec.md 8.4: AdMob's own reward-ad impression frequency
 *    cap is the natural backstop — SPEC임의확장금지, no extra limit invented here).
 *
 * `limit = FREE_DAILY_LIMIT + bonus`, `remaining = max(0, limit - used)`. Both keys carry a TTL
 * well past one calendar day so a stale date's keys clean themselves up automatically instead of
 * accumulating forever.
 */
class DiscoveryQuotaService(
    private val connection: StatefulRedisConnection<String, String>,
) {
    companion object {
        const val FREE_DAILY_LIMIT = 3
        const val REWARD_BONUS_COUNT = 2
        private val KEY_TTL: Duration = Duration.ofHours(48)
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }

    private fun today(): String = LocalDate.now(SEOUL_ZONE).toString()
    private fun usedKey(userId: String, date: String) = "discovery:used:$userId:$date"
    private fun bonusKey(userId: String, date: String) = "discovery:bonus:$userId:$date"

    private fun readCount(key: String): Int = connection.sync().get(key)?.toIntOrNull() ?: 0

    /** `GET /routes/discovery/quota`, and also used internally as the pre-generation quota check. */
    fun getStatus(userId: String): DiscoveryQuotaStatus {
        val date = today()
        return toStatus(used = readCount(usedKey(userId, date)), bonus = readCount(bonusKey(userId, date)))
    }

    /**
     * Called from DiscoveryService right after a route was actually generated — increments
     * today's `used` counter and returns the resulting (post-increment) quota status. Never call
     * this before generation succeeds; a caller should first inspect [getStatus] and refuse to
     * generate (403 DISCOVERY_QUOTA_EXCEEDED) when `remainingToday <= 0`.
     */
    fun recordUsed(userId: String): DiscoveryQuotaStatus {
        val date = today()
        val key = usedKey(userId, date)
        val sync = connection.sync()
        val used = sync.incr(key)
        sync.expire(key, KEY_TTL)
        return toStatus(used = used.toInt(), bonus = readCount(bonusKey(userId, date)))
    }

    /** `POST /routes/discovery/reward-unlock` — grants [REWARD_BONUS_COUNT] more generations for today. */
    fun addRewardBonus(userId: String): DiscoveryQuotaStatus {
        val date = today()
        val key = bonusKey(userId, date)
        val sync = connection.sync()
        val bonus = sync.incrby(key, REWARD_BONUS_COUNT.toLong())
        sync.expire(key, KEY_TTL)
        return toStatus(used = readCount(usedKey(userId, date)), bonus = bonus.toInt())
    }

    private fun toStatus(used: Int, bonus: Int): DiscoveryQuotaStatus {
        val limit = FREE_DAILY_LIMIT + bonus
        val remaining = (limit - used).coerceAtLeast(0)
        return DiscoveryQuotaStatus(usedToday = used, limit = limit, remainingToday = remaining)
    }
}
