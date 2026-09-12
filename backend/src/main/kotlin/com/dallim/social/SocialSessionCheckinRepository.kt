package com.dallim.social

import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * social_session_checkins persistence — docs/02-api-spec.md 17장(S-36/S-37). Plain Exposed DSL,
 * 같은 파일 SocialSessionCheckinWindow가 계산한 상태만 그대로 저장한다(판정 로직은 이 레포지토리
 * 밖에 있다 -- com.dallim.social.SocialSessionCheckinService 참고).
 */
open class SocialSessionCheckinRepository(private val database: Database) {

    data class CheckinRow(
        val userId: String,
        val nickname: String,
        val avatarId: String?,
        val status: SocialSessionCheckinStatus,
        val checkedInAt: Instant?,
        val distanceErrorM: Double?,
        val manualByHost: Boolean,
    )

    fun find(sessionId: String, userId: String): CheckinRow? = transaction(database) {
        (SocialSessionCheckinTable innerJoin UserTable)
            .selectAll()
            .where { (SocialSessionCheckinTable.sessionId eq sessionId) and (SocialSessionCheckinTable.userId eq userId) }
            .limit(1)
            .map { it.toCheckinRow() }
            .singleOrNull()
    }

    /** Ready Check(S-37) 목록 -- [participantUserIds]는 호스트+APPROVED 참가자 id 전체. 체크인
     * 행이 아직 없는 사람도 WAITING placeholder로 채워서 보여준다(참가자 목록은 항상 풀로 보여야
     * 하므로). */
    fun findAllForSession(sessionId: String, participantUserIds: List<String>): List<CheckinRow> {
        if (participantUserIds.isEmpty()) return emptyList()
        val existingByUserId = transaction(database) {
            (SocialSessionCheckinTable innerJoin UserTable)
                .selectAll()
                .where { (SocialSessionCheckinTable.sessionId eq sessionId) and (SocialSessionCheckinTable.userId inList participantUserIds) }
                .map { it.toCheckinRow() }
        }.associateBy { it.userId }

        val missingUserIds = participantUserIds.filterNot { it in existingByUserId }
        val placeholders = if (missingUserIds.isEmpty()) {
            emptyMap()
        } else {
            transaction(database) {
                UserTable.selectAll().where { UserTable.id inList missingUserIds }
                    .associate {
                        it[UserTable.id] to CheckinRow(
                            userId = it[UserTable.id],
                            nickname = it[UserTable.nickname] ?: "달림이",
                            avatarId = it[UserTable.avatarId],
                            status = SocialSessionCheckinStatus.WAITING,
                            checkedInAt = null,
                            distanceErrorM = null,
                            manualByHost = false,
                        )
                    }
            }
        }

        return participantUserIds.mapNotNull { existingByUserId[it] ?: placeholders[it] }
    }

    /** 자가 GPS 체크인(S-36) 성공 시 upsert. */
    open fun upsertSelfCheckin(sessionId: String, userId: String, status: SocialSessionCheckinStatus, checkedInAt: Instant, distanceErrorM: Double) {
        transaction(database) {
            if (existsRow(sessionId, userId)) {
                SocialSessionCheckinTable.update({
                    (SocialSessionCheckinTable.sessionId eq sessionId) and (SocialSessionCheckinTable.userId eq userId)
                }) {
                    it[SocialSessionCheckinTable.status] = status
                    it[SocialSessionCheckinTable.checkedInAt] = checkedInAt
                    it[SocialSessionCheckinTable.distanceErrorM] = distanceErrorM
                    it[SocialSessionCheckinTable.manualByHost] = false
                }
            } else {
                SocialSessionCheckinTable.insert {
                    it[SocialSessionCheckinTable.sessionId] = sessionId
                    it[SocialSessionCheckinTable.userId] = userId
                    it[SocialSessionCheckinTable.status] = status
                    it[SocialSessionCheckinTable.checkedInAt] = checkedInAt
                    it[SocialSessionCheckinTable.distanceErrorM] = distanceErrorM
                    it[SocialSessionCheckinTable.manualByHost] = false
                }
            }
        }
    }

