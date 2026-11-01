-- Append-only, hash-chained audit trail (see ARCHITECTURE.md, "Observability & Audit").
-- Dev bootstrap only: in a real environment the DBA owns this DDL and the secrets.

CREATE TABLE audit_event (
    seq         bigserial   PRIMARY KEY,
    id          uuid        NOT NULL UNIQUE,
    ts          timestamptz NOT NULL,
    service     text        NOT NULL,
    event_type  text        NOT NULL,
    trace_id    text,
    span_id     text,
    actor       text        NOT NULL,
    resource    text        NOT NULL,
    outcome     text        NOT NULL,
    duration_ms bigint      NOT NULL,
    payload     text        NOT NULL,   -- JSON text, kept verbatim so the hash can be re-verified (cast: payload::jsonb)
    prev_hash   text        NOT NULL,
    hash        text        NOT NULL UNIQUE
);

CREATE INDEX audit_event_trace_idx ON audit_event (trace_id);
CREATE INDEX audit_event_ts_idx    ON audit_event (ts);
CREATE INDEX audit_event_type_idx  ON audit_event (event_type, resource);

-- Append-only, enforced in the database rather than by convention.
CREATE FUNCTION audit_event_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only (% blocked)', TG_OP;
END $$;

CREATE TRIGGER audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_immutable();

-- The services connect as a role that can only insert and read.
CREATE ROLE audit_writer LOGIN PASSWORD 'audit_writer_dev';
GRANT INSERT, SELECT ON audit_event TO audit_writer;
GRANT USAGE, SELECT ON SEQUENCE audit_event_seq_seq TO audit_writer;
