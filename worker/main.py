"""신고 트리아지 워커 진입점 -- Redis Stream(reports:triage)을 Consumer Group으로 구독해서
LangGraph 그래프를 하나씩 실행한다. Kotlin 백엔드(backend/)는 이 프로세스의 존재를 전혀 모르고
XADD만 한다 -- 언어 경계는 Redis Stream 하나로 분리돼 있다.
"""
import logging
import os

import redis

from config import CONSUMER_GROUP, REDIS_URL, STREAM_KEY
from graph import build_graph

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)


def ensure_group(r: redis.Redis) -> None:
    try:
        r.xgroup_create(name=STREAM_KEY, groupname=CONSUMER_GROUP, id="0", mkstream=True)
        logger.info("Consumer group '%s' created on stream '%s'.", CONSUMER_GROUP, STREAM_KEY)
    except redis.ResponseError as exc:
        if "BUSYGROUP" not in str(exc):
            raise


def run() -> None:
    r = redis.from_url(REDIS_URL, decode_responses=True)
    ensure_group(r)
    app = build_graph()
    consumer_name = f"worker-{os.getpid()}"
    logger.info("Report triage worker started (consumer=%s, stream=%s).", consumer_name, STREAM_KEY)

    while True:
        response = r.xreadgroup(CONSUMER_GROUP, consumer_name, {STREAM_KEY: ">"}, count=1, block=5000)
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
