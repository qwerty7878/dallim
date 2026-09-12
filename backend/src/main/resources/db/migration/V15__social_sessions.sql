-- 소셜 세션 1단계 (docs/02-api-spec.md 17장, docs/01-feature-spec.md 1.11, v1.3 기획서
-- S-30~S-34). com.dallim.meetup(같이 달리기 모집)과 완전히 별개인 새 도메인 -- 재사용하지 않음.

-- 매너온도류 신뢰도 점수 (당근마켓 매너온도 컨셉), 36.5부터 시작. gender와 달리 공개 정보 --
-- 호스트/참가자 카드에 그대로 노출한다. 조정 로직(피드백/노쇼 반영)은 2단계(S-38)에서 만든다.
ALTER TABLE users ADD COLUMN running_temperature DOUBLE PRECISION NOT NULL DEFAULT 36.5;

CREATE TABLE social_sessions (
    id                          VARCHAR(32) PRIMARY KEY,
    host_user_id                VARCHAR(32) NOT NULL REFERENCES users (id),
    route_id                    VARCHAR(32) NOT NULL REFERENCES sketch_routes (id),

    title                       VARCHAR(60) NOT NULL,
    scheduled_at                TIMESTAMPTZ NOT NULL,

    min_participants            INTEGER NOT NULL,
    max_participants            INTEGER NOT NULL,

    -- 콤마 구분 자유 텍스트 (S-31 "러닝 스타일 4종") -- 정확한 값 목록이 SPEC에 없어 닫힌 값
    -- CHECK 제약을 걸지 않는다. com.dallim.social.SocialSessionTable 주석 참고.
    running_styles               VARCHAR(200),

    beginner_friendly           BOOLEAN NOT NULL DEFAULT FALSE,
    min_running_temperature     DOUBLE PRECISION,               -- NULL = 온도 조건 없음

    -- ANY | SAME_AS_HOST | FEMALE_ONLY | MALE_ONLY
    gender_condition            VARCHAR(16) NOT NULL DEFAULT 'ANY',

    description                 TEXT,

    meeting_point_lat           DOUBLE PRECISION NOT NULL,
    meeting_point_lng           DOUBLE PRECISION NOT NULL,
    meeting_point_description   TEXT,

    -- PROCEED | CANCEL | DECIDE_LATER
    rain_policy                 VARCHAR(16) NOT NULL DEFAULT 'DECIDE_LATER',

    -- RECRUITING | CANCELLED만 저장 -- NEAR_CONFIRMATION/CONFIRMED는 조회 시점에 계산 (com.dallim
    -- .meetup.MeetupTable과 동일 철학, 배치 잡 없음).
    status                      VARCHAR(16) NOT NULL DEFAULT 'RECRUITING',

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GET /social-sessions?routeId=... / 전체 목록 -- 다가오는 일정 순.
CREATE INDEX idx_social_sessions_scheduled_at ON social_sessions (scheduled_at ASC);
CREATE INDEX idx_social_sessions_route_id ON social_sessions (route_id, scheduled_at ASC);

-- 참가 신청자 -- 호스트는 여기 없다 (호스트는 social_sessions.host_user_id로 식별, 참가자가
-- 아니라 주최자라서 승인 리스트에 안 보여야 함, S-34).
CREATE TABLE social_session_applicants (
    session_id      VARCHAR(32) NOT NULL REFERENCES social_sessions (id),
    user_id         VARCHAR(32) NOT NULL REFERENCES users (id),

    -- PENDING | APPROVED | EXPIRED | CANCELLED
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    message         TEXT,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at    TIMESTAMPTZ,

    -- 복합 PK 자체가 "동일 세션 중복 신청 방지" 유니크 제약.
    PRIMARY KEY (session_id, user_id)
);

-- GET /social-sessions/{id}/applicants (호스트 조회), 정원 계산(APPROVED count)에 쓰임.
CREATE INDEX idx_social_session_applicants_session_status ON social_session_applicants (session_id, status);
