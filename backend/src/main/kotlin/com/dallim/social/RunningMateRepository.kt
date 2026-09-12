package com.dallim.social

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/** running_mates persistence — docs/02-api-spec.md 17장(S-39). */
open class RunningMateRepository(private val database: Database) {

    data class MateRow(val mateUserId: String, val runTogetherCount: Int, val lastRunTogetherAt: Instant)

    /** S-39 매칭 성립(또는 반복) 순간 upsert -- [userA]/[userB]는 순서 무관하게 넘겨도 된다
     * (내부에서 항상 사전순으로 정렬해 저장한다, com.dallim.social.RunningMateTable 문서 참고). */
    open fun upsertOnMatch(userA: String, userB: String, at: Instant) {
        val (lo, hi) = if (userA < userB) userA to userB else userB to userA
        transaction(database) {
            val currentCount = RunningMateTable.selectAll()
                .where { (RunningMateTable.userIdA eq lo) and (RunningMateTable.userIdB eq hi) }
                .limit(1)
                .map { it[RunningMateTable.runTogetherCount] }
                .singleOrNull()

            if (currentCount != null) {
                RunningMateTable.update({ (RunningMateTable.userIdA eq lo) and (RunningMateTable.userIdB eq hi) }) {
                    it[RunningMateTable.runTogetherCount] = currentCount + 1
                    it[RunningMateTable.lastRunTogetherAt] = at
                }
            } else {
                RunningMateTable.insert {
                    it[RunningMateTable.userIdA] = lo
                    it[RunningMateTable.userIdB] = hi
                    it[RunningMateTable.runTogetherCount] = 1
                    it[RunningMateTable.lastRunTogetherAt] = at
                }
            }
        }
    }

    /** GET /users/me/running-mates -- 내가 숨기지 않은 메이트만. */
    fun findVisibleMates(userId: String): List<MateRow> = transaction(database) {
        RunningMateTable.selectAll()
            .where {
                ((RunningMateTable.userIdA eq userId) and (RunningMateTable.hiddenByA eq false)) or
                    ((RunningMateTable.userIdB eq userId) and (RunningMateTable.hiddenByB eq false))
            }
            .map { it.toMateRow(userId) }
    }

    /** DELETE /users/me/running-mates/{mateUserId} -- 내 쪽(hidden_by_a 또는 hidden_by_b 중
     * 내 쪽)만 조용히 true로. 행이 없으면 그냥 no-op(멱등, 상대에게 알림 없음 -- SPEC 명시). */
    fun hideForUser(userId: String, mateUserId: String) {
        val (lo, hi) = if (userId < mateUserId) userId to mateUserId else mateUserId to userId
        transaction(database) {
            RunningMateTable.update({ (RunningMateTable.userIdA eq lo) and (RunningMateTable.userIdB eq hi) }) {
                if (userId == lo) it[RunningMateTable.hiddenByA] = true else it[RunningMateTable.hiddenByB] = true
            }
        }
    }

    private fun ResultRow.toMateRow(viewerUserId: String): MateRow {
        val mateUserId = if (this[RunningMateTable.userIdA] == viewerUserId) this[RunningMateTable.userIdB] else this[RunningMateTable.userIdA]
        return MateRow(
            mateUserId = mateUserId,
            runTogetherCount = this[RunningMateTable.runTogetherCount],
            lastRunTogetherAt = this[RunningMateTable.lastRunTogetherAt],
        )
    }
}
