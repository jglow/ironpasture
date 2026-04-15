-- Gold layer is append-only. No UPDATE or DELETE operations permitted.
-- NCIMS inspection reports generated from enriched sensor data and LLM analysis
CREATE TABLE gold_ncims_reports (
    id                          BIGSERIAL PRIMARY KEY,
    facility_name               VARCHAR(200),
    plant_id                    VARCHAR(50)       NOT NULL,
    batch_id                    VARCHAR(100)      NOT NULL,
    inspection_date             DATE              NOT NULL,
    inspector_id                VARCHAR(50),
    regulatory_authority        VARCHAR(100)      DEFAULT 'NCIMS',
    item16p_pasteurization_temp JSONB,
    item16p_hold_time           JSONB,
    item7r_scc                  JSONB,
    item7p_spc                  JSONB,
    item7p_coliform             JSONB,
    item16p_phosphatase         JSONB,
    cooler_temp_compliance      JSONB,
    overall_disposition         VARCHAR(30)       NOT NULL,
    llm_draft_narrative         TEXT,
    pmo_passages_referenced     JSONB,
    model_used                  VARCHAR(100),
    prompt_version              VARCHAR(50),
    silver_enrichment_id        BIGINT            NOT NULL,
    audit_timestamp             TIMESTAMP         DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_disposition
        CHECK (overall_disposition IN ('COMPLIANT', 'NON_COMPLIANT', 'REQUIRES_REVIEW'))
);
