"""주차별 훈련 스케줄 골격을 만드는 순수 함수 -- LLM/DB/네트워크 의존성이 전혀 없다.
`backend/.../racerecord/PaceSuggestion.kt`의 `PaceSuggestionCalculator`와 같은 이유로 순수
함수 형태를 유지한다: 단위 테스트가 쉬워야 하고(이번 라운드는 qa-engineer 강제 호출 대상은
아니지만 같은 원칙을 지킨다), LangGraph 노드(`trainingplan_graph.build_plan_skeleton_node`)는
이 함수를 감싸기만 한다.

SPEC(`docs/달림_화면별_상세기획서_v1.3.md` S-86)에는 "주차별 목표 거리는 점진적으로 늘리고
마지막 1~2주는 테이퍼", "세션 구성은 롱런 1회 + 인터벌/템포 1회 + 나머지 휴식"이라고만 적혀
있고 정확한 배율/증가율은 없다. 아래 상수들은 SPEC에 없는 임의 근사치이며, 채택 근거를 각
상수 옆에 남긴다(`PaceSuggestionCalculator`와 동일한 관례).
"""
from typing import Optional, TypedDict


class TrainingSession(TypedDict):
    session_index: int
    type: str  # LONG_RUN | TEMPO | INTERVAL | REST
    target_distance_km: Optional[float]


class TrainingWeek(TypedDict):
    week_number: int  # 1부터 시작, 마지막 주가 대회가 있는 주
    sessions: list[TrainingSession]


# 계획이 다루는 최대 주 수. 대회가 몇 달~몇 년 뒤라도 무한정 긴 계획을 만들지 않는다 -- 일반적인
# 마라톤 훈련 계획이 12~16주인 러닝 코칭 통념에 맞춘 상한(SPEC엔 없음).
MAX_PLAN_WEEKS = 16

# "10% 룰" -- 러닝 코칭에서 통용되는, 주간 훈련량을 전주 대비 10% 넘게 늘리지 않는다는 통념.
# 이 상한 때문에 목표(peak) 주간 거리에 못 미치고 계획이 끝날 수 있는데, 그건 의도된 동작이다
# (부상 방지가 정확히 peak에 도달하는 것보다 우선).
WEEKLY_GROWTH_CAP = 0.10

# 피크 주간 거리 = 목표(대회) 거리 * 이 배율. 대회 거리가 짧을수록(5K/10K) 주간 볼륨을 대회
# 거리의 몇 배로 쌓는 게 자연스럽고, 길어질수록(하프/풀) 그 배율이 낮아진다는 통념을 3단계로
# 단순화했다 -- 정확한 경계값/배율은 SPEC에 없는 근사치.
_PEAK_MULTIPLIER_SHORT = 3.0  # ~10km 이하
_PEAK_MULTIPLIER_MID = 2.2  # ~하프(21.0975km) 근방
_PEAK_MULTIPLIER_LONG = 1.6  # 풀/울트라
_SHORT_DISTANCE_THRESHOLD_KM = 10.0
_MID_DISTANCE_THRESHOLD_KM = 25.0

# 최근 러닝 이력이 없는 유저의 시작 주간 거리 = 목표 거리 * 이 비율(코칭 통념: 완전 무경험자가
# 아니라 "달림 앱을 쓰는 러너"라는 전제로, 대회 거리보다 약간 적은 양부터 시작한다고 가정).
_DEFAULT_START_RATIO_OF_TARGET = 0.6
# 시작 주간 거리가 피크 대비 이 비율을 넘지 않도록 clamp(이미 피크 근처인데 "점진적으로
# 늘린다"는 이름이 무색해지는 것을 방지).
_START_RATIO_OF_PEAK_CAP = 0.5
# 시작 주간 거리 하한(0에 가까운 값으로 계획이 시작되는 것을 방지).
_START_RATIO_OF_TARGET_FLOOR = 0.2

# 세션 구성 -- 작업 브리핑 지시대로 "롱런 1회 + 인터벌/템포 1회 + 나머지 휴식"을 대표 3세션
# 템플릿으로 표현한다(달력 7일을 전부 채우는 게 아니라, 그 주의 핵심 세션만 나열).
_LONG_RUN_SHARE = 0.4
_QUALITY_SHARE = 0.2


def _peak_multiplier(target_distance_km: float) -> float:
    if target_distance_km <= _SHORT_DISTANCE_THRESHOLD_KM:
        return _PEAK_MULTIPLIER_SHORT
    if target_distance_km <= _MID_DISTANCE_THRESHOLD_KM:
        return _PEAK_MULTIPLIER_MID
    return _PEAK_MULTIPLIER_LONG


