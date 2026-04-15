-- Bronze layer: chunked PMO regulatory text for RAG retrieval
CREATE TABLE bronze_pmo_chunks (
    id           BIGSERIAL PRIMARY KEY,
    chunk_id     VARCHAR(100)  UNIQUE NOT NULL,
    section      VARCHAR(100),
    item_code    VARCHAR(50),
    chunk_text   TEXT          NOT NULL,
    ingested_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
