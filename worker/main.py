"""신고 트리아지 워커 진입점 -- Redis Stream(reports:triage)을 Consumer Group으로 구독해서
LangGraph 그래프를 하나씩 실행한다. Kotlin 백엔드(backend/)는 이 프로세스의 존재를 전혀 모르고
XADD만 한다 -- 언어 경계는 Redis Stream 하나로 분리돼 있다.
"""
import logging
import os
import time

import redis

from config import CONSUMER_GROUP, REDIS_URL, STREAM_KEY
from graph import build_graph

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

# Redis/Postgres 컨테이너가 아직 준비되기 전에 이 프로세스가 먼저 뜰 수 있다(docker-compose의
# depends_on은 "컨테이너 시작"만 보장하지 "서비스 준비 완료"는 보장하지 않는다) -- 연결 자체와
# 폴링 루프 둘 다 재시도 가능하게 해서, 컨테이너에 restart policy를 얹지 않아도 일시적인 장애를
# 스스로 넘긴다.
_CONNECT_RETRY_SECONDS = 5


def connect_redis() -> redis.Redis:
    while True:
        try:
            r = redis.from_url(REDIS_URL, decode_responses=True, socket_keepalive=True)
            r.ping()
            return r
        except redis.RedisError as exc:
            logger.warning("Redis 연결 실패, %d초 후 재시도: %s", _CONNECT_RETRY_SECONDS, exc)
            time.sleep(_CONNECT_RETRY_SECONDS)


def ensure_group(r: redis.Redis) -> None:
    try:
        r.xgroup_create(name=STREAM_KEY, groupname=CONSUMER_GROUP, id="0", mkstream=True)
        logger.info("Consumer group '%s' created on stream '%s'.", CONSUMER_GROUP, STREAM_KEY)
    except redis.ResponseError as exc:
        if "BUSYGROUP" not in str(exc):
            raise


def run() -> None:
    r = connect_redis()
    ensure_group(r)
    app = build_graph()
    consumer_name = f"worker-{os.getpid()}"
    logger.info("Report triage worker started (consumer=%s, stream=%s).", consumer_name, STREAM_KEY)

    while True:
        try:
            response = r.xreadgroup(CONSUMER_GROUP, consumer_name, {STREAM_KEY: ">"}, count=1, block=5000)
        except redis.RedisError as exc:
            # Redis가 잠깐 끊겼다고 프로세스 자체가 죽으면(컨테이너 재시작 정책이 없는 한) 파이프라인
            # 전체가 조용히 멈춘다 -- 여기서 재연결하고 계속 돈다.
            logger.warning("Redis 폴링 중 오류, 재연결 시도: %s", exc)
            r = connect_redis()
            continue

        if not response:
            continue
        for _stream_name, messages in response:
            for message_id, fields in messages:
                report_id = fields.get("reportId", "?")
                logger.info("Processing report %s (redis id=%s)", report_id, message_id)
                try:
                    app.invoke(
                        {
                            "report_id": fields["reportId"],
                            "target_type": fields["targetType"],
                            "target_id": fields["targetId"],
                            "reporter_user_id": fields["reporterUserId"],
                            "reason": fields.get("reason"),
                        }
                    )
                    r.xack(STREAM_KEY, CONSUMER_GROUP, message_id)
                    logger.info("Report %s triaged.", report_id)
                except Exception:
                    logger.exception(
                        "신고 %s 처리 실패 -- ack하지 않음, 다음 폴링에서 재시도됩니다.", report_id
                    )


if __name__ == "__main__":
    run()
