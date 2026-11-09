-- Additive migration. Apply explicitly to the target database; never runs automatically.
CREATE TABLE IF NOT EXISTS agent_conversation (
    id UUID PRIMARY KEY,
    owner_name VARCHAR(255) NOT NULL,
    history_json TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_conversation_owner ON agent_conversation(owner_name);
