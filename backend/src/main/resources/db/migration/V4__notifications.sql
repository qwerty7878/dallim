-- In-app notification center (docs/02-api-spec.md 9장, docs/01-feature-spec.md 1.7 S-46).
-- Phase 1: in-app only. Phone system push (FCM) is a later round and needs no schema here.

CREATE TABLE notifications (
    id               VARCHAR(32) PRIMARY KEY,
    user_id          VARCHAR(32) NOT NULL REFERENCES users (id),

    type             VARCHAR(32) NOT NULL,       -- RUN_COMPLETED (only trigger in phase 1)
    title            VARCHAR(100) NOT NULL,
    body             VARCHAR(255) NOT NULL,
    related_run_id   VARCHAR(32) REFERENCES run_records (id),

    is_read          BOOLEAN NOT NULL DEFAULT FALSE,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- GET /notifications (latest first) / GET /notifications/unread-count, both scoped to user_id.
CREATE INDEX idx_notifications_user_id_created_at ON notifications (user_id, created_at DESC);
