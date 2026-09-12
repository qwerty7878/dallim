-- 소셜 세션 2단계 -- S-38 세션 종료 후 평가 / S-39 Running Mate. docs/02-api-spec.md 17장 이어서.

CREATE TABLE social_session_feedbacks (
    session_id             VARCHAR(32) NOT NULL REFERENCES social_sessions (id),
    from_user_id           VARCHAR(32) NOT NULL REFERENCES users (id),
    to_user_id             VARCHAR(32) NOT NULL REFERENCES users (id),

    tags                   VARCHAR(200),                    -- 콤마 구분, 최대 3개, 미선택은 NULL
    wants_to_run_again     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 재제출은 이 PK로 update(덮어쓰기) -- 별도 유니크 제약이 필요 없다.
    PRIMARY KEY (session_id, from_user_id, to_user_id)
);

-- GET .../feedback-targets / mate 매칭 판단(반대 방향 피드백 조회)에서 쓰임.
CREATE INDEX idx_social_session_feedbacks_session_to ON social_session_feedbacks (session_id, to_user_id);

-- S-39 Running Mate -- 세션에서 서로 "다시 같이 뛰고 싶어요"를 선택한 두 사용자가 성립한다.
-- user_id_a < user_id_b로 항상 정렬 저장해 (a,b)/(b,a) 중복을 막는다(서비스 계층에서 정렬 후
-- 저장, DB CHECK로 이중 방어).
CREATE TABLE running_mates (
    user_id_a               VARCHAR(32) NOT NULL REFERENCES users (id),
    user_id_b               VARCHAR(32) NOT NULL REFERENCES users (id),
    run_together_count       INTEGER NOT NULL DEFAULT 1,
    last_run_together_at      TIMESTAMPTZ NOT NULL,

    -- 팔로우가 아닌 상호 동의 기반이라 해제는 조용히 단방향(상대 알림 없음) -- 각자 자기 쪽만 true.
    hidden_by_a               BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_by_b               BOOLEAN NOT NULL DEFAULT FALSE,

    PRIMARY KEY (user_id_a, user_id_b),
    CONSTRAINT chk_running_mates_ordered CHECK (user_id_a < user_id_b)
);

-- GET /users/me/running-mates가 "내가 b인 쪽" 매칭도 찾아야 해서 별도 인덱스가 필요(a는 PK로
-- 이미 커버됨).
CREATE INDEX idx_running_mates_user_id_b ON running_mates (user_id_b);
