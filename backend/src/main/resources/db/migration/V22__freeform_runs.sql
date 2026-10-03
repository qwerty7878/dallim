-- 자유 러닝(freeform run) — 코스를 먼저 고르지 않고 바로 달리기 시작 → 완주 후 실제 GPS 궤적이
-- 곧 "그림" 결과가 되는 모드(v1.3 문서엔 없던, 사용자 요청으로 신규 편입된 기능. 나이키 런
-- 클럽처럼 코스 선택 없이 바로 시작하는 흐름 참고). CLAUDE.md 2026-09-26 결정 참고.
--
-- run_records.route_id는 지금까지 NOT NULL(모든 러닝이 사전에 고른 sketch_route를 목표로
-- 했음)이었으나, 자유 러닝은 목표 코스가 없으므로 NULL을 허용한다. NULL이면
-- com.dallim.run.RunJudgementService.judgeFreeform이 적용된다(구간 커버리지/Sketch Match 없이
-- 거리·시간·페이스·비정상속도만 판정).
ALTER TABLE run_records ALTER COLUMN route_id DROP NOT NULL;

-- 자유 러닝을 완주한 뒤 "이 경로를 코스로 등록"하면 그 러너의 실제 궤적(run_records.actual_path)을
-- 그대로 새 sketch_routes 행의 path로 복사해 만든다. 이번 라운드는 v1.3 PART 4.2가 전제하는 UGC
-- 모더레이션(금칙어 필터, 도로 안전 필터, 완주 전까지 비공개)을 전혀 구현하지 않고 필터 없이
-- 즉시 공개 등록한다 — 사용자 결정(실유저 없는 1인 개발 단계, 필터는 나중에 필요해지면 추가).
ALTER TABLE sketch_routes ADD COLUMN created_by_user_id VARCHAR(32) NULL REFERENCES users (id);
-- 어느 러닝에서 만들어진 코스인지 — "이미 이 러닝을 코스로 등록했는지" 중복 등록 방지 체크에 쓴다
-- (com.dallim.route.RouteRepository.findRouteIdBySourceRunId). NULL이면 큐레이션 코스.
ALTER TABLE sketch_routes ADD COLUMN source_run_id VARCHAR(32) NULL UNIQUE REFERENCES run_records (id);

CREATE INDEX idx_sketch_routes_created_by_user_id ON sketch_routes (created_by_user_id);
