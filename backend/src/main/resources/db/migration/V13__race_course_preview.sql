-- 대회 코스 미리 달리기(S-85) — docs/달림_화면별_상세기획서_v1.3.md 733~740행,
-- docs/02-api-spec.md 16.6(신규) 근거. 2026-09-12.
--
-- 대회(races)에 공식 코스(Route)를 연결하고, 그 코스를 몇 개 구간으로 잘라 각 구간을
-- "구간용 SketchRoute" row로 등록해둔다 — "이 구간 달리기"는 새 엔드포인트 없이 그
-- 구간의 routeId로 기존 POST /runs를 그대로 태우는 방식이라(작업 브리핑 지시), 완주 판정
-- (RunJudgementService)도 손대지 않는다.

ALTER TABLE races
    ADD COLUMN course_route_id VARCHAR(32) REFERENCES sketch_routes (id);

-- 구간용 SketchRoute를 표시하는 플래그. GET /routes 목록/탐색 조회에서는 제외하되(com.dallim.
-- route.RouteRepository.search/findTodaySketchCandidate의 WHERE 조건 참고),
-- GET /routes/{routeId}(직접 조회)와 POST /runs(그 id로 러닝 시작)는 평소처럼 동작한다.
ALTER TABLE sketch_routes
    ADD COLUMN is_preview_segment BOOLEAN NOT NULL DEFAULT FALSE;

-- 대회 하나의 공식 코스를 나눈 구간들. route_id는 그 구간만 잘라낸 SketchRoute(항상
-- is_preview_segment = TRUE인 row)를 가리킨다.
CREATE TABLE race_course_segments (
    id           VARCHAR(32) PRIMARY KEY,
    race_id      VARCHAR(32) NOT NULL REFERENCES races (id),
    route_id     VARCHAR(32) NOT NULL REFERENCES sketch_routes (id),
    label        VARCHAR(50) NOT NULL,   -- 예: "출발~1.4km"
    order_index  INTEGER NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_race_course_segments_race_id ON race_course_segments (race_id);
CREATE INDEX idx_sketch_routes_is_preview_segment ON sketch_routes (is_preview_segment);
