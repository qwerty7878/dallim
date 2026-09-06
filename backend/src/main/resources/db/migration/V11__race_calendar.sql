-- 대회 캘린더(Race Calendar) — docs/02-api-spec.md 16장,
-- docs/달림_화면별_상세기획서_v1.3.md S-80(대회 캘린더)/S-81(대회 상세) 근거.
-- 2026-09-07: docs/01-feature-spec.md 1.9/15.7이 유보해둔 부분을 채우는 라운드.
--
-- 주의: 이 도메인(com.dallim.race)은 com.dallim.racerecord(내가 과거에 뛴 대회의 완주
-- 이력/메달 선반, race_records 테이블)와 완전히 별개다 — 이쪽은 "앞으로 열릴 대회 정보를
-- 찾아보고 담아두는" 기능이다. 테이블명도 겹치지 않는다.
--
-- 접수 상태(UPCOMING/OPEN/CLOSED)는 컬럼으로 저장하지 않는다 — com.dallim.race.RaceService가
-- 매 조회 시 현재 시각과 registration_start/end를 비교해 계산한다(배치/스케줄러 불필요).

CREATE TABLE races (
    id                   VARCHAR(32) PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    region               VARCHAR(50) NOT NULL,   -- 자유 문자열, 예: "서울", "성남" — GET /races?region= 매칭 기준
    location             VARCHAR(200) NOT NULL,  -- 집결 장소
    race_date            TIMESTAMPTZ NOT NULL,   -- 대회 일시
    registration_start   TIMESTAMPTZ NOT NULL,
    registration_end     TIMESTAMPTZ NOT NULL,
    organizer            VARCHAR(100) NOT NULL,
    souvenir             VARCHAR(200),           -- 기념품, 선택

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 대회 하나에 여러 종목(5K/10K/하프/풀/울트라/트레일)이 있을 수 있고, 종목마다
-- 참가비/정원/컷오프가 다를 수 있어 자식 테이블로 모델링한다.
CREATE TABLE race_category_options (
    id               VARCHAR(32) PRIMARY KEY,
    race_id          VARCHAR(32) NOT NULL REFERENCES races (id),
    category         VARCHAR(16) NOT NULL,   -- FIVE_K | TEN_K | HALF | FULL | ULTRA | TRAIL
    distance_km      DOUBLE PRECISION,       -- ULTRA/TRAIL처럼 대회마다 거리가 제각각이면 null 허용
    fee_krw          INTEGER,
    capacity         INTEGER,
    cutoff_minutes   INTEGER
);

-- User <-> Race 담기(bookmark) — com.dallim.user의 saved_routes와 동일한 패턴이지만 대회용
-- 별도 테이블. "달림 러너 N명 참가 예정"은 이 테이블의 row 수로 대체한다(실제 참가 여부
-- 검증은 하지 않음 — 작업 브리핑 지시).
CREATE TABLE race_saves (
    id           VARCHAR(32) PRIMARY KEY,
    user_id      VARCHAR(32) NOT NULL REFERENCES users (id),
    race_id      VARCHAR(32) NOT NULL REFERENCES races (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_race_saves_user_race UNIQUE (user_id, race_id)
);

-- GET /races 목록: region 부분 일치 필터가 이 인덱스 없이도 순차 스캔에 걸리기엔 대회 수가
-- 적지만("연간 대회 수가 관리 가능한 규모", 수동 큐레이션 원칙), 접수 마감 임박순 정렬에는
-- 도움이 된다.
CREATE INDEX idx_races_registration_end ON races (registration_end);
CREATE INDEX idx_race_category_options_race_id ON race_category_options (race_id);
CREATE INDEX idx_race_category_options_category ON race_category_options (category);
CREATE INDEX idx_race_saves_race_id ON race_saves (race_id);
CREATE INDEX idx_race_saves_user_id ON race_saves (user_id);
