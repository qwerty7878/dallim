package com.dallim.social

import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * S-39 Running Mate — 한 세션에서 서로 "다시 같이 뛰고 싶어요"(wantsToRunAgain)를 선택한 두
 * 사용자가 성립한다(com.dallim.social.SocialSessionFeedbackService.submit). `userIdA < userIdB`
 * 로 항상 정렬해 저장해 (a,b)/(b,a) 중복을 막는다 -- 서비스 계층에서 정렬 후 저장하고, DB의
 * CHECK 제약(V18 마이그레이션)으로 이중 방어한다.
 *
 * 팔로우가 아닌 상호 동의 기반이므로 해제(DELETE /users/me/running-mates/{mateUserId})는
 * 조용히 단방향이다 -- hiddenByA/hiddenByB 중 요청한 쪽만 true로 바뀌고, 상대방에게는 아무
 * 변화도/알림도 없다(SPEC 명시).
 */
object RunningMateTable : Table("running_mates") {
    val userIdA = varchar("user_id_a", 32).references(UserTable.id)
    val userIdB = varchar("user_id_b", 32).references(UserTable.id)
    val runTogetherCount = integer("run_together_count").default(1)
    val lastRunTogetherAt = timestamp("last_run_together_at")
    val hiddenByA = bool("hidden_by_a").default(false)
    val hiddenByB = bool("hidden_by_b").default(false)

    override val primaryKey = PrimaryKey(userIdA, userIdB)
}
