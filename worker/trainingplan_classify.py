"""OpenAI 호출 -- 완성된 훈련 플랜 전체에 대한 짧은 한국어 격려 코멘트 하나를 생성한다.
신고 트리아지의 `classify.py`와 동일한 패턴(openai SDK 직접 호출, `response_format:
json_object`, 실패 시 재시도) -- 다만 여기는 유저가 기다리는 요청이라, 3회 모두 실패해도
예외를 위로 던지지 않고 정적 폴백 문구로 대체한다(그래야 그래프가 FAILED 없이 끝까지 간다 --
개인화 코멘트 하나 못 받았다고 플랜 생성 전체를 실패시킬 이유는 없다는 판단, 작업 브리핑 지시).
"""
import json
import logging
import time

from openai import OpenAI

from config import OPENAI_API_KEY, OPENAI_MODEL

logger = logging.getLogger(__name__)

_client = OpenAI(api_key=OPENAI_API_KEY)

_SYSTEM_PROMPT = """\
너는 러닝 앱 '달림'의 훈련 코치다. 유저가 목표로 담아둔 대회와, 그 대회까지 자동 생성된 \
주차별 훈련 플랜 요약, 유저의 최근 러닝 이력을 보고 짧은 한국어 격려 코멘트 하나를 작성해라. \
아래 JSON 스키마로만 답하라.

{
  "comment": "2~3문장의 한국어 격려 코멘트"
}

- 의학적 조언, 부상 관련 지시(예: "이렇게 하면 부상이 안 생겨요")는 절대 하지 마라 -- 그건
  이 코멘트와 별개로 앱이 항상 붙이는 고정 면책 문구가 담당한다.
- 과장된 확언("반드시 완주합니다") 대신 담백하게 격려하고, 훈련 플랜의 특징(예: 점진적 증가,
  테이퍼 주간)을 한 가지 정도 자연스럽게 언급해라.
- JSON 외의 다른 텍스트는 절대 출력하지 마라.
"""

_FALLBACK_COMMENT = (
    "훈련 플랜이 준비됐어요. 무리하지 않는 선에서 한 주씩 차근차근 따라가 보세요 -- "
    "꾸준함이 가장 중요해요."
)


def personalize_comment(context: dict) -> str:
    user_prompt = json.dumps(context, ensure_ascii=False, default=str)

    last_error: Exception | None = None
    for attempt in range(3):
        try:
            response = _client.chat.completions.create(
                model=OPENAI_MODEL,
                messages=[
                    {"role": "system", "content": _SYSTEM_PROMPT},
                    {"role": "user", "content": user_prompt},
                ],
                response_format={"type": "json_object"},
                temperature=0.7,
            )
            data = json.loads(response.choices[0].message.content)
            comment = data.get("comment")
            if isinstance(comment, str) and comment.strip():
                return comment.strip()
            raise ValueError(f"모델이 스키마를 벗어난 값을 반환함: {data}")
        except Exception as exc:  # noqa: BLE001 -- 어떤 원인이든 재시도 후 폴백
            last_error = exc
            logger.warning("훈련 플랜 코멘트 생성 실패(%d번째 시도): %s", attempt + 1, exc)
            time.sleep(2**attempt)

    logger.error("훈련 플랜 코멘트 생성 3회 모두 실패, 정적 폴백 문구로 대체: %s", last_error)
    return _FALLBACK_COMMENT
