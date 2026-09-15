-- 유저 차단 (사용자 지시로 신규 도입, docs/02-api-spec.md 18장). 스코프는 "채팅 메시지 발신자
-- 차단"으로 좁힌다 -- 세션 신청/매칭 등 다른 곳에 차단 효과를 전파하지 않는다(이번 라운드 범위
-- 밖). 서버는 이 테이블을 CRUD로만 노출하고 메시지를 직접 필터링하지 않는다 -- 안드로이드가
-- GET /users/me/blocks 결과로 채팅 화면에서 발신자를 스스로 가려낸다(채팅 히스토리/WebSocket
-- 응답 스키마는 전혀 건드리지 않는다).
CREATE TABLE blocked_users (
    blocker_user_id VARCHAR(32) NOT NULL REFERENCES users (id),
    blocked_user_id VARCHAR(32) NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_user_id, blocked_user_id)
);
