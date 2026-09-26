"""훈련 플랜(S-86) 전용 Postgres 접근. 신고 트리아지의 `db.py`와 커넥션 관리 패턴(전역 커넥션
하나, 끊기면 1회 재연결 후 재시도)은 동일하지만, 별도 프로세스(`trainingplan_main.py`)가 쓰는
파일이라 전역 커넥션 상태를 공유하지 않도록 일부러 분리했다(작업 브리핑 지시 -- 두 파이프라인을
섞지 말 것).
"""
import logging
import secrets
from typing import Optional

import psycopg2
import psycopg2.extras

from config import DATABASE_URL

logger = logging.getLogger(__name__)

_conn: "psycopg2.extensions.connection | None" = None

_ID_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

# 종목별 고정 거리 -- race_category_options.distance_km이 NULL인 경우(ULTRA/TRAIL처럼 대회마다
# 거리가 제각각인 종목, backend/.../race/Race.kt 주석 참고)의 폴백값. SPEC에 없는 임의값이며,
# 대회 카테고리 이름이 함의하는 대표 거리를 그대로 썼다(하프/풀은 공식 거리, 5K/10K는 이름
# 그대로, ULTRA/TRAIL은 흔한 국내 대회 거리로 근사).
_CATEGORY_DEFAULT_DISTANCE_KM = {
    "FIVE_K": 5.0,
    "TEN_K": 10.0,
    "HALF": 21.0975,
    "FULL": 42.195,
    "ULTRA": 50.0,
    "TRAIL": 21.0,
}


def generate_id(prefix: str, length: int = 8) -> str:
    """backend/.../common/IdGenerator.kt와 같은 모양의 짧은 접두 id (예: tps_a1b2c3d4).
    두 언어가 값을 주고받는 게 아니라 각자 자기 테이블 행의 id만 만들면 되므로, 알고리즘이
    100% 동일할 필요는 없다 -- 모양만 맞춘다."""
    suffix = "".join(secrets.choice(_ID_ALPHABET) for _ in range(length))
    return f"{prefix}_{suffix}"


def _get_connection():
    global _conn
    if _conn is None or _conn.closed:
        _conn = psycopg2.connect(DATABASE_URL)
    return _conn


def _with_connection(fn):
    global _conn
    try:
        return fn(_get_connection())
    except psycopg2.OperationalError:
        logger.warning("Postgres 연결이 끊어진 것으로 보여 재연결 후 1회 재시도합니다.")
        if _conn is not None:
            try:
                _conn.close()
            except Exception:  # noqa: BLE001
                pass
        _conn = None
        return fn(_get_connection())


def fetch_recent_avg_weekly_km(user_id: str, lookback_days: int = 56) -> Optional[float]:
    """최근 [lookback_days]일간 완주(COMPLETED)한 러닝의 distance_km 합 / (lookback_days/7) =
    평균 주간 거리. 이력이 아예 없으면 None(호출부가 기본값으로 대체, plan_skeleton.build_weeks
    참고). 페이스는 스케줄 생성엔 안 쓰고 LLM 개인화 코멘트용 컨텍스트에만 참고용으로 곁들인다."""

    def _run(conn):
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(
                """
                SELECT
                    COALESCE(SUM(distance_km), 0) AS total_distance_km,
                    AVG(average_pace_sec_per_km) AS avg_pace_sec_per_km,
                    COUNT(*) AS run_count
                FROM run_records
                WHERE user_id = %s
                  AND status = 'COMPLETED'
                  AND finished_at > now() - (%s || ' days')::interval
                """,
                (user_id, lookback_days),
            )
            row = cur.fetchone()
            if row is None or row["run_count"] == 0:
                return None, None
            weeks = lookback_days / 7.0
            avg_weekly_km = float(row["total_distance_km"]) / weeks
            avg_pace = float(row["avg_pace_sec_per_km"]) if row["avg_pace_sec_per_km"] is not None else None
            return avg_weekly_km, avg_pace

    return _with_connection(_run)


def fetch_race_context(race_id: str, category: str) -> Optional[dict]:
    """대회 이름/일시/이 종목의 목표 거리. distance_km이 NULL(ULTRA/TRAIL)이면
    [_CATEGORY_DEFAULT_DISTANCE_KM]로 대체한다. 대회 또는 이 종목 옵션이 아예 없으면
    None(호출부가 FAILED로 기록)."""

    def _run(conn):
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(
                """
                SELECT r.name AS race_name, r.race_date, rco.distance_km
                FROM races r
                JOIN race_category_options rco ON rco.race_id = r.id
                WHERE r.id = %s AND rco.category = %s
                LIMIT 1
                """,
                (race_id, category),
            )
            row = cur.fetchone()
            if row is None:
                return None
            distance_km = row["distance_km"]
            if distance_km is None:
                distance_km = _CATEGORY_DEFAULT_DISTANCE_KM.get(category, 10.0)
            return {
                "race_name": row["race_name"],
                "race_date": row["race_date"],
                "distance_km": float(distance_km),
            }

    return _with_connection(_run)


def match_route_for_distance(target_distance_km: float) -> Optional[dict]:
    """목표 거리에 가장 가까운 공개 코스(is_preview_segment = false) 하나. 후보가 하나도 없으면
    None(REST가 아닌 세션이라도 매칭 코스 없이 그냥 진행 -- 작업 브리핑이 "코스가 없으면 null
    허용"이라 명시)."""

    def _run(conn):
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(
                """
                SELECT id, name, distance_km
                FROM sketch_routes
                WHERE is_preview_segment = false
                ORDER BY ABS(distance_km - %s)
                LIMIT 1
                """,
                (target_distance_km,),
            )
            row = cur.fetchone()
            if row is None:
                return None
            return {"id": row["id"], "name": row["name"], "distance_km": float(row["distance_km"])}

    return _with_connection(_run)


def persist_ready_plan(plan_id: str, weeks: list[dict], comment: str) -> None:
    """세션 전량 재생성(재시도 대비 delete-then-insert) + status=READY 갱신을 한 트랜잭션으로."""

    def _run(conn):
        with conn.cursor() as cur:
            cur.execute("DELETE FROM training_plan_sessions WHERE plan_id = %s", (plan_id,))
            for week in weeks:
                for session in week["sessions"]:
                    cur.execute(
                        """
                        INSERT INTO training_plan_sessions
                            (id, plan_id, week_number, session_index, type, target_distance_km, matched_route_id)
                        VALUES (%s, %s, %s, %s, %s, %s, %s)
                        """,
                        (
                            generate_id("tps"),
                            plan_id,
                            week["week_number"],
                            session["session_index"],
                            session["type"],
                            session.get("target_distance_km"),
                            session.get("matched_route_id"),
                        ),
                    )
            cur.execute(
                """
                UPDATE training_plans
                SET status = 'READY', comment = %s, error_message = NULL, updated_at = now()
                WHERE id = %s
                """,
                (comment, plan_id),
            )
        conn.commit()

    _with_connection(_run)


def mark_plan_failed(plan_id: str, error_message: str) -> None:
    def _run(conn):
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE training_plans
                SET status = 'FAILED', error_message = %s, updated_at = now()
                WHERE id = %s
                """,
                (error_message[:2000], plan_id),
            )
        conn.commit()

    _with_connection(_run)
