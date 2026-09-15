"""환경 변수 로딩 -- 실패는 가능한 한 빨리, 명확하게(fail fast).

REDIS_URL / DATABASE_URL은 docker-compose 안에서 실행될 때 서비스명(redis/postgres)으로
기본값이 온다(로컬에서 컨테이너 밖에서 직접 돌릴 땐 오버라이드해서 localhost로 바꿔라).
OPENAI_API_KEY는 분류(classify 노드)에 반드시 필요해서 없으면 기동 자체를 막는다.
DISCORD_REPORT_WEBHOOK_URL은 선택 -- 없으면 notify_discord 노드가 경고만 로그로 남기고
건너뛴다(HIGH 심각도 신고도 DB에는 여전히 기록된다, 알림만 안 갈 뿐).
"""
import os
import sys


def _require(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        print(f"[config] {name} 환경 변수가 없습니다 -- 워커를 시작할 수 없습니다.", file=sys.stderr)
        sys.exit(1)
    return value


REDIS_URL = os.environ.get("REDIS_URL", "redis://localhost:6379/0")
DATABASE_URL = os.environ.get("DATABASE_URL", "postgresql://dallim:dallim@localhost:5432/dallim")
OPENAI_API_KEY = _require("OPENAI_API_KEY")
OPENAI_MODEL = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")
DISCORD_REPORT_WEBHOOK_URL = os.environ.get("DISCORD_REPORT_WEBHOOK_URL") or None

STREAM_KEY = "reports:triage"
CONSUMER_GROUP = "triage-workers"
