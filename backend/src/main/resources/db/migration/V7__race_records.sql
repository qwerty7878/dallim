-- 러닝 커리어(완주 이력) — docs/02-api-spec.md 15장,
-- docs/달림_화면별_상세기획서_v1.3.md S-04b/S-82/S-83 (2026-09-06 SPEC 편입).
-- 자기신고 이력 -- verified는 이번 라운드에서 항상 false, 인증 승격 경로(S-84)는 범위 밖.
-- see com.dallim.racerecord.RaceRecord for the domain model this backs.

CREATE TABLE race_records (
    id               VARCHAR(32) PRIMARY KEY,
    user_id          VARCHAR(32) NOT NULL REFERENCES users (id),

    race_name        VARCHAR(100) NOT NULL,
    -- FIVE_K | TEN_K | HALF | FULL | ULTRA | TRAIL | OTHER (Exposed enumerationByName -- 와이어
    -- 표현("5K"/"10K")과 다르다, com.dallim.racerecord.RaceRecordService.parseCategory 참고).
    category         VARCHAR(16) NOT NULL,

    -- OTHER는 필수(양수), 5K/10K/HALF/FULL은 카테고리 기준 고정값을 명시적으로 저장(클라이언트가
    -- 다른 값을 보내면 그 값을 신뢰), ULTRA/TRAIL은 대회마다 달라 고정값이 없어 null 허용.
    distance_km      DOUBLE PRECISION,

    year             INTEGER NOT NULL,          -- 1990 ~ 현재연도+1, RaceRecordService에서 검증
    record_seconds   INTEGER,                   -- hh:mm:ss를 초 단위로 저장, 선택 입력
    record_type      VARCHAR(16),               -- NET | GROSS, 선택 입력
    bib_number       VARCHAR(32),
    memo             TEXT,

    -- 자기신고 전용 -- 이번 라운드에 인증 승격 경로가 없어 항상 false로 저장된다(S-84는 범위 밖).
    verified         BOOLEAN NOT NULL DEFAULT FALSE,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GET /users/me/race-records -- 유저별 전체 이력 조회(연도 내림차순) + PB 계산이 매번 유저의
-- 전체 이력을 훑으므로 user_id 인덱스만으로 충분하다.
CREATE INDEX idx_race_records_user_id ON race_records (user_id);
