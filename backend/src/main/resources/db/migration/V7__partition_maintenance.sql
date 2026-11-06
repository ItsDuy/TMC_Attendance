-- V7: monthly partition provisioning (ADR-013)

CREATE OR REPLACE FUNCTION ensure_attendance_partition(p_month DATE)
    RETURNS VOID AS
$$
DECLARE
    v_start DATE := date_trunc('month', p_month)::date;
    v_end   DATE := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
    v_name  TEXT := 'attendance_records_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relkind = 'r' AND relname = v_name) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF attendance_records FOR VALUES FROM (%L) TO (%L)',
            v_name, v_start, v_end);
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT ensure_attendance_partition((CURRENT_DATE - INTERVAL '1 month')::date);
SELECT ensure_attendance_partition(CURRENT_DATE);
SELECT ensure_attendance_partition((CURRENT_DATE + INTERVAL '1 month')::date);
