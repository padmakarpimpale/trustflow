-- Historical cases remain NULL: their previous policy values cannot be reconstructed.
ALTER TABLE case_record ADD COLUMN policy_amount_threshold DECIMAL(12,2),
    ADD COLUMN policy_min_confidence DOUBLE,
    ADD COLUMN policy_always_review BIT,
    ADD COLUMN routing_version VARCHAR(80);
CREATE TABLE policy_change (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    actor VARCHAR(80) NOT NULL,
    before_values VARCHAR(1000) NOT NULL,
    after_values VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_case_owner_created ON case_record(owner_id, created_at, id);
CREATE INDEX idx_case_status_created ON case_record(status, created_at, id);
CREATE INDEX idx_outbox_pending ON outbox_event(published, id);
CREATE INDEX idx_audit_case_created ON audit_entry(case_id, created_at);
CREATE INDEX idx_notice_recipient_created ON notification(recipient_id, created_at);
CREATE INDEX idx_policy_history ON policy_change(policy_id, id);
