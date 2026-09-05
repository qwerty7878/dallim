package com.dallim.racerecord

import com.dallim.user.UserTable
import kotlinx.serialization.SerialName
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 러닝 커리어(완주 이력) 도메인 — docs/02-api-spec.md 15장,
 * docs/달림_화면별_상세기획서_v1.3.md PART 3-A "S-04b 러닝 커리어 입력" / PART 3-H
 * "S-82 내 대회(메달 선반)" / "S-83 완주 이력 등록/편집" 근거.
 *
 * 이번 라운드는 완주 이력 CRUD + PB 계산 + 페이스 제안만 구현한다 — 대회 캘린더(S-80/81),
 * 코스 미리달리기(S-85), 훈련플랜(S-86), 기록 인증(S-84)은 범위 밖(SPEC에 없어 보류).
 *
 * 자기신고 이력이므로 [RaceRecordTable.verified]는 이번 라운드에서 항상 false로 저장되고,
 * 승격 경로(운영 검토 → 인증)가 없다 — 응답에는 항상 `verified: false`가 명시된다
 * (CLAUDE.md 공통 규칙 2 인근의 신뢰 원칙과 별개로, 향후 배지/랭킹에서 함부로 신뢰 소스로
 * 쓰이지 않도록 하기 위함, 작업 브리핑 "절대 규칙" 참고).
 */
enum class RaceCategory {
    @SerialName("5K") FIVE_K,
    @SerialName("10K") TEN_K,
    HALF,
    FULL,
    ULTRA,
    TRAIL,
    OTHER,
}

/** hh:mm:ss 기록의 측정 기준 — 넷(출발선 통과 기준)/그로스(총) 타임. */
enum class RecordType {
    NET,
    GROSS,
}

object RaceRecordTable : Table("race_records") {
    val id = varchar("id", 32)
    val userId = varchar("user_id", 32).references(UserTable.id)

    val raceName = varchar("race_name", 100)
    val category = enumerationByName("category", 16, RaceCategory::class)

    // OTHER는 필수 입력(양수), 5K/10K/HALF/FULL은 카테고리로부터 유추한 값을 명시적으로 저장하되
    // 클라이언트가 보내면 그 값을 그대로 신뢰한다(공인 기록이 표기 거리와 정확히 안 맞는 경우 대응).
    // ULTRA/TRAIL은 대회마다 거리가 제각각이라 고정값이 없으므로 입력 시에만 저장한다.
    // — RaceRecordService.normalizeDistanceKm 참고.
    val distanceKm = double("distance_km").nullable()

    val year = integer("year")
    val recordSeconds = integer("record_seconds").nullable() // hh:mm:ss를 초 단위로 저장
    val recordType = enumerationByName("record_type", 16, RecordType::class).nullable()
    val bibNumber = varchar("bib_number", 32).nullable()
    val memo = text("memo").nullable()

    // 이번 라운드는 자기신고 전용 — 인증 승격 경로 없음(S-84는 범위 밖). 항상 false로 저장된다.
    val verified = bool("verified").default(false)

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId)
    }
}

data class RaceRecord(
    val id: String,
    val userId: String,
    val raceName: String,
    val category: RaceCategory,
    val distanceKm: Double?,
    val year: Int,
    val recordSeconds: Int?,
    val recordType: RecordType?,
    val bibNumber: String?,
    val memo: String?,
    val verified: Boolean,
    val createdAt: Instant,
)
