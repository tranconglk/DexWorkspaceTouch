ALTER TABLE license_challenges ADD COLUMN challenge TEXT;
ALTER TABLE license_challenges ADD COLUMN installation_id TEXT;
ALTER TABLE license_challenges ADD COLUMN device_hash TEXT;
ALTER TABLE license_challenges ADD COLUMN public_key TEXT;
ALTER TABLE license_challenges ADD COLUMN package_name TEXT;
ALTER TABLE license_challenges ADD COLUMN version_name TEXT;
ALTER TABLE license_challenges ADD COLUMN version_code INTEGER;
ALTER TABLE license_challenges ADD COLUMN signing_certificate_sha256 TEXT;

CREATE INDEX idx_license_challenges_id_state
ON license_challenges(id, consumed_at, expires_at);
