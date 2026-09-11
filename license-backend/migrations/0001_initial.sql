PRAGMA foreign_keys = ON;

CREATE TABLE licenses (
    id TEXT PRIMARY KEY NOT NULL,
    license_key_hash TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL CHECK (status IN ('NEW', 'ACTIVE', 'REVOKED', 'EXPIRED')),
    max_devices INTEGER NOT NULL CHECK (max_devices > 0),
    created_at INTEGER NOT NULL CHECK (created_at >= 0),
    expires_at INTEGER CHECK (expires_at IS NULL OR expires_at >= created_at),
    activated_at INTEGER CHECK (activated_at IS NULL OR activated_at >= created_at),
    last_check_at INTEGER CHECK (last_check_at IS NULL OR last_check_at >= created_at)
);

CREATE TABLE devices (
    id TEXT PRIMARY KEY NOT NULL,
    license_id TEXT NOT NULL,
    installation_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    public_key TEXT NOT NULL,
    key_algorithm TEXT NOT NULL,
    created_at INTEGER NOT NULL CHECK (created_at >= 0),
    last_seen_at INTEGER NOT NULL CHECK (last_seen_at >= created_at),
    revoked_at INTEGER CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    FOREIGN KEY (license_id) REFERENCES licenses(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    UNIQUE (license_id, installation_id)
);

CREATE INDEX idx_devices_license_id ON devices(license_id);
CREATE INDEX idx_devices_installation_id ON devices(installation_id);

CREATE TABLE license_events (
    id TEXT PRIMARY KEY NOT NULL,
    license_id TEXT NOT NULL,
    device_id TEXT,
    event_type TEXT NOT NULL,
    created_at INTEGER NOT NULL CHECK (created_at >= 0),
    metadata TEXT,
    FOREIGN KEY (license_id) REFERENCES licenses(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_license_events_license_id ON license_events(license_id, created_at);

CREATE TABLE license_challenges (
    id TEXT PRIMARY KEY NOT NULL,
    license_id TEXT,
    device_id TEXT,
    nonce_hash TEXT NOT NULL UNIQUE,
    purpose TEXT NOT NULL,
    created_at INTEGER NOT NULL CHECK (created_at >= 0),
    expires_at INTEGER NOT NULL CHECK (expires_at > created_at),
    consumed_at INTEGER CHECK (consumed_at IS NULL OR consumed_at >= created_at),
    FOREIGN KEY (license_id) REFERENCES licenses(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_license_challenges_lookup ON license_challenges(nonce_hash, purpose);
CREATE INDEX idx_license_challenges_expiry ON license_challenges(expires_at, consumed_at);
