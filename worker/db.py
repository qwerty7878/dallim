"""Postgres 접근 -- fetch_context(원문 조회)와 persist(트리아지 결과 저장)만 담당한다.
Kotlin 백엔드가 쓰는 Exposed 테이블과 정확히 같은 스키마를 그대로 SQL로 읽고 쓴다(별도 ORM
없음 -- 워커는 이 두 가지 짧은 쿼리만 하면 되므로 psycopg2 raw SQL이면 충분하다, 과설계 금지).

연결은 프로세스 생애주기 동안 하나만 재사용한다(요청마다 새로 열지 않음 -- 컨슈머 루프가
한 번에 신고 하나씩 순차 처리하므로 커넥션 풀은 필요 없다, 과설계 금지). 끊어졌으면 다음 호출에서
한 번 재연결 후 재시도한다.
"""
import logging

import psycopg2
import psycopg2.extras

from config import DATABASE_URL

logger = logging.getLogger(__name__)

_conn: "psycopg2.extensions.connection | None" = None


def _get_connection():
    global _conn
    if _conn is None or _conn.closed:
        _conn = psycopg2.connect(DATABASE_URL)
    return _conn


def _with_connection(fn):
    """fn(conn)을 실행한다. 끊긴 연결이었으면 한 번 재연결해서 재시도한다."""
    global _conn
    try:
        return fn(_get_connection())
    except psycopg2.OperationalError:
        logger.warning("Postgres 연결이 끊어진 것으로 보여 재연결 후 1회 재시도합니다.")
        if _conn is not None:
            try:
                _conn.close()
            except Exception:  # noqa: BLE001 -- 이미 죽은 연결을 닫다 나는 오류는 무시
                pass
        _conn = None
        return fn(_get_connection())


def fetch_context(target_type: str, target_id: str) -> str:
    """신고 대상의 원문을 사람이 읽을 수 있는 한 덩어리 텍스트로 만들어 classify 노드에 넘긴다."""

    def _run(conn):
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            if target_type == "CHAT_MESSAGE":
                cur.execute(
                    """
                    SELECT scm.body, scm.type, u.nickname AS sender_nickname, ss.title AS session_title
                    FROM social_session_chat_messages scm
                    JOIN social_sessions ss ON ss.id = scm.session_id
                    LEFT JOIN users u ON u.id = scm.sender_user_id
                    WHERE scm.id = %s
                    """,
                    (target_id,),
                )
                row = cur.fetchone()
                if row is None:
                    return "(신고 대상 메시지를 찾을 수 없음 -- 이미 삭제됐을 수 있음)"
                sender = row["sender_nickname"] or "(시스템 메시지)"
                return (
                    f"세션: {row['session_title']}\n"
                    f"메시지 타입: {row['type']}\n"
                    f"보낸 사람: {sender}\n"
                    f"내용: {row['body']}"
                )

            if target_type == "SOCIAL_SESSION":
                cur.execute(
                    "SELECT title, description FROM social_sessions WHERE id = %s",
                    (target_id,),
                )
                row = cur.fetchone()
                if row is None:
                    return "(신고 대상 세션을 찾을 수 없음 -- 이미 삭제됐을 수 있음)"
                return f"세션 제목: {row['title']}\n설명: {row['description'] or '(없음)'}"

            return f"(알 수 없는 target_type: {target_type})"

    return _with_connection(_run)


def persist_triage(report_id: str, category: str, severity: str, summary: str) -> None:
    """content_report_triage upsert -- 같은 report_id가 재전달(redelivery)돼도 안전하게 덮어쓴다."""

    def _run(conn):
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO content_report_triage (report_id, category, severity, summary)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (report_id) DO UPDATE
                    SET category = EXCLUDED.category,
                        severity = EXCLUDED.severity,
                        summary = EXCLUDED.summary
                """,
                (report_id, category, severity, summary),
            )
        conn.commit()

    _with_connection(_run)


def mark_notified(report_id: str) -> None:
    def _run(conn):
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE content_report_triage SET notified_at = now() WHERE report_id = %s",
                (report_id,),
            )
        conn.commit()

    _with_connection(_run)