    /** 호스트 수동 확인(S-36 예외 처리 -- GPS 오차/실내 집결 대비 필수 버튼, NO_SHOW 오판정 사후
     * 이의제기 해결 경로도 이걸로 겸한다). */
    open fun manualConfirm(sessionId: String, userId: String, confirmedAt: Instant) {
        transaction(database) {
            if (existsRow(sessionId, userId)) {
                SocialSessionCheckinTable.update({
                    (SocialSessionCheckinTable.sessionId eq sessionId) and (SocialSessionCheckinTable.userId eq userId)
                }) {
                    it[SocialSessionCheckinTable.status] = SocialSessionCheckinStatus.CHECKED_IN
                    it[SocialSessionCheckinTable.checkedInAt] = confirmedAt
                    it[SocialSessionCheckinTable.manualByHost] = true
                }
            } else {
                SocialSessionCheckinTable.insert {
                    it[SocialSessionCheckinTable.sessionId] = sessionId
                    it[SocialSessionCheckinTable.userId] = userId
                    it[SocialSessionCheckinTable.status] = SocialSessionCheckinStatus.CHECKED_IN
                    it[SocialSessionCheckinTable.checkedInAt] = confirmedAt
                    it[SocialSessionCheckinTable.manualByHost] = true
                }
            }
        }
    }

    /** /start(S-37) -- [participantUserIds] 중 아직 WAITING(행이 없는 경우 포함)인 사람을 전부
     * NO_SHOW로 일괄 전환한다. 이미 CHECKED_IN/LATE/NO_SHOW로 결정된 사람은 그대로 둔다. */
    fun markNoShowIfWaiting(sessionId: String, participantUserIds: List<String>) {
        if (participantUserIds.isEmpty()) return
        transaction(database) {
            val decidedUserIds = SocialSessionCheckinTable.selectAll()
                .where {
                    (SocialSessionCheckinTable.sessionId eq sessionId) and
                        (SocialSessionCheckinTable.userId inList participantUserIds) and
                        (SocialSessionCheckinTable.status neq SocialSessionCheckinStatus.WAITING)
                }
                .map { it[SocialSessionCheckinTable.userId] }
                .toSet()

            val existingUserIds = SocialSessionCheckinTable.selectAll()
                .where { (SocialSessionCheckinTable.sessionId eq sessionId) and (SocialSessionCheckinTable.userId inList participantUserIds) }
                .map { it[SocialSessionCheckinTable.userId] }
                .toSet()

            (participantUserIds.toSet() - decidedUserIds).forEach { userId ->
                if (userId in existingUserIds) {
                    SocialSessionCheckinTable.update({
                        (SocialSessionCheckinTable.sessionId eq sessionId) and (SocialSessionCheckinTable.userId eq userId)
                    }) {
                        it[SocialSessionCheckinTable.status] = SocialSessionCheckinStatus.NO_SHOW
                    }
                } else {
                    SocialSessionCheckinTable.insert {
                        it[SocialSessionCheckinTable.sessionId] = sessionId
                        it[SocialSessionCheckinTable.userId] = userId
                        it[SocialSessionCheckinTable.status] = SocialSessionCheckinStatus.NO_SHOW
                    }
                }
            }
        }
    }

    private fun existsRow(sessionId: String, userId: String): Boolean =
        SocialSessionCheckinTable.selectAll()
            .where { (SocialSessionCheckinTable.sessionId eq sessionId) and (SocialSessionCheckinTable.userId eq userId) }
            .limit(1)
            .count() > 0

    private fun ResultRow.toCheckinRow() = CheckinRow(
        userId = this[SocialSessionCheckinTable.userId],
        nickname = this[UserTable.nickname] ?: "달림이",
        avatarId = this[UserTable.avatarId],
        status = this[SocialSessionCheckinTable.status],
        checkedInAt = this[SocialSessionCheckinTable.checkedInAt],
        distanceErrorM = this[SocialSessionCheckinTable.distanceErrorM],
        manualByHost = this[SocialSessionCheckinTable.manualByHost],
    )
}
