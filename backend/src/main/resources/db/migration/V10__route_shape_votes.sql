-- 커뮤니티 투표(모양 맞추기) -- docs/02-api-spec.md 4장 (POST /routes/{routeId}/shape-votes),
-- docs/01-feature-spec.md 2.2.C, docs/달림_화면별_상세기획서_v1.3.md 297행(S-16 Route 상세:
-- "이름 + 이모지 + 커뮤니티 투표 상태(`고래 73% · 물고기 19%` / [나도 투표])")/271행.
--
-- sketch_routes.name은 생성 시점에 고정된 공식 이름이고, 이 테이블은 그것과 별개로 "다른
-- 사람들 눈엔 이게 뭘로 보이는지"를 자유 텍스트로 모으는 별도 집계다 -- 고정 후보 목록이 아니라
-- 자유 텍스트 제출 + 자동 집계(동일 라벨끼리 카운트) 방식.
--
-- (route_id, user_id) UNIQUE -- 한 사용자는 코스당 최신 투표 1개만 유지(재투표 시 upsert로
-- 교체, 누적 안 됨). RouteRepository.upsertShapeVote 참고.

CREATE TABLE route_shape_votes (
    id         VARCHAR(32) PRIMARY KEY,
    route_id   VARCHAR(32) NOT NULL REFERENCES sketch_routes (id),
    user_id    VARCHAR(32) NOT NULL REFERENCES users (id),
    label      VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_route_shape_votes_route_user UNIQUE (route_id, user_id)
);

-- GET /routes/{routeId} shapeVotes -- route_id 기준 라벨 집계(상위 5개) 조회 경로.
CREATE INDEX idx_route_shape_votes_route_id ON route_shape_votes (route_id);
