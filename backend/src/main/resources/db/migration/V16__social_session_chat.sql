-- 소셜 세션 2단계(S-35~S-39) 중 S-35 팀 채팅 -- docs/02-api-spec.md 17장 이어서.
-- com.dallim.meetup과 무관, com.dallim.social(1단계) 위에 얹는다.

-- S-37(/start)에서 세팅됨. 이 앱엔 별도의 명시적 "세션 종료" 액션이 없어서, 채팅 생명주기
-- (+24h 읽기전용, +7일 접근종료)를 이 필드 기준으로 근사한다 -- 합리적 단순화, 배치 없음.
ALTER TABLE social_sessions ADD COLUMN started_at TIMESTAMPTZ;

CREATE TABLE social_session_chat_messages (
    id                VARCHAR(32) PRIMARY KEY,
    session_id        VARCHAR(32) NOT NULL REFERENCES social_sessions (id),
    -- NULL = 시스템 메시지(예: 취소 사유 안내). 호스트 공지는 sender_user_id가 채워진 채
    -- type=HOST_ANNOUNCEMENT로 저장된다.
    sender_user_id    VARCHAR(32) REFERENCES users (id),

    -- TEXT | QUICK_MESSAGE | HOST_ANNOUNCEMENT | SYSTEM
    type              VARCHAR(20) NOT NULL,
    body              TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GET .../chat/messages 페이지네이션(최신 순), WebSocket 재연결 시 과거 메시지 불러오기용.
CREATE INDEX idx_social_session_chat_messages_session_created ON social_session_chat_messages (session_id, created_at DESC);

-- 범용 신고 테이블 -- 채팅 메시지 신고(S-35)와 세션 자체 신고(S-32 "[신고]" 액션, 1단계에서
-- SPEC에 있었지만 안 만들어졌던 gap을 이번에 같이 닫는다) 둘 다 이 테이블 하나로 받는다.
-- 자동 조치 없음(기록만) -- 모더레이션 큐는 이번 범위 밖.
CREATE TABLE content_reports (
    id                  VARCHAR(32) PRIMARY KEY,
    reporter_user_id    VARCHAR(32) NOT NULL REFERENCES users (id),
    -- CHAT_MESSAGE | SOCIAL_SESSION
    target_type         VARCHAR(20) NOT NULL,
    target_id           VARCHAR(64) NOT NULL,
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_reports_target ON content_reports (target_type, target_id);
