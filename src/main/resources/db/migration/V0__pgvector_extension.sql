-- Enable pgvector extension for embedding storage
-- Must run before any tables that depend on vector types
CREATE EXTENSION IF NOT EXISTS vector;
