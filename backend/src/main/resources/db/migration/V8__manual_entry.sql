-- Manual entry by a security guard (human fallback when a phone or kiosk is down).
-- A manual entry has no kiosk, and must say who entered it.
ALTER TABLE attendance_records ALTER COLUMN kiosk_id DROP NOT NULL;
ALTER TABLE attendance_records ADD COLUMN entered_by VARCHAR(64);

COMMENT ON COLUMN attendance_records.entered_by IS 'Guard username for source = MANUAL, else NULL';
