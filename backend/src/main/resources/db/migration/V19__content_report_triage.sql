-- 신고 트리아지 파이프라인 (사용자 지시로 신규 도입, docs/02-api-spec.md 17.18에 "신고에 대한
-- 자동 조치/모더레이션 큐는 범위 밖"이라 적혀 있던 것을 이번에 뒤집는다).
-- content_reports INSERT 직후 Kotlin 백엔드가 Redis Stream(reports:triage)에 job을 발행하고,
-- 별도 Python 워커(worker/, LangGraph + OpenAI + Discord 웹훅)가 분류 결과를 이 테이블에 쓴다.
-- content_reports 원본 테이블은 건드리지 않는다 -- 신고 원본과 AI 판정을 분리해서, 판정 로직이
-- 바뀌어도 원본 신고 데이터는 그대로 유지되게 한다.
CREATE TABLE content_report_triage (
    report_id     VARCHAR(32) PRIMARY KEY REFERENCES content_reports (id),
    -- SPAM | ABUSE | HARASSMENT | SAFETY | OTHER
    category      VARCHAR(20) NOT NULL,
    -- LOW | MEDIUM | HIGH | UNKNOWN(분류 실패 시 사람이 보게 남겨두는 용도)
    severity      VARCHAR(10) NOT NULL,
    summary       TEXT NOT NULL,
    -- HIGH라서 Discord로 알림을 보낸 시각. 그 외엔 NULL(알림 없음).
    notified_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
