"""OpenAI 호출 -- 신고 원문을 category/severity/summary로 구조화 분류한다.
LangChain 없이 openai SDK를 직접 쓴다(이 한 번의 분류 호출에 체인 추상화가 필요 없음, 과설계 금지).
"""
import json
import logging
import time

from openai import OpenAI

from config import OPENAI_API_KEY, OPENAI_MODEL

logger = logging.getLogger(__name__)

_client = OpenAI(api_key=OPENAI_API_KEY)

CATEGORIES = {"SPAM", "ABUSE", "HARASSMENT", "SAFETY", "OTHER"}
SEVERITIES = {"LOW", "MEDIUM", "HIGH"}

_SYSTEM_PROMPT = """\
너는 러닝 앱 '달림'의 신고 트리아지 담당자다. 사용자가 다른 사용자의 채팅 메시지 또는 소셜 러닝 \
세션을 신고했다. 신고 사유와 신고 대상 원문을 보고 아래 JSON 스키마로만 답하라. 자동으로 조치를 \
취하는 게 아니라, 운영자가 우선순위를 정할 수 있도록 분류만 하는 것이다.

{
  "category": "SPAM" | "ABUSE" | "HARASSMENT" | "SAFETY" | "OTHER",
  "severity": "LOW" | "MEDIUM" | "HIGH",
  "summary": "운영자가 한눈에 볼 한국어 한 줄 요약 (신고 사유 + 원문 핵심을 합쳐서)"
}

- SAFETY(오프라인 만남에서의 신체적 위협/성희롱 등 실제 안전 문제)나 심각한 HARASSMENT는 \
  severity를 HIGH로 매겨라 -- 이 경우에만 운영자에게 즉시 알림이 간다.
- 애매하면 과대평가하지 말고 MEDIUM/LOW로 보수적으로 판단해라.
- JSON 외의 다른 텍스트는 절대 출력하지 마라.
"""


def classify(reason: str | None, context_text: str) -> dict:
    """실패 시 최대 3회 재시도, 그래도 실패하면 UNKNOWN으로 사람이 보게 남긴다."""
    user_prompt = f"신고 사유: {reason or '(사유 없음)'}\n\n신고 대상 원문:\n{context_text}"

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
                temperature=0,
            )
            data = json.loads(response.choices[0].message.content)
            category = data.get("category")
            severity = data.get("severity")
            summary = data.get("summary")
            if category in CATEGORIES and severity in SEVERITIES and summary:
                return {"category": category, "severity": severity, "summary": summary}
            raise ValueError(f"모델이 스키마를 벗어난 값을 반환함: {data}")
        except Exception as exc:  # noqa: BLE001 -- 어떤 원인이든 재시도 후 UNKNOWN으로 폴백
            last_error = exc
            logger.warning("OpenAI 분류 실패(%d번째 시도): %s", attempt + 1, exc)
            time.sleep(2**attempt)

    logger.error("OpenAI 분류 3회 모두 실패, UNKNOWN으로 기록: %s", last_error)
    return {
        "category": "OTHER",
        "severity": "UNKNOWN",
        "summary": f"(자동 분류 실패 -- 운영자 직접 확인 필요: {last_error})",
    }
