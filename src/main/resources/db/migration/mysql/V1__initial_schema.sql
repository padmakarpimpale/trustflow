CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN','REVIEWER','SUBMITTER') NOT NULL
);
CREATE TABLE review_policy (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT,
    type ENUM('ACCESS','EXPENSE') NOT NULL UNIQUE,
    amount_threshold DECIMAL(12,2) NOT NULL,
    min_confidence DOUBLE NOT NULL,
    always_review BIT NOT NULL
);
CREATE TABLE case_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT,
    owner_id BIGINT NOT NULL,
    type ENUM('ACCESS','EXPENSE') NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    amount DECIMAL(12,2),
    access_level VARCHAR(30),
    status ENUM('APPROVED','PENDING_REVIEW','REJECTED') NOT NULL,
    recommendation ENUM('APPROVE','REJECT','REVIEW') NOT NULL,
    confidence DOUBLE,
    explanation VARCHAR(2000),
    model_version VARCHAR(100),
    escalation_reason VARCHAR(1000),
    policy_version BIGINT,
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6),
    CONSTRAINT fk_case_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);
CREATE TABLE review_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT NOT NULL UNIQUE,
    reviewer_id BIGINT NOT NULL,
    decision ENUM('APPROVED','PENDING_REVIEW','REJECTED') NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    overridden BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_review_case FOREIGN KEY (case_id) REFERENCES case_record(id),
    CONSTRAINT fk_review_user FOREIGN KEY (reviewer_id) REFERENCES app_user(id)
);
CREATE TABLE audit_entry (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    actor VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    detail VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_audit_case FOREIGN KEY (case_id) REFERENCES case_record(id)
);
CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    message VARCHAR(500) NOT NULL,
    read_flag BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_notification_user FOREIGN KEY (recipient_id) REFERENCES app_user(id)
);
CREATE TABLE outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    payload VARCHAR(2000) NOT NULL,
    published BIT NOT NULL,
    created_at DATETIME(6) NOT NULL
);
