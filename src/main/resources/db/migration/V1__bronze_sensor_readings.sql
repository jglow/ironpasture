-- Bronze layer: raw sensor readings ingested from dairy plant SCADA/PLC systems
CREATE TABLE bronze_sensor_readings (
    id                        BIGSERIAL PRIMARY KEY,
    plant_id                  VARCHAR(50)       NOT NULL,
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
    batch_id                  VARCHAR(100)      NOT NULL,
    ingested_at               TIMESTAMP         DEFAULT CURRENT_TIMESTAMP
);
