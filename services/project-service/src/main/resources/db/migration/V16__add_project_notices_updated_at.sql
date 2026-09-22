ALTER TABLE project_notices ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TRIGGER trg_project_notices_updated_at
    BEFORE UPDATE ON project_notices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
