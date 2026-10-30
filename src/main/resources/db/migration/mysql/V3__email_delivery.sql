-- Preserve existing cases and notification records during this additive upgrade.
ALTER TABLE outbox_event MODIFY COLUMN payload VARCHAR(12000) NOT NULL;
CREATE TABLE email_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    case_id BIGINT NOT NULL,
    username VARCHAR(80) NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    last_error VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6),
    INDEX idx_email_pending (status, next_attempt_at, id)
);
