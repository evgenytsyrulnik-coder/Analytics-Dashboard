-- Organizations
CREATE TABLE organizations (
    id              UUID PRIMARY KEY,
    external_id     VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Teams
CREATE TABLE teams (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL REFERENCES organizations(id),
    external_id     VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(org_id, external_id)
);

CREATE INDEX idx_teams_org_id ON teams(org_id);

-- Users (with password for simple auth)
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL REFERENCES organizations(id),
    external_id     VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(org_id, external_id)
);

CREATE INDEX idx_users_org_id ON users(org_id);
CREATE INDEX idx_users_email ON users(email);

-- User-Teams join table
CREATE TABLE user_teams (
    user_id         UUID NOT NULL REFERENCES users(id),
    team_id         UUID NOT NULL REFERENCES teams(id),
    PRIMARY KEY (user_id, team_id)
);

CREATE INDEX idx_user_teams_team_id ON user_teams(team_id);

-- Agent types
CREATE TABLE agent_types (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL REFERENCES organizations(id),
    slug            VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(org_id, slug)
);

CREATE INDEX idx_agent_types_org_id ON agent_types(org_id);

-- Agent runs
CREATE TABLE agent_runs (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL REFERENCES organizations(id),
    team_id         UUID REFERENCES teams(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    agent_type_slug VARCHAR(100) NOT NULL,
    model_name      VARCHAR(100),
    model_version   VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at     TIMESTAMP WITH TIME ZONE,
    duration_ms     BIGINT,
    input_tokens    BIGINT NOT NULL DEFAULT 0,
    output_tokens   BIGINT NOT NULL DEFAULT 0,
    total_tokens    BIGINT NOT NULL DEFAULT 0,
    input_cost      DECIMAL(18,6) NOT NULL DEFAULT 0,
    output_cost     DECIMAL(18,6) NOT NULL DEFAULT 0,
    total_cost      DECIMAL(18,6) NOT NULL DEFAULT 0,
    error_category  VARCHAR(100),
    error_message   CLOB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_runs_org_started ON agent_runs(org_id, started_at);
CREATE INDEX idx_agent_runs_user_started ON agent_runs(user_id, started_at);
CREATE INDEX idx_agent_runs_team_started ON agent_runs(team_id, started_at);
CREATE INDEX idx_agent_runs_status ON agent_runs(status);

-- Budgets
CREATE TABLE budgets (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL REFERENCES organizations(id),
    scope                   VARCHAR(20) NOT NULL,
    scope_id                UUID NOT NULL,
    monthly_limit           DECIMAL(18,6) NOT NULL,
    thresholds              CLOB NOT NULL DEFAULT '[0.5, 0.8, 1.0]',
    notification_channels   CLOB NOT NULL DEFAULT '["IN_APP", "EMAIL"]',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(org_id, scope, scope_id)
);

CREATE INDEX idx_budgets_org_id ON budgets(org_id);

-- Budget notifications
CREATE TABLE budget_notifications (
    id              UUID PRIMARY KEY,
    budget_id       UUID NOT NULL REFERENCES budgets(id),
    threshold       DECIMAL(5,2) NOT NULL,
    "month"         DATE NOT NULL,
    notified_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(budget_id, threshold, "month")
);

-- Exports
CREATE TABLE exports (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL REFERENCES organizations(id),
    requested_by    UUID NOT NULL REFERENCES users(id),
    report_type     VARCHAR(50) NOT NULL,
    filters         CLOB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    file_path       VARCHAR(500),
    row_count       INTEGER,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_exports_org_id ON exports(org_id);
