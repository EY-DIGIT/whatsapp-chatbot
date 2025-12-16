-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================
-- audit_logs
-- =====================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(15) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    message TEXT,
    raw_payload JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_phone
    ON audit_logs(phone);

-- =====================================
-- language
-- phone is PRIMARY KEY as per JPA
-- =====================================
CREATE TABLE IF NOT EXISTS language (
    phone VARCHAR(15) PRIMARY KEY,
    language VARCHAR(10) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================
-- grievances
-- =====================================
CREATE TABLE IF NOT EXISTS grievances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grievance_number VARCHAR(100),
    user_request_id VARCHAR(100),
    status VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- =====================================
-- feedbacks
-- grievanceId is String in JPA
-- =====================================
CREATE TABLE IF NOT EXISTS feedbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_request_id VARCHAR,
    rating INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);



CREATE INDEX IF NOT EXISTS idx_user_request_id
    ON feedbacks(user_request_id);

-- =====================================
-- user_request
-- =====================================
CREATE TABLE IF NOT EXISTS user_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(15) NOT NULL,
    request_for VARCHAR(10) NOT NULL,   -- EnumType.STRING (RequestFor)
    request_type VARCHAR(30),  -- EnumType.STRING (RequestType)
    owner_phone VARCHAR(15),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verification_request_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_request_phone
    ON user_request(phone);

CREATE INDEX IF NOT EXISTS idx_user_request_owner_phone
    ON user_request(owner_phone);


-- Create system_config table and seed MESSAGE_LOG_ENABLED flag
CREATE TABLE IF NOT EXISTS system_config (
    config_key VARCHAR(100) PRIMARY KEY,
    enabled BOOLEAN NOT NULL
);

-- Insert the default toggle if not present
INSERT INTO system_config (config_key, enabled)
SELECT 'MESSAGE_LOG_ENABLED', true
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'MESSAGE_LOG_ENABLED');