def _taper_ratios(total_weeks: int) -> list[float]:
    """마지막 N주에 곱할 감량 비율(테이퍼) -- 코칭 통념(대회 직전 훈련량을 40~65%로 줄인다)의
    단순화. 계획이 아주 짧으면(1주) 테이퍼를 따로 떼지 않고 그 한 주 자체를 가볍게 만든다
    (build_weeks의 호출부에서 처리)."""
    if total_weeks >= 4:
        return [0.65, 0.4]
    if total_weeks >= 2:
        return [0.5]
    return []


def _weekly_quality_type(week_number: int) -> str:
    # 매주 인터벌/템포를 번갈아 배치 -- SPEC은 "인터벌/템포 1회"라고만 하고 배분 규칙이 없어
    # 단순 홀짝 교대로 다양성만 준다(임의 근사치).
    return "TEMPO" if week_number % 2 == 1 else "INTERVAL"


def _sessions_for_week(week_number: int, weekly_km: float) -> list[TrainingSession]:
    long_run_km = round(weekly_km * _LONG_RUN_SHARE, 1)
    quality_km = round(weekly_km * _QUALITY_SHARE, 1)
    return [
        {"session_index": 0, "type": "LONG_RUN", "target_distance_km": long_run_km},
        {"session_index": 1, "type": _weekly_quality_type(week_number), "target_distance_km": quality_km},
        {"session_index": 2, "type": "REST", "target_distance_km": None},
    ]


def build_weeks(
    weeks_until_race: int,
    target_distance_km: float,
    recent_avg_weekly_km: Optional[float],
) -> list[TrainingWeek]:
    """D-day까지 남은 주 수와 목표(대회) 거리로 주차별 목표 거리 + 세션 구성을 만든다.

    [weeks_until_race]는 1 미만이어도(이미 지난 대회이거나 이번 주가 대회 주) 최소 1주로
    보정한다 -- 유저가 아무 때나 눌러도 항상 뭔가는 나와야 한다(에러를 던지지 않는다).
    [recent_avg_weekly_km]는 None이면(러닝 이력 없음) 기본값으로 대체한다.
    """
    total_weeks = max(1, min(weeks_until_race, MAX_PLAN_WEEKS))
    peak_weekly_km = target_distance_km * _peak_multiplier(target_distance_km)

    if recent_avg_weekly_km and recent_avg_weekly_km > 0:
        start_weekly_km = recent_avg_weekly_km
    else:
        start_weekly_km = target_distance_km * _DEFAULT_START_RATIO_OF_TARGET
    start_weekly_km = min(start_weekly_km, peak_weekly_km * _START_RATIO_OF_PEAK_CAP)
    start_weekly_km = max(start_weekly_km, target_distance_km * _START_RATIO_OF_TARGET_FLOOR)

    if total_weeks == 1:
        # 빌드 기간도 테이퍼 기간도 없는 "이번 주가 대회 주" 케이스 -- 무리하게 쌓지 않고
        # 가볍게 만든다(코칭 통념: 대회 임박 시 새로 볼륨을 늘리지 않는다).
        weekly_kms = [start_weekly_km * 0.5]
    else:
        taper_ratios = _taper_ratios(total_weeks)
        build_weeks_count = total_weeks - len(taper_ratios)

        build_kms: list[float] = []
        for i in range(build_weeks_count):
            if i == 0:
                value = start_weekly_km
            else:
                # 이론상 도달해야 할 선형 보간값과, "10% 룰" 상한 중 더 작은 쪽을 택한다 --
                # 후자가 우선한다(정확히 피크에 도달하는 것보다 부상 방지가 우선).
                fraction = i / max(build_weeks_count - 1, 1)
                linear_target = start_weekly_km + (peak_weekly_km - start_weekly_km) * fraction
                growth_capped = build_kms[-1] * (1 + WEEKLY_GROWTH_CAP)
                value = max(build_kms[-1], min(linear_target, growth_capped))
            build_kms.append(value)

        achieved_peak = build_kms[-1] if build_kms else start_weekly_km
        weekly_kms = build_kms + [achieved_peak * ratio for ratio in taper_ratios]

    weeks: list[TrainingWeek] = []
    for index, weekly_km in enumerate(weekly_kms):
        week_number = index + 1
        weeks.append({"week_number": week_number, "sessions": _sessions_for_week(week_number, weekly_km)})
    return weeks
