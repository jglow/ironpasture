-- Silver layer: processed PMO passages ready for embedding and retrieval
CREATE TABLE silver_pmo_passages (
    id               BIGSERIAL PRIMARY KEY,
    chunk_id         VARCHAR(100)  NOT NULL,
    section          VARCHAR(100),
    item_code        VARCHAR(50),
    passage_text     TEXT          NOT NULL,
    embedding_stored BOOLEAN       DEFAULT FALSE,
    processed_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
