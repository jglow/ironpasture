-- Silver layer: sensor readings enriched with threshold evaluation and PMO passage matching
CREATE TABLE silver_sensor_enriched (
    id                        BIGSERIAL PRIMARY KEY,
    bronze_reading_id         BIGINT            NOT NULL,
    plant_id                  VARCHAR(50)       NOT NULL,
    batch_id                  VARCHAR(100)      NOT NULL,
    reading_timestamp         TIMESTAMP         NOT NULL,
    pasteurization_temp_f     DOUBLE PRECISION,
    htst_hold_time_seconds    DOUBLE PRECISION,
    raw_milk_somatic_cell_count INTEGER,
    processed_milk_spc        INTEGER,
    coliform_count            INTEGER,
    ph                        DOUBLE PRECISION,
    cooler_temp_f             DOUBLE PRECISION,
    phosphatase_test          VARCHAR(20),
    operator_id               VARCHAR(50),
    threshold_status          JSONB             NOT NULL,
    matched_pmo_passages      TEXT,
    enriched_at               TIMESTAMP         DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bronze_reading
        FOREIGN KEY (bronze_reading_id)
        REFERENCES bronze_sensor_readings (id)
);
