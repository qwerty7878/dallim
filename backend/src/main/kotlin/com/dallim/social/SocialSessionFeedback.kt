package com.dallim.social

import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 사람 평가용 긍정 태그 전용 어휘집(S-38) — com.dallim.run.RouteFeedbackTags와 동일 원칙(별점
 * 없음, 부정 태그 절대 금지 -- 불만은 com.dallim.social.SocialSessionChatService의 신고 경로로만
 * 처리한다). 코스 평가(RouteFeedbackTags)와는 대상이 사람이라 어휘가 완전히 다르므로 별도로 둔다.
 */
object SocialFeedbackTags {
    val ALLOWED: Set<String> = setOf(
        "시간을 잘 지켜요",
        "페이스를 잘 맞춰줘요",
        "안전하게 달렸어요",
        "친절했어요",
        "분위기를 즐겁게 만들어요",
        "코스 정보를 잘 알려줬어요",
    )
    const val MAX_TAGS_PER_SUBMISSION = 3
}

/**
 * social_session_feedbacks — (session_id, from_user_id, to_user_id) 복합 PK 자체가 "재제출은
 * 덮어쓰기" 유니크 제약이다(com.dallim.social.SocialSessionApplicantTable과 동일 컨벤션).
 * 체크인한(CHECKED_IN/LATE) 참가자끼리만 오갈 수 있다 -- SocialSessionFeedbackService 참고.
 */
object SocialSessionFeedbackTable : Table("social_session_feedbacks") {
    val sessionId = varchar("session_id", 32).references(SocialSessionTable.id)
    val fromUserId = varchar("from_user_id", 32).references(UserTable.id)
    val toUserId = varchar("to_user_id", 32).references(UserTable.id)

    val tags = varchar("tags", 200).nullable() // 콤마 구분, 빈 선택은 null로 저장
    val wantsToRunAgain = bool("wants_to_run_again").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(sessionId, fromUserId, toUserId)
}
