CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    recipient VARCHAR(512) NOT NULL,
    subject VARCHAR(1024),
    body TEXT NOT NULL,
    provider_message_id VARCHAR(255),
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
CREATE INDEX idx_notifications_status_created_at ON notifications (status, created_at DESC);
