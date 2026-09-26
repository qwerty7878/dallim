package com.dallim.trainingplan

import com.dallim.race.RaceCategory
import com.dallim.route.SketchRouteTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * 훈련 플랜(S-86) 영속성 — docs/02-api-spec.md 19장. `training_plan_sessions`의 실제 생성/갱신은
 * 이 리포지토리가 아니라 워커(`worker/trainingplan_db.py`, psycopg2 raw SQL)가 한다 — Kotlin은
 * `training_plans` 행의 생성/상태 리셋과, READY가 된 뒤의 조회만 담당한다.
 */
class TrainingPlanRepository(private val database: Database) {

    fun findByUserAndRace(userId: String, raceId: String): TrainingPlan? = transaction(database) {
        TrainingPlanTable.selectAll()
            .where { (TrainingPlanTable.userId eq userId) and (TrainingPlanTable.raceId eq raceId) }
            .limit(1)
            .map { it.toTrainingPlan() }
            .singleOrNull()
    }

    fun findById(planId: String): TrainingPlan? = transaction(database) {
        TrainingPlanTable.selectAll()
            .where { TrainingPlanTable.id eq planId }
            .limit(1)
            .map { it.toTrainingPlan() }
            .singleOrNull()
    }

    /** 신규 PENDING 행 생성 — 호출 전 (userId, raceId)에 기존 행이 없음을 서비스가 이미 확인했다. */
    fun createPending(id: String, userId: String, raceId: String, category: RaceCategory): TrainingPlan {
        val now = Instant.now()
        transaction(database) {
            TrainingPlanTable.insert {
                it[TrainingPlanTable.id] = id
                it[TrainingPlanTable.userId] = userId
                it[TrainingPlanTable.raceId] = raceId
                it[TrainingPlanTable.category] = category
                it[status] = TrainingPlanStatus.PENDING
                it[comment] = null
                it[errorMessage] = null
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        return TrainingPlan(id, userId, raceId, category, TrainingPlanStatus.PENDING, null, null, now, now)
    }

    /** FAILED 상태였던 행을 재시도용으로 PENDING으로 되돌린다(중복 행 생성 방지, 작업 브리핑 지시). */
    fun resetToPending(planId: String, category: RaceCategory) {
        transaction(database) {
            TrainingPlanTable.update({ TrainingPlanTable.id eq planId }) {
                it[TrainingPlanTable.category] = category
                it[status] = TrainingPlanStatus.PENDING
                it[comment] = null
                it[errorMessage] = null
                it[updatedAt] = Instant.now()
            }
        }
    }

    /** GET 응답용 — READY 상태에서만 의미 있는 조회지만 상태 체크는 서비스 책임. 주차/세션
     * 순서(weekNumber, sessionIndex) 오름차순, 매칭 코스는 left join(REST/미매칭은 전부 null). */
    fun findSessionsByPlan(planId: String): List<TrainingPlanSession> = transaction(database) {
        (TrainingPlanSessionTable leftJoin SketchRouteTable)
            .selectAll()
            .where { TrainingPlanSessionTable.planId eq planId }
            .orderBy(
                TrainingPlanSessionTable.weekNumber to SortOrder.ASC,
                TrainingPlanSessionTable.sessionIndex to SortOrder.ASC,
            )
            .map { it.toTrainingPlanSession() }
    }

    private fun ResultRow.toTrainingPlan() = TrainingPlan(
        id = this[TrainingPlanTable.id],
        userId = this[TrainingPlanTable.userId],
        raceId = this[TrainingPlanTable.raceId],
        category = this[TrainingPlanTable.category],
        status = this[TrainingPlanTable.status],
        comment = this[TrainingPlanTable.comment],
        errorMessage = this[TrainingPlanTable.errorMessage],
        createdAt = this[TrainingPlanTable.createdAt],
        updatedAt = this[TrainingPlanTable.updatedAt],
    )

    private fun ResultRow.toTrainingPlanSession() = TrainingPlanSession(
        id = this[TrainingPlanSessionTable.id],
        planId = this[TrainingPlanSessionTable.planId],
        weekNumber = this[TrainingPlanSessionTable.weekNumber],
        sessionIndex = this[TrainingPlanSessionTable.sessionIndex],
        type = this[TrainingPlanSessionTable.type],
        targetDistanceKm = this[TrainingPlanSessionTable.targetDistanceKm],
        matchedRouteId = this[TrainingPlanSessionTable.matchedRouteId],
        matchedRouteName = this.getOrNull(SketchRouteTable.name),
        matchedRouteDistanceKm = this.getOrNull(SketchRouteTable.distanceKm),
    )
}
