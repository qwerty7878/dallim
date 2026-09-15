package com.dallim.social

import com.dallim.common.IdGenerator
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * social_session_chat_messages / content_reports persistence — docs/02-api-spec.md 17장(S-35).
 * Plain Exposed DSL throughout (no PostGIS columns here), same convention as
 * com.dallim.social.SocialSessionRepository.
 */
open class SocialSessionChatRepository(private val database: Database) {

    data class ChatMessageRow(
        val id: String,
        val sessionId: String,
        val senderUserId: String?,
        val senderNickname: String?,
        val senderAvatarId: String?,
        val type: ChatMessageType,
        val body: String,
        val createdAt: Instant,
    )

    /** WebSocket 수신 메시지 저장 + 브로드캐스트용 발신자 프로필 조회를 한 트랜잭션에서 처리한다.
     * [senderUserId]가 null이면 SYSTEM 메시지(닉네임/아바타 둘 다 null로 반환). */
    open fun insert(sessionId: String, senderUserId: String?, type: ChatMessageType, body: String): ChatMessageRow {
        val id = IdGenerator.socialChatMessage()
        val createdAt = Instant.now()
        var senderNickname: String? = null
        var senderAvatarId: String? = null

        transaction(database) {
            SocialSessionChatMessageTable.insert {
                it[SocialSessionChatMessageTable.id] = id
                it[SocialSessionChatMessageTable.sessionId] = sessionId
                it[SocialSessionChatMessageTable.senderUserId] = senderUserId
                it[SocialSessionChatMessageTable.type] = type
                it[SocialSessionChatMessageTable.body] = body
                it[SocialSessionChatMessageTable.createdAt] = createdAt
            }
            if (senderUserId != null) {
                UserTable.selectAll().where { UserTable.id eq senderUserId }.singleOrNull()?.let {
                    senderNickname = it[UserTable.nickname] ?: "달림이"
                    senderAvatarId = it[UserTable.avatarId]
                }
            }
        }
        return ChatMessageRow(id, sessionId, senderUserId, senderNickname, senderAvatarId, type, body, createdAt)
    }

    /** GET .../chat/messages — 최신 순 페이지네이션. */
    fun findPage(sessionId: String, page: Int, size: Int): Pair<List<ChatMessageRow>, Int> = transaction(database) {
        fun base() = (SocialSessionChatMessageTable leftJoin UserTable)
            .selectAll()
            .where { SocialSessionChatMessageTable.sessionId eq sessionId }

        val totalCount = base().count().toInt()
        val items = base()
            .orderBy(SocialSessionChatMessageTable.createdAt, SortOrder.DESC)
            .limit(size)
            .offset(page.toLong() * size)
            .map { it.toChatMessageRow() }
        items to totalCount
    }

    fun findById(sessionId: String, messageId: String): ChatMessageRow? = transaction(database) {
        (SocialSessionChatMessageTable leftJoin UserTable)
            .selectAll()
            .where { (SocialSessionChatMessageTable.id eq messageId) and (SocialSessionChatMessageTable.sessionId eq sessionId) }
            .limit(1)
            .map { it.toChatMessageRow() }
            .singleOrNull()
    }

    /** @return 생성된 report id -- 호출자가 [com.dallim.moderation.ReportTriageQueue]에 그대로 발행한다. */
    open fun report(reporterUserId: String, targetType: ContentReportTargetType, targetId: String, reason: String?): String {
        val id = IdGenerator.contentReport()
        transaction(database) {
            ContentReportTable.insert {
                it[ContentReportTable.id] = id
                it[ContentReportTable.reporterUserId] = reporterUserId
                it[ContentReportTable.targetType] = targetType
                it[ContentReportTable.targetId] = targetId
                it[ContentReportTable.reason] = reason
            }
        }
        return id
    }

    private fun ResultRow.toChatMessageRow(): ChatMessageRow {
        val senderUserId = this[SocialSessionChatMessageTable.senderUserId]
        return ChatMessageRow(
            id = this[SocialSessionChatMessageTable.id],
            sessionId = this[SocialSessionChatMessageTable.sessionId],
            senderUserId = senderUserId,
            senderNickname = senderUserId?.let { this[UserTable.nickname] ?: "달림이" },
            senderAvatarId = senderUserId?.let { this[UserTable.avatarId] },
            type = this[SocialSessionChatMessageTable.type],
            body = this[SocialSessionChatMessageTable.body],
            createdAt = this[SocialSessionChatMessageTable.createdAt],
        )
    }
}
