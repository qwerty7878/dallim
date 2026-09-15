"""Postgres 접근 -- fetch_context(원문 조회)와 persist(트리아지 결과 저장)만 담당한다.
Kotlin 백엔드가 쓰는 Exposed 테이블과 정확히 같은 스키마를 그대로 SQL로 읽고 쓴다(별도 ORM
없음 -- 워커는 이 두 가지 짧은 쿼리만 하면 되므로 psycopg2 raw SQL이면 충분하다, 과설계 금지).
"""
from contextlib import contextmanager

import psycopg2
import psycopg2.extras

from config import DATABASE_URL


@contextmanager
def connect():
    conn = psycopg2.connect(DATABASE_URL)
    try:
        yield conn
    finally:
        conn.close()


def fetch_context(target_type: str, target_id: str) -> str:
    """신고 대상의 원문을 사람이 읽을 수 있는 한 덩어리 텍스트로 만들어 classify 노드에 넘긴다."""
    with connect() as conn, conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
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


def persist_triage(report_id: str, category: str, severity: str, summary: str) -> None:
    """content_report_triage upsert -- 같은 report_id가 재전달(redelivery)돼도 안전하게 덮어쓴다."""
    with connect() as conn, conn.cursor() as cur:
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


def mark_notified(report_id: str) -> None:
    with connect() as conn, conn.cursor() as cur:
        cur.execute(
            "UPDATE content_report_triage SET notified_at = now() WHERE report_id = %s",
            (report_id,),
        )
        conn.commit()
