ALTER TABLE licenses ADD COLUMN updated_at INTEGER;
ALTER TABLE licenses ADD COLUMN revoked_at INTEGER;

UPDATE licenses SET updated_at = created_at WHERE updated_at IS NULL;

CREATE UNIQUE INDEX idx_license_events_created_once
ON license_events(license_id, event_type)
WHERE event_type = 'LICENSE_CREATED';

CREATE UNIQUE INDEX idx_license_events_revoked_once
ON license_events(license_id, event_type)
WHERE event_type = 'LICENSE_REVOKED';

CREATE UNIQUE INDEX idx_license_events_device_reset_once
ON license_events(device_id, event_type)
WHERE event_type = 'DEVICE_RESET';
