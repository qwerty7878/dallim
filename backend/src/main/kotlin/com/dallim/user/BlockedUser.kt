package com.dallim.user

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 유저 차단 (사용자 지시로 신규 도입, docs/02-api-spec.md 18장) — 스코프는 "채팅 메시지 발신자
 * 차단"으로 좁힌다. 세션 신청/매칭 등 다른 곳에 차단 효과를 전파하지 않는다(이번 라운드 범위
 * 밖 -- 만들지 않는다). 서버는 이 테이블을 CRUD로만 노출하고 채팅 메시지를 직접 필터링하지
 * 않는다 -- 클라이언트가 GET /users/me/blocks 결과로 화면에서 직접 걸러낸다.
 *
 * 복합 PK(blockerUserId, blockedUserId) 자체가 유니크 제약이라 별도 uniqueIndex는 불필요하다
 * (com.dallim.social.SocialSessionApplicantTable과 동일 컨벤션).
 */
object BlockedUserTable : Table("blocked_users") {
    val blockerUserId = varchar("blocker_user_id", 32).references(UserTable.id)
    val blockedUserId = varchar("blocked_user_id", 32).references(UserTable.id)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(blockerUserId, blockedUserId)
}
