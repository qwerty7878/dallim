package com.dallim.social

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * social_session_feedbacks persistence — docs/02-api-spec.md 17장(S-38). Plain Exposed DSL,
 * 재제출은 (session_id, from_user_id, to_user_id) PK 존재 여부로 분기해 update/insert.
 */
open class SocialSessionFeedbackRepository(private val database: Database) {

    data class FeedbackRow(val tags: List<String>, val wantsToRunAgain: Boolean)

    open fun find(sessionId: String, fromUserId: String, toUserId: String): FeedbackRow? = transaction(database) {
        SocialSessionFeedbackTable.selectAll()
            .where {
                (SocialSessionFeedbackTable.sessionId eq sessionId) and
                    (SocialSessionFeedbackTable.fromUserId eq fromUserId) and
                    (SocialSessionFeedbackTable.toUserId eq toUserId)
            }
            .limit(1)
            .map {
                FeedbackRow(
                    tags = it[SocialSessionFeedbackTable.tags]?.split(",")?.filter { tag -> tag.isNotBlank() } ?: emptyList(),
                    wantsToRunAgain = it[SocialSessionFeedbackTable.wantsToRunAgain],
                )
            }
            .singleOrNull()
    }

    /** POST /social-sessions/{id}/feedback — 재제출은 덮어쓰기(update)로 처리. */
    open fun upsert(sessionId: String, fromUserId: String, toUserId: String, tags: List<String>, wantsToRunAgain: Boolean) {
        val tagsValue = tags.takeIf { it.isNotEmpty() }?.joinToString(",")
        transaction(database) {
            val exists = SocialSessionFeedbackTable.selectAll()
                .where {
                    (SocialSessionFeedbackTable.sessionId eq sessionId) and
                        (SocialSessionFeedbackTable.fromUserId eq fromUserId) and
                        (SocialSessionFeedbackTable.toUserId eq toUserId)
                }
                .limit(1)
                .count() > 0

            if (exists) {
                SocialSessionFeedbackTable.update({
                    (SocialSessionFeedbackTable.sessionId eq sessionId) and
                        (SocialSessionFeedbackTable.fromUserId eq fromUserId) and
                        (SocialSessionFeedbackTable.toUserId eq toUserId)
                }) {
                    it[SocialSessionFeedbackTable.tags] = tagsValue
                    it[SocialSessionFeedbackTable.wantsToRunAgain] = wantsToRunAgain
                }
            } else {
                SocialSessionFeedbackTable.insert {
                    it[SocialSessionFeedbackTable.sessionId] = sessionId
                    it[SocialSessionFeedbackTable.fromUserId] = fromUserId
                    it[SocialSessionFeedbackTable.toUserId] = toUserId
                    it[SocialSessionFeedbackTable.tags] = tagsValue
                    it[SocialSessionFeedbackTable.wantsToRunAgain] = wantsToRunAgain
                }
            }
        }
    }
}
