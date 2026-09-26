-- 대회 목표 훈련 플랜(S-86) — docs/02-api-spec.md 19장, `docs/달림_화면별_상세기획서_v1.3.md`
-- S-86 근거. CLAUDE.md 2026-09-18 결정: PART 8 로드맵(MVP5권)보다 앞당겨 구현하고, RUN+ 결제
-- 게이트 없이 전면 무료로 공개한다.
--
-- 언어 경계는 content_report_triage(V19)와 동일한 원칙: Kotlin은 training_plans 행의 생성/
-- 상태 리셋만 하고, 실제 플랜 생성(이력 분석 -> 주차별 스케줄 -> 코스 매칭 -> LLM 코멘트)은
-- 별도 프로세스인 worker/trainingplan_main.py(Python + LangGraph)가 Redis Stream
-- (training-plans:generate)을 구독해서 처리한 뒤 이 두 테이블에 직접 쓴다.
--
-- 신고 트리아지와 달리 유저가 GET으로 결과를 기다려야 해서, 워커가 실패해도 반드시
-- status='FAILED'로 마무리해야 한다(그래야 GET이 영원히 PENDING에 머무르지 않는다).

CREATE TABLE training_plans (
    id             VARCHAR(32) PRIMARY KEY,
    user_id        VARCHAR(32) NOT NULL REFERENCES users (id),
    race_id        VARCHAR(32) NOT NULL REFERENCES races (id),
    -- FIVE_K | TEN_K | HALF | FULL | ULTRA | TRAIL (com.dallim.race.RaceCategory와 동일)
    category       VARCHAR(16) NOT NULL,
    -- PENDING | READY | FAILED
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- 워커(personalize_comment_node)가 채우는 개인화 코멘트. READY가 아니면 NULL.
    -- 컴플라이언스 면책 문구는 여기 저장하지 않는다 -- Kotlin이 항상 고정 상수로 응답에 붙인다
    -- (com.dallim.trainingplan.TrainingPlanDisclaimer).
    comment        TEXT,
    -- 워커가 그래프 실행 중 예외를 잡았을 때 기록(FAILED일 때만).
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 대회(및 그 안에서 고른 종목) 하나당 플랜 하나 -- 재요청 시 새 행을 만들지 않고 기존 행을
    -- 재사용/리셋한다(com.dallim.trainingplan.TrainingPlanService.requestGeneration 참고).
    CONSTRAINT uq_training_plans_user_race UNIQUE (user_id, race_id)
);

-- 플랜 하나의 주차별 세션들. "주간 목표 거리"는 별도 컬럼으로 두지 않는다 -- REST를 제외한 그
-- 주 세션들의 target_distance_km 합을 조회 시점에 계산한다(파생값을 저장해 세션이 갱신될 때
-- 어긋나는 것을 방지, com.dallim.trainingplan.TrainingPlanService 참고).
CREATE TABLE training_plan_sessions (
    id                 VARCHAR(32) PRIMARY KEY,
    plan_id            VARCHAR(32) NOT NULL REFERENCES training_plans (id),
    week_number        INTEGER NOT NULL,
    session_index      INTEGER NOT NULL,
    -- LONG_RUN | TEMPO | INTERVAL | REST
    type               VARCHAR(16) NOT NULL,
    -- REST는 NULL.
    target_distance_km DOUBLE PRECISION,
    -- 목표 거리에 가장 가까운 공개 sketch_routes(is_preview_segment = false). 후보가 없거나
    -- REST 세션이면 NULL.
    matched_route_id   VARCHAR(32) REFERENCES sketch_routes (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_training_plans_user_id ON training_plans (user_id);
CREATE INDEX idx_training_plan_sessions_plan_id ON training_plan_sessions (plan_id);
