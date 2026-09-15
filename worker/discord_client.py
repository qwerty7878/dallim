"""Discord 웹훅 알림 -- severity == HIGH인 신고만 여기로 온다."""
import logging

import requests

from config import DISCORD_REPORT_WEBHOOK_URL

logger = logging.getLogger(__name__)

_SEVERITY_COLOR = 0xE74C3C  # HIGH 전용 색(빨강) -- 이 채널엔 HIGH만 오므로 색 분기 불필요.


def notify_high_severity_report(
    report_id: str,
    target_type: str,
    target_id: str,
    category: str,
    summary: str,
    context_text: str,
) -> bool:
    """전송 성공 시 True. 웹훅 URL이 없으면 경고만 남기고 False -- 알림 없이도 DB엔 기록된다."""
    if not DISCORD_REPORT_WEBHOOK_URL:
        logger.warning(
            "DISCORD_REPORT_WEBHOOK_URL이 설정되지 않아 HIGH 신고(%s) 알림을 건너뜁니다.", report_id
        )
        return False

    payload = {
        "embeds": [
            {
                "title": "🚨 긴급 신고 접수",
                "color": _SEVERITY_COLOR,
                "fields": [
                    {"name": "분류", "value": category, "inline": True},
                    {"name": "대상 타입", "value": target_type, "inline": True},
                    {"name": "대상 ID", "value": target_id, "inline": True},
                    {"name": "신고 ID", "value": report_id, "inline": False},
                    {"name": "AI 요약", "value": summary, "inline": False},
                    {"name": "원문", "value": context_text[:1000], "inline": False},
                ],
            }
        ]
    }

    try:
        resp = requests.post(DISCORD_REPORT_WEBHOOK_URL, json=payload, timeout=10)
        resp.raise_for_status()
        return True
    except requests.RequestException as exc:
        logger.error("Discord 웹훅 전송 실패(report_id=%s): %s", report_id, exc)
        return False
