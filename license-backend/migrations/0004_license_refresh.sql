CREATE TABLE license_refresh_challenges (
    id TEXT PRIMARY KEY NOT NULL,
    license_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    challenge TEXT NOT NULL,
    nonce_hash TEXT NOT NULL UNIQUE,
    installation_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    package_name TEXT NOT NULL,
    signing_certificate_sha256 TEXT NOT NULL,
    created_at INTEGER NOT NULL CHECK (created_at >= 0),
    expires_at INTEGER NOT NULL CHECK (expires_at > created_at),
    consumed_at INTEGER CHECK (consumed_at IS NULL OR consumed_at >= created_at),
    FOREIGN KEY (license_id) REFERENCES licenses(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE INDEX idx_license_refresh_challenges_state
ON license_refresh_challenges(id, consumed_at, expires_at);

CREATE INDEX idx_license_refresh_challenges_expiry
ON license_refresh_challenges(expires_at, consumed_at);
