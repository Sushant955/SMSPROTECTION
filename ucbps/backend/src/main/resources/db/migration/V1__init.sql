CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(254) NOT NULL UNIQUE,
    password_hash     VARCHAR(100) NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    country           VARCHAR(2),
    reporting_consent BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE threat_indicators (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    number_hash VARCHAR(64)      NOT NULL,
    country     VARCHAR(2)       NOT NULL,
    type        VARCHAR(20)      NOT NULL,
    risk_score  DOUBLE PRECISION NOT NULL,
    time_bucket TIMESTAMPTZ      NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_indicators_hash ON threat_indicators(number_hash);
CREATE INDEX idx_indicators_user ON threat_indicators(user_id);
CREATE INDEX idx_indicators_created ON threat_indicators(created_at DESC);

CREATE TABLE number_reputation (
    number_hash       VARCHAR(64) PRIMARY KEY,
    report_count      INTEGER          NOT NULL,
    distinct_reporters INTEGER         NOT NULL,
    avg_risk          DOUBLE PRECISION NOT NULL,
    score             DOUBLE PRECISION NOT NULL,
    flagged           BOOLEAN          NOT NULL,
    updated_at        TIMESTAMPTZ      NOT NULL
);
CREATE INDEX idx_reputation_flagged ON number_reputation(flagged, updated_at);
