package com.dallim.race

import com.dallim.common.IdGenerator
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * 대회 캘린더 영속성 — docs/02-api-spec.md 16장. PostGIS와 무관한 평범한 Exposed DSL이라
 * com.dallim.meetup.MeetupRepository / com.dallim.racerecord.RaceRecordRepository와 같은 스타일.
 *
 * 종목(카테고리) 필터/배지/참가비 범위는 SQL GROUP BY가 아니라 Kotlin에서 계산한다 — "수동
 * 큐레이션 + 연간 관리 가능한 규모"(작업 브리핑, docs 677행)라 대회 수가 적어 N+1이나 성능
 * 문제가 나지 않는다(com.dallim.route.RouteService.tallyShapeVotes와 동일한 관례).
 */
class RaceRepository(private val database: Database) {

    fun findAll(region: String?): List<Race> = transaction(database) {
        var query = RaceTable.selectAll()
        if (region != null) {
            // 대소문자 무시 + 부분 일치("서울" -> "서울 송파구"도 매치) — 작업 브리핑의
            // "문자열 매칭" 지시를 그대로 구현한 것.
            query = query.andWhere { RaceTable.region.lowerCase() like "%${region.lowercase()}%" }
        }
        query.map { it.toRace() }
    }

    fun findById(raceId: String): Race? = transaction(database) {
        RaceTable.selectAll()
            .where { RaceTable.id eq raceId }
            .limit(1)
            .map { it.toRace() }
            .singleOrNull()
    }

    fun exists(raceId: String): Boolean = transaction(database) {
        RaceTable.selectAll().where { RaceTable.id eq raceId }.limit(1).count() > 0
    }

    fun findCategoriesByRace(raceId: String): List<RaceCategoryOption> = transaction(database) {
        RaceCategoryOptionTable.selectAll()
            .where { RaceCategoryOptionTable.raceId eq raceId }
            .map { it.toRaceCategoryOption() }
    }

    /** 배치 조회(N+1 방지) — GET /races 목록에서 대회마다 한 번씩 쿼리하지 않도록. */
    fun findCategoriesByRaces(raceIds: List<String>): Map<String, List<RaceCategoryOption>> {
        if (raceIds.isEmpty()) return emptyMap()
        return transaction(database) {
            RaceCategoryOptionTable.selectAll()
                .where { RaceCategoryOptionTable.raceId inList raceIds }
                .map { it.toRaceCategoryOption() }
                .groupBy { it.raceId }
        }
    }

    fun countSaved(raceId: String): Int = transaction(database) {
        RaceSaveTable.selectAll().where { RaceSaveTable.raceId eq raceId }.count().toInt()
    }

    fun countSavedByRaces(raceIds: List<String>): Map<String, Int> {
        if (raceIds.isEmpty()) return emptyMap()
        return transaction(database) {
            RaceSaveTable.selectAll()
                .where { RaceSaveTable.raceId inList raceIds }
                .map { it[RaceSaveTable.raceId] }
                .groupingBy { it }
                .eachCount()
        }
    }

    fun isSaved(userId: String, raceId: String): Boolean = transaction(database) {
        RaceSaveTable.selectAll()
            .where { (RaceSaveTable.userId eq userId) and (RaceSaveTable.raceId eq raceId) }
            .limit(1)
            .count() > 0
    }

    fun findSavedRaceIds(userId: String, raceIds: List<String>): Set<String> {
        if (raceIds.isEmpty()) return emptySet()
        return transaction(database) {
            RaceSaveTable.select(RaceSaveTable.raceId)
                .where { (RaceSaveTable.userId eq userId) and (RaceSaveTable.raceId inList raceIds) }
                .mapTo(mutableSetOf()) { it[RaceSaveTable.raceId] }
        }
    }

    /** 담기 — 이미 담았으면 no-op(com.dallim.user.SavedRouteRepository.save와 동일한 관례). */
    fun save(userId: String, raceId: String) {
        transaction(database) {
            val alreadySaved = RaceSaveTable.selectAll()
                .where { (RaceSaveTable.userId eq userId) and (RaceSaveTable.raceId eq raceId) }
                .limit(1)
                .count() > 0

            if (!alreadySaved) {
                RaceSaveTable.insert {
                    it[RaceSaveTable.id] = IdGenerator.raceSave()
                    it[RaceSaveTable.userId] = userId
                    it[RaceSaveTable.raceId] = raceId
                }
            }
        }
    }

    /** 담기 취소 — 담지 않은 상태에서 호출해도 no-op(idempotent). */
    fun unsave(userId: String, raceId: String) {
        transaction(database) {
            RaceSaveTable.deleteWhere {
                it.run { (RaceSaveTable.userId eq userId) and (RaceSaveTable.raceId eq raceId) }
            }
        }
    }

    /** GET /users/me/races — 대회 날짜(raceDate) 임박순. */
    fun findSavedRacesForUser(userId: String): List<Race> = transaction(database) {
        (RaceSaveTable innerJoin RaceTable)
            .selectAll()
            .where { RaceSaveTable.userId eq userId }
            .orderBy(RaceTable.raceDate, SortOrder.ASC)
            .map { it.toRace() }
    }

    private fun ResultRow.toRace() = Race(
        id = this[RaceTable.id],
        name = this[RaceTable.name],
        region = this[RaceTable.region],
        location = this[RaceTable.location],
        raceDate = this[RaceTable.raceDate],
        registrationStart = this[RaceTable.registrationStart],
        registrationEnd = this[RaceTable.registrationEnd],
        organizer = this[RaceTable.organizer],
        souvenir = this[RaceTable.souvenir],
        createdAt = this[RaceTable.createdAt],
    )

    private fun ResultRow.toRaceCategoryOption() = RaceCategoryOption(
        id = this[RaceCategoryOptionTable.id],
        raceId = this[RaceCategoryOptionTable.raceId],
        category = this[RaceCategoryOptionTable.category],
        distanceKm = this[RaceCategoryOptionTable.distanceKm],
        feeKrw = this[RaceCategoryOptionTable.feeKrw],
        capacity = this[RaceCategoryOptionTable.capacity],
        cutoffMinutes = this[RaceCategoryOptionTable.cutoffMinutes],
    )
}
