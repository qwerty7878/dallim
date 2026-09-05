package com.dallim.app.career

/**
 * S-04b/S-90 공용 종목 옵션 — docs/02-api-spec.md 15.1/15.3, docs/달림_화면별_상세기획서_v1.3.md
 * PART 3-A/3-H. `apiValue`는 백엔드 `RaceCategory` enum의 직렬화 이름과 정확히 일치해야 한다
 * (`5K`/`10K`는 숫자로 시작해 백엔드가 `@SerialName`으로 별도 매핑한 값 — backend
 * `RaceRecord.kt` 참고).
 *
 * [fixedDistanceKm]은 15.3 "거리 생략 시 카테고리 기준 고정값" 표를 그대로 옮긴 값으로, UI에서
 * "약 21.1km"처럼 보여주는 용도로만 쓴다(실제 값은 항상 서버가 채우거나 신뢰한다). `OTHER`만
 * [requiresDistanceInput]이 true — 15.3: "OTHER는 필수(양수), 그 외는 생략 가능".
 */
enum class RaceCategoryOption(val apiValue: String, val label: String, val fixedDistanceKm: Double?) {
    FIVE_K("5K", "5K", 5.0),
    TEN_K("10K", "10K", 10.0),
    HALF("HALF", "하프", 21.0975),
    FULL("FULL", "풀", 42.195),
    ULTRA("ULTRA", "울트라", null),
    TRAIL("TRAIL", "트레일", null),
    OTHER("OTHER", "기타", null),
    ;

    val requiresDistanceInput: Boolean get() = this == OTHER

    /** 15.6: 페이스 제안은 하프/풀 기록에서만 가능. */
    val supportsPaceSuggestion: Boolean get() = this == HALF || this == FULL
}

/** hh:mm:ss 기록의 측정 기준 — docs/02-api-spec.md 15.1 `RecordType` enum과 동일한 값 세트. */
enum class RecordTypeOption(val apiValue: String, val label: String) {
    NET("NET", "넷 (출발선 통과 기준)"),
    GROSS("GROSS", "그로스 (총 시간)"),
}
