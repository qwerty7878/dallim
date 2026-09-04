-- FCM device token registry (docs/02-api-spec.md 10장, phone system push -- 알림 2단계).
-- One row per physical device installation, identified by its FCM registration token; a user can
-- have many rows (multi-device push, docs/02-api-spec.md 10.1). fcm_token is globally unique --
-- re-registering the same token (app relaunch, token refresh no-op) upserts the existing row
-- instead of inserting a duplicate, and also lets a token that moves to a different logged-in
-- account (logout/login on the same device) get reassigned to its new owner.
CREATE TABLE device_tokens (
    id           VARCHAR(32) PRIMARY KEY,
    user_id      VARCHAR(32) NOT NULL REFERENCES users (id),
    fcm_token    VARCHAR(255) NOT NULL,
    platform     VARCHAR(16) NOT NULL, -- ANDROID only for now (docs/02-api-spec.md 10.1)

    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_device_tokens_fcm_token UNIQUE (fcm_token)
);

-- Fan-out lookup used by com.dallim.push.FcmPushService.sendToUser (10.2).
CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);
