CREATE TABLE fulfillments (
    fulfillment_id TEXT PRIMARY KEY NOT NULL,
    license_id TEXT NOT NULL,
    order_reference TEXT NOT NULL,
    customer_reference TEXT,
    status TEXT NOT NULL CHECK (status IN ('LICENSE_CREATED', 'DELIVERED', 'ACTIVE', 'REPLACED', 'REVOKED', 'CANCELLED')),
    delivery_status TEXT NOT NULL CHECK (delivery_status IN ('PENDING', 'DELIVERED')),
    max_devices INTEGER NOT NULL CHECK (max_devices >= 1),
    expires_at INTEGER,
    initial_release_version TEXT NOT NULL,
    initial_release_version_code INTEGER NOT NULL,
    initial_apk_url TEXT NOT NULL,
    apk_sha256 TEXT NOT NULL,
    apk_size INTEGER NOT NULL,
    signing_certificate_sha256 TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    replaces_license_id TEXT,
    replaced_by_license_id TEXT
);

CREATE INDEX idx_fulfillments_order_reference ON fulfillments(order_reference);
CREATE INDEX idx_fulfillments_license_id ON fulfillments(license_id);
CREATE INDEX idx_fulfillments_customer_reference ON fulfillments(customer_reference);
CREATE INDEX idx_fulfillments_updated_at ON fulfillments(updated_at DESC);

CREATE TABLE fulfillment_events (
    event_id TEXT PRIMARY KEY NOT NULL,
    fulfillment_id TEXT NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN ('LICENSE_CREATED', 'DELIVERED', 'DEVICE_RESET', 'LICENSE_REVOKED', 'LICENSE_REPLACED')),
    occurred_at TEXT NOT NULL,
    operator_identity TEXT NOT NULL,
    request_id TEXT,
    device_id TEXT,
    related_license_id TEXT,
    FOREIGN KEY (fulfillment_id) REFERENCES fulfillments(fulfillment_id) ON DELETE RESTRICT
);

CREATE INDEX idx_fulfillment_events_fulfillment ON fulfillment_events(fulfillment_id, occurred_at);
