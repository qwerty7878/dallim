package com.dallim.racerecord

import kotlin.math.pow

/**
 * "편안한 페이스" 등급 — docs/02-api-spec.md 2장 `comfortablePace` 값과 동일한 5단계
 * (android/app ProfileOptions.kt의 ComfortablePace enum과 이름/apiValue 모두 일치시켰다).
 * 분/km 경계값은 android 쪽 라벨("5'00" 이하" 등)에서 역산한 것으로 SPEC 문서 자체에 초 단위
 * 표는 없다.
 */
enum class ComfortablePaceBucket(val apiValue: String) {
    UNDER_5("PACE_UNDER_5"), // 5:00/km 이하 (더 빠름)
    PACE_5_6("PACE_5_6"), // 5:00 ~ 6:00/km
    PACE_6_7("PACE_6_7"), // 6:00 ~ 7:00/km
    PACE_7_8("PACE_7_8"), // 7:00 ~ 8:00/km
    OVER_8("PACE_OVER_8"), // 8:00/km 이상 (더 느림)
    ;

    companion object {
        fun fromSecondsPerKm(secPerKm: Double): ComfortablePaceBucket = when {
            secPerKm <= 300.0 -> UNDER_5
            secPerKm <= 360.0 -> PACE_5_6
            secPerKm <= 420.0 -> PACE_6_7
            secPerKm <= 480.0 -> PACE_7_8
            else -> OVER_8
        }
    }
}

/**
 * 하프/풀 완주 기록 → "편안한 페이스" 제안 — docs/달림_화면별_상세기획서_v1.3.md S-04b / S-83
 * "자동 처리: 기록 → 예상 페이스 환산 → 프로필 `편안한 페이스` 갱신 제안".
 *
 * SPEC에는 정확한 계산식이 없어 다음과 같이 근사했다(작업 브리핑 지시대로 채택 근거를 남김):
 *
 * 1. Riegel 공식(T2 = T1 × (D2/D1)^1.06)으로 입력 기록(하프 또는 풀)을 "풀코스 상당 기록"으로
 *    투영한다. 하프/풀 두 입력을 하나의 기준 거리(풀코스)로 정규화해 같은 로직을 태우기 위함이다.
 * 2. 그 풀코스 상당 페이스(초/km)에 보정 배율 [EASY_PACE_CORRECTION](1.15)을 곱해 "편안한
 *    러닝 페이스"로 늦춘다 — 대회 기록은 전력(레이스 페이스)이고, 일상적으로 편하게 뛰는 페이스는
 *    그보다 확연히 느리기 때문. 1.15~1.2배 범위 중 보수적인 쪽(1.15)을 택했다: 이 값은
 *    "쉬운 조깅 페이스가 마라톤 레이스 페이스보다 몇 % 느린가"에 대한 러닝 코칭 통념(대략
 *    10~20% 느림)에 맞춘 근사치이며, SPEC에 명시된 값이 아니다.
 * 3. 위에서 나온 초/km 값을 [ComfortablePaceBucket] 5단계 중 하나로 매핑한다.
 *
 * 순수 함수 — DB/네트워크 의존성 없음. RunJudgementService와 동일한 이유로 단위 테스트가
 * 쉬운 형태를 유지한다(qa-engineer가 다음 라운드에 검증할 것).
 */
object PaceSuggestionCalculator {
    private const val HALF_KM = 21.0975
    private const val FULL_KM = 42.195
    private const val RIEGEL_EXPONENT = 1.06

    // SPEC에 정확한 배율이 없어 임의로 채택한 보정값 — 클래스 doc 3번 항목 참고.
    private const val EASY_PACE_CORRECTION = 1.15

    /** HALF/FULL 이외의 종목은 대회 페이스 자체가 기준 거리와 무관해 제안 대상이 아니다(null). */
    fun suggest(category: RaceCategory, recordSeconds: Int): ComfortablePaceBucket? {
        if (recordSeconds <= 0) return null
        val distanceKm = when (category) {
            RaceCategory.HALF -> HALF_KM
            RaceCategory.FULL -> FULL_KM
            else -> return null
        }

        val projectedMarathonSeconds = recordSeconds * (FULL_KM / distanceKm).pow(RIEGEL_EXPONENT)
        val marathonPaceSecPerKm = projectedMarathonSeconds / FULL_KM
        val easyPaceSecPerKm = marathonPaceSecPerKm * EASY_PACE_CORRECTION
        return ComfortablePaceBucket.fromSecondsPerKm(easyPaceSecPerKm)
    }
}
