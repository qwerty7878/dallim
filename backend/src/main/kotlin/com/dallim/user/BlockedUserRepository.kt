package com.dallim.user

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * blocked_users persistence — docs/02-api-spec.md 18장. Plain Exposed DSL, same convention as
 * com.dallim.user.SavedRouteRepository (idempotent insert/delete, no soft-delete flag needed
 * since the row's mere existence is the block).
 */
open class BlockedUserRepository(private val database: Database) {

    data class BlockedUserRow(
        val userId: String,
        val nickname: String,
        val avatarId: String?,
        val blockedAt: Instant,
    )

    /** POST /users/me/blocks — 이미 차단했으면 조용히 no-op(멱등). */
    fun block(blockerUserId: String, blockedUserId: String) {
        transaction(database) {
            val alreadyBlocked = BlockedUserTable.selectAll()
                .where {
                    (BlockedUserTable.blockerUserId eq blockerUserId) and
                        (BlockedUserTable.blockedUserId eq blockedUserId)
                }
                .limit(1)
                .count() > 0

            if (!alreadyBlocked) {
                BlockedUserTable.insert {
                    it[BlockedUserTable.blockerUserId] = blockerUserId
                    it[BlockedUserTable.blockedUserId] = blockedUserId
                }
            }
        }
    }

    /** DELETE /users/me/blocks/{blockedUserId} — 관계가 없어도 그냥 성공(멱등). */
    fun unblock(blockerUserId: String, blockedUserId: String) {
        transaction(database) {
            BlockedUserTable.deleteWhere {
                it.run {
                    (BlockedUserTable.blockerUserId eq blockerUserId) and
                        (BlockedUserTable.blockedUserId eq blockedUserId)
                }
            }
        }
    }

    /** GET /users/me/blocks — 최근 차단순. BlockedUserTable에는 UserTable을 향한 FK가 두 개
     * (blockerUserId/blockedUserId)라 `innerJoin`의 자동 추론이 모호해진다 -- 명시적으로
     * blockedUserId <-> UserTable.id로만 조인한다(닉네임/아바타 조회 대상은 차단된 쪽이므로). */
    fun findBlocked(blockerUserId: String): List<BlockedUserRow> = transaction(database) {
        BlockedUserTable
            .join(UserTable, JoinType.INNER, onColumn = BlockedUserTable.blockedUserId, otherColumn = UserTable.id)
            .selectAll()
            .where { BlockedUserTable.blockerUserId eq blockerUserId }
            .orderBy(BlockedUserTable.createdAt, SortOrder.DESC)
            .map { it.toBlockedUserRow() }
    }

    private fun ResultRow.toBlockedUserRow() = BlockedUserRow(
        userId = this[BlockedUserTable.blockedUserId],
        nickname = this[UserTable.nickname] ?: "달림이",
        avatarId = this[UserTable.avatarId],
        blockedAt = this[BlockedUserTable.createdAt],
    )
}
