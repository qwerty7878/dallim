-- 코스 평가 태그(피드백) -- docs/02-api-spec.md 5장 (POST /runs/{runId}/feedback-tags),
-- docs/01-feature-spec.md 2.2.G, docs/달림_화면별_상세기획서_v1.3.md 301행(S-16 코스 상세)/
-- 395행(S-25 결과 화면). 완주 결과 화면에서 "3초 컷"으로 제출하는 중립/긍정 서술형 태그
-- (별점 없음, 부정 평가는 태그가 아니라 신고 경로로만 처리 -- 483~484행 원칙 그대로 적용).
-- 허용 어휘는 com.dallim.run.RouteFeedbackTags.ALLOWED 참고.
--
-- (run_id, tag) UNIQUE로 같은 러닝에 대한 중복 태그 삽입을 막고, RunRepository의
-- "이미 제출한 적이 있으면 idempotent 무시" 처리를 DB 레벨에서도 뒷받침한다.

CREATE TABLE route_feedback_tags (
    id         VARCHAR(32) PRIMARY KEY,
    run_id     VARCHAR(32) NOT NULL REFERENCES run_records (id),
    route_id   VARCHAR(32) NOT NULL REFERENCES sketch_routes (id),
    user_id    VARCHAR(32) NOT NULL REFERENCES users (id),
    tag        VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_route_feedback_tags_run_tag UNIQUE (run_id, tag)
);

-- GET /routes/{routeId} topFeedbackTags -- route_id 기준 태그 집계(상위 3개) 조회 경로.
CREATE INDEX idx_route_feedback_tags_route_id ON route_feedback_tags (route_id);
