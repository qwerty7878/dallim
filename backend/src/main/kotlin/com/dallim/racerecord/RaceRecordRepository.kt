package com.dallim.racerecord

import com.dallim.common.IdGenerator
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * 완주 이력 영속성 — docs/02-api-spec.md 15장. PostGIS 없는 평범한 Exposed DSL이라
 * com.dallim.meetup.MeetupRepository와 동일한 스타일. PB 계산은 여기서 하지 않고
 * RaceRecordService가 [findAllByUser] 결과 전체를 훑어 매 조회 시 계산한다(사용자당 이력
 * 수가 적어 성능 문제 없음 — 작업 브리핑 지시).
 */
class RaceRecordRepository(private val database: Database) {

    fun create(
        userId: String,
        raceName: String,
        category: RaceCategory,
        distanceKm: Double?,
        year: Int,
        recordSeconds: Int?,
        recordType: RecordType?,
        bibNumber: String?,
        memo: String?,
    ): String {
        val id = IdGenerator.raceRecord()
        transaction(database) {
            RaceRecordTable.insert {
                it[RaceRecordTable.id] = id
                it[RaceRecordTable.userId] = userId
                it[RaceRecordTable.raceName] = raceName
                it[RaceRecordTable.category] = category
                it[RaceRecordTable.distanceKm] = distanceKm
                it[RaceRecordTable.year] = year
                it[RaceRecordTable.recordSeconds] = recordSeconds
                it[RaceRecordTable.recordType] = recordType
                it[RaceRecordTable.bibNumber] = bibNumber
                it[RaceRecordTable.memo] = memo
                it[RaceRecordTable.verified] = false
            }
        }
        return id
    }

    /** 연도 내림차순, 동일 연도 내에서는 최신 등록순 — GET /users/me/race-records (15.1). */
    fun findAllByUser(userId: String): List<RaceRecord> = transaction(database) {
        RaceRecordTable.selectAll()
            .where { RaceRecordTable.userId eq userId }
            .orderBy(RaceRecordTable.year to SortOrder.DESC, RaceRecordTable.createdAt to SortOrder.DESC)
            .map { it.toRaceRecord() }
    }

    fun findById(id: String): RaceRecord? = transaction(database) {
        RaceRecordTable.selectAll()
            .where { RaceRecordTable.id eq id }
            .limit(1)
            .map { it.toRaceRecord() }
            .singleOrNull()
    }

    fun update(
        id: String,
        raceName: String,
        category: RaceCategory,
        distanceKm: Double?,
        year: Int,
        recordSeconds: Int?,
        recordType: RecordType?,
        bibNumber: String?,
        memo: String?,
    ) {
        transaction(database) {
            RaceRecordTable.update({ RaceRecordTable.id eq id }) {
                it[RaceRecordTable.raceName] = raceName
                it[RaceRecordTable.category] = category
                it[RaceRecordTable.distanceKm] = distanceKm
                it[RaceRecordTable.year] = year
                it[RaceRecordTable.recordSeconds] = recordSeconds
                it[RaceRecordTable.recordType] = recordType
                it[RaceRecordTable.bibNumber] = bibNumber
                it[RaceRecordTable.memo] = memo
            }
        }
    }

    fun delete(id: String): Boolean = transaction(database) {
        RaceRecordTable.deleteWhere { it.run { RaceRecordTable.id eq id } } > 0
    }

    private fun ResultRow.toRaceRecord() = RaceRecord(
        id = this[RaceRecordTable.id],
        userId = this[RaceRecordTable.userId],
        raceName = this[RaceRecordTable.raceName],
        category = this[RaceRecordTable.category],
        distanceKm = this[RaceRecordTable.distanceKm],
        year = this[RaceRecordTable.year],
        recordSeconds = this[RaceRecordTable.recordSeconds],
        recordType = this[RaceRecordTable.recordType],
        bibNumber = this[RaceRecordTable.bibNumber],
        memo = this[RaceRecordTable.memo],
        verified = this[RaceRecordTable.verified],
        createdAt = this[RaceRecordTable.createdAt],
    )
}
