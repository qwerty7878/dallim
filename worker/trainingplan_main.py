"""훈련 플랜(S-86) 워커 진입점 -- Redis Stream(training-plans:generate)을 Consumer Group으로
구독해서 LangGraph 그래프를 하나씩 실행한다. `main.py`(신고 트리아지)와 컨슈머 루프 구조는
같지만 별도 프로세스로 완전히 분리했다 -- 한쪽이 죽어도 다른 쪽 파이프라인에 영향이 없다.

신고 트리아지와의 결정적 차이: 그쪽은 실패하면 그냥 ack 안 하고 다음 폴링에서 재시도하면
그만이지만(fire-and-forget, 아무도 기다리지 않음), 여기는 유저가 `GET
/races/{raceId}/training-plan`으로 결과를 기다린다. 그래서 그래프가 예외로 끝나면 반드시
`training_plans.status`를 FAILED로 기록한 뒤 ack한다 -- 그래야 (a) GET이 영원히 PENDING에
머무르지 않고, (b) 일시적이지 않은 실패(예: 잘못된 race_id)가 재시도 폭주로 이어지지 않는다.
"""
import logging
import os
import time

import redis

from config import TRAINING_PLAN_CONSUMER_GROUP as CONSUMER_GROUP
from config import TRAINING_PLAN_STREAM_KEY as STREAM_KEY
from config import REDIS_URL
from trainingplan_db import mark_plan_failed
from trainingplan_graph import build_graph

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

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
    consumer_name = f"trainingplan-worker-{os.getpid()}"
    logger.info("Training plan worker started (consumer=%s, stream=%s).", consumer_name, STREAM_KEY)

    while True:
        try:
            response = r.xreadgroup(CONSUMER_GROUP, consumer_name, {STREAM_KEY: ">"}, count=1, block=5000)
        except redis.RedisError as exc:
            logger.warning("Redis 폴링 중 오류, 재연결 시도: %s", exc)
            r = connect_redis()
            continue

        if not response:
            continue
        for _stream_name, messages in response:
            for message_id, fields in messages:
                plan_id = fields.get("planId", "?")
                logger.info("Generating training plan %s (redis id=%s)", plan_id, message_id)
                try:
                    app.invoke(
                        {
                            "plan_id": fields["planId"],
                            "user_id": fields["userId"],
                            "race_id": fields["raceId"],
                            "race_category": fields["raceCategory"],
                            "race_date_iso": fields["raceDateIso"],
                        }
                    )
                    logger.info("Training plan %s ready.", plan_id)
                except Exception as exc:  # noqa: BLE001 -- 유저가 기다리므로 FAILED로 깔끔히 마무리
                    logger.exception("훈련 플랜 %s 생성 실패 -- FAILED로 기록합니다.", plan_id)
                    try:
                        mark_plan_failed(plan_id, str(exc))
                    except Exception:  # noqa: BLE001 -- FAILED 기록 자체가 실패해도 ack는 하고 넘어간다
                        logger.exception("훈련 플랜 %s를 FAILED로 기록하는 것마저 실패했습니다.", plan_id)
                finally:
                    # 성공/실패(FAILED 기록 포함) 모두 ack한다 -- 재시도 폭주 방지(작업 브리핑 지시).
                    r.xack(STREAM_KEY, CONSUMER_GROUP, message_id)


if __name__ == "__main__":
    run()
