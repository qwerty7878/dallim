-- 소셜 세션 2단계 -- S-36 GPS 체크인 / S-37 Ready Check. docs/02-api-spec.md 17장 이어서.

CREATE TABLE social_session_checkins (
    session_id          VARCHAR(32) NOT NULL REFERENCES social_sessions (id),
    user_id              VARCHAR(32) NOT NULL REFERENCES users (id),

    -- WAITING | CHECKED_IN | LATE | NO_SHOW
    status                VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    checked_in_at         TIMESTAMPTZ,
    distance_error_m      DOUBLE PRECISION,
    manual_by_host        BOOLEAN NOT NULL DEFAULT FALSE,

    -- 호스트도 이 테이블에 별도 행으로 들어갈 수 있다(social_session_applicants엔 호스트가
    -- 없어서, Ready Check 목록에서 호스트 자신의 체크인 상태도 자연스럽게 보이게 하려면 필요).
    PRIMARY KEY (session_id, user_id)
);

-- GET /social-sessions/{id}/ready-check, POST /start의 일괄 NO_SHOW 전환에서 쓰임.
CREATE INDEX idx_social_session_checkins_session_status ON social_session_checkins (session_id, status);
